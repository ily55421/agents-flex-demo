package com.agentsflex.showcase.knowledge;

import com.agentsflex.showcase.knowledge.parser.DocumentParserRegistry;
import com.agentsflex.showcase.knowledge.parser.ParsedDocument;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识灌入流水线：解析 → 切片 → 向量化 → 建索引，全程异步并落任务表。
 *
 * <p>对齐 WeKnora 的 knowledge_process 编排：上传请求登记任务后立即返回，后台线程
 * 复用 {@link KnowledgeService} 的同步入库核心（不重复实现写索引逻辑）。取消在
 * INDEXING（原子写索引）开始前生效——一旦进入写索引阶段任务会自然完成；队列满时
 * 提交直接失败为 FAILED 任务，不阻塞上传请求。</p>
 */
@Component
public class IngestPipeline {

    /** ThreadLocal 当前任务 ID：供动作内推进阶段与取消检查。 */
    private static final ThreadLocal<String> CURRENT_TASK = new ThreadLocal<>();

    private final KnowledgeService knowledgeService;
    private final DocumentParserRegistry parserRegistry;
    private final IngestTaskStore taskStore;
    private final KnowledgeProperties properties;
    /** 每个运行中任务的取消标志：阶段边界检查。 */
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private ThreadPoolTaskExecutor executor;

    /**
     * @param knowledgeService 知识库核心服务（@Lazy 打破构造期依赖环）
     * @param parserRegistry   文档解析器注册表
     * @param taskStore        任务状态存储
     * @param properties       并发与队列容量配置
     */
    public IngestPipeline(@Lazy KnowledgeService knowledgeService,
                          DocumentParserRegistry parserRegistry,
                          IngestTaskStore taskStore,
                          KnowledgeProperties properties) {
        this.knowledgeService = knowledgeService;
        this.parserRegistry = parserRegistry;
        this.taskStore = taskStore;
        this.properties = properties;
    }

    /** 初始化线程池：并发与队列容量可配，线程名带前缀便于日志定位。 */
    @PostConstruct
    public void init() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, properties.getIngestConcurrency()));
        executor.setMaxPoolSize(Math.max(1, properties.getIngestConcurrency()));
        executor.setQueueCapacity(Math.max(1, properties.getIngestQueueCapacity()));
        executor.setThreadNamePrefix("knowledge-ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
    }

    /**
     * 提交文件入库任务：立即登记 PENDING 任务并排队执行。
     *
     * @param fileName 原始文件名
     * @param bytes    文件字节流
     * @param title    标题；空则取文件名去扩展名
     * @return 任务视图
     */
    public Map<String, Object> submitFile(String fileName, byte[] bytes, String title) {
        String resolvedTitle = (title == null || title.trim().isEmpty())
                ? stripExtension(fileName) : title.trim();
        return submit(resolvedTitle, "FILE", fileName, () -> {
            ParsedDocument parsed = parserRegistry.parse(fileName, bytes);
            // 取消检查点：写索引（原子操作）开始前，取消不产生半篇文档
            beforeIndexing();
            return knowledgeService.addDocument(resolvedTitle, parsed.getMarkdown(), "FILE");
        });
    }

    /**
     * 提交文本入库任务（粘贴路径）。
     *
     * @param title   标题
     * @param content 正文
     * @return 任务视图
     */
    public Map<String, Object> submitText(String title, String content) {
        String resolvedTitle = (title == null || title.trim().isEmpty()) ? "未命名文档" : title.trim();
        return submit(resolvedTitle, "MANUAL", null, () -> {
            beforeIndexing();
            return knowledgeService.addDocument(resolvedTitle, content, "MANUAL");
        });
    }

    /**
     * 提交单篇文档重建任务（对齐 WeKnora reparse）：按原文与当前参数重新切片向量化。
     *
     * @param docId 文档 ID
     * @return 任务视图；文档不存在或无原文时任务直接 FAILED
     */
    public Map<String, Object> submitReparse(String docId) {
        Map<String, Object> existing = knowledgeService.getDocumentContent(docId);
        if (existing == null || !Boolean.TRUE.equals(existing.get("contentAvailable"))) {
            return submit("重建 " + docId, "REPARSE", null, () -> {
                throw new IllegalStateException("文档不存在或没有保存原始全文，无法重建索引");
            });
        }
        String title = String.valueOf(existing.get("title"));
        String content = String.valueOf(existing.get("content"));
        return submit(title, "REPARSE", null, () -> {
            beforeIndexing();
            return knowledgeService.updateDocument(docId, null, content);
        });
    }

    /**
     * 通用提交：登记任务 → 排队 → 工作线程推进阶段 → 收尾。
     *
     * @return 任务视图（含 taskId）
     */
    private Map<String, Object> submit(String title, String source, String fileName,
                                       TaskAction action) {
        String taskId = "task-" + UUID.randomUUID();
        taskStore.insert(taskId, title, source, fileName);
        AtomicBoolean cancelled = new AtomicBoolean();
        cancelFlags.put(taskId, cancelled);
        try {
            executor.execute(() -> runTask(taskId, cancelled, action));
        } catch (RejectedExecutionException error) {
            cancelFlags.remove(taskId);
            taskStore.finish(taskId, "FAILED", null, 0, "任务队列已满，请稍后重试");
        }
        return taskStore.find(taskId);
    }

    /** 工作线程主体：阶段推进 + 异常收尾；取消由动作内的检查点触发。 */
    private void runTask(String taskId, AtomicBoolean cancelled, TaskAction action) {
        CURRENT_TASK.set(taskId);
        try {
            taskStore.updateStage(taskId, "PARSING", 10);
            Map<String, Object> doc = action.run();
            if (cancelled.get()) {
                taskStore.finish(taskId, "CANCELLED", null, 0, null);
                return;
            }
            taskStore.updateStage(taskId, "FINALIZING", 90);
            String docId = String.valueOf(doc.get("docId"));
            int chunkCount = doc.get("chunkCount") instanceof Number number ? number.intValue() : 0;
            taskStore.finish(taskId, "COMPLETED", docId, chunkCount, null);
        } catch (TaskCancelledException cancelledError) {
            taskStore.finish(taskId, "CANCELLED", null, 0, null);
        } catch (Exception error) {
            taskStore.finish(taskId, "FAILED", null, 0, friendly(error));
        } finally {
            cancelFlags.remove(taskId);
            CURRENT_TASK.remove();
        }
    }

    /** INDEXING 前的边界：推进阶段并检查取消标志（尚未写索引，取消不产生半篇文档）。 */
    private void beforeIndexing() {
        String taskId = CURRENT_TASK.get();
        if (taskId == null) return;
        taskStore.updateStage(taskId, "INDEXING", 60);
        if (isCancelled(taskId)) {
            throw new TaskCancelledException();
        }
    }

    /** @param taskId 任务 ID
     *  @return 任务是否已被请求取消 */
    private boolean isCancelled(String taskId) {
        AtomicBoolean flag = cancelFlags.get(taskId);
        return flag != null && flag.get();
    }

    /** @param taskId 任务 ID
     *  @return 任务视图；不存在返回 {@code null} */
    public Map<String, Object> task(String taskId) {
        return taskStore.find(taskId);
    }

    /** @return 最近任务列表（供任务面板轮询） */
    public List<Map<String, Object>> tasks() {
        return taskStore.listRecent();
    }

    /**
     * 请求取消：任务进入 INDEXING 前生效；排队中任务直接置 CANCELLED。
     *
     * @return true=取消请求已受理；false=任务不存在或已终态
     */
    public boolean cancel(String taskId) {
        Map<String, Object> task = taskStore.find(taskId);
        if (task == null) return false;
        String status = String.valueOf(task.get("status"));
        if (IngestTaskStore.terminalStatuses().contains(status)) return false;
        AtomicBoolean flag = cancelFlags.get(taskId);
        if (flag != null) flag.set(true);
        if ("PENDING".equals(status)) {
            // 尚未开跑：直接终态，工作线程开始前会看到取消
            taskStore.finish(taskId, "CANCELLED", null, 0, null);
            cancelFlags.remove(taskId);
        }
        return true;
    }

    /** 压缩异常为单行可读文本。 */
    private static String friendly(Exception error) {
        String raw = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String text = raw.replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /** 去掉扩展名的标题推断。 */
    private static String stripExtension(String fileName) {
        if (fileName == null) return "未命名文档";
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    /** @return 当前活跃（未终态）任务是否存在（前端轮询停止条件） */
    public boolean hasActiveTasks() {
        return taskStore.hasActive();
    }

    /** 容器关闭：立即中断排队任务；运行中任务由 JVM 退出兜底。 */
    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /** 任务动作：封装一段入库逻辑，异常即任务失败。 */
    private interface TaskAction {
        /**
         * @return 入库后的文档视图（含 docId / chunkCount）
         * @throws Exception 任何失败
         */
        Map<String, Object> run() throws Exception;
    }

    /** 取消检查点命中：内部流转控制，不对外暴露。 */
    private static final class TaskCancelledException extends RuntimeException {
        private TaskCancelledException() {
            super("任务已取消");
        }
    }
}
