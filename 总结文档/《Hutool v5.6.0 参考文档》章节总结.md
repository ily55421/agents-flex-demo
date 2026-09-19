# 《Hutool v5.6.0 参考文档》章节总结

## 目录说明
-   本文档为技术工具类库参考手册，非传统书籍叙事结构。总结严格依据PDF提供的“目录”页（p.2-p.7）及正文模块顺序进行整理。
-   将“入门和安装”、“更新记录”视为前置章节；核心功能模块（如Core、JSON、Crypto等）按目录顺序逐一总结；“致谢”与“捐赠使用公开”作为附录处理。
-   由于文档为API参考性质，“逻辑推演”部分调整为“功能架构/设计思路”，“经典金句”调整为“核心API/代码示例”。

## 第1章：入门和安装

### 核心论点
-   **解决问题**：Java开发中基础工具方法缺失、重复造轮子及网络复制粘贴代码带来的Bug风险。
-   **核心观点**：Hutool是一个小而全的Java工具类库，通过静态方法封装降低API学习成本，使Java拥有函数式语言般的优雅，目标是“用一个工具方法代替一段复杂代码”。

### 关键概念/事件
-   **模块化设计**：包含hutool-core（核心）、hutool-json、hutool-crypto、hutool-db、hutool-http等18个独立模块，支持按需引入或`hutool-all`全量引入。
-   **JDK兼容性**：5.x版本支持JDK8+，Android平台未完全测试；JDK7需使用4.x版本。
-   **分支管理策略**：`v5-master`为发布分支（不接受PR），`v5-dev`为开发分支（接受PR），遵循无第三方依赖原则（extra模块除外）。
-   **安装方式**：支持Maven/Gradle依赖引入、非Maven项目Jar包引入及源码编译安装。

### 功能架构/设计思路
本章首先阐述Hutool的设计哲学（Hu+tool，寓意“难得糊涂”），对比传统开发模式与Hutool模式的差异（以MD5加密为例）。随后列出所有组件模块及其职责，明确版本兼容性与分支规范，最后提供多种安装途径与贡献代码规范（注释完备、Eclipse缩进、无新依赖）。

### 核心API/代码示例
> “Hutool的存在就是为了减少代码搜索成本，避免网络上参差不齐的代码出现导致的bug。” (p.9)

-   Maven坐标：`cn.hutool:hutool-all:5.6.0`
-   核心替换示例：`SecureUtil.md5()` 替代 搜索引擎+博客复制粘贴

## 第2章：更新记录

### 核心论点
-   **解决问题**：用户对新特性、Bug修复及版本变更内容的追踪需求。
-   **核心观点**：详细记录从5.0.0至5.6.0的版本迭代，体现工具的持续演进与社区反馈响应。

### 关键概念/事件
-   **v5.6.0新特性**：POI兼容5.x、FileTypeUtil长匹配优先、Convert增加汉字转数字、JSONUtil支持路径默认值等。
-   **关键Bug修复**：修复SimpleCache死锁、Snowflake时间回拨ID重复、Excel Sax读取公式错误、Linux下文件移动失败等问题。
-   **模块增强**：Crypto模块增加PBKDF2、SM2兼容各类密钥格式；Core模块增加StrMatcher、RadixUtil等。

### 功能架构/设计思路
按版本号倒序排列，每个版本下分“新特性”与“Bug修复”两类。条目格式统一为`【模块】变更内容（issue/pr编号）`，便于检索特定功能的演进历史与关联Issue。

### 核心API/代码示例
-   `Convert.chineseToNumber`：新增汉字转阿拉伯数字 (p.13)
-   `SecureUtil.hmacSha256`：新增HMAC-SHA256方法 (p.13)
-   `JSONUtil.getByPath`：支持默认值参数 (p.13)

## 第3章：核心（Hutool-core）

### 核心论点
-   **解决问题**：JDK原生API在类型转换、日期处理、IO操作、字符串处理等方面的繁琐与不一致。
-   **核心观点**：提供一套覆盖Java开发底层方方面面的静态工具集，作为项目中“util”包的友好替代，最大限度避免封装不完善带来的Bug。

### 关键概念/事件
-   **类型转换（Convert）**：统一转换入口，支持泛型、集合、日期、Unicode、Hex、金额大写及自定义转换器注册（ConverterRegistry）。
-   **日期时间（DateUtil/DateTime）**：封装Date/Calendar，提供解析、格式化、偏移、计时器、农历（ChineseDate）及JDK8 LocalDateTime适配。
-   **IO与文件（IoUtil/FileUtil）**：流拷贝、文件读写、监听（WatchMonitor）、Tailer、资源抽象（Resource/ClassPathResource）。
-   **JavaBean与反射**：Bean拷贝、Map互转、DynaBean动态操作、BeanPath表达式解析、Alias注解别名映射。
-   **集合与Map**：CollUtil/ListUtil/IterUtil增强操作，BiMap双向查找，TableMap可重复键值，Dict链式KV结构。

### 功能架构/设计思路
Core模块是Hutool基石，按功能域划分为克隆、转换、日期、IO、工具类、语言特性、JavaBean、集合、Map、编码、文本、比较器、异常、数学、线程、图片、网络等子章节。每个子章节均遵循“痛点分析 -> 工具封装 -> 代码示例”的结构，强调对JDK原生缺陷的弥补（如Cloneable泛型缺失、Properties不支持中文、正则API繁琐等）。

### 核心API/代码示例
> “Hutool中的工具方法来自每个用户的精雕细琢...既是大型项目开发中解决小问题的利器，也是小型项目中的效率担当。” (p.9)

-   `Convert.toStr(Object)` / `Convert.toList(TypeReference, Object)` (p.45)
-   `DateUtil.parse(String)` / `DateTime.of(Date)` (p.52)
-   `BeanUtil.copyProperties(source, target, CopyOptions)` (p.141)
-   `ReUtil.get(regex, content, groupIndex)` (p.119)
-   `IdUtil.getSnowflake(workerId, datacenterId)` (p.115)

## 第4章：日志（Hutool-log）

### 核心论点
-   **解决问题**：日志对象创建繁琐、Exception参数不支持占位符、多日志框架适配复杂。
-   **核心观点**：作为自动识别日志实现的门面，无需桥接包即可适配Slf4j/Logback/Log4j/JDK Logging/Console，并支持模板语法打印异常。

### 关键概念/事件
-   **LogFactory**：自动检测classpath下的日志框架jar包，按优先级创建对应Log实现；支持`setCurrentLogFactory`自定义全局实现。
-   **StaticLog**：提供纯静态方法调用，无需定义Log对象，适合极简场景。
-   **模板语法**：`log.error(e, "msg {}", arg)` 同时支持异常对象与占位符。
-   **配置约定**：无统一配置文件，直接使用各日志框架原生配置；未检测到配置文件时降级为Console输出。

### 功能架构/设计思路
先指出Slf4j等现有门面的不足，引出Hutool-log的改进点。详解自动适配原理与检测顺序，演示常规使用与自定义实现方式，最后解答常见警告信息（logging.properties缺失问题）。

### 核心API/代码示例
> “不需要桥接包而自动适配引入的日志框架，在无日志框架下依旧可以完美适配JDK Logging。” (p.207)

-   `Log log = LogFactory.get();` (p.208)
-   `StaticLog.info("This is static {} log.", "INFO");` (p.212)

## 第5章：缓存（Hutool-cache）

### 核心论点
-   **解决问题**：小型项目中Redis等外部缓存过重，JDK缺乏轻量级本地缓存实现。
-   **核心观点**：提供FIFO/LFU/LRU/Timed/Weak/File多种策略的简单内存缓存，受jodd-cache启发，适用于轻量级场景。

### 关键概念/事件
-   **缓存策略**：FIFO（先进先出）、LFU（最少使用）、LRU（最近最久未使用）、TimedCache（定时过期）、WeakCache（弱引用GC回收）。
-   **FileCache**：LFU/LRU文件缓存，将小文件byte[]缓存至内存，减少IO。
-   **CacheUtil**：统一工厂类，`newFIFOCache`/`newLRUCache`等快捷创建。
-   **定时清理**：TimedCache支持`schedulePrune`启动后台定时清理，否则仅在get时检查过期。

### 功能架构/设计思路
概述各策略优缺点与适用场景，逐一演示每种Cache的创建、put/get、过期设置及特殊机制（如WeakCache依赖GC、TimedCache定时器）。强调其定位为“简单实现”，复杂场景建议使用专业缓存中间件。

### 核心API/代码示例
-   `Cache<String,String> fifoCache = CacheUtil.newFIFOCache(3);` (p.218)
-   `timedCache.schedulePrune(5);` // 每5ms检查过期 (p.221)

## 第6章：JSON（Hutool-json）

### 核心论点
-   **解决问题**：JSON解析库众多但各有侧重，Hutool需在无强依赖前提下提供内置JSON支持。
-   **核心观点**：基于json.org改造，吸收FastJSON等优点，提供JSONObject/JSONArray/JSONUtil，实现Map/List接口无缝操作，支持Bean/XML/ResourceBundle互转。

### 关键概念/事件
-   **JSONObject/JSONArray**：实现Map/List接口，提供getStr/getInt等类型安全方法，避免强制转换。
-   **JSONUtil**：静态工具集，parseXXX/toXXX/readXXX/formatJsonStr等。
-   **Bean转换**：`parseObj(bean, ignoreNull, order)` 支持忽略空值与保持字段顺序；`toBean`反向转换。
-   **日期格式**：默认时间戳，可通过`setDateFormat`自定义。
-   **XML互转**：内置XML类支持JSON与XML字符串快速转换。

### 功能架构/设计思路
说明集成JSON模块的原因（工具链不可或缺），介绍核心类与辅助类。重点演示对象创建、字符串/Bean/Map解析、格式化输出、日期处理及与其他格式的转换能力。

### 核心API/代码示例
> “JSONObject实现了Map接口，JSONArray实现了List接口，这样我们便可以使用熟悉的API来操作JSON。” (p.225)

-   `JSONUtil.parseObj(userA, false, true)` // 保持有序 (p.230)
-   `json.setDateFormat("yyyy-MM-dd HH:mm:ss");` (p.231)

## 第7章：加密解密（Hutool-crypto）

### 核心论点
-   **解决问题**：JDK加密API分散、参数复杂，国密算法需额外库支持。
-   **核心观点**：统一封装对称/非对称/摘要/HMAC/签名/国密算法，提供SecureUtil快捷入口，集成BouncyCastle支持SM2/SM3/SM4。

### 关键概念/事件
-   **SecureUtil**：一站式工具，aes/des/rsa/md5/sha1/hmac/generateKey等。
-   **SymmetricCrypto**：AES/DES/DESede/RC2/PBE等，支持自定义Mode/Padding/IV。
-   **AsymmetricCrypto**：RSA/DSA/ECIES，支持公钥加密私钥解密、私钥签名公钥验签。
-   **Digester/HMac/Sign**：摘要、消息认证码、数字签名封装。
-   **SmUtil**：国密SM2（加密/签名）、SM3（摘要）、SM4（对称加密），需引入bcprov-jdk15to18。

### 功能架构/设计思路
按加密类型分节，每类先列JDK支持算法，再示Hutool封装用法。特别强调国密算法需BouncyCastle依赖，并给出SM2多种密钥构造方式（随机/自定义/曲线点/D值/Q值）。

### 核心API/代码示例
-   `SecureUtil.aes(key).encryptHex(content)` (p.237)
-   `SmUtil.sm2().encryptBcd(text, KeyType.PublicKey)` (p.249)
-   `DigestUtil.md5Hex(testStr)` (p.244)

## 第8章：DFA查找（Hutool-dfa）

### 核心论点
-   **解决问题**：敏感词过滤随关键词增多性能指数下降，HashSet遍历方案不可行。
-   **核心观点**：基于确定有穷自动机（DFA）构建关键词树，实现O(n)复杂度文本匹配，支持最短/最长匹配及跳过已匹配词。

### 关键概念/事件
-   **WordTree**：关键词树构建与匹配核心类。
-   **匹配模式**：`matchAll(text, -1, isDensity, isLongest)` 四象限组合（最短/最长 × 跳过/不跳过）。
-   **StopChar**：自动过滤特殊字符（如〓☆），提升脏文本匹配鲁棒性。
-   **性能优化**：`match`/`isMatch`找到首个即停止，优于全量匹配。

### 功能架构/设计思路
从实际业务痛点（内容清洗）引入DFA算法原理，演示WordTree构建与四种匹配模式的行为差异，说明特殊字符处理机制。

### 核心API/代码示例
> “每次查找正文只需要O(n)复杂度就可以搞定。” (p.253)

-   `tree.matchAll(text, -1, true, true)` // 最全关键词匹配 (p.254)

## 第9章：数据库（Hutool-db）

### 核心论点
-   **解决问题**：JDBC操作繁琐，ORM框架过重，小型项目单表操作缺乏轻量方案。
-   **核心观点**：基于ActiveRecord思想封装JDBC，使用Entity（Map）代替Bean，支持自动方言识别、事务Session、多数据源及命名占位符SQL。

### 关键概念/事件
-   **Entity**：继承HashMap的数据载体，兼作Where条件构造器。
-   **Db/SqlRunner**：CRUD封装，支持insert/del/update/find/page/query/execute/tx。
-   **Session**：单Connection事务操作，beginTransaction/commit/quietRollback。
-   **DsFactory**：自动识别HikariCP/Druid/Tomcat/C3P0/DBCP，统一db.setting配置，支持分组多数据源。
-   **命名占位符**：支持`:name`/`?name`/`@name`，简化复杂SQL参数绑定。

### 功能架构/设计思路
概述架构（DataSource/Executor/CRUD/Session/Handler/Dialect），演示MySQL增删改查、事务、命名查询、IN查询。详解db.setting配置与各连接池参数，说明多数据源分组机制。附Blob导出案例与驱动版本问题解答。

### 核心API/代码示例
-   `Db.use().insert(Entity.create("user").set("name", "test"))` (p.261)
-   `Db.use().query("select * from table where id=@id", paramMap)` (p.263)
-   `DSFactory.get("group_db1")` // 多数据源 (p.274)

## 第10章：HTTP客户端（Hutool-http）

### 核心论点
-   **解决问题**：Apache HttpClient庞大难用，OkHttp有学习成本，JDK HttpUrlConnection原始API繁琐。
-   **核心观点**：封装HttpUrlConnection，一行代码完成GET/POST/上传/下载，自动处理HTTPS/Cookie/Gzip/编码/304跳转。

### 关键概念/事件
-   **HttpUtil**：极简API，get/post/downloadFile/download，支持StreamProgress回调。
-   **HttpRequest**：链式调用，header/form/body/timeout/proxy/basicAuth/sslProtocol。
-   **HttpResponse**：getStatus/body/header/contentEncoding/isGzip。
-   **HtmlUtil**：escape/unescape/removeHtmlTag/cleanHtmlTag/filter（XSS防护）。
-   **UserAgentUtil**：解析UA字符串，获取浏览器/引擎/OS/平台/移动端信息。
-   **SimpleServer**：基于com.sun.net.httpserver封装，支持Action路由、静态文件服务、文件上传。

### 功能架构/设计思路
对比主流HTTP库优劣，突出Hutool轻量易用。分述HttpUtil（便捷）与HttpRequest（灵活）使用场景，演示文件上传下载、HTML处理、UA解析。附爬取开源中国资讯实战案例与SoapClient/SimpleServer用法。

### 核心API/代码示例
> “根据URL自动判断是请求HTTP还是HTTPS，不需要单独写多余的代码。” (p.285)

-   `HttpUtil.post(url, paramMap)` (p.286)
-   `HttpRequest.post(url).header(...).form(...).timeout(20000).execute().body()` (p.290)
-   `SoapClient.create(url).setMethod(...).setParam(...).send(true)` (p.304)

## 第11章：定时任务（Hutool-cron）

### 核心论点
-   **解决问题**：Quartz过于庞大复杂，Spring整合XML繁琐，Linux Crontab表达式直观但Java生态缺轻量实现。
-   **核心观点**：类Crontab配置文件驱动，兼容Cron4j/Quartz表达式，支持秒级精度与动态任务，start()即用。

### 关键概念/事件
-   **cron.setting**：配置文件定义任务，支持分组`[package]`，格式`Class.method=cron_expr`。
-   **CronUtil**：start()/stop()/setMatchSecond(true)/schedule()动态添加。
-   **表达式兼容**：5位（Cron4j）/6位（Quartz含秒）自动适配。
-   **守护模式**：`start(true)`设为daemon线程，stop时立即中断作业。

### 功能架构/设计思路
对比Quartz痛点，展示cron.setting配置与启动代码。说明秒匹配开关、动态任务API及守护线程注意事项。

### 核心API/代码示例
-   `CronUtil.start();` (p.310)
-   `CronUtil.setMatchSecond(true);` // 兼容Quartz (p.311)
-   `TestJob.run=*/10 * * * *` // 配置文件示例 (p.310)

## 第12章：扩展（Hutool-extra）

### 核心论点
-   **解决问题**：第三方优秀库（邮件/二维码/SSH/FTP/模板/分词/Spring/Cglib/拼音）API各异，直接耦合增加迁移成本。
-   **核心观点**：以可选依赖+门面模式封装第三方库，统一API，自动SPI识别实现，做到“一段代码，随意更换底层”。

### 关键概念/事件
-   **MailUtil**：封装javax.mail，mail.setting配置，支持SSL/STARTTLS/QQ授权码/Foxmail别名。
-   **QrCodeUtil**：封装Zxing，生成/解析二维码，支持Logo/纠错级别/颜色自定义。
-   **ServletUtil**：getParamMap/fillBean/getClientIP/getCookie/write等Servlet工具。
-   **TemplateUtil**：模板引擎门面，自动识别Beetl/Enjoy/Rythm/FreeMarker/Velocity/Thymeleaf。
-   **JschUtil/Ftp**：SSH端口映射、堡垒机穿透、FTP上传下载。
-   **TokenizerUtil**：中文分词门面，自动识别Ansj/HanLP/IK/Jcseg/Jieba/mmseg4j/Smartcn。
-   **SpringUtil/CglibUtil/PinyinUtil**：Spring Bean获取、高性能Bean拷贝、拼音转换门面。

### 功能架构/设计思路
每个子节遵循“引入依赖 -> 配置（如需）-> 统一API调用”模式。强调Hutool不强制依赖任何第三方，用户按需引入jar即可自动激活对应功能。

### 核心API/代码示例
-   `MailUtil.send("to@test.com", "标题", "内容", false)` (p.315)
-   `QrCodeUtil.generate(url, 300, 300, file)` (p.318)
-   `TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());` (p.323)
-   `SpringUtil.getBean("testDemo")` (p.331)

## 第13章：布隆过滤（Hutool-bloomFilter）

### 核心论点
-   **解决问题**：海量数据集合成员判断，传统数据结构空间与时间效率不足。
-   **核心观点**：提供BitMapBloomFilter简单实现，利用位数组与多Hash函数实现高效概率型成员查询。

### 关键概念/事件
-   **BitMapBloomFilter**：初始化容量，add/contains操作。
-   **原理**：K个Hash函数映射K个点置1，查询时全1则可能存在，有0则一定不存在。
-   **特性**：空间/查询时间远超一般算法，但有误识率且删除困难。

### 功能架构/设计思路
简述布隆过滤器原理与优缺点，给出初始化与add/contains代码示例。

### 核心API/代码示例
-   `BitMapBloomFilter filter = new BitMapBloomFilter(10);` (p.336)
-   `filter.contains("abc")` (p.336)

## 第14章：切面（Hutool-aop）

### 核心论点
-   **解决问题**：JDK动态代理API繁琐，缺乏轻量级AOP实现。
-   **核心观点**：封装JDK Proxy与Cglib，抽象Aspect切面，ProxyUtil一行代码生成代理对象，支持方法执行前后增强。

### 关键概念/事件
-   **ProxyUtil**：`proxy(target, aspectClass)` 生成代理。
-   **SimpleAspect**：空实现基类，重写before/after/afterException。
-   **TimeIntervalAspect**：内置计时切面，日志打印方法耗时。
-   **Cglib支持**：无需接口即可代理，API一致。

### 功能架构/设计思路
演示JDK动态代理与Cglib代理的使用对比，展示TimeIntervalAspect效果，简述代理对象创建原理（$Proxy0生成/加载/实例化/InvocationHandler调用）。

### 核心API/代码示例
-   `Animal cat = ProxyUtil.proxy(new Cat(), TimeIntervalAspect.class);` (p.339)
-   `Dog dog = ProxyUtil.proxy(new Dog(), TimeIntervalAspect.class);` // Cglib (p.340)

## 第15章：脚本（Hutool-script）

### 核心论点
-   **解决问题**：javax.script API直接使用较繁琐。
-   **核心观点**：ScriptUtil封装Javascript脚本执行与编译，提供eval/compile快捷方法。

### 关键概念/事件
-   **ScriptUtil.eval**：直接执行脚本字符串。
-   **ScriptUtil.compile**：编译为CompiledScript对象，可重复执行。

### 功能架构/设计思路
极简封装，仅两个核心方法，附代码示例。

### 核心API/代码示例
-   `ScriptUtil.eval("print('Script test!');");` (p.344)
-   `CompiledScript script = ScriptUtil.compile("...");` (p.344)

## 第16章：Office文档操作（Hutool-poi）

### 核心论点
-   **解决问题**：Apache POI功能强大但API复杂，Excel/Word操作代码冗长。
-   **核心观点**：封装POI，提供ExcelReader/ExcelWriter/BigExcelWriter/Word07Writer，简化读写、Sax大数据读取、样式自定义及Word生成。

### 关键概念/事件
-   **ExcelReader**：read()/readAll()/readAll(Class) 读取为List/Map/Bean；Sax模式（Excel03SaxReader/Excel07SaxReader）防OOM。
-   **ExcelWriter**：write(rows, true)/merge/addHeaderAlias/setSheet/getStyleSet，支持xls/xlsx/流/Servlet下载。
-   **BigExcelWriter**：大数据量写出，防止内存溢出。
-   **Word07Writer**：addText(Font, texts...) 分段写出docx。
-   **依赖要求**：poi-ooxml ≥4.1.2（5.x），xercesImpl ≥2.12.0（Sax读取）。

### 功能架构/设计思路
分读取、写出、大数据、Word四块。读取强调普通模式与Sax模式区别；写出演示List/Map/Bean/自定义标题/样式/多Sheet/Servlet下载；大数据单独BigExcelWriter；Word演示段落添加。附文件损坏排查指南。

### 核心API/代码示例
-   `ExcelUtil.getReader("aaa.xlsx").readAll(Person.class)` (p.350)
-   `ExcelUtil.read07BySax("aaa.xlsx", 0, rowHandler)` (p.352)
-   `writer.addHeaderAlias("name", "姓名");` (p.357)
-   `BigExcelWriter writer = ExcelUtil.getBigWriter("e:/xxx.xlsx");` (p.361)

## 第17章：系统属性调用-SystemUtil

### 核心论点
-   **解决问题**：System.getProperty(name) 键名难记、信息分散。
-   **核心观点**：封装系统属性获取，提供Jvm/Java/OS/User/Host/Runtime等信息的结构化对象。

### 关键概念/事件
-   **信息分类**：JvmSpecInfo/JvmInfo/JavaSpecInfo/JavaInfo/JavaRuntimeInfo/OsInfo/UserInfo/HostInfo/RuntimeInfo。
-   **RuntimeInfo**：内存总大小/已用/可用等运行时指标。

### 功能架构/设计思路
列出所有getXXXInfo方法及其返回内容，无复杂逻辑。

### 核心API/代码示例
-   `SystemUtil.getOsInfo()` (p.363)
-   `SystemUtil.getRuntimeInfo()` (p.364)

## 第18章：图形验证码（Hutool-captcha）

### 核心论点
-   **解决问题**：验证码生成与校验需求普遍，自行实现成本高。
-   **核心观点**：提供Line/Circle/Shear三种干扰验证码，ICaptcha接口统一createCode/verify/write，支持自定义CodeGenerator。

### 关键概念/事件
-   **ICaptcha/AbstractCaptcha**：核心接口与抽象实现，定义生成/验证/写出契约。
-   **三种实现**：LineCaptcha（线段）、CircleCaptcha（圆圈）、ShearCaptcha（扭曲）。
-   **CodeGenerator**：自定义验证码内容，如RandomGenerator（纯数字）、MathGenerator（四则运算）。
-   **写出目标**：文件/流/Servlet OutputStream。

### 功能架构/设计思路
介绍接口体系与三种内置实现，演示生成/写出/验证流程。展示自定义纯数字与算术验证码，附Servlet输出示例。

### 核心API/代码示例
-   `CaptchaUtil.createLineCaptcha(200, 100)` (p.365)
-   `captcha.setGenerator(new MathGenerator());` (p.367)

## 第19章：网络Socket（Hutool-socket）

### 核心论点
-   **解决问题**：JDK NIO/AIO原生API复杂，开发门槛高。
-   **核心观点**：简单封装NioServer/NioClient/AioServer/AioClient，简化异步Socket开发，但推荐生产环境使用Netty/t-io等专业框架。

### 关键概念/事件
-   **NIO封装**：NioServer/NioClient，ChannelHandler回调，ByteBuffer读写。
-   **AIO封装**：AioServer/AioClient，SimpleIoAction（accept/doAction），异步读写。
-   **定位声明**：非完整框架，仅作简单封装，高性能场景请用专业库。

### 功能架构/设计思路
简述Socket/BIO/NIO/AIO背景，明确Hutool封装的轻量定位。分别给出NIO服务端/客户端、AIO服务端/客户端的完整代码示例。

### 核心API/代码示例
-   `new NioServer(8080).setChannelHandler(...).listen()` (p.370)
-   `new AioServer(8899).setIoAction(...).start(true)` (p.372)

## 附录：致谢与捐赠使用公开

### 核心论点
-   **解决问题**：开源项目的社区感恩与资金透明度。
-   **核心观点**：感谢贡献者与捐赠者，公开域名/群福利/VIP/辣条等资金使用明细，接受社区监督。

### 关键概念/事件
-   **友情项目**：feilong-core、t-io、ActFramework、Voovan、JustAuth等互相借鉴与支持。
-   **捐赠用途**：hutool.cn域名（¥105）、群红包（¥100）、QQ群VIP（¥228）、作者辣条（¥2.5）等。
-   **书栈网致谢**：文档由进击的皇虫使用BookStack.CN构建，呼吁知识分享与纠错。

### 功能架构/设计思路
列出合作开源项目链接与简介，表格公示2017-2019年捐赠支出，表达持续维护决心。

### 核心API/代码示例
> “每一个issue，每一句吐槽都是Hutool进步的动力。” (p.376)

-   捐赠地址：https://gitee.com/loolly/hutool (p.376)