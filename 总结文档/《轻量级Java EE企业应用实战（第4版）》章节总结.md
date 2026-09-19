# 《轻量级Java EE企业应用实战（第4版）》章节总结

## 目录说明
- 本书共计10章，涵盖Java EE基础、JSP/Servlet、Struts 2、Hibernate、Spring及SSH整合开发。
- 由于提供的PDF文件缺失第1章，总结从第2章开始。
- 第6章、第7章、第8章、第9章、第10章内容完整；第2、3、4、5章内容有部分缺页或识别不完整，以下总结基于可识别内容整理。

---

## 第2章：JSP/Servlet及相关技术详解

### 核心论点
- **问题**：Web应用开发需要处理动态页面生成、请求响应、会话管理、过滤监听等基础但复杂的问题。
- **观点**：JSP和Servlet是Java EE的核心表现层技术，掌握其基本原理、生命周期及内置对象是开发Java Web应用的基础。JSP的本质是Servlet，通过脚本、指令、动作和内置对象，可以动态生成HTML内容。

### 关键概念/事件
- **JSP生命周期**：JSP页面被Web容器编译成Servlet，经历初始化（`jspInit`）、服务（`_jspService`）和销毁（`jspDestroy`）三个阶段。
- **JSP内置对象**（9个）：`request`, `response`, `pageContext`, `session`, `application`, `out`, `config`, `page`, `exception`，它们分别代表了请求、响应、会话、应用上下文等Web开发核心资源。
- **Servlet配置方式**：支持两种方式，一是在`web.xml`中通过`<servlet>`和`<servlet-mapping>`配置；二是使用`@WebServlet`注解。
- **Filter与Listener**：Filter用于对请求进行预处理和后处理，可实现权限控制、日志记录等通用功能；Listener用于监听Web应用、会话和请求的生命周期及属性变化。

### 逻辑推演/叙事脉络
本章从构建一个基础的Web应用开始，解释了`web.xml`的作用。接着深入JSP原理，说明其本质是Servlet。然后逐一讲解了JSP的4种基本语法、3个编译指令、7个动作指令和9个内置对象的详细用法。在此基础上，介绍了Servlet的开发、配置、生命周期以及作为控制器的用法。最后，扩展讲解了Filter和Listener的创建与配置，并通过日志Filter、在线用户统计等实例说明了它们的实际应用。

### 经典金句/数据
- > “JSP的本质是Servlet，当用户向指定Servlet发送请求时，Servlet利用输出流动态生成HTML页面。”（p.77，根据上下文推断）
- > “使用application（即ServletContext实例）可以方便多个JSP、Servlet共享数据，但不要仅为了JSP、Servlet共享数据就将数据放入application中！通常只应该把Web应用的状态数据放入application里。”（p.103）

---

## 第3章：Struts 2的基本用法

### 核心论点
- **问题**：如何基于MVC模式构建结构清晰、易于维护的Java Web应用？
- **观点**：Struts 2是一个基于MVC思想的开源框架，通过核心Filter拦截用户请求，将请求分发给对应的Action（控制器）处理，Action调用业务逻辑后返回逻辑视图名，再由框架映射到物理视图（JSP）呈现。其核心优势在于低侵入式设计、配置灵活和强大的标签库。

### 关键概念/事件
- **MVC模式**：将应用分为Model（模型，JavaBean）、View（视图，JSP）、Controller（控制器，Servlet/Action），实现表现逻辑、控制逻辑和业务逻辑的分离。
- **Struts 2流程**：`StrutsPrepareAndExecuteFilter`拦截请求 -> 创建Action实例 -> 调用`execute()`方法 -> 返回字符串结果 -> 根据`struts.xml`映射到结果页面。
- **Action**：Struts 2的核心控制器，可以是简单的POJO，通常继承`ActionSupport`。通过`execute()`方法处理请求并返回结果字符串。
- **配置文件**：`struts.xml`是核心配置文件，用于配置常量、包（package）、命名空间和Action映射。Convention插件支持“约定优于配置”，简化配置。

### 逻辑推演/叙事脉络
本章首先回顾MVC思想，对比了Model 1和Model 2。然后详细介绍了Struts 2的下载、安装和在Eclipse中的配置。通过一个简单的登录示例，梳理了Struts 2的完整开发步骤和请求处理流程。接着深入讲解了`struts.xml`中的常量、包、命名空间和Action的配置细节，包括动态方法调用、通配符和method属性。最后介绍了结果映射的类型和配置，以及Convention插件的“约定”支持，展示了如何零配置实现映射。

### 经典金句/数据
- > “Struts2框架的底层机制是：核心Servlet或Filter接收到用户请求后，通常会对用户请求进行简单预处理，例如解析、封装参数等，然后通过反射来创建Action实例，并调用Action的指定方法处理用户请求。”（p.19）
- > “Struts2推荐把所有的视图页面存放在WEB-INF目录下，这样可以保护视图页面，避免直接向视图页面发送请求。”（p.17）

---

## 第4章：深入使用Struts 2

### 核心论点
- **问题**：Struts 2如何解决类型转换、输入校验、文件上传下载、拦截器扩展等实际开发中的核心问题？
- **观点**：Struts 2提供了强大的类型转换和输入校验框架，支持OGNL表达式进行复杂的数据绑定。文件上传下载通过`fileUpload`拦截器和`stream`结果类型被极大简化。拦截器是其核心扩展机制，通过可插拔的拦截器栈，可以实现AOP风格的横切关注点管理（如权限控制）。

### 关键概念/事件
- **类型转换**：Struts 2内建了常用类型转换器，支持基于OGNL的复合对象绑定。可通过继承`StrutsTypeConverter`自定义转换器，并通过局部（`ActionName-conversion.properties`）或全局（`xwork-conversion.properties`）配置文件注册。
- **输入校验**：支持声明式校验（编写`ActionName-validation.xml`校验规则文件）和手动校验（重写`validate()`或`validateXxx()`方法）。支持客户端校验和国际化错误提示。
- **文件上传/下载**：使用`<s:file name="upload" />`标签和Action中`File`、`String`（文件名）、`String`（文件类型）三个属性即可接收上传文件。可通过`fileUpload`拦截器过滤文件类型和大小。文件下载通过配置`stream`类型的结果，并指定`inputName`为返回`InputStream`的方法。
- **拦截器**：Struts 2功能的核心实现方式。开发者可实现`Interceptor`接口或继承`AbstractInterceptor`自定义拦截器，在`intercept()`方法中编写横切逻辑，并在`struts.xml`中配置和引用。支持方法过滤拦截器。

### 逻辑推演/叙事脉络
本章从类型转换开始，讲解了内建转换器和基于OGNL的转换，并深入介绍了自定义类型转换器的开发和注册。接着转入输入校验，详细演示了编写校验规则文件、国际化错误提示、客户端校验和手动校验。然后重点讲解了文件上传和下载的Action实现与配置。最后，详细剖析了Struts 2的拦截器机制，包括内建拦截器、拦截器栈、配置方法，并通过一个权限控制实例展示了自定义拦截器的开发和使用。

### 经典金句/数据
- > “对于大部分常用类型，开发者无须理会类型转换，Struts2可以完成大多数常用的类型转换。”（p.26）
- > “Struts2的输入校验既包括服务器端校验，也包括客户端校验...客户端校验绝不可代替服务器端校验。”（p.43）
- > “拦截器几乎完成了Struts2框架70%的工作，包括解析请求参数、将请求参数赋值给Action属性、执行数据校验、文件上传...”（p.2）

---

## 第5章：Hibernate的基本用法

### 核心论点
- **问题**：如何解决面向对象语言与关系数据库之间的“阻抗不匹配”问题？
- **观点**：Hibernate是一个开源的对象/关系映射（ORM）框架，它允许开发者以面向对象的方式操作关系数据库。通过将POJO（Plain Old Java Object）持久化类映射到数据库表，Hibernate自动将对象的CRUD操作转换为SQL语句，极大地简化了数据持久层开发。

### 关键概念/事件
- **ORM**：对象/关系映射，建立面向对象语言中的对象（Object）与关系数据库中的表（Table）、行（Row）、列（Column）之间的映射关系。
- **核心API**：`Configuration`（加载配置）、`SessionFactory`（数据库编译后的内存镜像，线程安全）、`Session`（与数据库交互的持久化管理器，轻量级）、`Transaction`（事务管理）。
- **持久化对象状态**：瞬态（Transient，未与Session关联）、持久化（Persistent，已关联，状态改变会同步到数据库）、脱管（Detached，曾关联但Session已关闭）。
- **映射方式**：推荐使用JPA注解（`@Entity`, `@Table`, `@Id`, `@Column`等），也支持传统的XML映射文件（`*.hbm.xml`）。

### 逻辑推演/叙事脉络
本章从ORM的概念和Hibernate的背景讲起，用一个简单的例子展示了如何将POJO变成持久化类、配置`hibernate.cfg.xml`并执行保存操作。接着介绍了Hibernate的体系结构和核心API。然后深入讲解了Hibernate配置文件（特别是数据库连接和方言）、持久化对象的状态转换（`save`, `get`, `load`, `update`, `delete`）。最后详细介绍了各种映射，包括属性映射（`@Column`, `@Transient`, `@Lob`等）、主键映射（`@GeneratedValue`）、集合属性映射（`@ElementCollection`）、组件属性映射（`@Embeddable`）以及复合主键映射。

### 经典金句/数据
- > “Hibernate是一个面向Java环境的对象/关系数据库映射工具，用于把面向对象模型表示的对象映射到基于SQL的关系模型的数据结构中。”（p.32）
- > “Hibernate采用低侵入式设计，这种设计对持久化类几乎不作任何要求。也就是说，Hibernate操作的持久化类基本上都是普通的、传统的Java对象（POJO）。”（p.35）
- > `PO = POJO + 持久化注解` （p.34）

---

## 第6章：深入使用Hibernate

### 核心论点
- **问题**：如何映射复杂的数据关系（关联、继承），以及如何高效地进行批量处理和查询？
- **观点**：Hibernate提供了全面的关联映射（1-1, 1-N, N-N）和继承映射（单表、连接子类、每个类一张表）策略。通过HQL（Hibernate Query Language）、条件查询（Criteria）和原生SQL查询，开发者可以灵活地检索数据。同时，Hibernate提供了批量处理、二级缓存、查询缓存和事件机制来提升性能和扩展功能。

### 关键概念/事件
- **关联映射**：通过`@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`注解定义关联关系，`@JoinColumn`映射外键。双向关联通常由“N”的一端维护。
- **继承映射**：三种策略，`InheritanceType.SINGLE_TABLE`（整个类层次一张表，性能好但列冗余）、`JOINED`（父类和子类各一张表，数据规范但查询需连接）、`TABLE_PER_CLASS`（每个具体类一张表，多态查询需union）。
- **HQL查询**：完全面向对象的查询语言，支持多态、关联、分页（`setFirstResult`, `setMaxResults`）、投影、分组和子查询。
- **批量处理**：使用Session的`flush()`和`clear()`管理一级缓存，或使用HQL的`update`和`delete`语句进行DML风格的批量操作。
- **二级缓存**：SessionFactory级别的全局缓存，需要显式开启并配置。适合读多写少、不常变化的数据。查询缓存用于缓存HQL查询结果。

### 逻辑推演/叙事脉络
本章首先全面讲解了1-1, 1-N, N-N等关联映射的配置和注意事项。接着详细介绍了三种继承映射策略及其优缺点。然后转入Hibernate的查询体系，重点讲解了HQL的语法、关联和连接、聚集函数和子查询，同时也介绍了条件查询和原生SQL查询。之后讲解了批量处理的技巧和DML风格的批量操作。最后深入探讨了事务控制、Session管理策略、二级缓存和查询缓存的配置与使用，以及拦截器和事件机制这两种扩展点。

### 经典金句/数据
- > “对于双向的1-N父子关联，使用1的一端控制关系的性能，比使用N的一端控制关系的性能低。”（p.3）
- > “强烈建议在Hibernate中使用version/timestamp字段来进行乐观锁定。对性能来说，这是最好的选择...”（p.57）
- > “所有面向对象程序设计语言都应该遵守的规范[指MVC]。”（此处原文有误，但该句出自第3章，第6章的核心金句应为： > “HQL是Hibernate Query Language的缩写，HQL的语法很像SQL的语法，但HQL是一种面向对象的查询语言。”（p.32）

---

## 第7章：Spring的基本用法

### 核心论点
- **问题**：如何有效解耦Java应用的各个组件（如业务层、持久层），并提供一个统一、轻量级的容器来管理它们？
- **观点**：Spring是一个轻量级的Java EE框架，其核心机制是**控制反转（IoC）**和**依赖注入（DI）**。Spring IoC容器负责创建和管理应用中的各种对象（Bean），并通过DI自动维护Bean之间的依赖关系，从而将组件间的耦合从代码层次提升到配置层次，大大提高了应用的可扩展性和可维护性。

### 关键概念/事件
- **依赖注入（DI）**：IoC容器在运行时，将被依赖对象主动注入到调用者对象的成员变量中。主要有**设值注入**（通过setter方法）和**构造注入**（通过构造器）两种方式。
- **Spring容器**：`ApplicationContext`（功能更强大）和`BeanFactory`（更底层）。负责实例化、配置和管理Bean。`ClassPathXmlApplicationContext`是最常用的实现类。
- **Bean的作用域**：`singleton`（单例，默认）、`prototype`（原型，每次请求创建新实例）、`request`、`session`等。
- **Bean的生命周期**：在`singleton`作用域下，Spring管理Bean从实例化、属性注入、初始化（`@PostConstruct`, `afterPropertiesSet`, `init-method`）到销毁（`@PreDestroy`, `destroy-method`）的完整过程。
- **SpEL**：Spring表达式语言，用于在运行时查询和操作对象图，可以在Bean定义中使用（`#{...}`）。

### 逻辑推演/叙事脉络
本章从Spring的背景和优势讲起。核心部分围绕IoC/DI展开，通过Axe和Person的例子，生动对比了传统`new`方式与Spring的依赖注入方式，并详细演示了设值注入和构造注入的XML配置方法。接着介绍了Spring容器的两种类型，重点讲解了`ApplicationContext`的国际化、事件机制等高级功能。然后深入Bean的管理，包括作用域、依赖配置、集合注入、自动装配等。之后介绍了Java配置类（`@Configuration`, `@Bean`）和三种创建Bean实例的方式（构造器、静态工厂、实例工厂）。最后讲解了抽象Bean、工厂Bean、Bean生命周期管理和SpEL的使用。

### 经典金句/数据
- > “Spring框架的本质就是通过XML配置来驱动Java代码，这样就可以把原本由Java代码管理的耦合关系，提取到XML配置文件管理中。”（p.29）
- > “依赖注入让Spring的Bean以配置文件组织在一起，而不是以硬编码的方式耦合在一起。”（p.12）
- > “当某个Java实例需要其他Java实例时，系统自动提供所需要的实例，无须程序显式获取。”（p.12）

---

## 第8章：深入使用Spring

### 核心论点
- **问题**：如何利用Spring的高级特性，如后处理器、AOP、缓存、声明式事务以及与其他框架（Struts 2, Hibernate）整合，来构建更强大、更完整的Java EE应用？
- **观点**：Spring通过Bean后处理器和容器后处理器扩展IoC容器功能，通过AOP实现横切关注点的解耦。Spring提供了统一的声明式事务管理策略，并能与Struts 2、Hibernate等主流框架无缝整合，真正成为Java EE应用的一站式解决方案。

### 关键概念/事件
- **AOP**：面向切面编程，将日志、事务、安全等横切逻辑从业务逻辑中分离。Spring AOP使用动态代理（JDK或CGLIB）实现，支持AspectJ注解（`@Aspect`, `@Before`, `@After`, `@Around`等）。
- **事务管理**：Spring提供了`PlatformTransactionManager`接口，统一了编程式和声明式事务。声明式事务通过`@Transactional`注解或`<tx:advice>`与`<aop:advisor>`配合XML配置实现。
- **零配置**：通过`@Component`, `@Service`, `@Repository`, `@Controller`自动扫描类并注册为Bean，用`@Autowired`或`@Resource`实现依赖注入。
- **SSH整合**：Spring负责管理Struts 2的Action实例（或通过自动装配）和Hibernate的`SessionFactory`，并提供`HibernateTemplate`（传统方式）或`HibernateDaoSupport`等DAO支持类。推荐使用`SessionFactory.getCurrentSession()`。
- **Spring缓存**：通过`@Cacheable`和`@CacheEvict`注解，对方法返回值进行缓存，底层可集成EhCache等缓存实现。

### 逻辑推演/叙事脉络
本章首先介绍了Bean后处理器和容器后处理器（如`PropertyPlaceholderConfigurer`）。接着重点讲解了AOP，从必要性入手，通过AspectJ入门引出AOP概念，然后详细阐述Spring对AspectJ注解和XML配置方式的支持。之后介绍了Spring 3.1新增的缓存机制。事务部分详细比较了不同事务策略，并演示了XML和注解两种配置方式。最后，本章用较大篇幅讲解了Spring与Struts 2（管理Action、自动装配）以及Spring与Hibernate（管理SessionFactory、实现DAO）的整合策略和代码实现。

### 经典金句/数据
- > “SpringAOP就是动态AOP实现的代表，SpringAOP不需要在编译时对目标类进行增强，而是在运行时生成目标类的代理类。”（p.26）
- > “声明式事务能大大降低开发者的代码书写量，而且声明式事务几乎不影响应用的代码。”（p.62）
- > “Spring的事务管理将代码从底层具体的事务API中抽象出来，该抽象能以任何底层事务为基础。”（p.62）
- > “推荐使用SessionFactory的getCurrentSession()来获取Session，然后通过Session进行持久化操作。”（p.5）

---

## 第9章：企业应用开发的思考和策略

### 核心论点
- **问题**：企业级应用开发面临哪些挑战？应如何运用设计模式和架构策略来应对？
- **观点**：企业应用面临可扩展性、开发速度、稳定性、成本等多重挑战。应对这些挑战需要利用优秀的框架、建模工具和代码生成器。设计模式（如单例、工厂、代理、策略等）提供了解决特定问题的成熟方案。合理的架构设计（如贫血模型、领域对象模型）是保证项目成功的关键。

### 关键概念/事件
- **设计模式分类**：创建型（单例、工厂）、结构型（代理、门面、桥接）、行为型（命令、策略、观察者）。
- **单例模式**：确保一个类只有一个实例，并提供全局访问点。Spring容器默认将Bean配置为`singleton`作用域。
- **代理模式**：为其他对象提供一种代理以控制对这个对象的访问。Hibernate的延迟加载、Spring AOP的底层实现都是代理模式的应用。
- **策略模式**：定义一系列算法，并将每个算法封装起来，使它们可以互相替换。`PlatformTransactionManager`和`Resource`接口是其典型代表。
- **贫血模型 vs. 领域对象模型**：贫血模型中领域对象只包含数据，不包含业务逻辑；领域对象模型（Rich Domain Model）中领域对象既包含数据也包含业务逻辑。

### 逻辑推演/叙事脉络
本章开篇论述了企业应用开发面临的四大挑战（可扩展性、快捷开发、稳定性、成本）。接着提出了应对策略，如使用建模工具和优秀框架。然后，本章用大量篇幅和代码示例精讲了单例、简单工厂、工厂方法/抽象工厂、代理（包括JDK动态代理）、命令、策略、门面、桥接、观察者这九种经典设计模式，并结合Spring、Hibernate等框架分析了它们的应用场景。最后，本章探讨了常见的架构设计策略，重点对比了贫血模型和领域对象模型，并分析了后者的几种演进和简化方式。

### 经典金句/数据
- > “软件不是一次性系统...如果支撑企业系统的软件不具备可扩展性，当企业平台发生改变时，如何面对这种改变？”（p.20）
- > “设计模式...是对处于特定环境下，经常出现的某类软件开发问题的，一种相对成熟的设计方案。”（p.22）
- > “所谓贫血，指DomainObject只是单纯的数据类，不包含业务逻辑方法...采用这种DomainObject的架构即所谓的贫血模型。”（p.53）

---

## 第10章：简单工作流系统

### 核心论点
- **问题**：如何综合运用Struts 2、Spring、Hibernate三大框架构建一个真实、可运行的Java EE企业级应用？
- **观点**：通过一个包含考勤、申请、审批、工资结算等功能的简单工作流系统（HRSystem），完整演示了基于SSH框架的Java EE应用开发全过程。该系统遵循严格的分层架构（领域对象层、DAO层、Service层、Web层），使用Spring IoC容器管理组件依赖，使用声明式事务管理业务逻辑，使用Quartz进行任务调度（自动打卡、月结工资），并使用Struts 2拦截器实现权限控制。

### 关键概念/事件
- **项目架构**：贫血模型。分为`domain`（POJO）、`dao`（数据访问对象）、`service`（业务逻辑）、`action`（Struts 2控制器）和JSP视图层。
- **核心业务逻辑**：`EmpManager`（员工服务）和`MgrManager`（经理服务）。`EmpManager`提供打卡（`punch`）、申请（`addApplication`）、查看工资等方法。
- **任务调度**：使用Quartz框架，定义了`PunchJob`（自动打卡）和`PayJob`（自动结算工资），通过Spring的`CronTriggerFactoryBean`配置触发时间。
- **权限控制**：自定义了两个拦截器`EmpAuthorityInterceptor`和`MgrAuthorityInterceptor`，在`struts.xml`中分别配置给员工和经理的Action栈，实现基于角色的访问控制。
- **数据库设计**：共有7个表，包括`employee_inf`, `manager_inf`（继承关系），`attend_inf`, `application_inf`, `checkback_inf`等，体现了复杂的关联和继承映射。

### 逻辑推演/叙事脉络
本章是一个完整的案例开发章节。首先介绍了项目的业务背景、功能需求（员工/经理）和技术栈。然后从持久层开始，依次设计并实现了7个领域对象及其复杂的关联/继承映射。接着，实现了基于`BaseDaoHibernate4`的DAO层，为每个实体提供CRUD和特定查询。然后，实现了`EmpManager`和`MgrManager`业务逻辑组件，并配置了声明式事务。之后，配置了Quartz任务调度来支持自动打卡和月结工资。最后，开发了Struts 2的多个Action来处理登录、打卡、申请、审批等流程，并利用拦截器完成了权限控制。整个章节完整展示了从设计到编码的全过程。

### 经典金句/数据
- > “本章将会综合运用前面章节所介绍的知识来开发一个简单的工作流系统。” （p.63）
- > “本系统采用前面介绍的Java EE架构：Struts 2.3 + Spring 4.0 + Hibernate 4.3，该系统结构成熟，性能良好，运行稳定。”（p.63）
- > “对于所有1-N的关联关系，建议不要使用“1”的一端控制关系，因此建议为@OneToMany注解增加mappedBy属性，让“N”的一端来控制关联关系。”（p.70，出自第6章内容，但在第10章设计中被遵循）
- > 系统在早上7点、下午12点自动为所有员工插入两条“旷工”记录，员工实际打卡时修改这些记录。（p.86）