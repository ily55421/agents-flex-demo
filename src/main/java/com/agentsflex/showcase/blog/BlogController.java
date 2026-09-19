package com.agentsflex.showcase.blog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档博客的 HTTP 边界，取代原先独立的「总结文档」Python Web 服务。
 *
 * <p>路径统一挂在 {@code /api/blog/**} 下，与 {@code /api/graph}（电力拓扑图谱）区分：
 * 两者都叫「图谱」但数据源不同——博客图谱基于文档标题相似度，拓扑图谱来自 Neo4j。</p>
 *
 * <p>控制器只做参数校验与转发，检索、导入与图谱计算都在 {@link BlogStore}。</p>
 */
@RestController
@RequestMapping("/api/blog")
public class BlogController {

    private final BlogStore store;

    public BlogController(BlogStore store) {
        this.store = store;
    }

    /**
     * @return 全部分类及在用文档数
     */
    @GetMapping("/categories")
    public Map<String, Object> categories() {
        return store.categories();
    }

    /**
     * 文档列表（不含回收站）。
     *
     * @param category 分类 slug，省略表示全部
     * @param page     页码，从 1 开始
     * @param limit    每页条数，默认 20
     * @return 分页结构
     */
    @GetMapping("/documents")
    public Map<String, Object> documents(@RequestParam(required = false) String category,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int limit) {
        return store.documents(category, page, limit);
    }

    /**
     * 全文检索：中文子串通过 FTS5 trigram 命中，短查询自动回退 LIKE。
     *
     * @param q        查询串
     * @param page     页码
     * @param limit    每页条数
     * @param category 分类过滤，可为空
     * @return 命中列表（含 snippet）
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(required = false) String category) {
        return store.search(q, page, limit, category);
    }

    /**
     * 搜索建议：输入框下拉用，标题命中优先。
     *
     * @param q     查询串
     * @param limit 建议条数，默认 8
     * @return suggestions 列表
     */
    @GetMapping("/search-suggest")
    public Map<String, Object> suggest(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "8") int limit) {
        return store.suggest(q, limit);
    }

    /**
     * 文档详情，含原始 Markdown 全文。
     *
     * @param id 文档 id
     * @return document 字段
     */
    @GetMapping("/document")
    public Map<String, Object> document(@RequestParam long id) {
        return store.document(id);
    }

    /**
     * 相关文档推荐。
     *
     * @param id    中心文档 id
     * @param limit 条数，默认 5
     * @return documents 列表
     */
    @GetMapping("/related")
    public Map<String, Object> related(@RequestParam long id,
                                       @RequestParam(defaultValue = "5") int limit) {
        return store.related(id, limit);
    }

    /**
     * @return 站点统计：文档数、分类数、总字数、回收站数量
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }

    /**
     * @return 全部标签及文档数
     */
    @GetMapping("/tags")
    public Map<String, Object> tags() {
        return store.tags();
    }

    /**
     * 标签下的文档列表。
     *
     * @param slug  标签 slug
     * @param page  页码
     * @param limit 每页条数
     * @return 分页结构
     */
    @GetMapping("/tag")
    public Map<String, Object> tagDocuments(@RequestParam(required = false) String slug,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int limit) {
        return store.tagDocuments(slug, page, limit);
    }

    /**
     * 回收站列表。
     *
     * @param page  页码
     * @param limit 每页条数
     * @return 分页结构
     */
    @GetMapping("/trash")
    public Map<String, Object> trash(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int limit) {
        return store.trash(page, limit);
    }

    /**
     * 文档关系图谱：中心文档 + 一跳邻居。
     *
     * @param id    中心文档 id
     * @param depth 预留的跳数参数（当前实现固定为一跳，保留参数以兼容前端调用）
     * @return center / nodes / edges
     */
    @GetMapping("/graph")
    public Map<String, Object> graph(@RequestParam long id,
                                     @RequestParam(defaultValue = "1") int depth) {
        Map<String, Object> result = new LinkedHashMap<>(store.graph(id));
        result.put("depth", Math.max(1, Math.min(3, depth)));
        return result;
    }

    /**
     * 移入回收站。
     *
     * @param body 需包含 id
     * @return status / message
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody Map<String, Object> body) {
        return store.softDelete(requireId(body));
    }

    /**
     * 从回收站恢复。
     *
     * @param body 需包含 id
     * @return status / message
     */
    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestBody Map<String, Object> body) {
        return store.restore(requireId(body));
    }

    /**
     * 彻底删除。
     *
     * @param body 需包含 id
     * @return status / message
     */
    @PostMapping("/purge")
    public Map<String, Object> purge(@RequestBody Map<String, Object> body) {
        return store.purge(requireId(body));
    }

    /**
     * 增量同步源文档目录：新增与修改的重新入库，消失的移入回收站。
     *
     * @return status / data（imported/updated/skipped/deleted）
     */
    @PostMapping("/sync")
    public Map<String, Object> sync() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("data", store.importDocuments(true));
        return result;
    }

    /**
     * 全量重建检索索引与文档表（不触碰磁盘上的源文件）。
     *
     * @return status / data（imported/updated/skipped/deleted）
     */
    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("data", store.importDocuments(false));
        return result;
    }

    /**
     * 重建文档关系图谱并落库。
     *
     * @return status / relations_count
     */
    @PostMapping("/graph-rebuild")
    public Map<String, Object> graphRebuild() {
        return store.graphRebuild();
    }

    /** 从请求体取出必填的文档 id；缺失或非法时抛参数异常，由全局处理器转 409。 */
    private static long requireId(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("id");
        if (raw == null) {
            throw new IllegalArgumentException("缺少参数 id");
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("参数 id 不是合法数字: " + raw);
        }
    }
}
