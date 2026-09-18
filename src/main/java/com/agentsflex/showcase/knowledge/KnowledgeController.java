package com.agentsflex.showcase.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsflex.showcase.knowledge.parser.DocumentParserRegistry;
import com.agentsflex.showcase.knowledge.parser.ParsedDocument;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RAG 知识库的 HTTP 边界：状态、文档清单、文本/文件入库、检索测试、重建与 embedding 配置。
 * 控制器只做输入校验与格式转换，检索与持久化逻辑全部位于 {@link KnowledgeService}。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    /** 快照文件大小上限：全量向量以 Base64 JSON 计，3200 片段约 20MB，留足余量。 */
    private static final long MAX_SNAPSHOT_BYTES = 200L * 1024 * 1024;

    private final KnowledgeService knowledgeService;
    private final KnowledgeDocumentStore documentStore;
    private final KnowledgeSettingsStore settingsStore;
    private final KnowledgeProperties properties;
    private final DocumentParserRegistry parserRegistry;
    private final IngestPipeline ingestPipeline;
    private final ObjectMapper mapper;

    /**
     * @param knowledgeService 知识库核心服务
     * @param documentStore    DuckDB 文档元数据
     * @param settingsStore    向量模型配置与自建预设持久化
     * @param properties       上传限制等配置
     * @param parserRegistry   文档解析器注册表（多格式上传入库）
     * @param ingestPipeline   异步灌入流水线（任务状态机）
     * @param mapper           Jackson 序列化器（快照导出/导入）
     */
    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeDocumentStore documentStore,
                               KnowledgeSettingsStore settingsStore,
                               KnowledgeProperties properties,
                               DocumentParserRegistry parserRegistry,
                               IngestPipeline ingestPipeline,
                               ObjectMapper mapper) {
        this.knowledgeService = knowledgeService;
        this.documentStore = documentStore;
        this.settingsStore = settingsStore;
        this.properties = properties;
        this.parserRegistry = parserRegistry;
        this.ingestPipeline = ingestPipeline;
        this.mapper = mapper;
    }

    /**
     * @return 知识库状态（就绪度、模型签名、文档与切片计数、最近错误）
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return knowledgeService.status();
    }

    /**
     * @return 已入库文档清单，按创建时间倒序
     */
    @GetMapping("/documents")
    public List<Map<String, Object>> documents() {
        return documentStore.list();
    }

    /**
     * 以 JSON 文本方式添加文档（UI 粘贴录入路径）。
     *
     * @param body title 与 content 字段
     * @return 新文档元数据视图
     */
    @PostMapping("/documents")
    public Map<String, Object> addDocument(@RequestBody Map<String, String> body) {
        return knowledgeService.addDocument(body.get("title"), body.get("content"), "MANUAL");
    }

    /**
     * 上传文件并解析入库（multipart 路径）。支持 PDF / Word / Excel / PPT / HTML / Markdown /
     * 文本等格式，由 {@link DocumentParserRegistry} 归一化为 Markdown 后入库。
     *
     * @param file  上传文件；大小受 maxUploadBytes 限制，解析前另受 maxParseBytes 保护
     * @param title 可选标题，缺省使用文件名
     * @return 新文档元数据视图（附解析器、页数/表数等元数据）
     */
    @PostMapping("/documents/upload")
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "title", required = false) String title)
            throws IOException {
        String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!parserRegistry.supports(originalName)) {
            throw new IllegalArgumentException("不支持的文件格式 ." + extension
                    + "，当前支持：" + String.join(" / ", parserRegistry.supportedExtensions()));
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new IllegalArgumentException("文件超过大小限制 "
                    + properties.getMaxUploadBytes() / 1024 / 1024 + "MB");
        }
        if (file.getSize() > properties.getMaxParseBytes()) {
            throw new IllegalArgumentException("文件超过解析上限 "
                    + properties.getMaxParseBytes() / 1024 / 1024 + "MB");
        }
        String resolvedTitle = title == null || title.trim().isEmpty()
                ? originalName.substring(0, originalName.length() - extension.length() - 1)
                : title.trim();
        byte[] bytes = file.getBytes();
        if ("jsonl".equals(extension)) {
            // JSONL 走问答事实库路径：逐行解析为独立片段，检索粒度最细
            return knowledgeService.addJsonlDocument(resolvedTitle,
                    new String(bytes, StandardCharsets.UTF_8));
        }
        ParsedDocument parsed = parserRegistry.parse(originalName, bytes);
        Map<String, Object> view = knowledgeService.addDocument(resolvedTitle, parsed.getMarkdown(), "FILE");
        // 附上解析元数据：前端据此展示页数/表数/来源格式
        view.put("parser", parsed.getMetadata().get("parser"));
        for (Map.Entry<String, String> entry : parsed.getMetadata().entrySet()) {
            view.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return view;
    }

    /**
     * @return 支持的入库格式与对应解析器，供前端文件选择器限制与提示
     */
    @GetMapping("/parsers")
    public List<Map<String, Object>> parsers() {
        return parserRegistry.describe();
    }

    /**
     * 上传文件并异步入库：立即返回任务视图，解析/切片/向量化在后台推进，
     * 进度经 GET /tasks 轮询。
     *
     * @param file  上传文件；格式与大小校验同同步路径
     * @param title 可选标题，缺省使用文件名
     * @return 任务视图（含 taskId / status）
     */
    @PostMapping("/documents/upload/async")
    public Map<String, Object> uploadDocumentAsync(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "title", required = false) String title)
            throws IOException {
        String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        if (!parserRegistry.supports(originalName)) {
            throw new IllegalArgumentException("不支持的文件格式 ." + extensionOf(originalName)
                    + "，当前支持：" + String.join(" / ", parserRegistry.supportedExtensions()));
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new IllegalArgumentException("文件超过大小限制 "
                    + properties.getMaxUploadBytes() / 1024 / 1024 + "MB");
        }
        if (file.getSize() > properties.getMaxParseBytes()) {
            throw new IllegalArgumentException("文件超过解析上限 "
                    + properties.getMaxParseBytes() / 1024 / 1024 + "MB");
        }
        return ingestPipeline.submitFile(originalName, file.getBytes(), title);
    }

    /**
     * 粘贴文本异步入库（大文本场景避免请求线程被向量化阻塞）。
     *
     * @param body title 与 content 字段
     * @return 任务视图
     */
    @PostMapping("/documents/async")
    public Map<String, Object> addDocumentAsync(@RequestBody Map<String, String> body) {
        if (body.get("content") == null || body.get("content").trim().isEmpty()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        return ingestPipeline.submitText(body.get("title"), body.get("content"));
    }

    /**
     * 异步重建单篇文档索引（对齐 WeKnora reparse）。
     *
     * @param docId 文档 ID
     * @return 任务视图
     */
    @PostMapping("/documents/{docId}/reparse/async")
    public Map<String, Object> reparseDocumentAsync(@PathVariable String docId) {
        return ingestPipeline.submitReparse(docId);
    }

    /**
     * @return 最近灌入任务（上限 50），供任务面板轮询
     */
    @GetMapping("/tasks")
    public List<Map<String, Object>> tasks() {
        return ingestPipeline.tasks();
    }

    /**
     * @param taskId 任务 ID
     * @return 任务视图
     */
    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> task(@PathVariable String taskId) {
        Map<String, Object> task = ingestPipeline.task(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        return task;
    }

    /**
     * 请求取消任务：INDEXING（原子写索引）开始前生效。
     *
     * @param taskId 任务 ID
     * @return 取消结果
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable String taskId) {
        boolean accepted = ingestPipeline.cancel(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("taskId", taskId);
        return result;
    }

    /**
     * 切片预览：按给定窗口参数试算切片结果，不落库。
     *
     * <p>对齐 WeKnora 的 /chunker/preview 调试能力：调整 chunkSize/overlap 前先看效果，
     * 避免"改了参数 → 重建索引 → 才发现切碎了"的来回成本。</p>
     *
     * @param body content 必填；chunkSize / overlap 可选，缺省使用服务端配置
     * @return 每片的序号、标题路径、字符数与正文
     */
    @PostMapping("/chunker/preview")
    public Map<String, Object> previewChunking(@RequestBody Map<String, Object> body) {
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("切片预览内容不能为空");
        }
        int chunkSize = body.get("chunkSize") instanceof Number number
                ? number.intValue() : properties.getChunkSize();
        int overlap = body.get("overlap") instanceof Number number
                ? number.intValue() : properties.getChunkOverlap();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunkSize", chunkSize);
        result.put("overlap", overlap);
        result.put("chunks", knowledgeService.previewChunking(content, chunkSize, overlap));
        return result;
    }

    /**
     * 读取文档原始全文与元数据，供前端预览查看。
     * 旧版本导入的文档可能没有保存原始全文（contentAvailable=false），
     * 此时前端应提示用户粘贴新内容覆盖保存或删除后重新导入。
     *
     * @param docId 文档 ID
     * @return 含 docId / title / content / contentAvailable / chunkCount / charCount / source 的视图
     */
    @GetMapping("/documents/{docId}")
    public Map<String, Object> documentContent(@PathVariable String docId) {
        Map<String, Object> view = knowledgeService.getDocumentContent(docId);
        if (view == null) throw new IllegalArgumentException("文档不存在: " + docId);
        return view;
    }

    /**
     * 编辑文档全文：后端删除旧切片并重新切片 + 向量化，检索立即生效。
     *
     * @param docId 文档 ID
     * @param body  title 可选，content 必填
     * @return 更新后的文档元数据视图
     */
    @PutMapping("/documents/{docId}")
    public Map<String, Object> updateDocument(@PathVariable String docId,
                                              @RequestBody Map<String, String> body) {
        return knowledgeService.updateDocument(docId, body.get("title"), body.get("content"));
    }

    /**
     * @param docId 文档 ID
     * @return 删除结果
     */
    @DeleteMapping("/documents/{docId}")
    public Map<String, Object> deleteDocument(@PathVariable String docId) {
        knowledgeService.deleteDocument(docId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("docId", docId);
        return result;
    }

    /**
     * 检索测试：返回 TopK 命中片段、分数与生效模式；可限定单个文档 namespace。
     *
     * @param body query 必填，topK / namespace 可选
     * @return 命中列表与生效检索模式
     */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? null : String.valueOf(body.get("query"));
        int topK = body.get("topK") instanceof Number ? ((Number) body.get("topK")).intValue() : 0;
        String namespace = body.get("namespace") == null ? null : String.valueOf(body.get("namespace"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("namespace", namespace == null || namespace.isBlank() ? "all" : namespace);
        result.put("hits", knowledgeService.search(query, topK, namespace));
        return result;
    }

    /**
     * 配置知识库生效的 embedding 模型（向量档案面板可直接应用，不必等待创建 Agent）。
     * 配置会持久化到本地设置文件，重启后自动恢复，无需重新填写。
     *
     * @param body embeddingEndpoint / embeddingApiKey / embeddingModel / searchMode
     * @return 应用后的知识库状态
     */
    @PostMapping("/embedding")
    public Map<String, Object> configureEmbedding(@RequestBody Map<String, String> body) {
        knowledgeService.configure(body.get("embeddingEndpoint"), body.get("embeddingApiKey"),
                body.get("embeddingModel"), body.get("searchMode"));
        return knowledgeService.status();
    }

    /**
     * @return 用户自建向量预设清单（name/endpoint/model/apiKey），供前端快捷填充
     */
    @GetMapping("/embedding-presets")
    public List<Map<String, String>> embeddingPresets() {
        return settingsStore.presets();
    }

    /**
     * 新增或同名覆盖一个向量预设；应用面板填好的连接信息后可一键复用。
     *
     * @param body name / endpoint / model 必填，apiKey 可选
     * @return 更新后的预设清单
     */
    @PostMapping("/embedding-presets")
    public List<Map<String, String>> saveEmbeddingPreset(@RequestBody Map<String, String> body) {
        String name = textOrNull(body.get("name"));
        String endpoint = textOrNull(body.get("endpoint"));
        String model = textOrNull(body.get("model"));
        if (name == null || endpoint == null || model == null) {
            throw new IllegalArgumentException("预设名称、服务地址与向量模型不能为空");
        }
        settingsStore.upsertPreset(name, endpoint, model, textOrNull(body.get("apiKey")));
        return settingsStore.presets();
    }

    /**
     * @param name 预设名称
     * @return 更新后的预设清单
     */
    @DeleteMapping("/embedding-presets/{name}")
    public List<Map<String, String>> deleteEmbeddingPreset(@PathVariable String name) {
        if (!settingsStore.deletePreset(name)) {
            throw new IllegalArgumentException("预设不存在: " + name);
        }
        return settingsStore.presets();
    }

    /**
     * 重建知识库：清空向量与元数据，保留当前 embedding 配置并重新灌入示例。
     *
     * @return 重建后的状态
     */
    @PostMapping("/rebuild")
    public Map<String, Object> rebuild() {
        knowledgeService.rebuild();
        return knowledgeService.status();
    }

    /**
     * 导出知识库全量快照：文档清单（含原文）+ 全部向量缓存，供跨环境同步。
     * 导入端无需重新向量化，索引由缓存原地重建。
     *
     * @param response 快照 JSON 以附件形式下载
     */
    @GetMapping("/export")
    public void exportSnapshot(HttpServletResponse response) throws IOException {
        Map<String, Object> snapshot = knowledgeService.exportSnapshot();
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=knowledge-export-"
                + LocalDate.now() + ".json");
        mapper.writeValue(response.getOutputStream(), snapshot);
    }

    /**
     * 导入知识库快照：文档与向量按源优先 upsert 合并，随后用缓存原地重建索引，
     * 导入内容立即可检索且不产生 embedding 网络调用。幂等，可重复导入补齐。
     *
     * @param file {@code GET /export} 产出的快照 JSON 文件
     * @return 文档/向量合并计数与索引刷新结果
     */
    @PostMapping("/import")
    public Map<String, Object> importSnapshot(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("快照文件超过 " + MAX_SNAPSHOT_BYTES / 1024 / 1024 + "MB 限制");
        }
        Map<String, Object> payload;
        try (InputStream in = file.getInputStream()) {
            payload = mapper.readValue(in, new TypeReference<Map<String, Object>>() {
            });
        }
        return knowledgeService.importSnapshot(payload);
    }

    /**
     * 提取小写扩展名；无扩展名时返回空串。
     *
     * @param name 文件名
     * @return 不含点号的扩展名
     */
    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 取出请求字段并去除首尾空白；空白视为未填写，返回 {@code null}。
     *
     * @param value 原始字段值
     * @return 去除空白后的值；空白返回 {@code null}
     */
    private static String textOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
