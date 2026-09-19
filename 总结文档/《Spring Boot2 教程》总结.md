# 《Spring Boot2 教程》总结

## 第1章：Spring Boot 基础与环境搭建

### 核心论点

本章旨在解决传统 SSM/SSH 框架配置繁琐的问题，介绍 Spring Boot “开箱即用”的特性，并演示如何通过多种方式快速创建 Spring Boot 工程。
核心观点：Spring Boot 通过自动化配置和 Starter 依赖，让 Java 开发回归简单，无需 XML 配置即可快速构建生产级应用。

### 关键概念/事件

- **Starter 依赖**：一系列方便的依赖描述符，简化 Maven/Gradle 配置，如 `spring-boot-starter-web`。
- **parent POM**：`spring-boot-starter-parent` 提供了默认的 Java 版本、编码格式、依赖版本管理及插件配置。
- **项目创建方式**：介绍了在线创建（start.spring.io）、IDE 创建（IntelliJ IDEA/STS）以及手动 Maven 创建三种方式。
- **纯 Java 配置 SSM**：展示了如何在不使用 XML 的情况下，通过 `@Configuration`、`@ComponentScan` 和 `WebApplicationInitializer` 搭建 SSM 环境。

### 逻辑推演/叙事脉络

作者首先对比了传统 Java Web 开发的痛点（配置繁琐），引出 Spring Boot 的优势。接着详细讲解了 Spring Boot 的系统要求（JDK 8+, Maven 3.3+）。随后，通过三种不同的方式演示了项目的创建过程，并深入解析了 `pom.xml` 中 parent 的作用。最后，通过一个纯 Java 配置 SSM 的案例，展示了 Spring Boot 底层对 Spring 配置的简化逻辑，为后续理解自动化配置打下基础。

### 经典金句/数据

> “Spring Boot 的出现让 Java 开发又回归简单，因为确确实实解决了开发中的痛点... 绝对没有代码生成，也不需要 XML 配置。”

## 第2章：配置文件与属性注入

### 核心论点

本章解决如何在 Spring Boot 中管理外部配置的问题，重点对比 properties 和 YAML 格式，并讲解类型安全的属性注入机制。
核心观点：YAML 配置因其有序性和简洁性在复杂场景下优于 properties；`@ConfigurationProperties` 是实现类型安全配置注入的最佳实践。

### 关键概念/事件

- **配置文件位置优先级**：`config/` (项目根目录) > 根目录 > `classpath:/config/` > `classpath:/`。
- **YAML vs Properties**：YAML 支持数组、有序配置，但不支持 `@PropertySource`；Properties 无序但兼容性好。
- **类型安全注入**：使用 `@ConfigurationProperties(prefix="book")` 将配置文件中的属性批量绑定到 Bean 字段，替代繁琐的 `@Value`。
- **Profile 多环境配置**：通过 `application-dev.properties` 等文件实现不同环境的配置隔离。

### 逻辑推演/叙事脉络

作者首先介绍了 Spring Boot 默认加载 `application.properties` 的机制，并列出了四个默认查找路径及其优先级。接着，引入 YAML 格式，分析其优缺点及适用场景。随后，从传统的 `@Value` 注入过渡到更高效的 `@ConfigurationProperties`，展示了如何定义配置类并自动绑定属性。最后，简要提及了自定义配置文件名和位置的进阶用法。

### 经典金句/数据

> “YAML 中的数据是有序的，properties 中的数据是无序的，在一些需要路径匹配的配置中，顺序就显得尤为重要... 此时我们一般采用 yaml。”

## 第3章：自定义 Starter 与自动化配置原理

### 核心论点

本章深入探讨 Spring Boot 的核心魔法——自动化配置，通过手写一个自定义 Starter 来揭示 `@Conditional` 注解和 `spring.factories` 的工作原理。
核心观点：Starter 的本质是利用条件注解（`@Conditional`）根据 classpath 下的类是否存在来决定是否加载配置，并通过 `spring.factories` 注册自动配置类。

### 关键概念/事件

- **@Conditional 注解**：Spring 4 引入的条件化装配机制，如 `@ConditionalOnClass`，是自动化配置的基石。
- **spring.factories**：位于 `META-INF/` 下，用于声明自动配置类，Spring Boot 启动时读取此文件加载配置。
- **自定义 Starter 步骤**：创建 Maven 项目 -> 定义属性类 (`HelloProperties`) -> 定义服务类 (`HelloService`) -> 编写自动配置类 (`HelloServiceAutoConfiguration`) -> 配置 `spring.factories`。
- **EnableConfigurationProperties**：使 `@ConfigurationProperties` 注解的类生效并注册为 Bean。

### 逻辑推演/叙事脉络

作者先解释了 Starter 并非新技术，而是基于 Spring 已有功能的封装。接着，通过一个具体的“Hello”示例，分步演示了如何创建一个包含属性绑定、服务定义和自动配置的 Starter。重点讲解了 `@ConditionalOnClass` 如何控制配置生效，以及 `spring.factories` 如何让 Spring Boot 发现这个配置。最后，演示了如何在其他项目中引入并使用这个自定义 Starter。

### 经典金句/数据

> “条件注解并非一个新事物... Spring Boot 中，大量的自动化配置都是通过条件注解来实现的... 这正是 Starter 配置的核心之一。”

## 第4章：Web 开发：HTTPS、静态资源与模板引擎

### 核心论点

本章涵盖 Spring Boot Web 开发的常见需求，包括 HTTPS 配置、静态资源映射规则以及 Thymeleaf/Freemarker 模板引擎的整合。
核心观点：Spring Boot 内嵌容器使得 HTTPS 配置极简；静态资源有固定的默认查找路径；模板引擎整合零配置，只需引入依赖。

### 关键概念/事件

- **HTTPS 配置**：通过 `server.ssl.*` 属性配置密钥库，利用 `TomcatServletWebServerFactory` 实现 HTTP 到 HTTPS 的重定向。
- **静态资源映射**：默认从 `classpath:/static/`, `/public/`, `/resources/`, `/META-INF/resources/` 加载，映射路径为 `/`。
- **Thymeleaf 整合**：引入 starter 后自动配置，默认模板位置 `classpath:/templates/`，后缀 `.html`。支持在 JS 中获取 Model 变量。
- **Freemarker 整合**：类似 Thymeleaf，默认后缀 `.ftl`（2.2.0 后变为 `.ftlh`），支持自定义配置。

### 逻辑推演/叙事脉络

作者首先讲解了如何使用 JDK 工具生成证书并在 Spring Boot 中配置 HTTPS，以及如何通过代码实现 HTTP 强制跳转 HTTPS。接着，详细剖析了 Spring Boot 处理静态资源的默认策略及源码依据，并展示了自定义静态资源路径的方法。最后，分别介绍了 Thymeleaf 和 Freemarker 的整合过程，强调了“引入依赖即可用”的便捷性，并展示了基本的模板渲染和数据传递。

### 经典金句/数据

> “Spring Boot 中，默认情况下，一共有 5 个位置可以放静态资源... 请求地址中并不需要 static，如果加上了 static 反而多此一举会报 404 错误。”

## 第5章：异常处理与跨域解决方案

### 核心论点

本章解决 Web 开发中的两个通用问题：全局异常处理和跨域访问（CORS）。
核心观点：`@ControllerAdvice` 结合 `@ExceptionHandler` 可实现优雅的全局异常处理；后端配置 CORS 比前端 JSONP 更灵活且支持所有 HTTP 方法。

### 关键概念/事件

- **@ControllerAdvice**：增强型 Controller，用于全局异常处理、数据绑定和数据预处理。
- **全局异常处理**：定义统一异常处理类，捕获特定异常并返回统一格式（如 ModelAndView 或 JSON）。
- **CORS 跨域**：通过 `@CrossOrigin` 注解或全局配置 `WebMvcConfigurer.addCorsMappings` 解决跨域问题。
- **CSRF 防护**：简要提及 CORS 可能带来的 CSRF 风险及浏览器预检请求机制。

### 逻辑推演/叙事脉络

作者首先引入 `@ControllerAdvice` 的概念，展示如何通过它实现全局异常捕获，避免在每个 Controller 中重复编写 try-catch。接着，转向跨域问题，解释同源策略限制，对比 JSONP 的局限性，重点推荐 CORS 方案。演示了方法级别的 `@CrossOrigin` 和全局配置两种方式，并简要说明了其底层原理（响应头 `Access-Control-Allow-Origin`）。

### 经典金句/数据

> “JSONP 虽然能解决跨域但是有一个很大的局限性，那就是只支持 GET 请求... CORS 是一个 W3C 标准... 提供了 Web 服务从不同网域传来沙盒脚本的方法。”

## 第6章：任务调度与热部署

### 核心论点

本章介绍 Spring Boot 中的定时任务实现（`@Scheduled` 和 Quartz）以及开发阶段的热部署工具（DevTools）。
核心观点：简单定时任务首选 `@Scheduled`，复杂任务使用 Quartz；DevTools 通过双 ClassLoader 机制实现快速重启，配合 LiveReload 可实现静态资源实时刷新。

### 关键概念/事件

- **@Scheduled**：支持 `fixedRate`、`fixedDelay`、`cron` 表达式，需启用 `@EnableScheduling`。
- **Quartz 整合**：定义 `JobDetail` 和 `Trigger`，支持更复杂的调度逻辑和集群能力。
- **Spring Boot DevTools**：监听 classpath 变化，利用 restart ClassLoader 快速重启应用。
- **LiveReload**：嵌入在 DevTools 中，配合浏览器插件实现静态资源修改后的自动刷新，无需重启。

### 逻辑推演/叙事脉络

作者先讲解了 Spring 自带的 `@Scheduled` 注解用法，包括各种时间参数和 Cron 表达式。随后，针对更复杂的场景，介绍了 Quartz 的整合，包括 Job 的定义和 Trigger 的配置。接着，转向开发体验优化，介绍 DevTools 的原理（Base vs Restart ClassLoader）和使用方法，并特别推荐了 LiveReload 插件以提升前端开发效率。

### 经典金句/数据

> “Spring Boot 中热部署最最关键的原理就是两个不同的 classloader... base classloader 用来加载那些不会变化的类... restart classloader 则用来加载那些会发生变化的类。”

## 第7章：数据持久化：JdbcTemplate、MyBatis 与 JPA

### 核心论点

本章系统讲解 Spring Boot 中三种主流数据持久化方案的整合与使用：JdbcTemplate、MyBatis 和 Spring Data JPA，并涉及多数据源配置。
核心观点：JdbcTemplate 最简单但功能有限；MyBatis 灵活且流行；JPA 标准化且开发效率高；多数据源配置需手动管理 DataSource 和 SessionFactory/Template。

### 关键概念/事件

- **JdbcTemplate**：自动化配置，直接注入使用。支持增删改查，主键回填需 `PreparedStatementCreator`。
- **MyBatis**：引入 `mybatis-spring-boot-starter`。支持注解 SQL 和 XML Mapper。需配置 `mapper-locations` 或包扫描。
- **Spring Data JPA**：基于 Hibernate。定义 Repository 接口继承 `JpaRepository`，方法名规范查询（如 `findByUsername`）。支持 `@Query` 自定义 JPQL/Native SQL。
- **多数据源**：需手动配置多个 `DataSource` Bean，并分别为 JdbcTemplate/MyBatis/JPA 创建对应的 Template/SessionFactory/EntityManagerFactory，注意使用 `@Primary` 和 `@Qualifier`。

### 逻辑推演/叙事脉络

作者按复杂度递增顺序介绍三种方案。首先是最简单的 JdbcTemplate，展示基本 CRUD。其次是 MyBatis，涵盖注解式和 XML 式两种写法，以及 Mapper 扫描配置。然后是 JPA，强调其“方法名即查询”的特性及 Repository 接口体系。最后，针对高级需求，分别演示了这三种技术在多数据源场景下的配置差异，指出 JPA 多数据源配置最为复杂，需仔细管理 Entity Manager 和事务管理器。

### 经典金句/数据

> “Spring Data JPA 致力于减少数据访问层(DAO)的开发量。开发者唯一要做的，就是声明持久层的接口，其他都交给 Spring Data JPA 来帮你完成！”

## 第8章：缓存整合：Redis 与 Ehcache

### 核心论点

本章讲解如何在 Spring Boot 中整合 Redis 和 Ehcache 作为缓存实现，重点介绍 Spring Cache 抽象层的使用。
核心观点：Spring Cache 提供了统一的缓存抽象（`@Cacheable`, `@CachePut`, `@CacheEvict`），底层可无缝切换 Redis 或 Ehcache，推荐使用 Spring Cache 注解而非直接操作模板。

### 关键概念/事件

- **Spring Data Redis**：默认使用 Lettuce 连接池。提供 `RedisTemplate` 和 `StringRedisTemplate`。需注意 Key/Value 序列化问题（默认 JDK 序列化可能导致乱码）。
- **Spring Cache + Redis**：启用 `@EnableCaching`，配置 `RedisCacheManager`。使用 `@Cacheable` 等注解 declaratively 管理缓存。
- **Spring Cache + Ehcache**：引入 Ehcache 依赖，配置 `ehcache.xml`。同样使用 Spring Cache 注解，体验一致。
- **缓存注解**：`@CacheConfig` 类级别配置；`@Cacheable` 查询缓存；`@CachePut` 更新缓存；`@CacheEvict` 清除缓存。

### 逻辑推演/叙事脉络

作者首先介绍 Redis 的整合，指出直接操作 `RedisTemplate` 的繁琐，进而引入 Spring Cache 抽象。详细讲解了 `@Cacheable` 等核心注解的使用及 Key 生成策略。随后，以 Ehcache 为例，展示如何替换底层缓存实现而无需修改业务代码，强调了 Spring Cache 的解耦优势。最后，对比了两种缓存方案的配置差异。

### 经典金句/数据

> “Spring Cache 和 Redis、Ehcache 的关系就像 JDBC 与各种数据库驱动的关系... 从 Spring Cache 提供的统一接口来看，实现既可以是 Redis，也可以是 Ehcache。”

## 第9章：RESTful API 与 Swagger2 文档

### 核心论点

本章介绍如何快速构建 RESTful 风格 API，并利用 Swagger2 自动生成接口文档，解决前后端分离中的文档维护难题。
核心观点：Spring Data REST 可零代码暴露 JPA Repository 为 REST API；Swagger2 通过注解自动生成在线文档，极大降低沟通成本。

### 关键概念/事件

- **Spring Data REST**：引入 `spring-boot-starter-data-rest`，自动将 Repository 暴露为 REST 端点。支持分页、排序、自定义查询接口。
- **Swagger2 整合**：引入依赖，配置 `Docket` Bean，设置扫描包和 API 信息。
- **Swagger 注解**：`@Api`（类）、`@ApiOperation`（方法）、`@ApiImplicitParam`（参数）、`@ApiModelProperty`（模型字段）。
- **Security 集成**：若整合 Spring Security，需放行 Swagger 相关路径（`/swagger-ui.html`, `/v2/api-docs` 等）。

### 逻辑推演/叙事脉络

作者先简述 RESTful 理念，然后展示 Spring Data REST 如何仅凭一个 Repository 接口就生成完整的 CRUD API，并演示了定制查询和路径映射。接着，针对文档维护痛点，引入 Swagger2。详细讲解了配置类和常用注解的使用，展示了生成的在线文档界面及其测试功能。最后，提醒了在安全框架下需额外配置放行规则。

### 经典金句/数据

> “接口总是在不断的变化之中，有变化就要去维护，做过的小伙伴都知道这件事有多么头大！还好，有一些工具可以减轻我们的工作量，Swagger2 就是其中之一。”

## 第10章：安全框架：Spring Security 与 Shiro

### 核心论点

本章对比并演示了 Spring Security 和 Shiro 在 Spring Boot 中的整合，重点讲解 Spring Security 的内存用户配置、表单登录定制及 JWT 无状态认证。
核心观点：Spring Boot 生态下推荐 Spring Security，因其自动化配置更完善；JWT 适合前后端分离的无状态认证，需自定义过滤器处理 Token。

### 关键概念/事件

- **Shiro 整合**：配置 Realm、SecurityManager、ShiroFilterFactoryBean。支持原生配置和 Starter 两种方式。
- **Spring Security 基础**：引入依赖即启用保护。默认用户 `user`，密码随机。可通过 properties 或 Java Config 配置内存用户。
- **登录定制**：重写 `WebSecurityConfigurerAdapter`，配置 `formLogin`，自定义登录页、成功/失败处理器（返回 JSON）。
- **JWT 整合**：无状态登录。自定义 `JwtLoginFilter`（验证账号密码生成 Token）和 `JwtFilter`（解析 Token 设置 Authentication）。
- **验证码与 JSON 登录**：通过自定义 Filter 在 Security 链中加入验证码校验；重写 `UsernamePasswordAuthenticationFilter` 支持 JSON 格式登录。

### 逻辑推演/叙事脉络

作者先对比 Shiro 和 Spring Security 的优劣，建议在 Spring Boot 中使用后者。接着演示 Shiro 的基本整合。随后重点展开 Spring Security，从最简单的内存用户开始，逐步深入到定制登录流程、处理前后端分离的 JSON 交互。最后，针对微服务/前后端分离场景，详细介绍了结合 JWT 实现无状态认证的完整流程，包括 Token 生成、解析过滤器的编写及配置。

### 经典金句/数据

> “如果是 Spring Boot 项目，一般选择 Spring Security... 只要加入依赖，项目的所有接口都会被自动保护起来。”

## 第11章：邮件发送与微信公众号开发

### 核心论点

本章介绍实用功能集成：邮件发送（简单、附件、模板）和微信公众号后台开发（服务器验证、消息接收与回复）。
核心观点：Spring Boot Mail 简化了邮件发送；微信开发核心在于签名验证和 XML 消息的解析与构建。

### 关键概念/事件

- **邮件发送**：配置 SMTP 信息。使用 `JavaMailSender`。支持简单文本、附件、Inline 图片。推荐结合 Thymeleaf/Freemarker 制作邮件模板。
- **微信服务器验证**：GET 请求校验 signature（token, timestamp, nonce SHA1 加密）。
- **消息处理**：POST 请求接收微信服务器转发的 XML 消息。解析 XML，根据 MsgType 分发处理。
- **消息回复**：构建符合微信格式的 XML 响应（TextMessage 等），返回给微信服务器。

### 逻辑推演/叙事脉络

作者首先讲解邮件发送，从基础的 SimpleMailMessage 到复杂的 MimeMessageHelper（附件、图片），再到结合模板引擎生成 HTML 邮件。随后转向微信开发，解释了微信消息交互流程（用户->微信服务器->开发者服务器）。详细演示了 GET 请求的签名验证逻辑，以及 POST 请求中 XML 的解析、业务处理和 XML 响应的构建过程。

### 经典金句/数据

> “Spring Boot 中对于邮件发送，提供了相关的自动化配置类，使得邮件发送变得非常容易... 微信服务器的消息都是通过 POST 请求发给我的。”

## 第12章：部署与 Docker 集成

### 核心论点

本章讲解 Spring Boot 应用的打包（Jar/War 区别）及容器化部署（Docker），介绍多种 Docker 集成方案。
核心观点：Spring Boot Jar 是可执行 Jar，结构不同于普通 Jar；Docker 部署可通过 Maven 插件一键构建镜像，或使用 Jib 无需 Docker 环境构建。

### 关键概念/事件

- **Executable Jar**：由 `spring-boot-maven-plugin` repackage 生成。结构包含 `BOOT-INF/classes` 和 `BOOT-INF/lib`。不可被其他项目直接依赖。
- **Docker Maven Plugin**：配置 Dockerfile 和插件，执行 `mvn package` 自动构建镜像并推送到远程 Docker 主机。
- **Jib**：Google 开源工具，无需本地 Docker 环境，直接将应用构建成镜像并推送到 Registry。分层构建，速度快。
- **远程 Docker 部署**：IDEA 插件连接远程 Docker Daemon，可视化部署和管理容器。

### 逻辑推演/叙事脉络

作者先解释了 Spring Boot Jar 的特殊结构及其与普通 Jar 的区别，说明了为何不能直接依赖。接着，介绍了传统的 Docker 部署方式：编写 Dockerfile，使用 Maven 插件构建。随后，引入了更先进的 Jib 方案，强调其无需 Docker 守护进程的优势。最后，展示了如何利用 IDEA 插件简化远程 Docker 的操作流程。

### 经典金句/数据

> “Spring Boot 打成的 jar 可以执行，但是不可以被其他的应用所依赖... 可执行 jar 并不是 Spring Boot 独有的，Java 工程本身就可以打包成可执行 jar。”

---
