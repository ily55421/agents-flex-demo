package com.agentsflex.showcase.knowledge;

import com.yomahub.roguemap.embedding.UniversalEmbeddingProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yomahub.roguemap.memory.MemoryResult;
import com.yomahub.roguemap.memory.RogueMemory;
import com.yomahub.roguemap.memory.SearchMode;
import com.yomahub.roguemap.memory.SearchOptions;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG 知识库核心服务：RogueMemory 嵌入式向量库 + bge-m3（OpenAI 兼容 embeddings）。
 *
 * <p>每个文档使用独立 namespace（docId），删除文档即 {@code deleteByNamespace}；
 * 检索跨全部 namespace。embedding 模型以“endpoint|model”签名标识，签名变化而库中
 * 已有向量时拒绝继续写入，提示重建，避免不同维度/语义空间混存。
 * KEYWORD_ONLY 模式不依赖 embedding 服务，可在未配置向量模型时演示 BM25 检索。</p>
 */
@Service
public class KnowledgeService {

    private final KnowledgeProperties properties;
    private final KnowledgeDocumentStore store;
    private final KnowledgeSettingsStore settings;
    private final KnowledgeVectorCache vectorCache;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TextChunker chunker = new TextChunker();
    private final AtomicBoolean seeding = new AtomicBoolean();

    private volatile RogueMemory memory;
    private volatile UniversalEmbeddingProvider provider;
    private volatile String signature;
    /** 用户最近一次提交的向量模型配置意图（探测失败也保留，用于状态展示“已配置”）。 */
    private volatile String configuredEndpoint;
    private volatile String configuredModel;
    private volatile SearchMode searchMode = SearchMode.HYBRID;
    private volatile String lastError;
    private volatile boolean seeded;

    /**
     * @param properties  知识库默认配置（mmap 路径、检索模式、TopK、embedding 默认值）
     * @param store       DuckDB 文档元数据存储
     * @param settings    向量模型配置与自建预设的本地持久化
     * @param vectorCache embedding 向量持久化缓存；null 时向量直连不缓存（测试场景）
     */
    @org.springframework.beans.factory.annotation.Autowired
    public KnowledgeService(KnowledgeProperties properties, KnowledgeDocumentStore store,
                            KnowledgeSettingsStore settings, KnowledgeVectorCache vectorCache) {
        this.properties = properties;
        this.store = store;
        this.settings = settings;
        this.vectorCache = vectorCache;
        this.searchMode = parseMode(properties.getSearchMode());
    }

    /** 兼容旧构造：向量不缓存。 */
    public KnowledgeService(KnowledgeProperties properties, KnowledgeDocumentStore store,
                            KnowledgeSettingsStore settings) {
        this(properties, store, settings, null);
    }

    /**
     * 初始化知识库：丢弃上次遗留的 mmap 缓存，以纯关键词（BM25）模式打开索引，
     * 并从 DuckDB 重建已入库文档；随后异步恢复上次保存的向量模型配置。
     *
     * <p>向量模型配置不再要求用户每次重启后手动重填：最近一次应用的配置已持久化，
     * 启动后在后台线程重放 {@link #configure(String, String, String, String)}（含探测与
     * 重建索引），失败自动退化为 BM25 并记录原因，不会阻塞或拖垮启动。</p>
     */
    @jakarta.annotation.PostConstruct
    public void initialize() {
        discardStaleFiles();
        markDirty();
        try {
            openMemory(null);
            rebuildFromStore();
        } catch (RuntimeException error) {
            this.lastError = error.getMessage();
            if (memory == null) openMemory(null);
        }
        restoreSavedEmbeddingAsync();
    }

    /**
     * 后台重放上次保存的向量模型配置：放到独立线程是为了探测外部服务的网络等待
     * 不拖慢应用启动；恢复结果（成功重建索引或失败降级 BM25）通过 status 反映。
     */
    private void restoreSavedEmbeddingAsync() {
        Map<String, String> saved = settings.applied();
        if (saved == null || isBlank(saved.get("endpoint")) || isBlank(saved.get("model"))) return;
        Thread restorer = new Thread(() -> {
            try {
                configure(saved.get("endpoint"), saved.get("apiKey"),
                        saved.get("model"), saved.get("searchMode"));
            } catch (RuntimeException error) {
                this.lastError = "自动恢复向量模型配置失败：" + error.getMessage();
            }
        }, "knowledge-embedding-restore");
        restorer.setDaemon(true);
        restorer.start();
    }

    /** @return 字符串非空（含非空白内容）时为 true */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 应用 UI 或环境变量提供的 embedding 配置。签名未变化时幂等跳过；
     * 已有向量且签名不同则拒绝，要求先重建知识库。
     *
     * <p>即使目标服务探测失败也不抛异常：配置意图会被记录（status 返回
     * embeddingConfigured=true 与模型名），检索自动退化为 BM25，错误原因写入
     * lastError 供前端展示，避免“明明配置了却提示未配置”的割裂体验。</p>
     *
     * @param endpoint  OpenAI 兼容服务根地址（含 /v1），例如 http://127.0.0.1:18888/v1
     * @param apiKey    服务密钥；本地 Ollama 可空
     * @param model     模型名，例如 bge-m3
     * @param mode      检索模式 HYBRID / VECTOR_ONLY / KEYWORD_ONLY，可空
     * @return 是否发生了实际切换
     */
    public synchronized boolean configure(String endpoint, String apiKey, String model, String mode) {
        if (mode != null && !mode.trim().isEmpty()) {
            this.searchMode = parseMode(mode.trim().toUpperCase());
        }
        if (endpoint == null || endpoint.trim().isEmpty() || model == null || model.trim().isEmpty()) {
            // 未提供向量配置：保持 BM25 可用，检索走 KEYWORD_ONLY 语义。
            if (memory == null) openMemory(null);
            return false;
        }
        String normalizedEndpoint = endpoint.trim();
        if (normalizedEndpoint.endsWith("/")) {
            normalizedEndpoint = normalizedEndpoint.substring(0, normalizedEndpoint.length() - 1);
        }
        // 记录配置意图：即使连接失败也让界面显示“已配置”而非“未配置”。
        this.configuredEndpoint = normalizedEndpoint;
        this.configuredModel = model.trim();
        // 持久化配置意图：重启后自动重试同样的连接；探测失败也保存，服务恢复后一键重连。
        settings.saveApplied(normalizedEndpoint, apiKey == null ? "" : apiKey.trim(),
                model.trim(), searchMode.name());
        String newSignature = normalizedEndpoint + "|" + model.trim();
        if (newSignature.equals(signature) && memory != null) {
            ensureSeeded();
            return true;
        }
        if (memory != null && store.count() > 0 && signature != null) {
            throw new IllegalStateException("知识库已有向量数据，切换 embedding 模型前请先重建知识库");
        }
        UniversalEmbeddingProvider newProvider = new UniversalEmbeddingProvider(
                normalizedEndpoint, apiKey == null ? "" : apiKey.trim(), model.trim());
        // 先探测再切换：RogueMemory 构建 HNSW 索引时会调用 embeddings 探测维度，
        // 若服务未实现 /embeddings 会抛出含 HTML 的原始错误，这里提前转成可操作的中文提示。
        try {
            newProvider.embed("embedding connectivity probe");
        } catch (RuntimeException error) {
            // 连接失败：保留“已配置”状态与错误原因，检索自动退化为 BM25，不阻断使用。
            this.lastError = friendlyEmbeddingError(normalizedEndpoint, model.trim(), error);
            this.provider = null;
            this.signature = null;
            if (memory == null) openMemory(null);
            return true;
        }
        closeMemory();
        this.provider = newProvider;
        this.signature = newSignature;
        try {
            openMemory(newProvider);
        } catch (RuntimeException error) {
            // 索引初始化失败时退回关键词模式，保证知识库与页面仍可用。
            this.lastError = friendlyEmbeddingError(normalizedEndpoint, model.trim(), error);
            this.provider = null;
            this.signature = null;
            openMemory(null);
            return true;
        }
        this.lastError = null;
        rebuildFromStore();
        ensureSeeded();
        return true;
    }

    /**
     * 把 embedding 服务的原始异常整理为一句可操作的中文提示。
     * 供应商常返回整页 HTML，直接透传会淹没界面，因此剥离标签并截断。
     *
     * @param endpoint 服务地址
     * @param model    模型名
     * @param error    原始异常
     * @return 不含 HTML 的单行错误说明
     */
    private static String friendlyEmbeddingError(String endpoint, String model, RuntimeException error) {
        String raw = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String text = raw.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (text.length() > 180) text = text.substring(0, 180) + "...";
        String hint = text.contains("404")
                ? "该地址未实现 OpenAI 兼容的 /embeddings 路由，请改用支持向量化的服务（如 Ollama http://localhost:11434/v1）"
                : "请确认服务地址、模型名与密钥可用";
        return "向量模型不可用（" + model + " @ " + endpoint + "）：" + text + "。" + hint + "。";
    }

    /**
     * 添加一篇文本文档：切片后逐块写入 RogueMemory（namespace = docId），并登记 DuckDB 元数据。
     *
     * @param title   文档标题
     * @param content 正文；空内容会被拒绝
     * @param source  来源类型 MANUAL / FILE / BUILTIN
     * @return 新文档的元数据视图
     */
    public synchronized Map<String, Object> addDocument(String title, String content, String source) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        RogueMemory active = requireMemory();
        List<String> chunks = chunker.chunk(content);
        if (chunks.isEmpty()) throw new IllegalArgumentException("文档没有可入库的有效内容");
        String docId = "kb-" + UUID.randomUUID();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("title", title == null || title.trim().isEmpty() ? "未命名文档" : title.trim());
        metadata.put("source", source);
        try {
            for (int index = 0; index < chunks.size(); index++) {
                Map<String, String> chunkMeta = new LinkedHashMap<>(metadata);
                chunkMeta.put("chunkIndex", String.valueOf(index));
                active.add(chunks.get(index), chunkMeta, docId);
            }
        } catch (RuntimeException error) {
            this.lastError = error.getMessage();
            rollbackPartial(active, docId, chunks.size());
            throw error;
        }
        active.checkpoint();
        store.insert(docId, metadata.get("title"), source, chunks.size(),
                content.trim().length(), signature == null ? "KEYWORD_ONLY" : signature, content.trim());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("docId", docId);
        view.put("title", metadata.get("title"));
        view.put("source", source);
        view.put("chunkCount", chunks.size());
        view.put("charCount", (long) content.trim().length());
        view.put("embeddingSignature", signature == null ? "KEYWORD_ONLY" : signature);
        view.put("createdAt", System.currentTimeMillis());
        return view;
    }

    /**
     * 混合检索知识库；namespace 为空时跨全部文档。
     *
     * @param query     查询文本
     * @param topK      返回条数；小于等于 0 时使用配置默认值
     * @param namespace 限定检索的文档 namespace（docId）；null 或 all 表示全部
     * @return 命中片段列表，含内容、标题、来源与分数
     */
    public List<Map<String, Object>> search(String query, int topK, String namespace) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        RogueMemory active = requireMemory();
        int limit = topK > 0 ? topK : properties.getTopK();
        SearchOptions options = (namespace == null || namespace.isBlank()
                || "all".equalsIgnoreCase(namespace))
                ? SearchOptions.builder().build()
                : SearchOptions.builder().namespace(namespace.trim()).build();
        List<MemoryResult> results = active.search(query.trim(), limit, options);
        List<Map<String, Object>> values = new ArrayList<>();
        for (MemoryResult result : results) {
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("docId", result.getNamespace());
            hit.put("title", result.getMetadata() == null ? null : result.getMetadata().get("title"));
            hit.put("source", result.getMetadata() == null ? null : result.getMetadata().get("source"));
            hit.put("chunkIndex", result.getMetadata() == null ? null
                    : result.getMetadata().get("chunkIndex"));
            hit.put("content", result.getContent());
            hit.put("score", result.getScore());
            hit.put("mode", searchMode.name());
            values.add(hit);
        }
        return values;
    }

    /** 兼容旧调用：全库检索。 */
    public List<Map<String, Object>> search(String query, int topK) {
        return search(query, topK, null);
    }

    /**
     * 供 Agent 工具调用的格式化检索：返回带标题、分数与来源的纯文本片段列表；
     * 知识库为空或未配置时返回模型可解释的说明文本而不是抛异常。
     *
     * @param query     检索问题
     * @param namespace 限定文档 namespace；null 或 all 表示全部
     * @return 多行文本；每行一个命中片段
     */
    public String searchForTool(String query, String namespace) {
        if (memory == null) return "知识库尚未初始化。";
        List<Map<String, Object>> hits;
        try {
            hits = search(query, properties.getTopK(), namespace);
        } catch (RuntimeException error) {
            return "知识库检索失败：" + error.getMessage();
        }
        if (hits.isEmpty()) return "知识库中没有找到与问题相关的资料。";
        StringBuilder text = new StringBuilder("知识库检索命中 " + hits.size() + " 条片段：\n");
        for (Map<String, Object> hit : hits) {
            text.append("【").append(hit.get("title")).append(" · 片段")
                    .append(hit.get("chunkIndex")).append(" · 相关度 ")
                    .append(String.format("%.3f", hit.get("score"))).append("】")
                    .append(hit.get("content")).append('\n');
        }
        return text.toString();
    }

    /** 兼容旧调用：全库检索。 */
    public String searchForTool(String query) {
        return searchForTool(query, null);
    }

    /**
     * 导入 JSONL 问答事实库：每行一个 JSON 对象，取 station/category/text 字段；
     * 每条 text 作为独立知识片段写入同一文档 namespace，检索粒度最细。
     *
     * @param title     文档标题（通常是来源文件名）
     * @param jsonlText JSONL 全文；空行与无法解析的行自动跳过
     * @return 新文档元数据视图（chunkCount = 成功导入的条数）
     */
    public synchronized Map<String, Object> addJsonlDocument(String title, String jsonlText) {
        if (jsonlText == null || jsonlText.trim().isEmpty()) {
            throw new IllegalArgumentException("JSONL 内容不能为空");
        }
        RogueMemory active = requireMemory();
        String docId = "kb-" + UUID.randomUUID();
        Map<String, String> docMeta = new LinkedHashMap<>();
        docMeta.put("title", title == null || title.trim().isEmpty() ? "问答事实库" : title.trim());
        docMeta.put("source", "FILE");
        int imported = 0;
        try {
            for (String line : jsonlText.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                Map<String, Object> record = parseJsonLine(trimmed);
                if (record == null) continue;
                Object textValue = record.get("text");
                if (!(textValue instanceof String) || ((String) textValue).isBlank()) continue;
                Map<String, String> chunkMeta = new LinkedHashMap<>(docMeta);
                Object station = record.get("station");
                Object category = record.get("category");
                if (station != null) chunkMeta.put("station", String.valueOf(station));
                if (category != null) chunkMeta.put("category", String.valueOf(category));
                chunkMeta.put("chunkIndex", String.valueOf(imported));
                active.add(((String) textValue).trim(), chunkMeta, docId);
                imported++;
            }
        } catch (RuntimeException error) {
            this.lastError = error.getMessage();
            rollbackPartial(active, docId, imported);
            throw error;
        }
        if (imported == 0) {
            throw new IllegalArgumentException("JSONL 中没有可导入的条目（需要包含 text 字段）");
        }
        active.checkpoint();
        store.insert(docId, docMeta.get("title"), "FILE", imported,
                jsonlText.trim().length(), signature == null ? "KEYWORD_ONLY" : signature, jsonlText.trim());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("docId", docId);
        view.put("title", docMeta.get("title"));
        view.put("source", "FILE");
        view.put("chunkCount", imported);
        view.put("charCount", (long) jsonlText.trim().length());
        view.put("embeddingSignature", signature == null ? "KEYWORD_ONLY" : signature);
        view.put("createdAt", System.currentTimeMillis());
        return view;
    }

    /**
     * 安全解析单行 JSON；解析失败返回 null 而不是中断整批导入。
     *
     * @param line 单行 JSON 文本
     * @return 解析后的映射；失败返回 {@code null}
     */
    private Map<String, Object> parseJsonLine(String line) {
        try {
            return mapper.readValue(line, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception error) {
            return null;
        }
    }

    /**
     * 读取文档原始全文与元数据，供前端预览。
     *
     * @param docId 文档 ID
     * @return 包含 docId / title / content / contentAvailable / chunkCount / charCount / source 的视图；
     *         文档不存在时返回 {@code null}
     */
    public synchronized Map<String, Object> getDocumentContent(String docId) {
        String content = store.loadContent(docId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("docId", docId);
        view.put("title", titleOf(docId));
        view.put("chunkCount", chunkCountOf(docId));
        view.put("source", sourceOf(docId));
        if (content == null || content.isEmpty()) {
            // content 列迁移前入库的旧文档没有保存原始全文，标记为不可预览；
            // 前端提示可粘贴新内容覆盖保存（保存后即写入 content 列）。
            view.put("content", null);
            view.put("contentAvailable", false);
            view.put("charCount", 0L);
        } else {
            view.put("content", content);
            view.put("contentAvailable", true);
            view.put("charCount", (long) content.length());
        }
        return view;
    }

    /**
     * 编辑文档全文：删除旧 namespace 的所有切片，按新文本重新切片并向量化，
     * 更新 DuckDB 元数据与原始全文。修改后检索立即使用新向量。
     *
     * @param docId   文档 ID
     * @param title   新标题（可空，沿用原标题）
     * @param content 新全文；非空
     * @return 更新后的文档元数据视图
     */
    public synchronized Map<String, Object> updateDocument(String docId, String title, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        RogueMemory active = requireMemory();
        String resolvedTitle = title == null || title.trim().isEmpty() ? titleOf(docId) : title.trim();
        List<String> chunks = chunker.chunk(content.trim());
        if (chunks.isEmpty()) throw new IllegalArgumentException("文档没有可入库的有效内容");
        String previousTitle = titleOf(docId);
        String source = sourceOf(docId);
        // 1. 删除旧向量切片，避免新旧内容混存。
        active.deleteByNamespace(docId);
        // 2. 按新内容重新写入切片。
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("title", resolvedTitle);
        metadata.put("source", source == null ? "MANUAL" : source);
        try {
            for (int index = 0; index < chunks.size(); index++) {
                Map<String, String> chunkMeta = new LinkedHashMap<>(metadata);
                chunkMeta.put("chunkIndex", String.valueOf(index));
                active.add(chunks.get(index), chunkMeta, docId);
            }
        } catch (RuntimeException error) {
            this.lastError = error.getMessage();
            throw error;
        }
        active.checkpoint();
        store.update(docId, resolvedTitle, chunks.size(), content.trim().length(),
                signature == null ? "KEYWORD_ONLY" : signature, content.trim());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("docId", docId);
        view.put("title", resolvedTitle);
        view.put("source", source == null ? "MANUAL" : source);
        view.put("chunkCount", chunks.size());
        view.put("charCount", (long) content.trim().length());
        view.put("embeddingSignature", signature == null ? "KEYWORD_ONLY" : signature);
        view.put("createdAt", System.currentTimeMillis());
        view.put("updatedFrom", previousTitle);
        return view;
    }

    /** 从元数据表读取文档标题；不存在返回 docId 本身。 */
    private String titleOf(String docId) {
        for (Map<String, Object> row : store.list()) {
            if (docId.equals(row.get("docId"))) return String.valueOf(row.get("title"));
        }
        return docId;
    }

    /** 从元数据表读取文档来源类型。 */
    private String sourceOf(String docId) {
        for (Map<String, Object> row : store.list()) {
            if (docId.equals(row.get("docId"))) {
                Object source = row.get("source");
                return source == null ? null : String.valueOf(source);
            }
        }
        return null;
    }

    /** 从元数据表读取切片数。 */
    private int chunkCountOf(String docId) {
        for (Map<String, Object> row : store.list()) {
            if (docId.equals(row.get("docId"))) {
                Object count = row.get("chunkCount");
                return count instanceof Number n ? n.intValue() : 0;
            }
        }
        return 0;
    }

    /**
     * 删除文档：先删 RogueMemory namespace，再删 DuckDB 元数据。
     *
     * @param docId 文档 ID
     */
    public synchronized void deleteDocument(String docId) {
        RogueMemory active = requireMemory();
        active.deleteByNamespace(docId);
        active.checkpoint();
        store.delete(docId);
    }

    /**
     * 重建知识库：关闭并删除 mmap 文件、清空元数据；保留当前 embedding 配置，
     * 之后可重新灌入示例或导入新文档。
     */
    public synchronized void rebuild() {
        closeMemory();
        try {
            deleteMmapFiles();
        } catch (IOException error) {
            throw new IllegalStateException("重建知识库失败：" + error.getMessage(), error);
        }
        store.clear();
        seeded = false;
        openMemory(provider);
        ensureSeeded();
    }

    /**
     * 导出知识库全量快照：文档清单（含原始全文）与全部向量缓存。
     *
     * <p>跨环境同步用：目标实例导入后无需重新向量化，索引重建全部由向量缓存供数。
     * 向量为原始小端序 float 字节，由 Jackson 序列化为 Base64 字符串。</p>
     *
     * @return 含 version / exportedAt / documents / vectors 的快照映射
     */
    public Map<String, Object> exportSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", 1);
        snapshot.put("exportedAt", System.currentTimeMillis());
        snapshot.put("documents", store.listWithContent());
        snapshot.put("vectors", vectorCache == null
                ? Collections.emptyList() : vectorCache.listAllVectors());
        return snapshot;
    }

    /**
     * 导入知识库快照：文档与向量按源优先 upsert 合并，随后用缓存原地重建索引，
     * 导入内容立即对检索可见且不产生 embedding 网络调用（新文本除外）。
     *
     * <p>非事务：两张表逐行写入，中途失败可重复导入（幂等）补齐。索引重建失败时
     * 不影响已导入数据，重启应用即可恢复。</p>
     *
     * @param payload {@link #exportSnapshot()} 产出的快照映射
     * @return 文档与向量的合并计数及索引刷新结果
     */
    public synchronized Map<String, Object> importSnapshot(Map<String, Object> payload) {
        Object version = payload.get("version");
        if (!(version instanceof Number n) || n.intValue() != 1) {
            throw new IllegalArgumentException("不支持的快照版本: " + version + "，期望 version=1");
        }
        List<Map<String, Object>> documents = castList(payload.get("documents"));
        List<Map<String, Object>> vectors = castList(payload.get("vectors"));

        java.util.Set<String> existingDocIds = store.existingDocIds();
        int documentsUpserted = 0;
        int documentsAdded = 0;
        for (Map<String, Object> doc : documents) {
            Object docIdValue = doc.get("docId");
            if (docIdValue == null) continue;
            String docId = String.valueOf(docIdValue);
            boolean added = store.upsertDocument(docId,
                    stringOr(doc.get("title"), "未命名文档"),
                    stringOr(doc.get("source"), "MANUAL"),
                    numberOr(doc.get("chunkCount"), 0).intValue(),
                    numberOr(doc.get("charCount"), 0).longValue(),
                    doc.get("embeddingSignature") == null ? null : String.valueOf(doc.get("embeddingSignature")),
                    doc.get("content") == null ? null : String.valueOf(doc.get("content")),
                    numberOr(doc.get("createdAt"), System.currentTimeMillis()).longValue());
            if (added && !existingDocIds.contains(docId)) documentsAdded++;
            documentsUpserted++;
        }

        int vectorsAdded = 0;
        int vectorsSkipped = 0;
        java.util.Map<String, java.util.Set<String>> existingBySignature = new LinkedHashMap<>();
        for (Map<String, Object> entry : vectors) {
            Object hashValue = entry.get("textHash");
            String signature = entry.get("signature") == null ? null : String.valueOf(entry.get("signature"));
            if (hashValue == null || signature == null || vectorCache == null) {
                vectorsSkipped++;
                continue;
            }
            String textHash = String.valueOf(hashValue);
            java.util.Set<String> existing = existingBySignature.computeIfAbsent(
                    signature, ignored -> vectorCache.existingTextHashes(signature));
            if (existing.contains(textHash)) {
                vectorsSkipped++;
                continue;
            }
            float[] vector = decodeVector(entry);
            vectorCache.saveVector(signature, textHash, vector);
            existing.add(textHash);
            vectorsAdded++;
        }

        boolean indexRefreshed = refreshIndexQuietly();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentsUpserted", documentsUpserted);
        result.put("documentsAdded", documentsAdded);
        result.put("vectorsAdded", vectorsAdded);
        result.put("vectorsSkipped", vectorsSkipped);
        result.put("indexRefreshed", indexRefreshed);
        result.put("lastError", lastError);
        return result;
    }

    /**
     * 关闭当前索引并从 DuckDB 原地重建（向量走缓存），导入后立即可检索。
     * 失败时退回关键词模式并记录原因，已导入的数据不受影响（重启可重试）。
     */
    private boolean refreshIndexQuietly() {
        try {
            closeMemory();
            openMemory(provider);
            rebuildFromStore();
            ensureSeeded();
            return true;
        } catch (RuntimeException error) {
            this.lastError = "导入后索引重建失败：" + error.getMessage();
            if (memory == null) openMemory(null);
            return false;
        }
    }

    /** 快照 documents/vectors 字段的安全列表转换；类型不符视为空。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        if (!(value instanceof List<?> list)) return Collections.emptyList();
        List<Map<String, Object>> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) values.add((Map<String, Object>) map);
        }
        return values;
    }

    /** 解析快照中的 Base64 向量为 float 数组；字节序与导出一致（小端）。 */
    private static float[] decodeVector(Map<String, Object> entry) {
        Object encoded = entry.get("vector");
        if (!(encoded instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("快照向量缺少 vector 字段 (textHash="
                    + entry.get("textHash") + ")");
        }
        byte[] bytes = Base64.getDecoder().decode(text);
        float[] vector = new float[bytes.length / Float.BYTES];
        // 与 KnowledgeVectorCache.saveVector 相同的大端字节序
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(vector);
        return vector;
    }

    /** 取映射中的字符串值；null 返回默认值。 */
    private static String stringOr(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    /** 取映射中的数值；null 或非数返回默认值。 */
    private static Number numberOr(Object value, Number fallback) {
        return value instanceof Number n ? n : fallback;
    }

    /**
     * @return 知识库状态：就绪度、文档/切片计数、生效签名、维度与最近错误
     */
    public Map<String, Object> status() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ready", memory != null);
        view.put("searchMode", searchMode.name());
        view.put("topK", properties.getTopK());
        // 用户提交过向量模型配置即视为“已配置”（即使连接失败），便于界面展示意图；
        // 实际生效与否由 provider 是否可用决定，失败原因始终在 lastError。
        view.put("embeddingConfigured", provider != null || configuredModel != null);
        view.put("embeddingSignature", signature);
        view.put("embeddingModel", signature == null ? configuredModel : signature.split("\\|", 2)[1]);
        view.put("dimension", provider == null ? 0 : provider.getDimension());
        view.put("documentCount", store.count());
        view.put("chunkCount", store.totalChunks());
        view.put("signatures", store.signatures());
        view.put("seeded", seeded);
        view.put("lastError", lastError);
        view.put("mmapPath", properties.getMmapPath());
        // 最近一次应用的向量配置（含 Key，仅回显给本机前端用于重启后免重填）。
        view.put("savedEmbedding", settings.applied());
        return view;
    }

    /**
     * @return 当前生效的 embedding 签名；未配置向量模型时为 {@code null}
     */
    public String currentSignature() {
        return signature;
    }

    /**
     * 首次获得可用 embedding 配置且知识库为空时，自动灌入内置示例文档；
     * 失败只记录状态，不阻断主流程。
     */
    private void ensureSeeded() {
        if (seeded || provider == null || store.count() > 0
                || !seeding.compareAndSet(false, true)) {
            return;
        }
        try {
            for (SeedDocument seed : SEED_DOCUMENTS) {
                addDocument(seed.title, seed.content, "BUILTIN");
            }
            seeded = true;
        } catch (RuntimeException error) {
            this.lastError = "示例知识库灌入失败：" + error.getMessage();
        } finally {
            seeding.set(false);
        }
    }

    /**
     * 打开（或重建）RogueMemory 实例。provider 为空时退化为纯 BM25 关键词检索。
     *
     * <p>provider 一律经 {@link CachedEmbeddingProvider} 包装：启动重建与检索产生的
     * 向量计算先查 DuckDB 缓存，未命中才访问真实服务，使重启后的全量重建不再依赖
     * embedding 服务的可用性与耗时。</p>
     *
     * <p>崩溃防护：RogueMemory 默认的 autoExpand(true) 会在文件写满时扩展并重新映射
     * mmap，Windows 上旧映射地址在扩展后可能失效，运行期访问时直接触发
     * EXCEPTION_ACCESS_VIOLATION（无法用 Java 异常捕获）。因此这里一次性分配足够大的
     * 固定文件并关闭自动扩展，写入不触发重映射，保持运行期稳定。</p>
     *
     * @param embeddingProvider 当前 embedding 客户端，可为空
     */
    private void openMemory(UniversalEmbeddingProvider embeddingProvider) {
        SearchMode effective = embeddingProvider == null ? SearchMode.KEYWORD_ONLY : searchMode;
        long fixedSize = 1L * 1024 * 1024 * 1024;
        RogueMemory.MmapBuilder builder = RogueMemory.mmap()
                .persistent(properties.getMmapPath())
                .searchMode(effective)
                .allocateSize(fixedSize)
                .maxFileSize(fixedSize)
                .autoExpand(false);
        if (embeddingProvider != null) {
            builder = builder.embeddingProvider(new CachedEmbeddingProvider(
                    embeddingProvider, signature, vectorCache));
        }
        this.memory = builder.build();
    }

    /**
     * 启动时丢弃上次进程遗留的 mmap 数据文件与 dirty 标记。
     *
     * <p>RogueMemory 重新打开已持久化文件时会在 parseRecordHeader 越界导致 JVM 原生
     * 崩溃（无法捕获），因此本项目不再尝试“安全恢复”旧文件，而是每次启动强制新建，
     * 索引内容统一由 {@link #rebuildFromStore()} 从 DuckDB 重新切片写入。文档元数据
     * 与原文都在 DuckDB，丢弃 mmap 缓存不丢失任何用户数据。</p>
     */
    private void discardStaleFiles() {
        try {
            Files.deleteIfExists(Path.of(properties.getMmapPath() + ".mem"));
            Files.deleteIfExists(Path.of(properties.getMmapPath() + ".dirty"));
            Files.deleteIfExists(Path.of(properties.getMmapPath() + ".wal"));
        } catch (Exception error) {
            this.lastError = "知识库缓存文件清理失败：" + error.getMessage();
        }
    }

    /**
     * 从 DuckDB 元数据与原文重建 mmap 索引：遍历全部文档，逐篇重新切片并写入
     * RogueMemory，最后 checkpoint 并回写切片数。旧格式文档（未保存原文）跳过。
     */
    private void rebuildFromStore() {
        List<Map<String, Object>> rows = store.list();
        if (rows.isEmpty()) return;
        RogueMemory active = requireMemory();
        int rebuilt = 0;
        for (Map<String, Object> row : rows) {
            String docId = String.valueOf(row.get("docId"));
            String content = store.loadContent(docId);
            if (content == null || content.trim().isEmpty()) continue;
            Object titleObj = row.get("title");
            String title = titleObj == null ? docId : String.valueOf(titleObj);
            Object sourceObj = row.get("source");
            String source = sourceObj == null ? "MANUAL" : String.valueOf(sourceObj);
            List<String> chunks = chunker.chunk(content.trim());
            if (chunks.isEmpty()) continue;
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("title", title);
            metadata.put("source", source);
            try {
                for (int index = 0; index < chunks.size(); index++) {
                    Map<String, String> chunkMeta = new LinkedHashMap<>(metadata);
                    chunkMeta.put("chunkIndex", String.valueOf(index));
                    active.add(chunks.get(index), chunkMeta, docId);
                }
            } catch (RuntimeException error) {
                this.lastError = "索引重建失败（" + title + "）：" + error.getMessage();
                continue;
            }
            store.update(docId, title, chunks.size(), content.trim().length(),
                    signature == null ? "KEYWORD_ONLY" : signature, content.trim());
            rebuilt++;
        }
        active.checkpoint();
        if (rebuilt > 0) {
            this.seeded = true;
            this.lastError = null;
        }
    }

    /**
     * @return 当前可用的 RogueMemory；未初始化时按现有配置尝试初始化
     */
    private RogueMemory requireMemory() {
        if (memory == null) {
            configure(properties.getEmbeddingEndpoint(), properties.getEmbeddingApiKey(),
                    properties.getEmbeddingModel(), null);
        }
        if (memory == null) openMemory(null);
        return memory;
    }

    /**
     * 入库中途失败时回滚该文档已写入的片段，避免产生半篇文档。
     *
     * @param active   当前 RogueMemory
     * @param docId    文档 namespace
     * @param attempts 计划写入的切片数（仅用于日志语义）
     */
    private static void rollbackPartial(RogueMemory active, String docId, int attempts) {
        try {
            active.deleteByNamespace(docId);
        } catch (RuntimeException ignored) {
            // 回滚失败时保留原始异常给调用方。
        }
    }

    /**
     * 关闭当前 RogueMemory 实例并释放 mmap 资源。
     */
    private void closeMemory() {
        RogueMemory active = this.memory;
        this.memory = null;
        if (active != null) {
            try {
                active.close();
            } catch (Exception ignored) {
                // 关闭失败不影响重建流程。
            }
        }
    }

    /**
     * 删除 mmap 持久化文件，使重建后的知识库从空状态开始。
     * RogueMemory 会把 persistent(path) 落盘为 path + ".mem"（可能还有同名 sidecar），
     * 因此按“父目录下以配置路径名开头的文件”统一清理；删除失败必须报错，
     * 避免旧索引在 Windows 上静默残留。
     */
    private void deleteMmapFiles() throws IOException {
        Path target = Path.of(properties.getMmapPath()).toAbsolutePath().normalize();
        Path parent = target.getParent();
        String baseName = target.getFileName().toString();
        if (parent == null || !Files.isDirectory(parent)) {
            Files.deleteIfExists(target);
            return;
        }
        IOException failure = null;
        try (java.util.stream.Stream<Path> siblings = Files.list(parent)) {
            for (Path file : (Iterable<Path>) siblings::iterator) {
                if (file.getFileName().toString().startsWith(baseName)) {
                    try {
                        Files.delete(file);
                    } catch (IOException error) {
                        failure = error;
                    }
                }
            }
        }
        if (failure != null) {
            throw new IllegalStateException("清理知识库文件失败：" + failure.getMessage(), failure);
        }
    }

    /**
     * 解析检索模式，非法值回退 HYBRID。
     *
     * @param value 模式名
     * @return SearchMode 枚举
     */
    private static SearchMode parseMode(String value) {
        try {
            return SearchMode.valueOf(value);
        } catch (Exception ignored) {
            return SearchMode.HYBRID;
        }
    }

    /**
     * Spring 容器关闭时释放 mmap 资源。
     */
    @PreDestroy
    public void shutdown() {
        closeMemory();
        clearDirty();
    }

    /**
     * 写入 dirty 标记，表示当前正持有 mmap 数据文件。
     * 正常关闭时清除；进程崩溃或被强杀后标记残留，下次启动据此隔离可疑文件。
     */
    private void markDirty() {
        try {
            Path marker = Path.of(properties.getMmapPath() + ".dirty");
            if (marker.getParent() != null) {
                Files.createDirectories(marker.getParent());
            }
            if (!Files.exists(marker)) {
                Files.writeString(marker, String.valueOf(System.currentTimeMillis()));
            }
        } catch (IOException ignored) {
            // 标记失败仅损失一次崩溃保护，不影响主流程。
        }
    }

    /**
     * 正常关闭时清除 dirty 标记，说明数据文件已安全释放。
     */
    private void clearDirty() {
        try {
            Files.deleteIfExists(Path.of(properties.getMmapPath() + ".dirty"));
        } catch (IOException ignored) {
            // 清理失败时下次启动多做一次隔离重建，安全侧倾斜。
        }
    }

    /** 内置示例文档条目。 */
    private static final class SeedDocument {
        private final String title;
        private final String content;

        private SeedDocument(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    /**
     * 预置示例知识库：4 篇 AI Agent 市场研究资料。首次获得可用 embedding 配置且
     * 知识库为空时自动灌入，保证打开页面即可演示 RAG 检索。
     */
    private static final List<SeedDocument> SEED_DOCUMENTS = Collections.unmodifiableList(Arrays.asList(
            new SeedDocument("2026 企业级 AI Agent 采购趋势",
                    "2026 年上半年，企业级 AI Agent 采购重点从“模型能力”转向“运行时可靠性”。调研显示，78% 的受访企业把工具调用成功率、"
                            + "人工审批链路和预算控制列为选型第一梯队指标，其次是上下文管理与长会话压缩能力。采购决策人普遍要求供应商"
                            + "提供可观测的 Trace 与事件流，而不是只演示单次问答效果。国内头部云厂商与开源框架都在补齐挂起恢复、"
                            + "审批策略和重试退避等企业级特性。"),
            new SeedDocument("Agents-Flex 框架能力盘点",
                    "Agents-Flex 是面向 Java 生态的 Agent 应用框架，2.2 版本的核心能力包括：原生 AgentEvent 事件流、JSON Schema 动态表单、"
                            + "工具级人工审批策略、消息与 Token 双维度上下文压缩、协作式挂起恢复、Token/工具/墙钟三维预算控制、"
                            + "指数退避重试以及 OpenTelemetry 原生埋点。框架通过 AgentRunner 与 AgentTurn Snapshot 管理状态，"
                            + "应用层不需要自行实现状态机。生态模块覆盖 chat、embedding、向量存储与可观测性。"),
            new SeedDocument("bge-m3 嵌入模型评测摘要",
                    "BAAI 发布的 bge-m3 是主打多语言、多功能、多粒度的文本嵌入模型，支持超过 100 种语言，最大输入长度 8192 Token，"
                            + "输出向量维度 1024，同时支持稠密检索、稀疏检索与多向量检索三种用法。在中文语义检索基准上，"
                            + "bge-m3 的 nDCG 与召回率普遍优于同尺寸模型，适合知识库问答与 RAG 场景。常见部署方式包括 Ollama、"
                            + "Xinference 与 vLLM，均可通过 OpenAI 兼容的 /v1/embeddings 接口调用。"),
            new SeedDocument("RAG 混合检索实践笔记",
                    "生产 RAG 系统越来越多采用“向量 ANN + 关键词 BM25”的混合召回，再用 RRF（Reciprocal Rank Fusion）融合排序。"
                            + "纯向量检索对专有名词、编号和精确短语召回偏弱，纯关键词又缺乏语义泛化；两路互补后 TopK 命中率通常提升 10%~25%。"
                            + "工程要点：切片保留段落边界并做少量重叠；元数据带上来源与片段序号方便引用溯源；embedding 模型切换必须重建索引，"
                            + "因为不同模型的向量空间与维度互不兼容。")));
}
