package com.agentsflex.showcase.blog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文档分类规则：从原 Python 服务（server.py 的 {@code CATEGORY_RULES}）等价迁移而来。
 *
 * <p>判定顺序为「文件名关键词 → 正文前 500 字关键词」，命中即停，{@code other} 兜底。
 * 规则顺序即优先级，越靠前越先匹配，因此调整顺序会改变分类结果。</p>
 *
 * <p>原实现的部分关键词里混入了零宽字符（如 {@code d\u200bdos}、{@code x\u200bss}），
 * 迁移时已清理：这类字符会让子串匹配永远失败，属于原实现的隐性缺陷。</p>
 */
public final class BlogCategoryRules {

    /**
     * 单条分类规则。
     *
     * @param slug     分类标识，写入数据库并作为接口入参
     * @param name     分类显示名，同时也是 docs/ 下子目录的匹配名
     * @param icon     分类图标（emoji）
     * @param keywords 关键词列表，全部按小写子串匹配
     */
    public record Rule(String slug, String name, String icon, List<String> keywords) {
    }

    /** 兜底分类标识，规则表最后一项。 */
    public static final String OTHER_SLUG = "other";

    private static final List<Rule> RULES = List.of(
            new Rule("ai", "AI与机器学习", "🤖", List.of(
                    "ai", "人工智能", "机器学习", "深度学习", "大模型", "llm", "gpt",
                    "cursor", "视觉ai", "geo实战", "deepseek", "通用人工智能",
                    "神经网络", "transformer", "自然语言", "nlp", "计算机视觉",
                    "ai辅助编程", "不写代码", "2022十大科技趋势")),
            new Rule("mobile", "移动端开发", "📱", List.of(
                    "android", "ios", "swift", "kotlin", "flutter", "react native",
                    "rn ", "rn.", "uniapp", "uni-app", "小程序", "微信开发",
                    "移动端", "app开发", "手机开发", "objective-c", "xcode",
                    "selenium")),
            new Rule("interview", "面试与求职", "🎯", List.of(
                    "面试题大全", "面试突击", "程序员代码面试指南",
                    "java面试", "java基础面试", "面试题(java", "leetcode solutions",
                    "leetcode 前", "算法题解析")),
            new Rule("java", "Java与JVM", "☕", List.of(
                    "java", "jvm", "spring", "springboot", "springcloud",
                    "maven", "gradle", "tomcat", "servlet", "hibernate", "mybatis",
                    "netty", "dubbo", "log4j", "slf4j", "logback", "junit",
                    "mockito", "swagger", "lombok", "jwt", "oauth", "jpa", "jdbc",
                    "effective java", "java并发", "java编程思想", "java核心",
                    "java基础", "java优化", "java性能", "java架构",
                    "java数据结构和算法", "java机器学习", "java程序设计",
                    "java课程", "java夜未眠", "码出高效", "阿里巴巴java",
                    "java开发手册", "java面试", "java程序员", "教妹学",
                    "java从小白", "javafx", "camel in action", "深入剖析tomcat",
                    "深入理解java虚拟机", "实战java", "java高并发", "javaguide",
                    "hutool", "guns", "rocketmq", "mafka", "hibernate validator",
                    "selenium3")),
            new Rule("database", "数据库与存储", "🗄️", List.of(
                    "mysql", "redis", "mongo", "neo4j", "sql", "数据库", "索引优化",
                    "向量存储", "新型数据库", "elasticsearch", "clickhouse", "tidb",
                    "数据库原理", "数据库技术")),
            new Rule("bigdata", "大数据与分布式", "🌐", List.of(
                    "spark", "hadoop", "flink", "kafka", "rocketmq", "mafka",
                    "微服务", "云原生", "kubernetes", "k8s", "docker", "容器",
                    "springcloud", "spring cloud", "nacos", "sentinel", "seata",
                    "knative", "serverless", "分布式", "大数据", "hive", "hbase",
                    "zookeeper", "dubbo", "rpc", "消息队列", "中间件",
                    "大型网站系统", "性能优化手册")),
            new Rule("frontend", "前端与Web", "🎨", List.of(
                    "前端", "webpack", "vue", "react", "angular", "typescript",
                    "javascript", "html", "css", "node", "jquery", "bootstrap",
                    "ria", "web", "浏览器", "http", "https", "dns", "小程序")),
            new Rule("arch", "架构与设计", "🏗️", List.of(
                    "架构", "设计模式", "重构", "领域驱动", "ddd", "微服务架构",
                    "系统架构", "软件架构", "架构师", "架构决策", "研发管理",
                    "端到端流程", "流程管理", "大话架构思维")),
            new Rule("os", "操作系统与底层", "⚙️", List.of(
                    "操作系统", "linux", "windows", "编译原理", "编译器", "自制编译",
                    "计算机组成", "计算机原理", "cpu", "内存管理", "进程", "线程底层",
                    "鸟哥", "私房菜", "日志系统", "自制操作系统", "30天自制",
                    "大话计算机")),
            new Rule("philosophy", "哲学与人文", "📖", List.of(
                    "哲学", "黑格尔", "老子", "孟子", "春秋", "资本论", "马克思",
                    "贝克莱", "唯心", "唯物", "逻辑", "中国思想", "中国历代词",
                    "柳如是", "文学", "历史", "文革", "神逻辑", "熊逸",
                    "哲学大全", "小逻辑", "精神哲学", "高效阅读法", "数学思维")),
            new Rule("management", "管理与经营", "💼", List.of(
                    "稻盛", "京瓷", "活法", "干法", "经营", "阿米巴", "六项精进",
                    "敬天爱人", "日航", "企业家", "创业", "管理", "华为", "熵减",
                    "技术创新", "研发能力", "跨文化管理", "商道", "人生哲学",
                    "你的梦想", "创造高收益", "经营十二条", "实学", "拯救人类",
                    "今生无憾", "回归哲学", "在萧条中", "企业家成功")),
            new Rule("algo", "数学与算法", "🔢", List.of(
                    "算法", "数据结构", "数学", "线性代数", "概率", "统计", "离散",
                    "编程珠玑", "程序员数学", "算法面试", "分支限界", "动态规划",
                    "贪心", "回溯", "排序", "查找", "图论", "树", "链表",
                    "leetcode", "算法题", "十五个经典算法")),
            new Rule("tools", "工具与实践", "🔧", List.of(
                    "git", "maven", "gradle", "vim", "ide", "cursor",
                    "开发工具", "构建工具", "版本控制", "调试")),
            new Rule("edu", "教育与考试", "🎓", List.of(
                    "北外", "课程", "考试", "系统分析师", "软考", "复习", "教程",
                    "入门指南", "学习", "培训", "作业", "实验", "课程设计",
                    "网站建设", "网站设计", "计算机图像", "大数据分析",
                    "互联网软件", "管理信息系统", "软件工程")),
            new Rule("network", "网络与安全", "🔒", List.of(
                    "网络", "tcp", "ip", "http", "安全", "加密", "防火墙", "ddos",
                    "xss", "sql注入", "csrf", "漏洞", "渗透", "实名举报", "学术造假",
                    "计算机网络")),
            new Rule(OTHER_SLUG, "其他", "📁", List.of()));

    private BlogCategoryRules() {
    }

    /**
     * @return 全部规则（含兜底项），顺序即匹配优先级
     */
    public static List<Rule> rules() {
        return RULES;
    }

    /**
     * 双重分类判断：先按文件名匹配，再回退到正文前 500 字。
     *
     * @param filename       文件名（含扩展名）
     * @param contentPreview 正文开头片段，可为空
     * @return 命中的分类 slug；无命中返回 {@link #OTHER_SLUG}
     */
    public static String classify(String filename, String contentPreview) {
        String nameLower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (OTHER_SLUG.equals(rule.slug())) {
                continue;
            }
            for (String keyword : rule.keywords()) {
                if (nameLower.contains(keyword)) {
                    return rule.slug();
                }
            }
        }
        if (contentPreview != null && !contentPreview.isEmpty()) {
            String preview = contentPreview.substring(0, Math.min(500, contentPreview.length()))
                    .toLowerCase(Locale.ROOT);
            for (Rule rule : RULES) {
                if (OTHER_SLUG.equals(rule.slug())) {
                    continue;
                }
                for (String keyword : rule.keywords()) {
                    if (preview.contains(keyword)) {
                        return rule.slug();
                    }
                }
            }
        }
        return OTHER_SLUG;
    }

    /**
     * 按分类显示名反查 slug，用于 docs/ 下按目录名决定分类的场景。
     *
     * @param directoryName 目录名，例如「Java与JVM」
     * @return 对应 slug；目录名不是已知分类时返回 {@code null}，交由关键词自动判定
     */
    public static String slugOfDirectory(String directoryName) {
        for (Rule rule : RULES) {
            if (rule.name().equals(directoryName)) {
                return rule.slug();
            }
        }
        return null;
    }

    /**
     * @return slug → 显示名 的有序映射，供建库时 upsert 分类表
     */
    public static Map<String, Rule> bySlug() {
        Map<String, Rule> map = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            map.put(rule.slug(), rule);
        }
        return map;
    }
}
