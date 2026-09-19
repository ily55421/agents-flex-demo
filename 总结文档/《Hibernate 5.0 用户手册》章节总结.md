# 《Hibernate 5.0 用户手册》章节总结

## 目录说明
- 本总结依据提供的 PDF 文档《Hibernate用户手册 Hibernate-纯 java的关系型持久层框 5.0.0.Final》的目录结构与正文内容整理。
- 文档包含序言、第1章至第19章正文，以及附录A和附录B。
- 部分章节（如第17章OSGi、第18章Envers）内容较为独立且详细，均按独立章节处理。

## 序言
### 核心论点
- **问题**：面向对象开发与关系型数据库之间存在“阻抗不匹配”，导致开发成本高、SQL/JDBC代码繁琐。
- **观点**：Hibernate 作为 ORM 解决方案，旨在减少工程中 95% 的 SQL 与 JDBC 操作，让开发者专注于领域模型与业务逻辑，同时不剥夺使用原生 SQL 的能力。

### 关键概念/事件
- **ORM (Object/Relational Mapping)**：解决对象模型与关系模型之间不匹配的技术。
- **Unit of Work**：一种设计模式，Hibernate 基于此模式管理事务与持久化上下文。
- **数据建模原则**：理解数据建模有助于更好地使用 Hibernate。

### 逻辑推演/叙事脉络
序言首先指出了传统 JDBC 开发在对象-关系映射上的痛点，引入了 Hibernate 作为解决方案的定位。接着简要介绍了 Hibernate 的核心价值（减少样板代码、封装数据库差异），并推荐了前置知识（数据建模、事务处理、设计模式），最后指引新手从入门指南开始阅读。

### 经典金句/数据
> “Hibernate的设计目标是减少工程中９５％的 SQL与 JDBC操作。”
> “Hibernate不只关心 java类到数据库表的映射... 还提供了数据查询与检索工具。”

## 第1章：Architecture（体系架构）
### 核心论点
- **问题**：Hibernate 在 Java 应用与数据库之间扮演什么角色？其核心组件如何协作？
- **观点**：Hibernate 位于应用与数据库之间，通过 `SessionFactory`（线程安全、重量级）创建 `Session`（单线程、轻量级、工作单元），并通过 `Transaction` 抽象底层事务机制。

### 关键概念/事件
- **SessionFactory**：代表应用程序域模型到数据库的映射，是创建 Session 的工厂，维护二级缓存和连接池，建立代价大，通常整个应用只有一个。
- **Session**：单线程、短生命周期对象，代表“工作单元”，封装 JDBC 连接，维护一级缓存。
- **Contextual Sessions**：通过 `SessionFactory.getCurrentSession()` 获取与当前上下文（如 JTA 事务或线程）绑定的 Session，简化会话管理。

### 逻辑推演/叙事脉络
本章首先概述了 Hibernate 的整体架构位置，随后详细定义了三个核心 API 接口：`SessionFactory`、`Session` 和 `Transaction`。最后重点讨论了 `Contextual Sessions` 的演变，从早期的 ThreadLocal 辅助类到 Hibernate 3.1+ 的可插拔 `CurrentSessionContext` 策略（JTA, Thread, Managed），强调了“每请求一会话”模式的实现方式。

### 经典金句/数据
> “SessionFactory的建立代价很大... 所以一个应用只能有一个 SessionFactory。”
> “Session（会话）是一个单线程，短生命周期的对象，是按'Unit of Work（工作单元）'模式的概念构建的。”

## 第2章：Domain Model（域模型）
### 核心论点
- **问题**：什么样的 Java 类适合被 Hibernate 持久化？
- **观点**：Hibernate 偏好 POJO/JavaBean 风格的域模型，虽非强制但建议遵守无参构造、标识属性、非 final 类、getter/setter 以及正确的 equals/hashCode 实现，以兼容 JPA 并优化性能。

### 关键概念/事件
- **POJO 模型规范**：包括无参构造函数（public/protected）、标识属性（Identifier）、非 final 类（支持代理懒加载）、Getter/Setter 方法。
- **equals() 与 hashCode()**：在 ORM 中实现这两个方法较为复杂，建议使用业务键（Natural ID）而非生成的主键 ID，以避免瞬态对象加入集合时的问题。
- **动态模型**：支持使用 Map 而非实体类进行持久化，适用于原型开发或特定集成场景，但缺乏编译时检查。

### 逻辑推演/叙事脉络
本章首先定义了域模型在 ORM 中的核心地位。接着详细阐述了 POJO 模型的五大最佳实践，特别深入讨论了 `equals/hashCode` 在不同状态（瞬时、托管、游离）和不同会话场景下的陷阱及解决方案（如使用 Natural ID）。最后简要介绍了动态模型作为一种替代方案及其优缺点。

### 经典金句/数据
> “尽管 Hibernate没有将这些规范看作是强制要求，但是 JPA是要求的。因此如果你的应用有向 JPA移植的可能性，你最好遵守严格的 POJO模型。”
> “更好的方法是实现 equals/hashCode方法，利用 natural-id（自然 ID）或者 business-key（业务主键）。”

## 第3章：Bootstrap（引导、启动）
### 核心论点
- **问题**：如何初始化 Hibernate 并构建 `SessionFactory` 或 `EntityManagerFactory`？
- **观点**：Hibernate 5.0 引入了新的原生引导 API，分为构建 `ServiceRegistry`、`Metadata` 和 `SessionFactory` 三个阶段，比旧版 `Configuration` 更灵活且模块化；同时也支持标准的 JPA 引导方式。

### 关键概念/事件
- **ServiceRegistry**：持有 Hibernate 运行所需服务，分为 `BootstrapServiceRegistry`（类加载、集成器）和 `StandardServiceRegistry`（配置、连接池等）。
- **Metadata**：包含域模型解析结果及映射信息，通过 `MetadataSources` 添加注解类、XML 资源等构建。
- **Native Bootstrapping**：三步走策略：构建 StandardServiceRegistry -> 构建 Metadata -> 构建 SessionFactory。
- **JPA Bootstrapping**：支持容器管理（EE）和应用管理（SE）两种模式，通过 `persistence.xml` 或 `Persistence.createEntityManagerFactory` 启动。

### 逻辑推演/叙事脉络
本章首先区分了原生引导和 JPA 引导。重点讲解了原生引导的三个步骤：首先配置 ServiceRegistry（处理底层服务和配置），然后构建 Metadata（解析映射元数据），最后构建 SessionFactory。提供了完整的代码示例展示链式调用。随后简述了 JPA 兼容模式的引导流程，强调了其在不同环境下的适用性。

### 经典金句/数据
> “术语 bootstrapping（引导）是指初始化并且启动软件的组件。”
> “建议的处理方法是自己建立一个 StandardServiceRegistry，沿以下路径传递它... 这些对象都将持有同一个 StandardServiceRegistry。”

## 第4章：持久化 Context（上下文）
### 核心论点
- **问题**：实体在 Hibernate 中有哪些状态？如何在这些状态间转换？
- **观点**：实体存在瞬时、托管、游离、删除四种状态。`Session`/`EntityManager` 是持久化上下文的载体，通过 save, get, update, merge, delete 等方法管理实体状态及生命周期。

### 关键概念/事件
- **实体状态**：Transient（瞬时）、Managed/Persistent（托管/持久）、Detached（游离）、Removed（删除）。
- **获取实体**：`load/getReference`（返回代理，懒加载，可能抛异常）与 `get/find/load`（立即加载，返回 null 或实体）。
- **Natural-ID 访问**：支持通过业务键（@NaturalId）加载实体，提供 `getReference` 和 `load` 两种方式。
- **游离态处理**：`lock/update`（重新关联，可能执行 select-before-update）与 `merge`（复制状态到新托管实例，推荐用于 JPA）。

### 逻辑推演/叙事脉络
本章首先定义了持久化上下文的概念及实体的四种状态。接着按操作类型展开：持久化（save/persist）、删除、获取（区分懒加载代理与立即加载）、通过 Natural-ID 获取、刷新（refresh）、更新托管实体。重点辨析了游离态数据的两种处理方式：重新关联（lock/update）与合并（merge），并指出了 JPA 仅支持 merge 的差异。最后介绍了验证对象状态和懒加载状态的工具方法。

### 经典金句/数据
> “复位是指游离态的实体实例被再次取回，并且再次与持久化上下文关联（再次变成托管态）。”
> “合并过程是指：将一个传入的游离态的对象中的数据复制到一个新的托管态的对象中。”

## 第5章：访问数据库
### 核心论点
- **问题**：Hibernate 如何连接数据库？如何适配不同数据库方言？
- **观点**：通过 `ConnectionProvider` 抽象数据库连接获取（支持 DataSource, C3P0, HikariCP 等），通过 `Dialect` 抽象数据库 SQL 语法差异，实现数据库可移植性。

### 关键概念/事件
- **ConnectionProvider**：扩展点，用于自定义连接获取。Hibernate 根据配置优先级自动选择实现（DataSource > C3P0 > Proxool > Hikari > 内置）。
- **连接池配置**：详细列出了 C3P0、Proxool、HikariCP 的具体配置属性前缀及映射关系。
- **Dialect（方言）**：`org.hibernate.dialect.Dialect` 及其子类，封装特定数据库的 SQL 变体。Hibernate 5.0 能自动检测方言，也可手动指定。

### 逻辑推演/叙事脉络
本章首先介绍 `ConnectionProvider` 接口及其在 Hibernate 中的优先级选择机制。随后逐一详解了主流连接池（C3P0, Proxool, Hikari）的集成配置方法。最后讨论了 `Dialect` 的作用，列举了支持的数据库方言列表，并说明了方言解析机制（自动检测 vs 手动配置）。

### 经典金句/数据
> “虽然 SQL比较规范，但是每个数据库供应商使用自己的 SQL定义语法。这些语法是标准 SQL的子集或者是超集。这就是数据库方言（dialect）的由来。”

## 第6章：事务与并发控制
### 核心论点
- **问题**：Hibernate 如何管理事务？有哪些常见的事务模式？
- **观点**：Hibernate 提供统一的事务 API (`org.hibernate.Transaction`) 隔离底层 JDBC 或 JTA 事务。推荐“每请求一会话”模式，避免“每操作一会话”反模式。长对话可通过乐观锁和游离对象实现。

### 关键概念/事件
- **物理事务**：基于 JDBC 或 JTA。Hibernate 通过 `TransactionCoordinator` 管理。
- **Hibernate Transaction API**：提供 begin, commit, rollback 等统一接口，屏蔽底层差异（JDBC/JTA/CMT）。
- **事务模式**：
    - **Session-per-operation**：反模式，每次操作开闭会话，性能差。
    - **Session-per-request**：推荐模式，一个请求对应一个会话和事务。
    - **Conversations**：长对话，跨多个请求/事务，通过乐观锁或扩展会话实现。

### 逻辑推演/叙事脉络
本章首先区分了物理事务与逻辑事务。接着详细介绍了 Hibernate 的事务 API 及其在不同环境（JDBC, JTA-BMT, JTA-CMT）下的行为一致性。随后分析了四种事务模式，重点批判了 Session-per-operation，推崇 Session-per-request，并探讨了长对话（Conversations）的实现策略（自动版本控制、游离对象、扩展会话）。最后指出 Session 非线程安全及内存溢出风险。

### 经典金句/数据
> “在单线程中每次数据库访问都打开、关闭 Session，这是一种反模式... 将所有与数据库的通信都封装在事务中。”
> “在这种模式中，有一个通用的技巧，就是定义一个当前会话（current session）... Hibernate支持这个技术是通过 SessionFactory的 getCurrentSession方法。”

## 第7章：JNDI
### 核心论点
- **问题**：Hibernate 如何与 JNDI 交互？
- **观点**：Hibernate 可选通过 JNDI 查找 SessionFactory、DataSource 或 JTA 事务管理器，通过统一的 `JndiService` 进行配置和管理。

### 关键概念/事件
- **JNDI 交互场景**：请求 JNDI 绑定的 SessionFactory、指定 JNDI 命名的 DataSource、JTA 平台查找。
- **JndiService**：统一服务接口，通过 `hibernate.jndi.class` 和 `hibernate.jndi.url` 配置 InitialContext。

### 逻辑推演/叙事脉络
本章简短说明了 Hibernate 使用 JNDI 的三种主要场景，并指出所有 JNDI 调用均通过 `JndiService` 统一管理。提供了配置 InitialContext  factory 和 provider URL 的方法，并提示若需多命名服务器需自定义实现。

## 第8章：锁
### 核心论点
- **问题**：Hibernate 支持哪些锁机制？如何实现乐观锁和悲观锁？
- **观点**：Hibernate 支持乐观锁（版本号/时间戳）和悲观锁（数据库级锁）。乐观锁通过 `@Version` 实现，防止丢失更新；悲观锁通过 `LockMode` 显式请求 `SELECT FOR UPDATE` 实现。

### 关键概念/事件
- **乐观锁**：假设冲突少，提交时检查。通过 `@Version` 注解（版本号或时间戳）实现。版本号更安全，时间戳精度可能不足。
- **悲观锁**：假设冲突多，读取即锁定。通过 `LockMode.UPGRADE` 等枚举值显式请求数据库锁。Hibernate 会根据数据库能力降级处理。
- **LockMode**：定义了 WRITE, UPGRADE, READ, NONE 等锁级别。

### 逻辑推演/叙事脉络
本章首先定义了乐观锁与悲观锁的概念。详细讲解了乐观锁的实现机制，推荐使用版本号而非时间戳，并说明了数据库生成版本号的配置。随后介绍了悲观锁，解释了 `LockMode` 各类别的含义及使用场景（如 `load`, `lock`, `setLockMode`），并指出 Hibernate 会自动适配数据库支持的锁语法。

### 经典金句/数据
> “乐观锁假设多个事务正常完成非没有相互影响，因此在事务提交之间，事务运行时不锁定它们影响的数据资源。”
> “悲观锁假设并发事务之间有冲突，要求它们读取数据后就锁定资源，直到应用完成，不再使用数据才解除锁定。”

## 第9章：Fetching（抓取）
### 核心论点
- **问题**：如何优化数据加载策略以避免 N+1 问题或过度加载？
- **观点**：抓取策略分为静态（映射定义）和动态（查询定义）。推荐静态标记懒加载，动态查询中按需立即抓取（Join Fetch）。支持 SELECT, JOIN, BATCH, SUBSELECT 等多种策略。

### 关键概念/事件
- **抓取时机**：Eager（立即） vs Lazy（延迟）。
- **抓取策略**：
    - **SELECT**：额外 SQL 查询，可懒加载。
    - **JOIN**：SQL Join，立即加载，无法懒加载。
    - **BATCH**：批量加载，优化 N+1。
    - **SUBSELECT**：子查询加载集合。
- **动态抓取**：通过 HQL `join fetch` 或 Criteria `fetch()` 在查询时覆盖静态策略。
- **Fetch Profiles**：允许在运行时启用/禁用预定义的抓取配置。

### 逻辑推演/叙事脉络
本章首先分解了抓取的两个维度：何时抓取（Eager/Lazy）和如何抓取（策略）。介绍了四种基本策略（SELECT, JOIN, BATCH, SUBSELECT）。通过“登录”和“查看项目”两个用例，演示了如何在不抓取关联、动态查询抓取（join fetch）以及使用 Fetch Profiles 配合 Natural-ID 加载等不同场景下优化抓取行为。强调了 Hibernate 推荐静态懒加载、动态急加载的最佳实践。

### 经典金句/数据
> “Hibernate推荐使用静态标记用于相关的 lazy（懒加载），使用动态抓取策略用于 eager（立即加载）。”
> “Fetch joins在子查询中无效... Fetch join不能用于分页查询。”

## 第10章：批处理
### 核心论点
- **问题**：如何处理大量数据的插入、更新和删除以提高性能？
- **观点**：利用 JDBC 批处理功能，通过配置 `hibernate.jdbc.batch_size` 等参数，将多个 SQL 语句合并发送，减少网络往返。注意版本化数据和排序对批处理的影响。

### 关键概念/事件
- **JDBC 批处理**：将多个 SQL 语句组合为一个 PreparedStatement 批次发送。
- **关键配置**：
    - `hibernate.jdbc.batch_size`：批处理大小，0 或负数禁用。
    - `hibernate.jdbc.batch_versioned_data`：是否对版本化数据使用批处理（默认 false，因驱动兼容性）。
    - `hibernate.order_inserts/updates`：排序 SQL 以增加批处理命中率，但可能增加死锁风险或降低性能。

### 逻辑推演/叙事脉络
本章简要讨论了 JDBC 批处理在 Hibernate 中的应用。解释了批处理的原理（减少网络访问），并列出了控制批处理行为的关键配置项，特别指出了版本化数据和 SQL 排序对批处理效率及正确性的影响，提醒用户根据应用场景权衡利弊。

## 第11章：缓冲
### 核心论点
- **问题**：如何配置和管理 Hibernate 的二级缓存？
- **观点**：Hibernate 集成第三方缓存提供者（如 Ehcache, Infinispan）。通过 `RegionFactory` 配置缓存插件，并通过一系列开关控制二级缓存、查询缓存的行为及并发策略。

### 关键概念/事件
- **RegionFactory**：集成第三方缓存的 SPI，如 `ehcache`, `infinispan`。
- **缓存配置**：
    - `use_second_level_cache`：启用二级缓存。
    - `use_query_cache`：启用查询结果缓存。
    - `default_cache_concurrency_strategy`：默认并发策略（read-only, read-write 等）。
- **缓存管理**：通过 `org.hibernate.Cache` 接口清理或访问二级缓存数据。

### 逻辑推演/叙事脉络
本章首先说明了 Hibernate 二级缓存的可插拔性。介绍了如何通过 `RegionFactory` 集成 Ehcache 或 Infinispan。随后详细列出了控制缓存行为的关键配置项，包括启用开关、查询缓存、最小化写入、结构化条目等。最后简述了运行时通过 Cache API 管理缓存数据的方法。

## 第12章：拦截器和事件
### 核心论点
- **问题**：如何在 Hibernate 内部操作中注入自定义逻辑？
- **观点**：通过拦截器（Interceptor）和事件监听器（EventListener）机制，可以在持久化生命周期（保存、更新、删除、加载等）的各个节点执行自定义代码，如审计、安全检查等。

### 关键概念/事件
- **拦截器 (Interceptors)**：实现 `org.hibernate.Interceptor` 接口，可作用于 Session 级别或 SessionFactory 级别。用于检查或操作属性（如审计时间戳）。
- **事件系统 (Event System)**：基于 `EventType` 和监听器接口（如 `LoadEventListener`）。允许替换或补充默认行为。监听器应无状态。
- **JPA 回调**：支持 `@PrePersist`, `@PostLoad` 等注解，可在实体类或监听器类中定义回调方法。

### 逻辑推演/叙事脉络
本章首先介绍了拦截器机制，区分了 Session 范围和 SessionFactory 范围的拦截器及其线程安全性要求。接着深入讲解了原生事件系统，说明了如何通过自定义监听器替换默认行为（如加载时的安全检查），并提到了 JACC 安全集成。最后介绍了 JPA 标准的生命周期回调注解及其执行顺序。

### 经典金句/数据
> “监听器应该设计为无状态的；它们在请求之间共享，并且必须不保存任何实例属性值。”

## 第13章：HQL与 JPQL
### 核心论点
- **问题**：如何使用面向对象的查询语言检索数据？
- **观点**：HQL 和 JPQL 是面向对象的查询语言，语法类似 SQL 但操作实体和属性。HQL 功能更丰富（如 Insert, bulk update versioned），JPQL 更具可移植性。支持多种参数绑定、聚合、子查询及动态实例化。

### 关键概念/事件
- **语句类型**：Select, Update, Delete (HQL/JPQL); Insert (仅 HQL)。
- **FROM 子句**：根实体引用、显式 Join (Inner/Left/Fetch)、隐式 Join (Path Expressions)、集合成员引用、多态查询。
- **表达式与谓词**：算术运算、字符串串联、聚合函数、标量函数、Case 表达式、各种谓词 (Like, In, Between, Exists 等)。
- **查询 API**：Hibernate `Query` 接口 vs JPA `TypedQuery` 接口。参数绑定（命名、位置）。结果处理（list, uniqueResult, scroll, iterate）。

### 逻辑推演/叙事脉络
本章是全书最长章节之一，系统讲解了 HQL/JPQL 语法。从大小写敏感性开始，依次详解 Select/Update/Delete/Insert 语句结构。深入剖析 FROM 子句的各种 Join 方式和路径表达式。接着列举了丰富的表达式、函数和谓词。最后对比了 Hibernate 和 JPA 的查询 API，包括参数绑定、执行方法及结果转换（动态实例化、Tuple 等）。

### 经典金句/数据
> “JPQL是 HQL的子集（JPQL的灵感来于 HQL）。一个 JPQL一定是一个 HQL，相反就不正确了。”
> “Fetch joins在子查询中无效。”

## 第14章：Criteria
### 核心论点
- **问题**：如何以类型安全的方式构建动态查询？
- **观点**：JPA Criteria API 提供了类型安全的查询构建方式，通过 `CriteriaBuilder` 和 `CriteriaQuery` 对象图构建查询，避免了字符串拼接错误，适合动态查询场景。旧的 Hibernate Criteria API 已过时。

### 关键概念/事件
- **CriteriaBuilder**：工厂类，用于创建查询、表达式、谓词。
- **CriteriaQuery**：查询主体，定义选择类型（实体、Tuple、Wrapper、Array）。
- **Roots & Joins**：定义查询根实体和关联路径。
- **Type-Safety**：利用 Metamodel（如 `Person_.age`）实现编译时检查。
- **Tuple Query**：使用 `javax.persistence.Tuple` 处理多列结果，支持按类型、位置、别名访问。

### 逻辑推演/叙事脉络
本章首先声明旧的 Hibernate Criteria API 已过时，聚焦于 JPA Criteria API。介绍了 `CriteriaBuilder` 和 `CriteriaQuery` 的基本用法。详细讲解了如何选择单个实体、表达式、多个值（数组、Wrapper、Tuple）。随后讲解了 FROM 子句中的 Roots 和 Joins，以及 Fetch 抓取。最后简述了路径表达式和参数绑定。强调了类型安全和 Metamodel 的重要性。

## 第15章：Native SQL查询（原生 SQL查询，本地 SQL查询）
### 核心论点
- **问题**：如何执行原生 SQL 并利用 Hibernate 的结果映射功能？
- **观点**：Hibernate 允许执行原生 SQL，并通过 `addEntity`, `addScalar`, `addJoin` 等方法将结果集映射回实体或标量。支持命名 SQL 查询、存储过程及自定义 CRUD SQL。

### 关键概念/事件
- **SQLQuery 接口**：通过 `session.createSQLQuery` 创建。
- **结果映射**：
    - **Scalar**：`addScalar` 指定列名和类型。
    - **Entity**：`addEntity` 映射到实体类。
    - **Join**：`addJoin`  eagerly 加载关联。
    - **Alias Injection**：使用 `{alias.*}` 语法让 Hibernate 自动注入列别名，解决多表同名列冲突。
- **命名 SQL 查询**：在映射文件或注解中定义，支持 `<return-join>`, `<load-collection>`。
- **自定义 CRUD**：通过 `@SQLInsert`, `@SQLUpdate` 等注解或 XML 标签重写默认的 SQL 语句。

### 逻辑推演/叙事脉络
本章首先介绍了执行原生 SQL 的基本方法，包括标量查询和实体查询。重点解决了多表连接时的列名冲突问题，引入了别名注入语法 `{alias.*}`。接着讲解了命名 SQL 查询的定义与执行，包括标量、实体、关联和集合的映射。随后讨论了存储过程的调用规范及限制。最后介绍了如何自定义实体和集合的 INSERT/UPDATE/DELETE SQL 语句，以及自定义加载查询。

### 经典金句/数据
> “上面的{cat.*}与{mother.*}标记是“所有属性”的简写... Hibernate也还会为每个属性注入列别名。”

## 第16章：Multi-tenancy（多租户）
### 核心论点
- **问题**：Hibernate 如何支持多租户架构？
- **观点**：Hibernate 支持三种多租户数据隔离策略：独立数据库、独立 Schema、鉴别器（分区）。通过 `MultiTenantConnectionProvider` 和 `CurrentTenantIdentifierResolver` 接口实现租户识别和连接路由。

### 关键概念/事件
- **隔离策略**：
    - **DATABASE**：每个租户独立数据库。
    - **SCHEMA**：每个租户独立 Schema。
    - **DISCRIMINATOR**：同一表通过列值区分租户（5.0 计划支持）。
- **核心接口**：
    - `MultiTenantConnectionProvider`：提供特定租户的连接。
    - `CurrentTenantIdentifierResolver`：解析当前租户 ID。
- **配置**：通过 `hibernate.multiTenancy` 指定策略，并配置上述接口的实现。

### 逻辑推演/叙事脉络
本章首先定义了多租户概念及三种主流数据隔离方法。接着介绍了 Hibernate 的多租户 API，特别是 `withOptions().tenantIdentifier()` 的使用。详细说明了 `MultiTenantConnectionProvider` 和 `CurrentTenantIdentifierResolver` 两个核心扩展点的作用及配置方式。最后给出了两种典型的实现示例：多连接池（对应 Database/Schema）和单连接池切换 Schema。

## 第17章：OSGi
### 核心论点
- **问题**：如何在 OSGi 模块化环境中使用 Hibernate？
- **观点**：Hibernate 通过 `hibernate-osgi` 模块支持 OSGi，提供容器管理 JPA、非托管 JPA 和非托管 Native 三种配置模式。关键在于利用 OSGi 服务注册发现 `EntityManagerFactory` 或 `SessionFactory`，而非手动构建。

### 关键概念/事件
- **hibernate-osgi**：独立模块，隔离 OSGi 依赖，提供特定的 ClassLoader 和服务发现。
- **三种配置模式**：
    - **容器管理 JPA**：利用 Aries JPA 等容器自动创建 EMF。
    - **非托管 JPA**：Bundle 自行管理 EMF，通过 OSGi 服务获取 `PersistenceProvider`。
    - **非托管 Native**：Bundle 自行管理 SF，通过 OSGi 服务获取 `SessionFactory`。
- **Bundle 导入**：需导入 `javax.persistence`, `org.hibernate.proxy`, `javassist.util.proxy` 等包。

### 逻辑推演/叙事脉络
本章首先介绍了 OSGi 环境的挑战及 `hibernate-osgi` 模块的作用。依次详解了三种配置模式：容器管理 JPA（依赖 Aries JPA，Blueprint 注入）、非托管 JPA（通过服务发现 PersistenceProvider 创建 EMF）、非托管 Native（通过服务发现 SessionFactory）。强调了在 OSGi 中必须通过服务获取工厂实例，而非直接 new 或常规引导，以正确处理 ClassLoader。最后提及了扩展点注册和已知限制。

## 第18章：Envers
### 核心论点
- **问题**：如何轻松实现实体数据的历史版本审计？
- **观点**：Hibernate Envers 通过简单的 `@Audited` 注解自动跟踪实体变化，生成审计表（_AUD）和版本表（REVINFO）。支持自定义版本实体、查询历史数据、属性级别变更跟踪及审计表分区。

### 关键概念/事件
- **基础使用**：添加 `hibernate-envers` 依赖，在实体类加 `@Audited`。自动生成审计表。
- **版本实体 (Revision Entity)**：默认 `REVINFO` 表。可自定义，通过 `@RevisionEntity` 和 `RevisionListener` 记录额外信息（如用户 IP）。
- **审计策略**：默认策略（简单，查询慢） vs Validity 策略（记录结束版本，查询快，写入稍慢）。
- **查询 API**：`AuditReader` 提供类似 Criteria 的 API，支持按版本查实体、按实体查版本、按属性变更查版本。
- **高级特性**：属性级别变更标志（Modified Flags）、条件审计、审计表分区。

### 逻辑推演/叙事脉络
本章首先介绍了 Envers 的基础知识和配置。详细讲解了版本日志机制，包括默认版本实体和自定义版本实体的实现。对比了两种审计策略的优劣。核心部分介绍了强大的查询 API，支持水平（某版本状态）和垂直（某实体历史）查询，以及属性级别的变更追踪。最后讨论了高级主题如条件审计、Schema 理解和审计表分区策略。

### 经典金句/数据
> “Hibernate Envers的目的是为你应用程序的实体数据提供历史版本。非常像源代码控制管理工具，如：Subversion或 Git。”

## 第19章：数据库可移植性思考
### 核心论点
- **问题**：如何确保 Hibernate 应用在不同数据库间可移植？
- **观点**：依靠 Dialect 自动解析、增强的标识符生成器（SequenceStyleGenerator/TableGenerator）以及逻辑函数映射，Hibernate 能在很大程度上屏蔽数据库差异，实现代码和映射的可移植性。

### 关键概念/事件
- **Dialect 解析**：Hibernate 3.2+ 支持通过 JDBC Metadata 自动检测 Dialect，也可注册自定义解析器。
- **标识符生成**：推荐使用 enhanced generators (`SequenceStyleGenerator`, `TableGenerator`) 替代传统的 native/identity/sequence，以更好地模拟序列行为并提高可移植性。
- **数据库函数**：通过 `SQLFunctionRegistry` 注册逻辑函数名到物理函数的映射，解决不同数据库函数名不一致问题。

### 逻辑推演/叙事脉络
本章首先重申了可移植性的目标。接着从三个方面展开：Dialect 的自动检测与自定义解析；标识符生成策略的演进，推荐增强型生成器以解决 identity 策略的局限性；数据库函数的逻辑映射机制。旨在指导开发者编写更少依赖特定数据库特性的代码。

## 附录 A Legacy Bootstrapping（过时的引导方式）
### 核心论点
- **问题**：旧版 `Configuration` 引导方式如何迁移到新 API？
- **观点**：旧版 `Configuration` 已过时，新 API 将其职责拆分为 `ServiceRegistry`, `MetadataSources`, `MetadataBuilder` 等。提供了详细的映射表指导迁移。

### 关键概念/事件
- **Configuration 缺点**：单体大对象，职责不清。
- **迁移映射**：列出了 `Configuration` 方法与新 API 方法的对应关系，如 `addClass` -> `MetadataSources.addAnnotatedClass`, `setProperty` -> `StandardServiceRegistryBuilder.applySetting` 等。

### 逻辑推演/叙事脉络
本章简要回顾了旧版 `Configuration` 的使用方式，指出其缺点。核心内容是提供了一份详细的迁移对照表，帮助开发者将基于 `Configuration` 的代码重构为 Hibernate 5.0 的新引导 API。

## 附录 B Legacy（过时的）Hibernate Criteria查询
### 核心论点
- **问题**：旧的 Hibernate Criteria API 有哪些功能？
- **观点**：旧 Criteria API 提供了直观的查询构建方式，支持约束、排序、关联、投影、子查询等。但该 API 已过时，建议迁移至 JPA Criteria API。

### 关键概念/事件
- **基本用法**：`session.createCriteria`, `Restrictions`, `Order`。
- **关联查询**：`createCriteria`, `createAlias`。
- **投影与聚合**：`Projections`, `groupProperty`。
- **子查询**：`DetachedCriteria`, `Subqueries`。
- **Example 查询**：基于实例模板的查询。

### 逻辑推演/叙事脉络
本章作为存档，介绍了旧版 Criteria API 的主要功能，包括建立实例、添加约束、排序、处理关联、动态抓取、组件、集合、Example 查询、投影聚合、分离查询与子查询、以及 Natural ID 查询。旨在为遗留代码提供参考，并再次强调应使用 JPA Criteria API。