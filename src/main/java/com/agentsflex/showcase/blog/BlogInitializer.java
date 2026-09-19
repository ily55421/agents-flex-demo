package com.agentsflex.showcase.blog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动时初始化文档博客：建库 + 首次全量导入 / 后续增量同步。
 *
 * <p>导入在后台线程执行，不阻塞应用启动——源目录有 500+ 篇 Markdown，
 * 首次全量导入需要几秒到几十秒，同步执行会让 18080 端口迟迟不可用。</p>
 *
 * <p>失败只记录日志，不影响其它模块：博客是独立功能，不应因为源目录缺失
 * 或数据库被占用而拖垮整个服务。</p>
 */
@Component
public class BlogInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BlogInitializer.class);

    private final BlogStore store;
    private final BlogProperties properties;

    public BlogInitializer(BlogStore store, BlogProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            store.initSchema();
        } catch (RuntimeException error) {
            log.error("[blog] 初始化数据库失败，文档博客不可用: {}", error.getMessage(), error);
            return;
        }
        if (!properties.isImportOnStartup()) {
            log.info("[blog] 已关闭启动导入，需手动调用 POST /api/blog/sync");
            return;
        }
        Thread importer = new Thread(this::importQuietly, "blog-import");
        importer.setDaemon(true);
        importer.start();
    }

    /** 空库走全量导入，已有数据只做增量同步；异常只记日志，避免线程静默死亡。 */
    private void importQuietly() {
        try {
            int existing = store.documentCount();
            boolean incremental = existing > 0;
            Map<String, Object> result = store.importDocuments(incremental);
            log.info("[blog] 启动导入完成（{}）: {}", incremental ? "增量" : "全量", result);
        } catch (RuntimeException error) {
            log.error("[blog] 启动导入失败，可稍后手动调用 POST /api/blog/sync: {}", error.getMessage(), error);
        }
    }
}
