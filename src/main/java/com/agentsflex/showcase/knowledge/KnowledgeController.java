package com.agentsflex.showcase.knowledge;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("txt", "md", "markdown", "jsonl");

    private final KnowledgeService knowledgeService;
    private final KnowledgeDocumentStore documentStore;
    private final KnowledgeProperties properties;

    /**
     * @param knowledgeService 知识库核心服务
     * @param documentStore    DuckDB 文档元数据
     * @param properties       上传限制等配置
     */
    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeDocumentStore documentStore,
                               KnowledgeProperties properties) {
        this.knowledgeService = knowledgeService;
        this.documentStore = documentStore;
        this.properties = properties;
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
     * 上传 .txt/.md 文件并解析入库（multipart 路径）。文件名决定默认标题与格式校验。
     *
     * @param file  上传文件；仅支持 UTF-8 文本，大小受 maxUploadBytes 限制
     * @param title 可选标题，缺省使用文件名
     * @return 新文档元数据视图
     */
    @PostMapping("/documents/upload")
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "title", required = false) String title)
            throws IOException {
        String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 .txt / .md 文件，收到：" + originalName);
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new IllegalArgumentException("文件超过大小限制 "
                    + properties.getMaxUploadBytes() / 1024 / 1024 + "MB");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String resolvedTitle = title == null || title.trim().isEmpty()
                ? originalName.substring(0, originalName.length() - extension.length() - 1)
                : title.trim();
        if ("jsonl".equals(extension)) {
            return knowledgeService.addJsonlDocument(resolvedTitle, content);
        }
        return knowledgeService.addDocument(resolvedTitle, content, "FILE");
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
     *
     * @param body embeddingEndpoint / embeddingApiKey / embeddingModel / searchMode
     * @return 应用后的知识库状态；API Key 不回显
     */
    @PostMapping("/embedding")
    public Map<String, Object> configureEmbedding(@RequestBody Map<String, String> body) {
        knowledgeService.configure(body.get("embeddingEndpoint"), body.get("embeddingApiKey"),
                body.get("embeddingModel"), body.get("searchMode"));
        return knowledgeService.status();
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
     * 提取小写扩展名；无扩展名时返回空串。
     *
     * @param name 文件名
     * @return 不含点号的扩展名
     */
    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
