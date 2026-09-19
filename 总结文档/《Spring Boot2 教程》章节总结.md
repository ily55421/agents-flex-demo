# 《Spring Boot2 教程》章节总结

## 目录说明
- 本书为技术教程类文档，非传统章节式书籍。总结依据 PDF 中的主要标题层级进行逻辑分章。
- 由于原文是由多篇独立博客/教程文章汇编而成，部分“章节”实为独立的技术主题。
- 章节划分依据正文中的大标题（如“Spring Boot介绍”、“配置文件”、“数据持久化”等）进行归纳整理。

## 第1章：纯 Java 搭建 SSM 环境与 Spring Boot 简介
### 核心论点
- **问题**：传统 SSM 框架配置繁琐，XML 易出错；Spring Boot 如何解决这一痛点？
- **观点**：Spring Boot 通过“约定优于配置”和自动化配置，消除了 XML 配置，使 Java 开发回归简单，是微服务架构的基础。

### 关键概念/事件
- **纯 Java 配置 SSM**：使用 `@Configuration`、`@ComponentScan` 和 `WebApplicationInitializer` 替代 `web.xml` 和 XML 配置文件，实现无 XML 的 SSM 环境搭建。
- **Spring Boot 核心特性**：开箱即用、内嵌服务器（Tomcat/Jetty）、无需代码生成、无需 XML 配置、提供非功能性功能（监控、安全等）。
- **项目创建方式**：在线创建（start.spring.io）、IDE 创建（IntelliJ IDEA/STS）、Maven 手动创建。

### 逻辑推演/叙事脉络
本章首先回顾了如何使用纯 Java 代码（注解）替代传统的 XML 来搭建 SSM 环境，展示了 `WebApplicationInitializer` 的作用及静态资源、视图解析器的 Java 配置方式。随后引出 Spring Boot，介绍其背景、优势及系统要求，并详细演示了三种创建 Spring Boot 工程的方法，最后分析了项目结构及 `pom.xml` 中 `parent` 依赖的作用（版本管理、编码设置、插件配置等）。

### 经典金句/数据
> “Spring Boot的出现让 Java开发又回归简单，因为确确实实解决了开发中的痛点...所有的 Java工程师都有必要掌握好 Spring Boot。”

## 第2章：配置文件与属性注入
### 核心论点
- **问题**：Spring Boot 中如何管理配置？properties 与 yaml 有何区别？如何优雅地注入属性？
- **观点**：YAML 因有序性和简洁性更受推荐；类型安全的属性注入（`@ConfigurationProperties`）比 `@Value` 更高效且不易出错。

### 关键概念/事件
- **配置文件位置与优先级**：默认加载顺序为 `config/` (根目录) > 根目录 > `config/` (classpath) > classpath。可通过 `spring.config.location` 自定义。
- **Properties vs YAML**：YAML 支持有序配置（对 Zuul 等路由规则重要），支持数组/对象嵌套，但不支持 `@PropertySource`。
- **属性注入方式**：
    - `@Value`：逐个注入，适合少量配置。
    - `@ConfigurationProperties`：类型安全，批量注入到 Bean，推荐用于复杂配置。

### 逻辑推演/叙事脉络
本章首先介绍了 Spring Boot 配置文件的四种默认存放位置及其优先级，以及自定义路径和文件名的方法。接着对比了 properties 和 yaml 两种格式的优劣，重点讲解了 YAML 的数组注入和有序性特点。最后深入讲解了属性注入，从传统的 `@Value` 过渡到 Spring Boot 推荐的类型安全属性注入 `@ConfigurationProperties`，并展示了其在 Bean 中的应用。

### 经典金句/数据
> “yaml中的数据是有序的，properties中的数据是无序的，在一些需要路径匹配的配置中，顺序就显得尤为重要（例如我们在 Spring Cloud Zuul中的配置）。”

## 第3章：Starter 原理与条件注解
### 核心论点
- **问题**：Spring Boot 的“自动化配置”黑魔法是如何实现的？如何自定义 Starter？
- **观点**：条件注解（`@Conditional`）是 Spring Boot 自动化配置的基石；Starter 本质上是封装了自动配置类和依赖管理的 Maven 模块。

### 关键概念/事件
- **自定义 Starter**：创建普通 Maven 项目，定义配置属性类（`@ConfigurationProperties`）、服务类、自动配置类（`@Configuration` + `@ConditionalOnClass`），并在 `META-INF/spring.factories` 中注册。
- **条件注解（@Conditional）**：Spring 4 引入，根据特定条件（如类路径存在某类、环境变量值）决定是否创建 Bean。`@Profile` 是其特例。
- **spring.factories**：Spring Boot 启动时通过 `EnableAutoConfiguration` 读取该文件，加载指定的自动配置类。

### 逻辑推演/叙事脉络
本章通过“徒手撸一个 Starter”的案例，拆解了 Starter 的内部结构：属性类、服务类、自动配置类及 `spring.factories` 注册机制。随后深入底层，讲解了条件注解 `@Conditional` 的原理及其在 Spring 中的基础用法（如根据系统属性动态创建 Bean），并指出 `@Profile` 也是基于条件注解实现的，从而揭示了 Spring Boot 自动化配置的核心逻辑。

### 经典金句/数据
> “条件注解并非一个新事物...甚至可以说条件注解是整个 Spring Boot的基石。”

## 第4章：HTTPS 配置与页面模板整合
### 核心论点
- **问题**：如何在 Spring Boot 中启用 HTTPS？如何整合 Thymeleaf 和 Freemarker 模板引擎？
- **观点**：Spring Boot 对 HTTPS 和主流模板引擎提供了开箱即用的支持，只需少量配置或依赖即可集成。

### 关键概念/事件
- **HTTPS 配置**：使用 JDK `keytool` 生成证书，在 `application.properties` 中配置 `server.ssl.*` 属性。可通过配置 `TomcatServletWebServerFactory` 实现 HTTP 到 HTTPS 的重定向。
- **Thymeleaf 整合**：引入 `spring-boot-starter-thymeleaf`，默认模板位置 `classpath:/templates/`，后缀 `.html`。支持在 JS 中直接获取 Model 变量。
- **Freemarker 整合**：引入 `spring-boot-starter-freemarker`，默认后缀 `.ftl`（2.2.0+ 变为 `.ftlh`）。零配置即可使用，也可通过 `spring.freemarker.*` 自定义。

### 逻辑推演/叙事脉络
本章首先讲解了 HTTPS 的基础知识及在 Spring Boot 中的配置步骤，包括证书生成、属性配置及 HTTP 重定向方案。随后转向视图层，分别详细介绍了 Thymeleaf 和 Freemarker 两种模板引擎的整合过程，包括依赖引入、默认配置解析、Controller 编写及模板语法示例，并对比了两者在 Spring Boot 中的自动化配置差异。

### 经典金句/数据
> “Thymeleaf支持 HTML原型...既可以让前端工程师在浏览器中直接打开查看样式，也可以让后端工程师结合真实数据查看显示效果。”

## 第5章：静态资源处理与全局异常/数据绑定
### 核心论点
- **问题**：Spring Boot 如何处理静态资源映射？如何统一处理异常和全局数据？
- **观点**：Spring Boot 默认映射 5 个静态资源路径；`@ControllerAdvice` 是实现全局异常处理、数据绑定和预处理的强大工具。

### 关键概念/事件
- **静态资源映射**：默认路径包括 `classpath:/META-INF/resources/`, `/resources/`, `/static/`, `/public/` 及 `/` (webapp)。请求路径不包含目录名（如 `/static/`）。
- **@ControllerAdvice 功能**：
    1. **全局异常处理**：`@ExceptionHandler` 捕获异常并返回统一视图或 JSON。
    2. **全局数据绑定**：`@ModelAttribute` 向所有 Model 添加公共数据。
    3. **全局数据预处理**：`@InitBinder` 解决表单参数名冲突（如多个对象同名属性）。

### 逻辑推演/叙事脉络
本章首先分析了 Spring Boot 中静态资源的默认加载策略，通过源码解读解释了为何访问静态资源无需加 `/static/` 前缀，并展示了自定义静态资源路径的方法。接着引入了 `@ControllerAdvice` 注解，分三个维度展开：全局异常处理（简化错误页面）、全局数据绑定（共享模型数据）、全局数据预处理（解决参数命名冲突），展示了其在简化开发中的作用。

### 经典金句/数据
> “@ControllerAdvice，顾名思义，这是一个增强的 Controller...灵活使用这三个功能，可以帮助我们简化很多工作。”

## 第6章：异常页面定制与 CORS 跨域
### 核心论点
- **问题**：如何自定义 Spring Boot 默认的错误页面？如何解决前后端分离中的跨域问题？
- **观点**：错误页面可通过静态/动态模板按状态码或系列码定制；CORS 是解决跨域的标准方案，支持注解和全局配置。

### 关键概念/事件
- **自定义错误页面**：
    - **静态**：在 `classpath:/static/error/` 下放置 `404.html`、`5xx.html` 等。
    - **动态**：在 `classpath:/templates/error/` 下放置模板，可获取 `timestamp`, `status`, `error` 等默认属性。
    - **高级定制**：继承 `DefaultErrorAttributes` 自定义错误数据，或实现 `ErrorViewResolver` 自定义视图解析逻辑。
- **CORS 跨域**：
    - **注解方式**：`@CrossOrigin` 加在方法或类上。
    - **全局配置**：实现 `WebMvcConfigurer` 并重写 `addCorsMappings`。
    - **CSRF 防护**：跨域可能带来 CSRF 风险，需区分简单请求与预先请求。

### 逻辑推演/叙事脉络
本章先探讨了 Spring Boot 默认错误页面的机制，介绍了如何通过命名规范（状态码/系列码）定制静态或动态错误页面，并深入讲解了如何通过自定义 `ErrorAttributes` 和 `ErrorViewResolver` 来完全掌控错误响应数据和视图。随后转向网络通信问题，解释了同源策略与 CORS 原理，演示了在 Spring Boot 中通过注解和全局配置两种方式实现跨域资源共享，并简要提及了 CSRF 潜在风险。

### 经典金句/数据
> “发生了 500错误-->查找动态 500.html页面-->查找静态 500.html--> 查找动态 5xx.html-->查找静态 5xx.html。”

## 第7章：启动任务与定时任务
### 核心论点
- **问题**：如何在应用启动时执行初始化逻辑？如何实现定时任务？
- **观点**：`CommandLineRunner` 和 `ApplicationRunner` 用于启动任务；`@Scheduled` 适用于简单定时，Quartz 适用于复杂调度。

### 关键概念/事件
- **启动任务**：
    - `CommandLineRunner`：接收字符串数组参数。
    - `ApplicationRunner`：接收 `ApplicationArguments`，可解析键值对参数。
    - 均支持 `@Order` 指定执行顺序。
- **定时任务**：
    - **@Scheduled**：支持 `fixedRate`, `fixedDelay`, `cron` 表达式。需开启 `@EnableScheduling`。
    - **Quartz**：引入 `spring-boot-starter-quartz`。核心概念 JobDetail（做什么）和 Trigger（何时做）。支持 `MethodInvokingJobDetailFactoryBean`（无参）和 `JobDetailFactoryBean`（有参）。

### 逻辑推演/叙事脉络
本章首先介绍了两种应用启动任务的实现方式：`CommandLineRunner` 和 `ApplicationRunner`，对比了它们在参数处理上的差异。随后进入定时任务主题，先讲解了基于 Spring 原生 `@Scheduled` 注解的简单用法及其属性含义，接着介绍了更强大的 Quartz 框架整合，包括 Job 的定义方式（Bean 或继承 QuartzJobBean）以及 Trigger 的配置（SimpleTrigger 和 CronTrigger），展示了从简单到复杂的调度解决方案。

### 经典金句/数据
> “fixedRate表示任务执行之间的时间间隔，具体是指两次任务的开始时间间隔... fixedDelay表示本次任务结束到下次任务开始之间的时间间隔。”

## 第8章：接口文档 Swagger2 与 MVC 自定义配置
### 核心论点
- **问题**：如何自动生成和维护 API 文档？如何正确自定义 Spring MVC 配置而不破坏自动化配置？
- **观点**：Swagger2 通过注解生成在线文档；自定义 MVC 配置应实现 `WebMvcConfigurer` 接口，避免继承 `WebMvcConfigurationSupport` 导致自动化配置失效。

### 关键概念/事件
- **Swagger2 整合**：引入依赖，配置 `Docket` Bean（扫描包、API 信息）。常用注解：`@Api`, `@ApiOperation`, `@ApiImplicitParam`, `@ApiModelProperty`。
- **MVC 自定义配置陷阱**：
    - `WebMvcConfigurerAdapter`：已过时（Java 8+ 接口有 default 方法）。
    - `WebMvcConfigurer`：推荐方式，实现接口即可。
    - `WebMvcConfigurationSupport` / `@EnableWebMvc`：会导致 Spring Boot 的 MVC 自动化配置（如消息转换器、静态资源映射）失效，仅建议在纯 Java 配置 SSM 中使用。

### 逻辑推演/叙事脉络
本章首先解决了前后端分离中的文档维护痛点，介绍了 Swagger2 的整合步骤、核心配置类 `Docket` 的构建以及常用注解的使用，展示了如何生成在线交互式文档。随后深入探讨了 Spring Boot 中自定义 MVC 配置的正确姿势，通过对比 `WebMvcConfigurerAdapter`、`WebMvcConfigurer` 和 `WebMvcConfigurationSupport`，明确了在 Spring Boot 环境下应优先实现 `WebMvcConfigurer` 接口，以避免覆盖默认的自动化配置。

### 经典金句/数据
> “如果在 Spring Boot中使用继承 WebMvcConfigurationSupport来实现自定义 Spring MVC配置...都会导致 Spring Boot中默认的 Spring MVC自动化配置失效。”

## 第9章：数据持久化之 JdbcTemplate
### 核心论点
- **问题**：如何使用最轻量级的 JDBC 封装进行数据操作？如何配置多数据源？
- **观点**：`JdbcTemplate` 是最简单的持久化方案，适合简单 SQL 操作；多数据源需手动配置 DataSource 和 JdbcTemplate Bean。

### 关键概念/事件
- **基本用法**：引入 `spring-boot-starter-jdbc` 和驱动。`update` 用于增删改，`query` 用于查询（配合 `RowMapper` 或 `BeanPropertyRowMapper`）。
- **主键回填**：使用 `PreparedStatementCreator` 和 `KeyHolder`。
- **多数据源配置**：
    1. 配置多个 DataSource（使用 `@ConfigurationProperties` 绑定不同前缀）。
    2. 创建多个 `JdbcTemplate` Bean，分别注入对应的 DataSource。
    3. 使用时通过 `@Qualifier` 或 `@Resource` 指定具体的 JdbcTemplate。

### 逻辑推演/叙事脉络
本章介绍了 Spring Boot 整合 `JdbcTemplate` 的过程，从基本依赖引入、属性配置到 CRUD 操作演示，特别强调了 `BeanPropertyRowMapper` 的便利性。随后进阶到多数据源场景，讲解了如何通过手动配置多个 `DataSource` 和 `JdbcTemplate` Bean 来实现对不同数据库的操作，指出了在多 Bean 环境下注入时需明确指定名称的重要性。

### 经典金句/数据
> “JdbcTemplate算是最简单的数据持久化方案了...比起 Jdbc还是要方便很多的。”

## 第10章：数据持久化之 MyBatis
### 核心论点
- **问题**：如何整合 MyBatis？如何处理 Mapper XML 文件位置？如何实现多数据源？
- **观点**：MyBatis 在 Spring Boot 中几乎开箱即用；多数据源需为每个数据源单独配置 `SqlSessionFactory` 和 `SqlSessionTemplate`，并指定 Mapper 扫描路径。

### 关键概念/事件
- **基本整合**：引入 `mybatis-spring-boot-starter`。支持注解版（`@Select` 等）和 XML 版。
- **XML 位置**：可放在 Mapper 接口同包下（需配置 pom 资源过滤）或 `resources/mapper` 下（需配置 `mybatis.mapper-locations`）。
- **多数据源配置**：
    1. 配置多个 DataSource。
    2. 为每个数据源创建独立的 `SqlSessionFactory` 和 `SqlSessionTemplate` Bean。
    3. 使用 `@MapperScan` 指定不同包路径对应的 `sqlSessionFactoryRef`。

### 逻辑推演/叙事脉络
本章首先演示了 MyBatis 在 Spring Boot 中的快速整合，对比了全注解开发和 XML 映射文件开发的配置差异，特别说明了 XML 文件放置位置及打包时的注意事项。随后深入讲解了 MyBatis 多数据源的复杂配置，核心在于为每个数据源独立构建 `SqlSessionFactory` 和 `SqlSessionTemplate`，并通过 `@MapperScan` 将不同的 Mapper 接口绑定到特定的会话工厂，实现了数据源的隔离与复用。

### 经典金句/数据
> “复杂的就直接上分布式数据库中间件，简单的再考虑多数据源。”

## 第11章：数据持久化之 Spring Data JPA
### 核心论点
- **问题**：什么是 JPA 和 Spring Data JPA？如何使用方法名推导查询？
- **观点**：JPA 是规范，Hibernate 是实现；Spring Data JPA 通过方法名规范极大简化了 DAO 层开发，无需编写 SQL 即可完成常见 CRUD。

### 关键概念/事件
- **JPA 基础**：ORM 规范，实体类注解（`@Entity`, `@Id`, `@GeneratedValue`）。
- **Spring Data JPA**：
    - **Repository 接口**：继承 `JpaRepository` 获得通用 CRUD 能力。
    - **方法名推导**：如 `findByUsernameContaining`，框架自动解析方法名生成查询。
    - **自定义查询**：使用 `@Query` 注解编写 JPQL 或原生 SQL；更新操作需加 `@Modifying` 和 `@Transactional`。
- **多数据源**：配置多个 `EntityManagerFactory` 和 `TransactionManager`，并通过 `@EnableJpaRepositories` 指定不同包路径对应的工厂引用。

### 逻辑推演/叙事脉络
本章首先厘清了 JPA 规范与 Hibernate 实现的关系，介绍了 Spring Data JPA 的核心优势。通过实例展示了如何定义 Entity 和 Repository，重点讲解了基于方法名约定的查询推导机制，以及如何使用 `@Query` 进行自定义查询。最后，详细剖析了 JPA 多数据源的配置，这是三者中最复杂的，涉及 `LocalContainerEntityManagerFactoryBean` 和事务管理器的独立配置及包路径隔离。

### 经典金句/数据
> “Spring Data JPA做的便是规范方法的名字，根据符合规范的名字来确定方法需要实现什么样的逻辑。”

## 第12章：缓存整合 Redis 与 Ehcache
### 核心论点
- **问题**：如何整合 Redis 和 Ehcache 作为缓存？Spring Cache 抽象层的作用是什么？
- **观点**：Spring Cache 提供了统一的缓存抽象（`@Cacheable` 等注解），底层可切换 Redis 或 Ehcache 实现，解耦了业务代码与缓存技术。

### 关键概念/事件
- **Spring Data Redis**：
    - **RedisTemplate**：操作对象，默认 JDK 序列化（key 有前缀）。
    - **StringRedisTemplate**：操作字符串，默认 String 序列化。
    - **Spring Cache 整合 Redis**：引入 `spring-boot-starter-cache` 和 Redis 依赖，开启 `@EnableCaching`，使用注解管理缓存。
- **Ehcache 整合**：引入 Ehcache 依赖，配置 `ehcache.xml`，同样通过 Spring Cache 注解使用。
- **核心注解**：`@CacheConfig`（类级配置），`@Cacheable`（查），`@CachePut`（更），`@CacheEvict`（删）。

### 逻辑推演/叙事脉络
本章首先介绍了 Redis 在 Spring Boot 中的两种使用方式：直接使用 `RedisTemplate` 和通过 Spring Cache 抽象层。重点讲解了 Spring Cache 的核心注解及其属性（key 生成、条件等），并演示了如何将其底层实现从 Redis 切换到 Ehcache，强调了 Spring Cache 作为统一门面屏蔽底层差异的价值。同时指出了 Redis 序列化对 Key 格式的影响及解决方案。

### 经典金句/数据
> “Spring Cache和 Redis、Ehcache的关系就像 JDBC与各种数据库驱动的关系。”

## 第13章：RESTful API 与 Nginx/Session 共享
### 核心论点
- **问题**：如何快速构建 RESTful API？如何在集群环境下共享 Session？
- **观点**：Spring Data REST 可基于 Repository 瞬间生成 RESTful API；Spring Session + Redis 是解决分布式 Session 共享的主流方案。

### 关键概念/事件
- **Spring Data REST**：引入 `spring-boot-starter-data-rest`，自动将 `JpaRepository` 暴露为 REST 接口。支持自定义搜索接口（`@RestResource`）和路径映射。
- **Nginx 基础**：反向代理与负载均衡概念。
- **Session 共享**：
    - **问题**：集群环境下，不同节点 Session 不互通。
    - **方案**：Spring Session + Redis。引入 `spring-session-data-redis`，无需修改代码，框架自动拦截 Session 操作并同步至 Redis。
    - **配合 Nginx**：Nginx 负载均衡转发请求，后端服务通过 Redis 共享 Session，实现无状态登录体验。

### 逻辑推演/叙事脉络
本章首先展示了 Spring Data REST 的强大功能，仅需定义 Repository 即可自动生成标准的 CRUD REST 接口，并介绍了如何定制搜索接口。随后转向分布式架构问题，引入 Nginx 作为负载均衡器，指出由此引发的 Session 共享问题，并给出了标准解决方案：Spring Session + Redis。通过实例演示了如何在零代码侵入的情况下实现 Session 的分布式存储与读取。

### 经典金句/数据
> “开发者使用 Spring Session，一旦配置完成后，具体的用法就像使用一个普通的 Session一样。”

## 第14章：安全框架 Shiro 与 Spring Security
### 核心论点
- **问题**：如何选择和整合安全框架？Spring Security 的基本配置与高级用法有哪些？
- **观点**：Spring Boot 项目推荐 Spring Security（自动化配置好）；Shiro 轻量但配置稍繁。Spring Security 支持内存用户、自定义登录、JSON 登录、验证码及 JWT 无状态认证。

### 关键概念/事件
- **Shiro 整合**：两种方式，一是传统 Java Config（Realm, SecurityManager, ShiroFilterFactoryBean），二是使用 `shiro-spring-boot-web-starter`。
- **Spring Security 基础**：引入依赖即保护所有接口。默认用户 user/随机密码。
- **自定义配置**：
    - **用户来源**：内存、配置文件、数据库。
    - **登录定制**：自定义登录页、成功/失败处理器（返回 JSON）。
    - **验证码**：自定义过滤器插入到 `UsernamePasswordAuthenticationFilter` 之前。
    - **JSON 登录**：重写过滤器，从 Request Body 读取 JSON。
    - **角色继承**：配置 `RoleHierarchy`。
    - **JWT 无状态**：自定义登录过滤器生成 Token，自定义校验过滤器解析 Token，关闭 CSRF 和 Session。

### 逻辑推演/叙事脉络
本章对比了 Shiro 和 Spring Security，并分别演示了整合过程。重点篇幅在于 Spring Security 的深度定制：从基本的内存用户配置，到前后端分离场景下的 JSON 登录、验证码集成、未认证请求返回 JSON 而非重定向，再到基于 JWT 的无状态认证架构搭建。层层递进，展示了 Spring Security 在复杂业务场景下的灵活性。

### 经典金句/数据
> “如果是 Spring Boot项目，一般选择 Spring Security...只要加入依赖，项目的所有接口都会被自动保护起来。”

## 第15章：部署、热部署与邮件发送
### 核心论点
- **问题**：如何优化开发体验（热部署）？如何发送邮件？如何打包和部署 Docker 镜像？
- **观点**：DevTools + LiveReload 提升开发效率；Spring Mail 简化邮件发送；Jib 和 Docker Maven Plugin 简化容器化部署。

### 关键概念/事件
- **热部署**：`spring-boot-devtools` 实现类加载重启；LiveReload 浏览器插件实现静态资源刷新。
- **邮件发送**：引入 `spring-boot-starter-mail`，配置 SMTP。支持简单文本、附件、图片资源、Freemarker/Thymeleaf 模板邮件。
- **Jar 包结构**：可执行 Jar（BOOT-INF/classes）与普通 Jar 的区别。
- **Docker 部署**：
    - **Docker Maven Plugin**：编写 Dockerfile，配置插件，一键构建镜像并推送。
    - **Jib**：Google 开源，无需 Docker 环境，无需 Dockerfile，直接构建镜像并推送到仓库，分层构建速度快。

### 逻辑推演/叙事脉络
本章汇集了多个实用话题。首先介绍了开发阶段的热部署技巧，区分了代码重启和静态资源刷新。接着详细讲解了 Spring Boot 发送邮件的五种姿势，从简单文本到复杂的模板邮件。最后聚焦部署，分析了 Spring Boot 可执行 Jar 的内部结构，并对比了两种 Docker 镜像构建方案：传统的 Docker Maven Plugin（需 Dockerfile）和 Google Jib（无 Dockerfile，更高效），推荐了现代化的容器部署实践。

### 经典金句/数据
> “Jib会自动读取项目的构建配置...只将发生变更的层推送到 registers来缩短构建时间。”

## 第16章：微信公众号开发与面试题
### 核心论点
- **问题**：如何用 Spring Boot 开发微信公众号后台？Spring Boot 高频面试题有哪些？
- **观点**：公众号开发核心是签名校验与 XML 消息收发；面试题涵盖了配置、原理、安全、部署等全方位知识点。

### 关键概念/事件
- **公众号开发**：
    - **接入校验**：GET 请求，校验 signature（token, timestamp, nonce SHA1 加密）。
    - **消息收发**：POST 请求，解析微信发来的 XML，业务处理后封装 XML 返回。
    - **消息类型**：文本、图片、事件（订阅、点击）等，需不同处理逻辑。
- **高频面试题**：
    - Starter 原理、Parent 作用、YAML 优势、跨域解决、Security vs Shiro、Session 共享、热部署、定时任务、Swagger、Jar 包区别、Bootstrap vs Application 配置等。

### 逻辑推演/叙事脉络
本章首先通过一个实战案例——微信公众号后台开发，展示了 Spring Boot 处理非 JSON 协议（XML）和外部签名校验的能力，梳理了从接入验证到消息收发的完整流程。最后，附录了一份详细的 Spring Boot 高频面试题集锦，对全书涉及的知识点进行了回顾和提炼，帮助读者巩固核心概念，应对面试挑战。

### 经典金句/数据
> “刨根问底，一步到位，再遇到类似问题就可以分分钟解决了。”