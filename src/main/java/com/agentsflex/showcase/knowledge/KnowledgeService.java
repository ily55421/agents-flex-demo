package com.agentsflex.showcase.knowledge;

import com.yomahub.roguemap.embedding.UniversalEmbeddingProvider;
import com.yomahub.roguemap.memory.MemoryResult;
import com.yomahub.roguemap.memory.RogueMemory;
import com.yomahub.roguemap.memory.SearchMode;
import com.yomahub.roguemap.memory.SearchOptions;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final TextChunker chunker = new TextChunker();
    private final AtomicBoolean seeding = new AtomicBoolean();

    private volatile RogueMemory memory;
    private volatile UniversalEmbeddingProvider provider;
    private volatile String signature;
    private volatile SearchMode searchMode = SearchMode.HYBRID;
    private volatile String lastError;
    private volatile boolean seeded;

    /**
     * @param properties 知识库默认配置（mmap 路径、检索模式、TopK、embedding 默认值）
     * @param store      DuckDB 文档元数据存储
     */
    public KnowledgeService(KnowledgeProperties properties, KnowledgeDocumentStore store) {
        this.properties = properties;
        this.store = store;
        this.searchMode = parseMode(properties.getSearchMode());
    }

    /**
     * 应用 embedding 环境变量默认值（若有），随后打开 BM25 索引并按需灌入示例，
     * 使知识库在服务启动后即可用，而不是等到第一次读写才初始化。
     */
    @javax.annotation.PostConstruct
    public void initialize() {
        try {
            String endpoint = properties.getEmbeddingEndpoint();
            String model = properties.getEmbeddingModel();
            if (endpoint != null && !endpoint.trim().isEmpty()
                    && model != null && !model.trim().isEmpty()) {
                configure(endpoint, properties.getEmbeddingApiKey(), model, null);
            } else {
                openMemory(null);
            }
        } catch (RuntimeException error) {
            this.lastError = error.getMessage();
            if (memory == null) openMemory(null);
        }
    }

    /**
     * 应用 UI 或环境变量提供的 embedding 配置。签名未变化时幂等跳过；
     * 已有向量且签名不同则拒绝，要求先重建知识库。
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
        String newSignature = normalizedEndpoint + "|" + model.trim();
        if (newSignature.equals(signature) && memory != null) {
            ensureSeeded();
            return false;
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
            this.lastError = friendlyEmbeddingError(normalizedEndpoint, model.trim(), error);
            throw new IllegalStateException(this.lastError);
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
            throw new IllegalStateException(this.lastError);
        }
        this.lastError = null;
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
                content.trim().length(), signature == null ? "KEYWORD_ONLY" : signature);
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
     * 混合检索知识库（跨全部文档 namespace）。
     *
     * @param query 查询文本
     * @param topK  返回条数；小于等于 0 时使用配置默认值
     * @return 命中片段列表，含内容、标题、来源与分数
     */
    public List<Map<String, Object>> search(String query, int topK) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        RogueMemory active = requireMemory();
        int limit = topK > 0 ? topK : properties.getTopK();
        List<MemoryResult> results = active.search(query.trim(), limit,
                SearchOptions.builder().build());
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

    /**
     * 供 Agent 工具调用的格式化检索：返回带标题、分数与来源的纯文本片段列表；
     * 知识库为空或未配置时返回模型可解释的说明文本而不是抛异常。
     *
     * @param query 检索问题
     * @return 多行文本；每行一个命中片段
     */
    public String searchForTool(String query) {
        if (memory == null) return "知识库尚未初始化。";
        List<Map<String, Object>> hits;
        try {
            hits = search(query, properties.getTopK());
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
     * @return 知识库状态：就绪度、文档/切片计数、生效签名、维度与最近错误
     */
    public Map<String, Object> status() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ready", memory != null);
        view.put("searchMode", searchMode.name());
        view.put("topK", properties.getTopK());
        view.put("embeddingConfigured", provider != null);
        view.put("embeddingSignature", signature);
        view.put("embeddingModel", signature == null ? null : signature.split("\\|", 2)[1]);
        view.put("dimension", provider == null ? 0 : provider.getDimension());
        view.put("documentCount", store.count());
        view.put("chunkCount", store.totalChunks());
        view.put("signatures", store.signatures());
        view.put("seeded", seeded);
        view.put("lastError", lastError);
        view.put("mmapPath", properties.getMmapPath());
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
     * @param embeddingProvider 当前 embedding 客户端，可为空
     */
    private void openMemory(UniversalEmbeddingProvider embeddingProvider) {
        SearchMode effective = embeddingProvider == null ? SearchMode.KEYWORD_ONLY : searchMode;
        RogueMemory.MmapBuilder builder = RogueMemory.mmap()
                .persistent(properties.getMmapPath())
                .searchMode(effective)
                .autoExpand(true);
        if (embeddingProvider != null) {
            builder = builder.embeddingProvider(embeddingProvider);
        }
        this.memory = builder.build();
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
        Path target = Paths.get(properties.getMmapPath()).toAbsolutePath().normalize();
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
