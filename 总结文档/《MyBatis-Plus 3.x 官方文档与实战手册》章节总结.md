# 《MyBatis-Plus 3.x 官方文档与实战手册》章节总结

## 目录说明
本书总结基于《MyBatis-Plus 3.x 文档手册》与《MyBatis-Plus入门》两份官方及实战资料深度融合而成。章节划分以官方文档的核心模块（简介、快速开始、注解、核心功能、插件扩展、配置、FAQ）为主干，并将《入门》教程中的实战代码、配置案例与踩坑经验（如主键自增报错）无缝嵌入对应章节，形成从理论到实战的完整闭环。

---

## 前言：致谢与导读
### 核心论点
本章主要说明了文档的构建背景、知识来源以及 MyBatis-Plus 框架的核心愿景。
作者的核心观点是：MyBatis-Plus 致力于成为 MyBatis 的最佳搭档，通过开源社区的共建与知识传承，为开发者提供高效、优雅的数据访问层解决方案。

### 关键概念/事件
- **书栈 (BookStack.CN)**：本手册的构建平台，致力于知识的整理、归类与开源传承。
- **魂斗罗搭档理念**：将 MyBatis 与 MyBatis-Plus 的关系比喻为魂斗罗中的 1P 与 2P，强调“只做增强，不做改变”的协同理念。

### 逻辑推演/技术脉络
从文档的生成背景切入，感谢开源社区与贡献者，随后引出框架的愿景与定位，为后续的技术展开奠定“无侵入、强增强”的基调。

### 经典金句/数据
> “我们的愿景是成为 MyBatis 最好的搭档，就像魂斗罗中的 1P、2P，基友搭配，效率翻倍。”

---

## 第1章：简介与特性
### 核心论点
本章试图回答“MyBatis-Plus 是什么以及它能为开发者带来什么价值”的问题。
核心观点是：MyBatis-Plus 是一个 MyBatis 的增强工具，在 MyBatis 的基础上只做增强不做改变，为简化开发、提高效率而生。

### 关键概念/事件
- **无侵入与损耗小**：引入 MP 不会对现有工程产生影响，启动即自动注入基本 CRUD，性能基本无损耗。
- **强大的 CRUD 与 Lambda 支持**：内置通用 Mapper/Service，支持 Lambda 表达式编写查询条件，避免字段写错。
- **内置插件体系**：提供分页、性能分析、全局拦截（防全表删除/更新）、乐观锁等开箱即用的插件。
- **代码生成器**：支持通过代码或 Maven 插件快速生成 Mapper、Model、Service、Controller 层代码。

### 逻辑推演/技术脉络
作者首先定义了 MP 的框架定位，接着以列表形式详尽列举了其 12 大核心特性，随后展示了其支持的广泛数据库类型（MySQL、Oracle、PostgreSQL 及各类国产数据库），最后给出了框架的底层结构图与优秀企业接入案例，证明了其工业级可用性。

### 经典金句/数据
> “无侵入：只做增强不做改变，引入它不会对现有工程产生影响，如丝般顺滑。”

---

## 第2章：快速开始与安装配置
### 核心论点
本章解决“如何以最快速度在 Spring Boot 项目中集成 MP 并跑通第一个 CRUD”的问题。
核心观点是：集成 MP 极其简单，只需引入 Starter 依赖、配置数据源并扫描 Mapper 接口，即可免去 XML 编写直接进行面向对象操作。

### 关键概念/事件
- **依赖引入**：使用 `mybatis-plus-boot-starter` 替代原生的 MyBatis 依赖，避免版本冲突。
- **@MapperScan**：在启动类或配置类上扫描 Mapper 接口所在包。
- **BaseMapper<T>**：Mapper 接口继承该泛型接口，即刻拥有 17 个单表 CRUD 方法。
- **实战踩坑（入门案例）**：在《入门》实战中，若实体类主键配置为 `IdType.AUTO` 但数据库表未开启自增，会抛出 `Field 'id' doesn't have a default value` 异常，需确保数据库与注解策略一致。

### 逻辑推演/技术脉络
按照“建表 -> 引入依赖 -> 配置 application.yml -> 编写 Entity 与 Mapper -> 编写 JUnit 测试类”的标准工程流进行推演。结合《入门》PDF 中的 IDEA 实操步骤，演示了从 0 到 1 建立 Spring Boot 项目并成功执行 `selectList(null)` 的全过程。

### 经典金句/数据
> “通过以上几个简单的步骤，我们就实现了 User 表的 CRUD 功能，甚至连 XML 文件都不用编写！”

---

## 第3章：核心注解体系
### 核心论点
本章解答“MP 如何通过注解实现 Java 对象与数据库表的精准映射及特殊业务逻辑标记”。
核心观点是：通过 `mybatis-plus-annotation` 包下的注解，开发者可以优雅地处理表名映射、主键策略、字段验证及逻辑删除等复杂场景。

### 关键概念/事件
- **@TableName**：映射表名，支持 `autoResultMap` 自动构建结果集映射。
- **@TableId**：标记主键，支持 `AUTO`（数据库自增）、`ID_WORKER`（雪花算法）、`UUID` 等策略。
- **@TableField**：处理非主键字段，支持 `FieldStrategy`（字段验证策略）、`FieldFill`（自动填充策略）及 `typeHandler`（类型处理器）。
- **@TableLogic**：标记逻辑删除字段，使 MP 的 CRUD 方法自动转换为 `UPDATE` 或追加 `WHERE` 条件。
- **@Version**：标记乐观锁版本号字段。

### 逻辑推演/技术脉络
以实体类的字段生命周期为线索，从类级别的表名映射，到主键生成策略，再到普通字段的验证与填充，最后到特殊业务场景（逻辑删除、乐观锁、枚举映射），逐层剖析注解的属性与默认行为。

### 经典金句/数据
> “MyBatis-Plus 从 3.0.3 之后移除了代码生成器与模板引擎的默认依赖，需要手动添加相关依赖。”（注：此处的依赖管理变更体现了框架的模块化设计）

---

## 第4章：核心功能
### 核心论点
本章全面解析 MP 替代手写 XML 的核心武器库。
核心观点是：通过代码生成器、通用 CRUD 接口与强大的条件构造器，开发者可以彻底告别繁琐的单表 SQL 编写。

### 关键概念/事件
- **AutoGenerator（代码生成器）**：通过配置全局、数据源、包、策略及模板，一键生成多层代码。
- **CRUD 接口**：`BaseMapper` 提供底层操作，`IService` 提供 `save`、`remove`、`update`、`get`、`list`、`page` 等语义化更强的 Service 层方法。
- **条件构造器 (Wrapper)**：`QueryWrapper` 和 `UpdateWrapper` 支持链式调用与 Lambda 表达式（如 `eq(User::getName, "Tom")`），彻底解决字段名硬编码问题。
- **分页插件**：基于 MyBatis 物理分页，配置 `PaginationInterceptor` 后，传入 `Page` 对象即可自动拦截并改写 SQL。

### 逻辑推演/技术脉络
先介绍代码生成器提升初始化效率；接着对比 Mapper 层与 Service 层 CRUD 方法的命名差异（如 `selectById` vs `getById`）；随后重点剖析 Wrapper 的 API 设计（`allEq`, `like`, `nested`, `apply` 等）；最后说明分页插件与主键生成器（Sequence/雪花算法）的底层机制。结合《入门》中的 `testWrapper` 和 `testPage` 测试用例进行佐证。

### 经典金句/数据
> “不支持以及不赞成在 RPC 调用中把 Wrapper 进行传输。wrapper 很重，传输 wrapper 可以类比为你的 controller 用 map 接收值（开发一时爽，维护火葬场）。”

---

## 第5章：插件与扩展功能
### 核心论点
本章探讨“如何利用 MP 的插件机制应对高并发、多租户、数据安全等企业级复杂场景”。
核心观点是：MP 提供了丰富的拦截器与解析器，通过简单的 Bean 注入即可实现全局性的业务规则管控。

### 关键概念/事件
- **自动填充 (MetaObjectHandler)**：实现该接口并重写 `insertFill` / `updateFill`，配合 `@TableField(fill=...)` 实现创建时间、操作人等字段的自动注入。
- **乐观锁插件**：通过 `OptimisticLockerInterceptor` 实现 `set version = newVersion where version = oldVersion` 的并发控制。
- **执行 SQL 分析打印**：推荐集成 `p6spy` 组件，完美输出 SQL 及执行时长，替代旧版性能分析插件。
- **多租户 / 动态表名解析器**：通过 `TenantSqlParser` 和 `DynamicTableNameParser` 在 SQL 解析阶段自动追加 `tenant_id` 或替换表名。
- **动态数据源**：基于 `dynamic-datasource-spring-boot-starter`，使用 `@DS` 注解实现主从分离或多库动态切换。

### 逻辑推演/技术脉络
按业务场景分类展开：从数据保护（逻辑删除、攻击 SQL 阻断）、自动化（自动填充）、并发控制（乐观锁），到监控（p6spy），再到复杂架构（多租户、动态数据源）。《入门》PDF 中的 `MyObjectHandler` 和 `MyBatisPlusConfig` 配置类完美印证了这些插件的 Spring Boot 集成方式。

### 经典金句/数据
> “乐观锁：顾名思义，十分乐观，它总是认为不会出现问题... 执行更新时，set version = newVersion where version = oldVersion。”

---

## 第6章：高级配置详解
### 核心论点
本章解答“MP 的全局配置如何与 MyBatis 原生配置协同工作”。
核心观点是：MP 的配置体系分为 MyBatis 原生 `Configuration`、MP 全局策略 `GlobalConfig` 和数据库策略 `DbConfig` 三层，支持细粒度的行为控制。

### 关键概念/事件
- **DbConfig**：控制表前缀、主键策略、逻辑删除值、字段验证策略（`FieldStrategy`）。
- **FieldStrategy**：决定 insert/update/select 时如何处理 null 或空字符串（如 `NOT_NULL`, `NOT_EMPTY`, `IGNORED`）。
- **Configuration**：继承 MyBatis 原生配置，如 `mapUnderscoreToCamelCase`（驼峰映射）、`callSettersOnNulls`（null 值调用 setter）。

### 逻辑推演/技术脉络
从 Spring Boot 的 `application.yml` 配置结构切入，由外向内逐层解析 `mybatis-plus.configuration` 与 `mybatis-plus.global-config.db-config` 的属性字典，明确了各个配置的默认值与生效范围。

### 经典金句/数据
> “当用户有更新字段为空字符串或者 null 的需求时，需要对 FieldStrategy 策略进行调整。”

---

## 第7章：常见问题与避坑指南（FAQ）
### 核心论点
本章汇总了开发者在实际使用 MP 时最高频的报错与疑惑。
核心观点是：大部分“异常”源于对 MP 默认行为或 MyBatis 底层机制的误解，通过正确的配置与注解即可规避。

### 关键概念/事件
- **Invalid bound statement (not found)**：通常由 Mapper 扫描路径错误、XML 未正确打包或继承了错误的 BaseMapper 导致。
- **JS 精度丢失**：`ID_WORKER` 生成的 Long 型主键过长，前端 JS 无法解析，需通过 Jackson/FastJson 序列化为 String。
- **tinyint(1) 映射问题**：MySQL 驱动默认将 `tinyint(1)` 映射为 Boolean，需在 JDBC URL 追加 `tinyInt1isBit=false`。
- **逻辑删除与自动填充冲突**：`deleteById` 入参不是 Entity，导致自动填充失效，需改用 `update` 方法或自定义 SqlInjector。
- **实战报错复盘**：《入门》中 `Field 'id' doesn't have a default value` 的根本原因是实体类期望数据库自增或 MP 生成 ID，但数据库表结构未设置自增且 MP 主键策略配置不匹配。

### 逻辑推演/技术脉络
以 Q&A 形式直击痛点，覆盖了 XML 扫描、泛型注入、类型转换、缓存刷新、关键字转义等 15+ 个高频问题，并给出了代码级或配置级的明确解决方案。

### 经典金句/数据
> “不要怀疑，正视自己，这个异常（Invalid bound statement）肯定是你插入的姿势不对......”
> “检查是不是用了 long 而不是 Long！long 类型默认值为 0，而 MP 只会判断是否为 null。”