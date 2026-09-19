package com.agentsflex.showcase.blog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档博客配置，映射 application.yml 的 {@code agents-flex.blog.*}。
 *
 * <p>该模块把原先独立的「总结文档」Python Web 服务（SQLite + FTS5）并入主服务：
 * 复用 SQLite 的 trigram 分词器获得中文子串检索能力，同时避免引入进程外依赖。
 * 数据库默认落在 {@code ./data/blog/}（{@code data/} 已被 gitignore），
 * 首次启动按 {@link #docsSrc} 全量导入，之后只做增量同步。</p>
 */
@Component
@ConfigurationProperties(prefix = "agents-flex.blog")
public class BlogProperties {

    /** SQLite 数据库文件路径；相对路径以进程工作目录为基准。 */
    private String dbPath = "./data/blog/docs.db";

    /** Markdown 源文档根目录：根下 *.md 与 docs/分类名/*.md 都会被导入。 */
    private String docsSrc = "./总结文档";

    /** 列表分页默认条数；请求可通过 limit 覆盖，服务端上限 100。 */
    private int pageLimit = 20;

    /** 是否在启动时执行一次增量导入；关闭后需手动调用 POST /api/blog/sync。 */
    private boolean importOnStartup = true;

    public String getDbPath() {
        return dbPath;
    }

    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    public String getDocsSrc() {
        return docsSrc;
    }

    public void setDocsSrc(String docsSrc) {
        this.docsSrc = docsSrc;
    }

    public int getPageLimit() {
        return pageLimit;
    }

    public void setPageLimit(int pageLimit) {
        this.pageLimit = pageLimit;
    }

    public boolean isImportOnStartup() {
        return importOnStartup;
    }

    public void setImportOnStartup(boolean importOnStartup) {
        this.importOnStartup = importOnStartup;
    }
}
