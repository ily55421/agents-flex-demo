# 《Spring实战（第4版）》章节总结

## 目录说明

本书共计21章，分为4个部分。PDF内容完整，目录与正文对应关系清晰。以下总结严格遵循原书目录顺序，从前言、第1部分至第4部分逐章整理。部分页面存在OCR识别痕迹，但核心内容可完整提取。

---

## 前言/关于本书

### 核心论点

本书旨在帮助读者掌握Spring框架的核心特性与高级用法，通过实际示例代码展示Spring如何简化企业级Java开发。作者Craig Walls强调，Spring的核心使命是“简化Java开发”，本书按照Spring模块化的特点组织内容，既适合初学者系统学习，也可作为资深开发者的参考手册。

### 关键概念/事件

- **目标读者**：具有一定Java编程基础的企业级Java开发人员、测试人员。
- **内容组织**：全书分为4部分——核心、Web、后端集成、服务集成。
- **第4版更新**：新增基于Java的配置、条件化配置、Spring MVC增强、Thymeleaf、Spring Security Java配置、Spring Data、声明式缓存、WebSocket/STOMP、Spring Boot等内容。
- **代码规范**：示例代码可于www.manning.com/SpringinActionFourthEdition下载。
- **作者在线论坛**：读者可访问Manning出版社在线论坛与作者和其他读者交流。

### 逻辑推演/叙事脉络

前言首先阐述了Spring框架的历史地位和演变过程，从替代EJB到成为企业级Java开发的事实标准。随后说明了本书第4版的更新重点，包括基于Java的配置、条件化配置、Thymeleaf、Spring Security、Spring Data、缓存、WebSocket和Spring Boot等新特性。最后给出了本书的路线图，按4个部分逐一介绍各章主题，并说明了代码规范、下载方式和读者交流渠道。

### 经典金句/数据

> “Spring的使命：简化Java开发。”(p.24)

> “全球有超过100000的开发者使用本书来学习Spring。”(p.2)

---

## 第1章：Spring之旅

### 核心论点

本章回答“Spring是什么、能做什么”这一根本问题。作者的核心观点是：Spring通过依赖注入(DI)、面向切面编程(AOP)和模板技术，全方位简化Java开发，让POJO具备企业级服务能力。

### 关键概念/事件

- **依赖注入(DI)**：对象无需自行创建或管理依赖关系，由Spring容器在运行时注入，实现松耦合。
- **面向切面编程(AOP)**：将横切关注点（日志、事务、安全）从业务逻辑中分离，模块化为可重用的切面。
- **应用上下文(ApplicationContext)**：Spring容器的核心接口，负责创建、装配和管理bean的生命周期。
- **模板消除样板代码**：如JdbcTemplate封装JDBC样板代码，让开发者专注核心逻辑。
- **Spring生态**：包括Spring Security、Spring Data、Spring Boot、Spring Web Flow等众多子项目。

### 逻辑推演/叙事脉络

本章从Spring的诞生背景（替代重量级EJB）出发，阐述了Spring的四大简化策略：POJO编程、依赖注入、AOP和模板技术。通过Knight和Quest的例子演示了构造器注入和XML/Java配置；通过Minstrel的例子演示了AOP的前置/后置通知。随后介绍了Spring容器的类型（BeanFactory vs ApplicationContext）和bean的生命周期（12个步骤）。最后概览了Spring的模块结构（6类20+模块）和Spring Portfolio中的重要子项目（Web Flow、Security、Data、Boot等），以及Spring 3.1/3.2/4.0的新特性。

### 经典金句/数据

> “Spring旨在通过模板封装来消除样板式代码。”(p.35)

> “在Spring中，对象无需自己查找或创建与其所关联的其他对象。相反，容器负责把需要相互协作的对象引用赋予各个对象。”(p.49)

- Spring框架包含20个不同的模块(p.40)
- 全球有超过100000的开发者使用本书学习Spring(p.2)

---

## 第2章：装配Bean

### 核心论点

本章回答“如何告诉Spring创建哪些bean以及如何装配它们”。作者的核心建议是：优先使用自动化配置（组件扫描+自动装配），其次使用JavaConfig，最后才考虑XML配置。

### 关键概念/事件

- **组件扫描(@ComponentScan)**：Spring自动发现带有@Component注解的类并创建bean。
- **自动装配(@Autowired/@Inject)**：Spring自动满足bean之间的依赖关系。
- **JavaConfig(@Configuration/@Bean)**：通过Java代码显式配置bean，类型安全且对重构友好。
- **XML配置**：传统的配置方式，使用<bean>元素声明bean，支持c-命名空间和p-命名空间简化。
- **混合配置**：使用@Import和@ImportResource在JavaConfig中引用其他配置/XML。

### 逻辑推演/叙事脉络

本章按照“自动化配置→JavaConfig→XML配置→混合配置”的顺序展开。首先以CD和CDPlayer为例，演示了@Component和@ComponentScan实现组件扫描，@Autowired实现自动装配。随后转向JavaConfig，用@Bean注解声明bean并演示构造器注入。然后介绍XML配置，包括<bean>声明、<constructor-arg>、c-命名空间、<property>、p-命名空间以及集合装配（<list>/<set>）。最后展示了如何在JavaConfig中引用XML（@ImportResource）以及在XML中引用JavaConfig（<bean>声明配置类）。每种方式都配有测试代码验证。

### 经典金句/数据

> “在Spring中装配bean有三种主要方式：自动化配置、基于Java的显式配置以及基于XML的显式配置。我建议尽可能地使用自动配置的机制。显式配置越少越好。”(p.50)

- @ComponentScan默认扫描与配置类相同的包(p.52)
- c-命名空间使用“c:参数名-ref”语法简化构造器注入(p.65)
- p-命名空间使用“p:属性名-ref”语法简化属性注入(p.71)

---

## 第3章：高级装配

### 核心论点

本章解决Spring装配中的高级场景：不同环境的配置切换、条件化bean创建、自动装配歧义性处理、bean作用域控制以及运行时值注入。

### 关键概念/事件

- **Profile**：通过@Profile注解和spring.profiles.active/default属性，实现不同环境（开发/测试/生产）使用不同bean配置。
- **条件化bean(@Conditional)**：Spring 4引入，通过实现Condition接口的matches()方法，在运行时决定是否创建bean。
- **@Primary与@Qualifier**：解决自动装配歧义性，@Primary标记首选bean，@Qualifier缩小选择范围（支持自定义限定符注解）。
- **Bean作用域**：单例（默认）、原型（@Scope("prototype")）、会话、请求。会话/请求作用域需配合代理（proxyMode）解决注入单例bean的问题。
- **SpEL(Spring表达式语言)**：运行时计算值注入，支持字面量、bean引用、属性/方法调用、运算符、正则、集合操作等。

### 逻辑推演/叙事脉络

本章从环境差异问题出发（开发用嵌入式数据库 vs 生产用JNDI），引出@Profile注解的使用方式（类级别/方法级别/XML中的<beans profile>）以及激活profile的多种途径。随后介绍Spring 4的@Conditional机制，并以@Profile底层实现（ProfileCondition）为例说明。接着处理自动装配歧义性：先用@Primary简单解决，再用@Qualifier精确限定，最后创建自定义限定符注解（如@Cold、@Creamy）。然后讨论bean作用域，重点说明会话/请求作用域如何通过代理注入单例bean。最后介绍运行时值注入的两种方式：属性占位符（@PropertySource+Environment/@Value）和SpEL（#{...}表达式），并详细列举了SpEL支持的运算符和集合操作。

### 经典金句/数据

> “Spring为环境相关的bean所提供的解决方案并不是在构建的时候做出决策，而是等到运行时再来确定。这样的结果就是同一个部署单元能够适用于所有的环境，没有必要进行重新构建。”(p.80)

- @Profile注解在Spring 4中基于@Conditional和ProfileCondition实现(p.88)
- SpEL表达式放在“#{...}”中，属性占位符放在“${...}”中(p.102)

---

## 第4章：面向切面的Spring

### 核心论点

本章回答“如何将横切关注点（日志、事务、安全）从业务逻辑中分离”。作者的核心观点是：AOP通过切面（通知+切点）实现横切关注点的模块化，Spring AOP基于代理，支持方法级别的拦截。

### 关键概念/事件

- **AOP术语**：通知(Advice，5种类型)、连接点(Join point)、切点(Pointcut)、切面(Aspect)、引入(Introduction)、织入(Weaving，编译期/类加载期/运行期)。
- **Spring AOP限制**：只支持方法级别的连接点（基于动态代理），但可通过AspectJ补充字段/构造器拦截。
- **注解创建切面(@Aspect)**：使用@Before、@After、@AfterReturning、@AfterThrowing、@Around，配合@Pointcut定义切点，通过@EnableAspectJAutoProxy启用。
- **XML声明切面**：使用aop命名空间的<aop:config>、<aop:aspect>、<aop:pointcut>、<aop:before>等元素。
- **参数化通知**：使用args()限定符将方法参数传递给通知。
- **引入新功能(@DeclareParents)**：为现有类动态添加新接口实现。
- **注入AspectJ切面**：通过factory-method="aspectOf()"获取AspectJ切面实例并注入依赖。

### 逻辑推演/叙事脉络

本章以“家庭用电监控”类比横切关注点，引出AOP概念。首先定义AOP术语（通知、连接点、切点、切面、引入、织入），说明Spring AOP只支持方法拦截（基于代理）。随后介绍AspectJ切点表达式语言的Spring支持版本（execution、within、args、@args、@target、@annotation等）以及bean()指示器。接着以Audience（观众）为例，演示了如何使用@AspectJ注解创建切面（前置、后置、返回、异常通知），并用@Pointcut复用切点表达式。然后介绍更强大的环绕通知(@Around)及其ProceedingJoinPoint参数。再演示参数化通知（TrackCounter记录磁道播放次数）和引入新功能（@DeclareParents为Performance添加Encoreable）。最后说明XML配置方式（<aop:config>）和如何为AspectJ切面注入依赖（factory-method="aspectOf"）。

### 经典金句/数据

> “AOP能够使这些服务模块化，并以声明的方式将它们应用到它们需要影响的组件中去。所造成的结果就是这些组件会具有更高的内聚性并且会更加关注自身的业务，完全不需要了解涉及系统服务所带来复杂性。”(p.30)

- Spring支持5种通知：Before、After、After-returning、After-throwing、Around(p.112)
- Spring支持AspectJ的11个切点指示器中的9个(p.116)

---

## 第5章：构建Spring Web应用程序

### 核心论点

本章回答“如何使用Spring MVC构建Web应用”。作者的核心观点是：Spring MVC基于前端控制器模式，DispatcherServlet作为核心，通过注解驱动（@Controller、@RequestMapping）将请求映射到处理器方法，支持模型-视图-控制器分离。

### 关键概念/事件

- **DispatcherServlet**：前端控制器，接收请求并路由到控制器。
- **处理器映射**：根据URL将请求映射到对应的控制器方法。
- **@Controller与@RequestMapping**：声明控制器和请求映射（支持value、method、produces、consumes等属性）。
- **模型(Model)**：控制器将数据放入Model，传递给视图渲染。
- **视图解析器(ViewResolver)**：将逻辑视图名解析为物理视图（如InternalResourceViewResolver解析JSP）。
- **请求参数绑定**：@RequestParam处理查询参数，@PathVariable处理路径变量。
- **表单处理**：@PostMapping处理表单提交，结合@ModelAttribute绑定表单数据到对象。
- **表单校验**：使用JSR-303校验注解（@NotNull、@Size等），结合@Valid和Errors对象处理校验失败。

### 逻辑推演/叙事脉络

本章从Spring MVC请求处理流程（DispatcherServlet→处理器映射→控制器→模型→视图解析器→视图）开始，介绍了如何用AbstractAnnotationConfigDispatcherServletInitializer配置DispatcherServlet（替代web.xml），以及@EnableWebMvc启用Spring MVC。随后以Spittr（微博应用）为例，编写HomeController和SpittleController，演示@RequestMapping、Model、视图解析（JSP）。接着介绍如何接受请求输入：@RequestParam处理分页参数（max、count），@PathVariable处理REST风格路径（/spittles/12345）。然后处理表单注册：SpitterController中的registerForm（GET）展示表单，processRegistration（POST）处理提交，重定向到基本信息页（/spitter/{username}）。最后引入表单校验：在Spitter实体类添加@NotNull和@Size注解，控制器方法添加@Valid和Errors参数，用<sf:errors>在JSP中显示错误信息。

### 经典金句/数据

> “Spring MVC的控制器只是方法上添加了@RequestMapping注解的类，这个注解声明了它们所要处理的请求。”(p.146)

- DispatcherServlet映射为“/”时成为应用的默认Servlet，处理所有进入应用的请求(p.143)
- @RequestParam的defaultValue属性用于指定参数缺失时的默认值(p.156)

---

## 第6章：渲染Web视图

### 核心论点

本章回答“如何将控制器产生的模型数据渲染为HTML响应”。作者介绍了Spring支持的多种视图技术：JSP（InternalResourceViewResolver）、Apache Tiles布局、以及新兴的Thymeleaf模板引擎。

### 关键概念/事件

- **ViewResolver接口**：逻辑视图名→物理视图实现，Spring内置13种实现。
- **InternalResourceViewResolver**：解析JSP视图，支持前缀/后缀配置，可设置为JstlView以支持JSTL国际化。
- **Spring表单绑定标签库(<sf:xxx>)**：<sf:form>、<sf:input>、<sf:password>、<sf:errors>，实现模型对象与表单域的双向绑定及错误展示。
- **Spring通用标签库(<s:xxx>)**：<s:message>国际化、<s:url>构建URL（支持Servlet上下文路径、参数、路径变量、转义）、<s:escapeBody>内容转义。
- **Apache Tiles**：定义可重用的页面布局，通过<definition>和<put-attribute>组合头部、主体、底部模板。
- **Thymeleaf**：原生模板（纯HTML），无需标签库，使用th:属性（th:href、th:object、th:field、th:errors等），支持SpEL表达式。

### 逻辑推演/叙事脉络

本章首先介绍ViewResolver接口和Spring内置的13种视图解析器。然后重点讲解JSP视图：配置InternalResourceViewResolver（前缀/WEB-INF/views/，后缀.jsp），使用Spring表单绑定标签库（<sf:form>绑定commandName，<sf:input>绑定path，<sf:errors>显示校验错误，支持cssClass/cssErrorClass样式），以及通用标签库（<s:message>国际化，需配置MessageSource如ResourceBundleMessageSource；<s:url>构建带路径参数和转义的URL）。接着介绍Apache Tiles：配置TilesConfigurer加载定义文件（tiles.xml），TilesViewResolver解析视图，通过<definition>和<put-attribute>定义布局模板（page.jsp引用header/body/footer）。最后介绍Thymeleaf：配置ThymeleafViewResolver、SpringTemplateEngine和TemplateResolver，模板使用th:属性（th:href="@{...}"、th:object="${...}"、th:field="*{...}"、th:errors="*{...}"）和th:each迭代错误信息，特点是纯HTML可在浏览器中直接预览。

### 经典金句/数据

> “JSP模板是原生的，不依赖于标签库。它能在接受原始HTML的地方进行编辑和渲染。”(p.191)

- Spring提供了13个内置ViewResolver实现(p.170)
- Thymeleaf模板需要配置三个bean：ThymeleafViewResolver、SpringTemplateEngine、TemplateResolver(p.192)

---

## 第7章：Spring MVC的高级技术

### 核心论点

本章解决Spring MVC中的高级配置和处理需求：DispatcherServlet的自定义配置（customizeRegistration）、额外的Servlet/Filter注册、multipart文件上传、异常处理（@ExceptionHandler）、控制器通知（@ControllerAdvice）以及重定向中的flash属性传递数据。

### 关键概念/事件

- **Servlet容器配置**：通过WebApplicationInitializer或扩展AbstractAnnotationConfigDispatcherServletInitializer，重载customizeRegistration()配置multipart等。
- **Multipart解析器**：配置MultipartResolver（CommonsMultipartResolver或StandardServletMultipartResolver），处理文件上传，使用@RequestPart或MultipartFile参数接收。
- **异常处理**：@ExceptionHandler注解处理控制器内的异常；@ControllerAdvice定义全局异常处理器。
- **Flash属性**：RedirectAttributes.addFlashAttribute()在重定向后依然存活的属性，解决POST/Redirect/GET模式中的数据传递问题。

### 逻辑推演/叙事脉络

本章首先介绍DispatcherServlet配置的替代方案：通过customizeRegistration()设置multipart临时目录；通过实现WebApplicationInitializer注册额外的Servlet/Filter；通过重载getServletFilters()注册映射到DispatcherServlet的Filter；以及在web.xml中声明DispatcherServlet（适用于Servlet 3.0以下容器）。随后详细讲解multipart文件上传：配置MultipartResolver，编写控制器方法接收MultipartFile参数。然后处理异常：使用@ResponseStatus将异常映射为HTTP状态码，用@ExceptionHandler处理特定异常，用@ControllerAdvice创建全局异常处理。最后介绍跨重定向传递数据：通过URL模板传递简单数据，通过flash属性（RedirectAttributes.addFlashAttribute）传递复杂对象，flash属性存储在会话中，重定向后自动移除。

### 经典金句/数据

> “在处理POST类型的请求时，在请求处理完成后，最好进行一下重定向，这样浏览器的刷新就不会重复提交表单了。”(p.162)

- customRegistration()方法中的ServletRegistration.Dynamic可用于设置multipart配置、load-on-startup优先级和初始化参数(p.199)
- 所有getServletFilters()返回的Filter都会自动映射到DispatcherServlet上(p.200)

---

## 第8章：使用Spring Web Flow

*说明：该PDF中第8章仅包含目录框架（8.1装配流程执行器、8.2流程组件、8.3披萨流程案例、8.4保护Web流程、8.5小结），无完整正文内容。以下总结基于可识别的标题和框架信息整理。*

### 核心论点

Spring Web Flow是Spring MVC的扩展，用于构建会话式、基于流程的Web应用程序（如购物车、向导功能），通过状态（State）、转移（Transition）和流程数据（Flow Data）管理用户交互流程。

### 关键概念/事件

- **流程执行器(FlowExecutor)**：驱动流程执行的引擎，负责启动和执行流程。
- **流程注册表(FlowRegistry)**：存储和管理流程定义的容器。
- **状态(State)**：流程中的节点，包括视图状态(View State)、动作状态(Action State)、决策状态(Decision State)、子流程状态(Subflow State)、结束状态(End State)。
- **转移(Transition)**：状态之间的转换，由事件触发。
- **流程数据**：流程作用域(Flow Scope)、会话作用域(Conversation Scope)、请求作用域(Request Scope)等。
- **披萨流程案例**：演示如何定义基本流程、收集顾客信息、构建订单、支付。

### 逻辑推演/叙事脉络

本章假设的展开顺序：首先配置Spring Web Flow（装配FlowExecutor、配置FlowRegistry、处理流程请求），然后介绍流程的组件（状态类型、转移规则、流程数据作用域），接着通过披萨订购流程案例综合演示，最后说明如何保护Web流程。

### 经典金句/数据

> 说明：该部分PDF无正文，无法提取金句。

---

## 第9章：保护Web应用

### 核心论点

本章回答“如何为Spring Web应用添加安全性”。作者的核心观点是：Spring Security通过Filter链实现声明式安全，支持用户认证、请求拦截、视图级安全保护。

### 关键概念/事件

- **Spring Security模块**：核心、Web配置、认证、ACL、标签库等。
- **过滤Web请求**：DelegatingFilterProxy代理到Spring管理的Filter，拦截请求进行安全处理。
- **用户存储**：内存用户存储(InMemoryUserDetailsManager)、基于JDBC的数据库存储、LDAP存储、自定义UserDetailsService。
- **请求拦截**：通过antMatchers()配置路径权限，使用Spring表达式（@PreAuthorize、@PostAuthorize）进行方法级安全。
- **强制通道安全**：requiresChannel()确保HTTP/HTTPS使用。
- **CSRF防护**：默认启用，防止跨站请求伪造。
- **自定义登录页**：formLogin()配置自定义登录页面。
- **Remember-me功能**：token存储用户身份。
- **视图安全**：JSP中使用Spring Security标签库（<sec:authorize>），Thymeleaf使用Spring Security方言。

### 逻辑推演/叙事脉络

本章从Spring Security简介开始，说明其模块组成和过滤Web请求的机制（DelegatingFilterProxy）。随后介绍如何编写简单的安全性配置：@EnableWebSecurity、继承WebSecurityConfigurerAdapter。然后选择用户存储方式：内存用户、数据库（JdbcUserDetailsManager）、LDAP、自定义UserDetailsService。接着配置请求拦截：通过antMatchers()设置路径权限规则，使用Spring表达式（hasRole、hasIpAddress等），通过requiresChannel()强制通道安全（HTTPS），默认启用CSRF防护（可禁用但不推荐）。之后配置用户认证：自定义登录页、启用HTTP Basic认证、Remember-me功能（token存储）、退出处理。最后保护视图：JSP中使用Spring Security标签库（<sec:authorize access="isAuthenticated()">），Thymeleaf使用Spring Security方言。

### 经典金句/数据

> “安全对于许多应用都是一个非常关键的切面。利用Spring AOP，Spring Security为Spring应用提供了声明式的安全机制。”(p.43)

- Spring Security默认启用CSRF防护，可在配置中调用.csrf().disable()禁用但不推荐(p.?)

---

## 第10章：通过Spring和JDBC征服数据库

### 核心论点

本章回答“如何使用Spring简化JDBC数据访问”。作者的核心观点是：Spring的数据访问哲学是“模板化”，通过异常体系（DataAccessException）和模板类（JdbcTemplate）消除样板代码，简化数据库操作。

### 关键概念/事件

- **Spring数据访问异常体系**：DataAccessException是运行时异常，封装了特定数据库的错误信息（如BadSqlGrammarException、DuplicateKeyException），无需强制捕获。
- **数据访问模板化**：JdbcTemplate封装了连接管理、语句创建、结果集处理、异常转换等样板代码。
- **配置数据源**：JNDI数据源（JndiObjectFactoryBean）、连接池（BasicDataSource）、JDBC驱动（DriverManagerDataSource）、嵌入式数据源（EmbeddedDatabaseBuilder）、使用profile选择数据源。
- **JdbcTemplate常用方法**：queryForObject()、query()、update()、execute()，支持RowMapper将结果集映射为对象。

### 逻辑推演/叙事脉络

本章首先阐述Spring的数据访问哲学：模板化+运行时异常体系。通过对比原生JDBC的样板代码（程序清单1.12）和JdbcTemplate的简洁代码（程序清单1.13），直观展示Spring的改进。随后介绍配置数据源的多种方式：JNDI（适用于生产环境）、连接池（如BasicDataSource，适用于QA）、JDBC驱动（简单但无连接池）、嵌入式数据库（适用于开发和测试），并通过profile切换不同环境的数据源。最后重点讲解JdbcTemplate的使用：如何获取JdbcTemplate（通过DataSource），以及使用queryForObject、query（配合RowMapper）、update等方法执行数据库操作。

### 经典金句/数据

> “模板能够让你的代码关注于自身的职责。”(p.36)

- 原生JDBC查询员工代码中，大量代码是样板式的连接创建、异常处理和资源关闭(p.34-35)
- JdbcTemplate的queryForObject()方法接受SQL查询、RowMapper对象和查询参数(p.36)

---

## 第11章：使用对象-关系映射持久化数据

### 核心论点

本章回答“如何在Spring中集成JPA和Hibernate实现ORM数据持久化”，并介绍Spring Data JPA如何自动生成Repository实现，进一步简化数据访问层开发。

### 关键概念/事件

- **Spring集成Hibernate**：通过LocalSessionFactoryBean声明SessionFactory，使用HibernateTemplate（已废弃）或直接使用SessionFactory。
- **Spring与JPA集成**：配置实体管理器工厂（LocalEntityManagerFactoryBean、LocalContainerEntityManagerFactoryBean+JpaVendorAdapter，或JNDI获取），使用@PersistenceContext注入EntityManager。
- **基于JPA的Repository**：编写DAO实现类，使用@Repository注解，利用EntityManager进行CRUD操作。
- **Spring Data JPA**：通过继承JpaRepository接口，Spring自动生成Repository实现（无需实现类），支持方法名解析查询（如findByUsernameAndPassword）。
- **自定义查询**：使用@Query注解和JPQL或原生SQL。
- **混合自定义功能**：通过自定义接口+实现类+Spring Data Repository继承该接口实现。

### 逻辑推演/叙事脉络

本章首先介绍在Spring中集成Hibernate的方式：声明SessionFactory bean（LocalSessionFactoryBean），使用getCurrentSession()获取Session，编写不依赖Spring的Hibernate代码。随后转向JPA（Java Persistence API），配置实体管理器工厂（重点推荐LocalContainerEntityManagerFactoryBean+JpaVendorAdapter如HibernateJpaVendorAdapter），使用@PersistenceContext注入EntityManager，编写基于JPA的Repository。最后重点介绍Spring Data JPA的强大功能：只需定义接口继承JpaRepository或CrudRepository，Spring自动生成实现；支持通过方法名约定（findBy+属性名）生成查询；通过@Query注解声明自定义JPQL/SQL查询；通过自定义接口和实现类混合添加自定义方法。

### 经典金句/数据

> “Spring Data使得在Spring中使用任何数据库都变得非常容易。”(p.44)

- Spring Data JPA只需继承JpaRepository接口，Spring自动生成Repository实现(p.?)

---

## 第12章：使用NoSQL数据库

### 核心论点

本章回答“如何在Spring中使用MongoDB（文档数据库）、Neo4j（图数据库）和Redis（键值存储）”。作者的观点是：Spring Data项目为各种NoSQL数据库提供了统一的编程模型，包括模板类和自动Repository生成。

### 关键概念/事件

- **MongoDB**：文档数据库，使用@Document注解实体，@Id标记ID字段，MongoTemplate进行CRUD，或继承MongoRepository自动生成实现。
- **Neo4j**：图数据库，使用@NodeEntity注解实体，@Relationship定义关系，Neo4jTemplate操作，或继承GraphRepository。
- **Redis**：键值存储，使用JedisConnectionFactory连接，RedisTemplate<K,V>操作，支持多种序列化器（JdkSerializationRedisSerializer、Jackson2JsonRedisSerializer等）。

### 逻辑推演/叙事脉络

本章分三部分介绍三种NoSQL数据库。第一部分MongoDB：配置MongoClient、MongoTemplate，实体添加@Document/@Id/@Field注解，使用MongoTemplate（find、save、remove）或继承MongoRepository（支持方法名查询）。第二部分Neo4j：配置嵌入式或远程服务器，实体标注@NodeEntity和@Relationship，使用Neo4jTemplate或继承GraphRepository。第三部分Redis：配置JedisConnectionFactory，使用RedisTemplate（opsForValue、opsForHash等），以及不同值序列化器的选择和影响。

### 经典金句/数据

> “新的数据库种类，通常被称之为NoSQL数据库，提供了使用数据的新方法，这些方法会比传统的关系型数据库更为合适。”(p.44)

- Spring Data支持MongoDB、Neo4j、Redis等多种NoSQL数据库(p.?)

---

## 第13章：缓存数据

### 核心论点

本章回答“如何为Spring应用添加声明式缓存”。作者的核心观点是：通过注解（@Cacheable、@CacheEvict、@CachePut）将缓存逻辑从业务代码中分离，Spring自动管理缓存的填充和失效。

### 关键概念/事件

- **启用缓存**：@EnableCaching注解启用声明式缓存支持。
- **缓存管理器**：配置CacheManager（如ConcurrentMapCacheManager、EhCacheCacheManager、RedisCacheManager）。
- **@Cacheable**：将方法结果存入缓存，后续相同参数调用直接返回缓存值。
- **@CacheEvict**：移除缓存条目，常用于更新或删除操作。
- **@CachePut**：更新缓存条目（方法总会执行）。
- **条件缓存**：condition和unless属性指定缓存条件。
- **XML声明缓存**：使用<cache:annotation-driven>和<cache:advice>在XML中配置缓存规则。

### 逻辑推演/叙事脉络

本章首先介绍如何启用缓存支持：在配置类添加@EnableCaching，配置CacheManager（如ConcurrentMapCacheManager）。随后详细讲解三种核心缓存注解：@Cacheable（填充缓存，可用于查询方法）、@CachePut（更新缓存，方法总会执行）、@CacheEvict（移除缓存，beforeInvocation属性可选），并说明key属性自定义缓存键、condition和unless属性条件控制。然后介绍XML配置方式（<cache:annotation-driven>和<cache:advice>）。最后小结。

### 经典金句/数据

> “Spring 3.1引入了环境profile功能。借助于profile，就能根据应用部署在什么环境之中选择不同的数据源bean。”(p.45)

- @CacheEvict的beforeInvocation属性默认为false（方法执行成功后移除），设为true则方法执行前移除(p.?)

---

## 第14章：保护方法应用

### 核心论点

本章回答“如何通过Spring Security保护方法级别的安全”。作者的核心观点是：通过注解（@Secured、@RolesAllowed、@PreAuthorize、@PostAuthorize）实现方法级安全控制，支持表达式进行细粒度权限校验和输入/输出过滤。

### 关键概念/事件

- **启用方法安全**：@EnableGlobalMethodSecurity(securedEnabled=true, jsr250Enabled=true, prePostEnabled=true)。
- **@Secured**：Spring Security注解，限制角色访问（如@Secured("ROLE_USER")）。
- **@RolesAllowed**：JSR-250标准注解，功能类似@Secured。
- **@PreAuthorize**：方法执行前校验权限，支持Spring表达式（如hasRole、hasPermission）。
- **@PostAuthorize**：方法执行后校验权限，可访问返回值（returnObject）。
- **@PreFilter**：过滤方法输入参数（集合或数组）。
- **@PostFilter**：过滤方法返回值（集合或数组）。

### 逻辑推演/叙事脉络

本章首先介绍如何启用方法级别安全：@EnableGlobalMethodSecurity的三个属性（securedEnabled、jsr250Enabled、prePostEnabled）。随后分别展示@Secured注解（限制角色访问）和@RolesAllowed注解（JSR-250标准）。然后重点讲解基于表达式的安全控制：@PreAuthorize在方法执行前校验（支持hasRole、hasPermission、自定义表达式），@PostAuthorize在方法执行后校验（可访问returnObject）。最后介绍输入/输出过滤：@PreFilter过滤方法参数集合，@PostFilter过滤返回值集合。

### 经典金句/数据

> “通过AOP，Spring Security为Spring应用提供了声明式的安全机制。”(p.43)

- @EnableGlobalMethodSecurity的prePostEnabled=true时启用@PreAuthorize和@PostAuthorize(p.?)

---

## 第15章：使用远程服务

### 核心论点

本章回答“如何通过Spring暴露和使用远程服务”。作者介绍了Spring支持的多远程调用技术：RMI、Hessian、Burlap、Spring HttpInvoker以及基于JAX-WS的Web Service。

### 关键概念/事件

- **Spring远程调用概览**：支持RMI、Hessian、Burlap、HttpInvoker、JAX-WS，通过RmiServiceExporter、HessianServiceExporter等导出服务，通过RmiProxyFactoryBean等访问服务。
- **RMI**：基于Java的远程方法调用，RmiServiceExporter将bean导出为RMI服务，RmiProxyFactoryBean访问RMI服务。
- **Hessian/Burlap**：基于HTTP的轻量级远程调用（Hessian二进制、Burlap XML），HessianServiceExporter导出，HessianProxyFactoryBean访问。
- **Spring HttpInvoker**：基于HTTP但使用Java序列化，HttpInvokerServiceExporter导出，HttpInvokerProxyFactoryBean访问。
- **JAX-WS**：Java API for XML Web Services，使用SimpleJaxWsServiceExporter导出，JaxWsPortProxyFactoryBean访问。

### 逻辑推演/叙事脉络

本章首先概览Spring支持的远程调用技术。然后按技术分别介绍：RMI（导出RmiServiceExporter，访问RmiProxyFactoryBean）；Hessian和Burlap（导出HessianServiceExporter，访问HessianProxyFactoryBean，区别在于消息格式）；Spring HttpInvoker（导出HttpInvokerServiceExporter，访问HttpInvokerProxyFactoryBean，优势是使用Java序列化支持复杂对象图）。最后介绍基于JAX-WS的Web Service：使用SimpleJaxWsServiceExporter自动导出@WebService注解的bean，客户端使用JaxWsPortProxyFactoryBean访问。

### 经典金句/数据

- Spring支持RMI、Hessian、Burlap、HttpInvoker、JAX-WS等远程调用技术(p.?)

---

## 第16章：使用Spring MVC创建REST API

### 核心论点

本章回答“如何使用Spring MVC构建RESTful API”。作者的核心观点是：REST基于HTTP方法（GET/POST/PUT/DELETE）和资源表述，Spring通过@Controller、@RequestMapping（method属性）、@ResponseBody、@RequestBody以及消息转换器（HttpMessageConverter）提供REST支持。

### 关键概念/事件

- **REST基础知识**：资源（URI标识）、表述（JSON/XML/HTML）、HTTP方法语义（GET获取、POST创建、PUT更新、DELETE删除）、无状态。
- **Spring REST支持**：@RequestMapping的method属性区分HTTP方法，@ResponseBody将返回值自动转换为响应体，@RequestBody将请求体绑定到方法参数。
- **内容协商**：produces和consumes属性匹配请求的Accept和Content-Type头。
- **HTTP消息转换器**：MappingJackson2HttpMessageConverter自动转换JSON/Java对象。
- **@RestController**：组合@Controller和@ResponseBody，专为REST服务设计。
- **RestTemplate客户端**：支持GET（getForObject、getForEntity）、POST（postForObject、postForLocation）、PUT（put）、DELETE（delete），以及更高阶的exchange方法。

### 逻辑推演/叙事脉络

本章首先介绍REST的基本原则（资源、表述、HTTP方法语义）。然后说明Spring MVC如何支持REST：通过@RequestMapping的method属性区分GET/POST/PUT/DELETE，通过@ResponseBody将方法返回值直接写入响应体，通过@RequestBody将请求体绑定到参数，通过produces/consumes实现内容协商。接着介绍HTTP消息转换器的作用，尤其是MappingJackson2HttpMessageConverter实现JSON自动转换。之后展示如何提供资源之外的内容：发送错误信息（@ResponseStatus、@ExceptionHandler）、设置响应头部（ResponseEntity）。最后编写REST客户端：使用RestTemplate的各种方法（getForObject、postForObject、put、delete等）消费REST API，以及exchange方法发送自定义请求。

### 经典金句/数据

> “Spring MVC 3.2的控制器可以使用Servlet 3.0的异步请求，允许在一个独立的线程中处理请求。”(p.46)

- @RestController是@Controller和@ResponseBody的组合注解(p.?)

---

## 第17章：Spring消息

### 核心论点

本章回答“如何使用Spring实现异步消息”。作者介绍了两种消息规范：JMS（Java Message Service）和AMQP（Advanced Message Queuing Protocol，以RabbitMQ实现），通过JmsTemplate和RabbitTemplate简化消息发送，通过@JmsListener和@RabbitListener实现消息驱动的POJO。

### 关键概念/事件

- **异步消息简介**：发送消息到消息代理（Message Broker），接收者异步处理，实现解耦、缓冲、弹性。
- **JMS**：Java消息服务标准，ActiveMQ实现，JmsTemplate发送消息，@JmsListener接收消息。
- **Spring JMS配置**：配置ConnectionFactory（如ActiveMQConnectionFactory）、JmsTemplate、@EnableJms启用监听。
- **消息驱动POJO**：@JmsListener注解将方法标记为消息监听器，自动接收消息。
- **AMQP**：高级消息队列协议，RabbitMQ实现，RabbitTemplate发送消息，@RabbitListener接收消息。
- **AMQP核心概念**：交换机(Exchange)（direct、topic、fanout、headers）、队列(Queue)、绑定(Binding)。

### 逻辑推演/叙事脉络

本章首先介绍异步消息的概念和优点（解耦、缓冲、弹性）。然后分两部分介绍JMS和AMQP。JMS部分：配置消息代理（ActiveMQ），使用JmsTemplate发送消息（convertAndSend、receiveAndConvert），@JmsListener创建消息驱动的POJO。AMQP部分：AMQP与JMS的区别（AMQP是线级协议，定义交换机和队列），配置Spring AMQP（RabbitMQ连接工厂、RabbitTemplate），使用RabbitTemplate发送消息（convertAndSend指定exchange和routingKey），@RabbitListener接收消息。

### 经典金句/数据

> “异步消息的一个主要优点就在于它能够实现客户端和服务端之间的松耦合。”(p.?)

- AMQP核心概念包括交换机、队列和绑定(p.?)

---

## 第18章：使用WebSocket和STOMP实现消息功能

### 核心论点

本章回答“如何实现Web端实时通信”。作者介绍了Spring对WebSocket的支持，包括低层级API和基于STOMP的高层级消息模型（类似@Controller的消息处理），支持客户端-服务端双向消息。

### 关键概念/事件

- **WebSocket**：在单个TCP连接上提供全双工通信，Spring通过@EnableWebSocket和WebSocketHandler支持低层级API。
- **SockJS**：应对不支持WebSocket的场景（如旧浏览器），提供WebSocket的模拟实现（HTTP长轮询等）。
- **STOMP**：简单文本导向消息协议，在WebSocket之上提供更高级的消息模型（类似AMQP的destination和订阅）。
- **启用STOMP**：@EnableWebSocketMessageBroker，配置MessageBrokerRegistry（启用简单消息代理或外部代理如RabbitMQ）。
- **@MessageMapping**：类似@RequestMapping，处理STOMP消息（从客户端发送到服务端）。
- **@SendTo**：将方法返回值发送到指定的destination（广播给所有订阅的客户端）。
- **@SubscribeMapping**：处理订阅请求。
- **目标用户消息**：@SendToUser将消息发送给特定用户，通过SimpMessagingTemplate.convertAndSendToUser()实现。

### 逻辑推演/叙事脉络

本章首先介绍Spring低层级WebSocket API：@EnableWebSocket，实现WebSocketHandler接口，或继承TextWebSocketHandler。然后介绍SockJS应对不支持WebSocket的场景（模拟WebSocket）。接着重点讲解基于STOMP的消息模型：@EnableWebSocketMessageBroker启用STOMP消息代理，配置MessageBrokerRegistry（setApplicationDestinationPrefixes设置应用前缀，enableSimpleBroker启用内存代理）。编写STOMP控制器：@MessageMapping处理客户端消息，返回值可通过@SendTo广播；@SubscribeMapping处理订阅请求。然后介绍为目标用户发送消息：@SendToUser将响应发送给特定用户，SimpMessagingTemplate.convertAndSendToUser()主动发送。最后处理消息异常（@MessageExceptionHandler）。

### 经典金句/数据

- Spring 4.0引入了对WebSocket编程的支持，包括JSR-356和基于SockJS/STOMP的高级消息模型(p.47)

---

## 第19章：使用Spring发送Email

### 核心论点

本章回答“如何在Spring应用中发送电子邮件”。作者介绍了Spring的邮件抽象层：MailSender接口和JavaMailSenderImpl实现，支持简单文本邮件、带附件的邮件、富文本（HTML）邮件，以及使用Velocity或Thymeleaf模板生成邮件内容。

### 关键概念/事件

- **配置邮件发送器**：JavaMailSenderImpl，设置host（如smtp.gmail.com）、port、username、password、protocol（smtp）。
- **SimpleMailMessage**：简单文本邮件，设置from、to、subject、text。
- **MimeMessage**：复杂邮件（附件、HTML），通过JavaMailSender.createMimeMessage()和MimeMessageHelper简化操作。
- **附件**：MimeMessageHelper.addAttachment()添加文件。
- **富文本**：MimeMessageHelper.setText(text, true)第二个参数true表示HTML。
- **模板生成邮件**：VelocityEngineUtils或Thymeleaf模板引擎生成HTML内容。

### 逻辑推演/叙事脉络

本章首先介绍如何配置邮件发送器：JavaMailSenderImpl并设置SMTP服务器参数。然后演示使用SimpleMailMessage发送简单文本邮件。接着介绍复杂邮件的构建：通过MimeMessage和MimeMessageHelper支持附件（addAttachment）和HTML内容（setText(html, true)）。最后介绍使用模板生成邮件内容：Velocity（VelocityEngineUtils.mergeTemplateIntoString）和Thymeleaf（SpringTemplateEngine.process）将模板和数据合并生成HTML，再发送邮件。

### 经典金句/数据

- 使用模板生成邮件内容可避免在代码中硬编码HTML字符串(p.?)

---

## 第20章：使用JMX管理Spring Bean

### 核心论点

本章回答“如何通过JMX监控和管理Spring Bean”。作者的核心观点是：通过@ManagedResource、@ManagedOperation、@ManagedAttribute等注解将Spring bean暴露为MBean（管理Bean），使用MBeanExporter自动注册到JMX服务器，支持本地和远程管理。

### 关键概念/事件

- **JMX**：Java管理扩展，监控和管理应用（如查看属性、调用方法）。
- **MBeanExporter**：将Spring bean导出为MBean，自动注册到MBeanServer。
- **注解驱动MBean**：@ManagedResource(description)标记类，@ManagedAttribute暴露属性，@ManagedOperation暴露方法。
- **MBean命名**：通过assembler和naming策略控制MBean的ObjectName。
- **远程MBean**：通过配置MBeanServerConnectionFactory和ConnectorServerFactoryBean暴露远程访问（RMI端口）。
- **代理MBean**：MBeanProxyFactoryBean将远程MBean代理为本地接口。
- **通知**：MBean发送Notification，Spring通过NotificationListener接收和处理。

### 逻辑推演/叙事脉络

本章首先介绍如何将Spring bean导出为MBean：配置MBeanExporter，指定需要暴露的bean。然后介绍三种暴露方式：通过名称暴露（传统方式）、通过接口定义操作和属性（StandardMBean）、通过注解驱动（@ManagedResource、@ManagedAttribute、@ManagedOperation）。接着处理MBean冲突（避免ObjectName重复）。之后介绍远程MBean：配置ConnectorServerFactoryBean暴露RMI端口，客户端通过MBeanServerConnectionFactoryBean连接，或使用MBeanProxyFactoryBean将远程MBean代理为本地接口。最后介绍处理通知：实现NotificationListener接口监听MBean发送的Notification。

### 经典金句/数据

- JMX（Java Management Extensions）用于监控和修改应用程序的运行时配置(p.?)

---

## 第21章：借助Spring Boot简化Spring开发

### 核心论点

本章回答“Spring Boot如何进一步简化Spring开发”。作者的核心观点是：Spring Boot通过自动配置（auto-configuration）和starter依赖，大幅减少甚至消除Spring配置，提供CLI（命令行接口）快速开发，以及Actuator监控应用运行状况。

### 关键概念/事件

- **Spring Boot简介**：自动配置、starter依赖简化构建、CLI快速原型、Actuator监控。
- **Starter依赖**：spring-boot-starter-web、spring-boot-starter-data-jpa、spring-boot-starter-security等，传递性引入必要依赖。
- **自动配置**：根据类路径中的jar包和配置属性自动创建bean。
- **Spring Boot CLI**：使用Groovy脚本开发，无需编译和打包，直接运行。
- **Actuator**：提供REST端点（/health、/info、/metrics、/trace、/beans等）监控应用内部状况。
- **构建可执行JAR**：Spring Boot Maven/Gradle插件将应用打包为可执行JAR（内嵌Tomcat）。

### 逻辑推演/叙事脉络

本章首先介绍Spring Boot的四项核心特性：starter依赖、自动配置、CLI、Actuator。然后演示如何使用Spring Boot构建Web应用：添加starter-web依赖，编写@RestController（处理请求），将JSP/Thymeleaf作为视图，添加静态资源，使用starter-data-jpa持久化，运行SpringApplication.run()。接着介绍Spring Boot CLI：使用Groovy编写控制器和Repository（实现接口自动提供CRUD方法），通过spring run命令运行。最后介绍Actuator：添加starter-actuator依赖，自动暴露REST端点（/health、/beans、/metrics、/trace等）监控应用。

### 经典金句/Data

> “Spring Boot是一个崭新的令人兴奋的项目，它以Spring的视角，致力于简化Spring本身。Spring Boot大量依赖于自动配置技术，它能够消除大部分（在很多场景中，甚至是全部）Spring配置。”(p.45)

- Actuator提供/health、/info、/metrics、/trace、/beans等监控端点(p.?)

---

## 结语

本书第4版全面覆盖了Spring框架的核心特性（DI、AOP）、Web开发（Spring MVC、Tiles、Thymeleaf、WebSocket）、后端数据持久化（JDBC、JPA、NoSQL、缓存）、安全性（Spring Security）、服务集成（远程调用、REST、消息、Email、JMX）以及革命性的Spring Boot。通过Spittr案例贯穿始终，展示了如何从零开始构建完整的企业级Spring应用。