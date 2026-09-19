# 《21天学通JAVA-WEB开发》章节总结

## 目录说明
- 本总结依据上传 PDF 文件的实际章节目录与正文内容整理。
- 书籍共包含21章，涵盖从环境搭建、JSP基础、Servlet、Filter/Listener、设计模式（DAO/MVC）到主流框架（Struts 2, Hibernate, Spring）及SSH整合开发的完整学习路径。

## 第1章：搭建开发环境
### 核心论点
本章旨在解决Java Web开发的入门门槛问题，即如何正确配置运行环境。核心观点是：熟练掌握JDK、Tomcat、Eclipse及MyEclipse的安装与配置是进行后续Web开发的基础前提。

### 关键概念/事件
- **JDK与环境变量**：安装Java开发工具包并配置`PATH`和`CLASSPATH以支持编译运行。
- **Tomcat服务器**：安装Apache Tomcat作为Web容器，并配置虚拟目录以部署应用。
- **MyEclipse插件**：在Eclipse基础上安装MyEclipse插件，提供可视化的Web项目创建、服务器整合及部署功能。

### 逻辑推演/叙事脉络
本章按照“基础环境->服务器->开发工具->整合使用”的顺序展开。首先介绍JDK的下载、安装及环境变量配置，并通过HelloWorld验证；接着讲解Tomcat的安装及虚拟目录配置，通过JSP示例验证服务器运行；随后介绍Eclipse及MyEclipse插件的安装；最后演示如何在MyEclipse中创建Web项目、整合Tomcat服务器并部署运行第一个JSP页面。

### 经典金句/数据
> “学好本章是学习以后知识的基础，读者一定要熟练地掌握本章的知识。” (p.1-2)

## 第2章：JSP的基础语法
### 核心论点
本章主要回答“什么是JSP及其基本构成元素是什么”的问题。核心观点是：JSP通过在HTML中嵌入Java脚本代码来构建动态网页，其执行机制分为转译（JSP转Servlet）和请求（Servlet执行）两个阶段。

### 关键概念/事件
- **JSP运行机制**：首次访问时JSP被转译为Servlet源码并编译为class文件，后续访问直接执行class文件，修改后重新转译。
- **JSP声明语句 (`<%! %>`)**：用于声明全局变量和方法，多个用户共享。
- **JSP Scriptlets (`<% %>`)**：包含符合Java语法的代码块，用于处理逻辑。
- **JSP表达式 (`<%= %>`)**：用于将Java表达式的结果转换为字符串输出到页面。

### 逻辑推演/叙事脉络
本章首先简介JSP的特点及与ASP的区别，重点阐述其“先转译后执行”的运行机制。随后详细分解JSP的三种脚本元素（声明、Scriptlets、表达式）的语法与用途，并介绍HTML注释与JSP注释的区别。最后通过综合练习巩固变量声明、运算及输出的基本操作。

### 经典金句/数据
> “如果该JSP页面为第一次执行，那么会经过这两个阶段，而如果不是第一次执行，那么将只会执行请求阶段。” (p.2-4)

## 第3章：JSP指令元素
### 核心论点
本章解决“如何配置JSP页面的全局属性及引入外部资源”的问题。核心观点是：通过page、include和taglib指令，可以控制页面行为、复用代码模块以及使用标签库。

### 关键概念/事件
- **page指令**：设置页面全局属性，如`import`导包、`contentType`设置编码、`errorPage`处理异常等。
- **include指令 (`<%@ include %>`)**：静态包含文件，在编译前将被包含文件内容插入当前页面，适用于静态文本或代码片段。
- **taglib指令**：引入自定义标签库，指定URI和前缀，以便在JSP中使用标签。

### 逻辑推演/叙事脉络
本章依次详细介绍page指令的各个属性（language, import, session, errorPage等），解释其作用及默认值。接着讲解include指令的用法，区分包含文本、HTML及JSP文件的场景。最后简要介绍taglib指令用于引入标签库。通过综合练习强化对指令属性的配置及文件包含的理解。

### 经典金句/数据
> “使用<%@ include%>指令元素是将所有的被包含文件包含进来之后，然后再进行编译等处理，可以简单的称其为先包含再处理。” (p.4-7, 注：此处原文虽在第4章对比中提到，但概念源于本章指令介绍)

## 第4章：JSP动作元素
### 核心论点
本章主要解决“如何在运行时动态包含文件、跳转页面及传递参数”的问题。核心观点是：JSP动作元素（如`jsp:include`, `jsp:forward`）在请求处理阶段执行，提供了比指令更灵活的动态控制能力。

### 关键概念/事件
- **`<jsp:include>`**：动态包含文件，先执行被包含文件，再将结果插入。与静态include的区别在于“先处理后包含”。
- **`<jsp:forward>`**：服务器端跳转，用户地址栏不变，跳转后原页面剩余代码不再执行。
- **`<jsp:param>`**：配合include或forward使用，用于传递键值对参数。
- **`<jsp:plugin>`**：用于在页面中嵌入Java Applet或Bean，自动适配浏览器标签。

### 逻辑推演/叙事脉络
本章首先对JSP动作元素进行分类。重点讲解`jsp:include`与`jsp:forward`的语法及应用场景，特别对比了`jsp:include`与`<%@ include %>`在执行时机上的本质区别（动态vs静态）。接着介绍`jsp:param`如何配合前两者传递参数。最后简述`jsp:plugin`及相关标签用于嵌入客户端Java组件。

### 经典金句/数据
> “<jsp:include>动作元素动态的包含文件...可以简单的称其为先处理再包含。” (p.4-7)

## 第5章：JSP内置对象
### 核心论点
本章解决“如何在JSP页面中隐式获取请求、响应及会话状态信息”的问题。核心观点是：JSP提供了9个内置对象，分为Servlet相关、I/O相关、Context相关及Error相关四类，极大地简化了Web开发中的数据交互与状态管理。

### 关键概念/事件
- **四大作用域**：page、request、session、application，决定了属性保存的生命周期和可见范围。
- **request对象**：获取客户端请求参数、头信息及Cookie。
- **response对象**：设置响应头、实现页面重定向或刷新。
- **session对象**：跟踪用户会话状态，保存用户特定信息。
- **application对象**：代表整个Web应用上下文，所有用户共享。

### 逻辑推演/叙事脉络
本章首先对9个内置对象进行分类介绍。重点阐述四种属性保存范围（page, request, session, application）的区别。随后逐一详解request、response、out、session、application等常用对象的功能与方法。最后通过一个用户登录案例，综合演示如何利用这些对象实现表单提交、逻辑判断及页面跳转。

### 经典金句/数据
> “JSP中提供了四种属性保存范围，分别为page、request、session以及application。” (p.5-3)

## 第6章：数据库操作
### 核心论点
本章解决“如何在Java Web应用中连接和操作MySQL数据库”的问题。核心观点是：通过JDBC驱动程序，利用Connection、Statement/PreparedStatement及ResultSet对象，可以实现标准的CRUD数据库操作。

### 关键概念/事件
- **JDBC驱动加载**：使用`Class.forName()`加载MySQL驱动。
- **Connection对象**：通过`DriverManager.getConnection()`建立数据库连接。
- **Statement与PreparedStatement**：前者用于执行静态SQL，后者支持预编译和参数化查询，更安全高效。
- **ResultSet**：封装查询结果集，通过游标遍历获取数据。

### 逻辑推演/叙事脉络
本章首先介绍MySQL及JDBC驱动的下载安装。接着详细讲解JDBC连接数据库的步骤：加载驱动、建立连接。随后重点介绍Statement和PreparedStatement两种语句对象的使用，包括执行更新（add/update/delete）和查询（select）操作。最后强调资源关闭顺序（ResultSet->Statement->Connection），并通过用户注册案例实战演练。

### 经典金句/数据
> “数据库在每次使用之后都必须进行关闭，这样可以释放大量资源。关闭数据库操作的顺序与打开数据库操作的顺序相反。” (p.6-9)

## 第7章：JSP和JavaBean
### 核心论点
本章解决“如何实现业务逻辑与显示分离以提高代码复用性”的问题。核心观点是：JavaBean作为可复用的软件组件，封装了数据和业务逻辑，通过JSP动作标签（useBean, setProperty, getProperty）可在页面中便捷调用，实现MVC雏形。

### 关键概念/事件
- **JavaBean规范**：public类、无参构造器、私有属性及public getter/setter方法。
- **`<jsp:useBean>`**：实例化或查找JavaBean对象，可指定scope（page/request/session/application）。
- **`<jsp:setProperty>`**：设置Bean属性，支持自动匹配表单参数（property="*"）或手动指定。
- **`<jsp:getProperty>`**：获取并输出Bean属性值。

### 逻辑推演/叙事脉络
本章首先定义JavaBean及其规范，强调其在分离逻辑与显示方面的优势。接着详细介绍如何在JSP中使用`jsp:useBean`实例化Bean。随后深入讲解`jsp:setProperty`的四种用法，特别是如何自动绑定表单数据。最后介绍`jsp:getProperty`获取数据，以及Bean在不同作用域下的生命周期管理与移除方法。

### 经典金句/数据
> “将业务逻辑进行封装，使得业务逻辑代码和显示代码相分离，不会互相干扰。” (p.7-5)

## 第8章：EL表达式
### 核心论点
本章解决“如何简化JSP页面中的数据访问与表达式编写”的问题。核心观点是：EL（Expression Language）提供了一种简洁的语法`${}`来访问Bean属性、集合数据及内置对象，替代复杂的JSP Scriptlets，提高页面可读性。

### 关键概念/事件
- **EL运算符**：支持算术、关系、逻辑、条件及empty验证运算符。
- **EL内置对象**：包括4个作用域对象（pageScope等）、param/paramValues（请求参数）、cookie、header、initParam及pageContext。
- **存取器**：使用`.`和`[]`运算符访问Bean属性、Map键值或数组/列表索引。

### 逻辑推演/叙事脉络
本章首先介绍EL的基本语法及各类运算符（算术、关系、逻辑等）。接着重点详解EL的11个内置对象，特别是如何替代request.getParameter()获取参数，以及如何访问不同作用域的属性。最后介绍如何使用`.`和`[]`存取器灵活访问JavaBean、Map及集合中的数据，并通过计算器案例展示EL的综合应用。

### 经典金句/数据
> “如果不指定范围，如使用${name}...它的默认值会从page范围内找，如果找不到，再依次到request、session、application范围中找。” (p.8-12)

## 第9章：JSTL标签库
### 核心论点
本章解决“如何在JSP页面中实现流程控制、迭代及国际化等功能而不使用Java脚本”的问题。核心观点是：JSTL（JSP Standard Tag Library）提供了一套标准标签库（核心、SQL、格式化、XML、函数），使JSP页面更加规范、易维护。

### 关键概念/事件
- **核心标签库 (`c:`)**：包括`<c:set>`(设值), `<c:out>`(输出), `<c:if>`/`<c:choose>`(判断), `<c:forEach>`(循环), `<c:import>`(导入)等。
- **SQL标签库 (`sql:`)**：直接在JSP中执行数据库操作（不推荐在生产环境使用，但作为学习内容提及）。
- **格式化标签库 (`fmt:`)**：处理数字、日期格式化及国际化（i18n）消息显示。
- **XML标签库 (`x:`)**：解析和操作XML数据。

### 逻辑推演/叙事脉络
本章按JSTL的五类标签库逐一介绍。重点讲解核心标签库，详细说明变量管理、条件判断和循环遍历的标签用法。随后简述SQL标签库进行数据库操作的方法，格式化标签库处理日期数字及国际化的功能，以及XML标签库解析XML的能力。最后通过嵌套循环生成表格的案例展示标签库的实际应用。

### 经典金句/数据
> “使用<c:out>输出内容比使用Scriptlets代码要更加简单，方便页面维护。” (p.9-4)

## 第10章：Servlet开发基础
### 核心论点
本章解决“如何理解和使用Servlet作为Java Web的核心控制器”的问题。核心观点是：Servlet是运行在服务器端的Java程序，负责处理请求、执行业务逻辑并生成响应，其生命周期由容器管理，是JSP背后的技术基础。

### 关键概念/事件
- **Servlet生命周期**：加载与初始化(init)、服务(service/doGet/doPost)、销毁(destroy)。
- **HttpServlet类**：继承此类并重写doGet/doPost方法来处理HTTP请求。
- **HttpServletRequest/Response**：分别封装请求信息和响应操作，与JSP内置对象对应。
- **ServletContext/Session**：分别代表应用上下文和会话对象，需在Servlet中通过特定方法获取。

### 逻辑推演/叙事脉络
本章首先介绍Servlet的概念、优点及第一个Servlet程序的编写。接着深入剖析Servlet的生命周期三个步骤。随后详细讲解HttpServlet中doGet、doPost及service方法的区别与应用场景。最后介绍Servlet中常用的接口（Request, Response, Session, Context）及其与JSP内置对象的对应关系，并通过读取文件内容的案例进行实践。

### 经典金句/数据
> “Servlet虽然具有如此多的优点，但是其并没有大规模的被采用，最大的原因在于其编写起来非常困难...不过Servlet的发展为后面JSP的诞生和发展打下了牢固的基础。” (p.10-2)

## 第11章：Filter开发
### 核心论点
本章解决“如何在请求到达Servlet/JSP之前或响应返回客户端之前进行统一预处理”的问题。核心观点是：Filter（过滤器）通过拦截请求/响应链，实现了字符编码设置、权限验证、敏感词过滤等横切关注点的模块化配置。

### 关键概念/事件
- **Filter接口**：需实现init、doFilter、destroy方法。
- **FilterChain**：在doFilter中调用`chain.doFilter()`将请求传递给下一个过滤器或目标资源。
- **常见应用场景**：字符编码过滤器（统一UTF-8）、登录验证过滤器（检查Session）、非法文字过滤器。

### 逻辑推演/叙事脉络
本章首先定义Filter的作用及配置方式（web.xml）。接着详解Filter的生命周期及doFilter方法中request/response/chain参数的作用。随后通过三个典型案例（非法文字过滤、字符编码统一、登录权限验证）展示Filter的具体实现逻辑。最后通过IP过滤的综合练习巩固Filter的配置与编码技巧。

### 经典金句/数据
> “借助于过滤器可以实现如下功能...过滤非法文字和信息...设置统一字符编码...对用户进行登录验证。” (p.11-2)

## 第12章：Listener开发
### 核心论点
本章解决“如何监听Web应用中关键对象（Context, Session, Request）的生命周期事件及属性变化”的问题。核心观点是：Listener（监听器）允许开发者在特定事件发生时自动执行代码，常用于统计在线人数、记录日志或资源清理。

### 关键概念/事件
- **ServletContextListener**：监听应用启动和销毁，常用于初始化全局资源。
- **HttpSessionListener**：监听Session的创建和销毁，常用于统计在线用户数。
- **AttributeListener**：监听各作用域属性的添加、替换和移除事件。

### 逻辑推演/叙事脉络
本章首先介绍Listener的分类（Context, Session, Request相关）。重点详解ServletContextListener和HttpSessionListener的接口方法及应用场景。接着介绍AttributeListener用于监控属性变化。最后通过一个“在线用户列表显示”的综合案例，演示如何利用SessionListener统计并展示当前活跃用户。

### 经典金句/数据
> “通过Listener可以监听容器中某一执行动作，并根据其要求做出相应的响应。” (p.12-2)

## 第13章：DAO设计模式
### 核心论点
本章解决“如何将数据库操作代码从JSP/Servlet中剥离以实现分层架构”的问题。核心观点是：DAO（Data Access Object）模式通过接口、实现类、VO、工厂类和数据库连接类的分工，实现了数据访问逻辑的封装与解耦，提高了代码的可维护性和可移植性。

### 关键概念/事件
- **VO (Value Object)**：与数据库表字段对应的实体类，仅包含属性和getter/setter。
- **DAO接口与实现**：接口定义操作规范，实现类包含具体JDBC代码。
- **DAO工厂**：通过静态方法返回DAO实例，屏蔽具体实现类，便于切换数据库实现。
- **DatabaseConnection**：封装数据库连接的获取与关闭，简化重复代码。

### 逻辑推演/叙事脉络
本章首先指出直接在JSP中写JDBC代码的弊端，引出DAO模式。接着详细拆解DAO模式的五个组成部分：数据库连接类、VO类、DAO接口、DAO实现类、DAO工厂类，并解释各自职责。最后通过查询所有记录的示例，展示各组件如何协同工作完成数据库操作。

### 经典金句/数据
> “使用DAO设计模式可以很好的解决如上的问题...JSP只需要关注数据的显示，而不需要去关注数据是从哪里来的。” (p.13-2)

## 第14章：MVC设计模式
### 核心论点
本章解决“如何构建结构清晰、易于维护的大型Web应用架构”的问题。核心观点是：MVC（Model-View-Controller）模式通过将应用分为模型（业务/数据）、视图（显示）和控制器（流程控制），实现了关注点分离，其中Model 2（JSP+Servlet+JavaBean）是Java Web的标准实现。

### 关键概念/事件
- **Model 1 vs Model 2**：Model 1仅用JSP，耦合度高；Model 2引入Servlet作为控制器，JSP仅作视图。
- **MVC角色映射**：JSP=View，Servlet=Controller，JavaBean/DAO=Model。
- **控制器职责**：接收请求、调用模型、选择视图、转发请求。

### 逻辑推演/叙事脉络
本章首先介绍MVC的基本概念及三个组件的职责。接着对比Model 1和Model 2架构，分析Model 1的缺陷（耦合、难维护）及Model 2的优势。最后通过改进用户登录案例，展示如何用Servlet充当控制器，JavaBean充当模型，JSP充当视图，实现标准的MVC流程。

### 经典金句/数据
> “Model 2中使用Servlet来充当控制器，而JSP只是充当显示...可以把Servlet看成是一个大管家，它负责所有的业务逻辑并通过JavaBean来操作数据库以及决定显示页面。” (p.14-6)

## 第15章：Struts 2基础
### 核心论点
本章解决“如何使用Struts 2框架简化MVC开发中的控制器配置与输入校验”的问题。核心观点是：Struts 2基于WebWork内核，通过拦截器机制和POJO Action，实现了请求处理、类型转换、校验及结果视图配置的标准化与自动化。

### 关键概念/事件
- **Action类**：普通的POJO类，包含属性和execute方法，无需继承特定基类（但常继承ActionSupport）。
- **struts.xml配置**：定义Package、Action映射及Result结果视图。
- **输入校验**：继承ActionSupport重写validate()方法，或使用addFieldError添加错误信息。
- **国际化 (i18n)**：通过资源文件实现页面文本和错误提示的多语言支持。

### 逻辑推演/叙事脉络
本章首先介绍Struts 2的起源及安装配置。接着通过登录案例演示Action类的编写、struts.xml的配置及执行流程。随后介绍如何改进Action（实现Action接口），并利用ActionSupport进行输入校验。最后讲解如何通过资源文件实现页面内容和校验信息的国际化。

### 经典金句/数据
> “Struts 2是以WebWork为核心，采用拦截器的机制来处理用户的请求...业务逻辑控制器能够与Servlet API完全的脱离开。” (p.15-2)

## 第16章：Struts 2高级应用
### 核心论点
本章解决“如何处理复杂数据类型转换及实施严格的服务器端校验”的问题。核心观点是：Struts 2内置了强大的类型转换器和校验框架，支持自动类型转换、声明式校验规则配置及客户端校验生成，提升了开发效率与应用安全性。

### 关键概念/事件
- **内建类型转换器**：自动处理基本类型、日期、数组、集合等与字符串间的转换。
- **服务器端校验**：强调其必要性，防止绕过客户端脚本的攻击。
- **校验框架**：通过XML配置文件定义校验规则（必填、范围、正则等），支持国际化错误消息。
- **短路校验**：配置short-circuit属性，一旦某规则失败即停止后续校验。

### 逻辑推演/叙事脉络
本章首先介绍Struts 2内建的类型转换器及其对数组、集合的支持。接着强调服务器端校验的重要性，并演示如何通过代码（addFieldError）和校验框架（XML配置）实现校验。最后介绍如何在校验框架中结合国际化资源文件，以及如何启用客户端校验功能和配置校验短路。

### 经典金句/数据
> “服务器端校验是整个Web应用中最重要的一道防线...客户端校验就像是一把锁，能够防君子但是不能防小人。” (p.16-10)

## 第17章：持久化框架Hibernate
### 核心论点
本章解决“如何通过ORM技术消除JDBC繁琐代码并实现面向对象数据库操作”的问题。核心观点是：Hibernate作为ORM框架，通过映射文件将Java对象与数据库表关联，开发者只需操作对象即可自动同步数据库，简化了持久层开发。

### 关键概念/事件
- **ORM (Object/Relation Mapping)**：对象与关系数据库的映射机制。
- **核心类**：Configuration（配置加载）、SessionFactory（会话工厂，线程安全）、Session（持久化管理核心）。
- **对象状态**：临时态（Transient）、持久态（Persistent）、脱管态（Detached）。
- **映射文件 (.hbm.xml)**：定义类属性与表字段的对应关系。

### 逻辑推演/叙事脉络
本章首先介绍ORM概念及Hibernate优势。接着详解Hibernate的核心配置类及SessionFactory/Session的作用。重点讲解Hibernate中对象的三种状态及其转换。最后通过创建持久化类、映射文件及配置文件，演示如何使用Hibernate进行数据的增删改查操作。

### 经典金句/数据
> “通过创建一个持久化类来映射一个数据库表...当我们使用面向对象的方式来操作持久化对象时，ORM框架能自动将这些操作转换成SQL语句。” (p.17-3)

## 第18章：Struts 2整合Hibernate开发
### 核心论点
本章解决“如何将Struts 2表现层与Hibernate持久层结合构建分层应用”的问题。核心观点是：通过引入业务逻辑层（Service）和DAO层，Struts 2负责请求分发，Hibernate负责数据持久化，二者通过中间层解耦，形成清晰的四层架构。

### 关键概念/事件
- **四层架构**：表现层（Struts 2）、业务逻辑层（Service）、持久层（Hibernate/DAO）、数据库层。
- **DAO工厂与Service工厂**：用于解耦各层依赖，便于管理和替换实现。
- **整合流程**：Struts Action调用Service，Service调用DAO，DAO使用Hibernate Session操作数据库。

### 逻辑推演/叙事脉络
本章首先明确分层思想及整合策略。接着回顾Hibernate持久层设计（DAO接口/实现/工厂）。然后设计业务逻辑组件（Service接口/实现/工厂）。最后通过产品管理的案例（查询、添加、删除、更新），展示Struts 2 Action如何层层调用至Hibernate完成完整业务流程。

### 经典金句/数据
> “一个好的应用一般都采用多层设计...最上一层为表现层...第二层为业务逻辑层...第三层为持久化层...第四层为数据库层。” (p.18-2)

## 第19章：Spring开发
### 核心论点
本章解决“如何通过IoC和AOP技术降低组件耦合度并增强系统功能”的问题。核心观点是：Spring作为轻量级容器，通过依赖注入（IoC）管理对象生命周期与依赖关系，通过面向切面编程（AOP）集中处理事务、日志等横切关注点。

### 关键概念/事件
- **IoC (控制反转)/DI (依赖注入)**：对象被动接收依赖，而非主动查找，降低耦合。
- **装配方式**：Setter注入、构造器注入；自动装配（byName, byType, constructor, autodetect）。
- **AOP (面向切面编程)**：将分散的服务（如事务、安全）模块化，通过通知（Advice）和切入点（Pointcut）织入业务逻辑。

### 逻辑推演/叙事脉络
本章首先介绍Spring的核心优势（轻量、IoC、AOP）。接着通过示例对比传统硬编码与Spring IoC容器的区别，详解Setter和构造器注入及自动装配模式。随后引入AOP概念，解释其思想及在Spring中的实现（前置、后置、异常通知等），展示如何在不修改业务代码的情况下增强功能。

### 经典金句/数据
> “使用反向控制，对象是被动接收依赖类而不是主动去找，从而降低耦合度。” (p.19-3)

## 第20章：Struts 2整合Spring开发
### 核心论点
本章解决“如何利用Spring容器管理Struts 2 Action及业务组件以实现全面解耦”的问题。核心观点是：通过Spring插件，Struts 2 Action的创建及依赖注入交由Spring管理，使得Action不再依赖具体业务实现类，极大提升了系统的可测试性与灵活性。

### 关键概念/事件
- **Spring插件整合**：在web.xml配置ContextLoaderListener，加载applicationContext.xml。
- **依赖注入Action**：在Struts Action中定义Service属性，由Spring自动注入实现类。
- **管理SessionFactory**：Spring接管Hibernate SessionFactory的创建与管理，简化事务配置。

### 逻辑推演/叙事脉络
本章首先介绍整合所需的JAR包及web.xml配置。接着阐述整合策略：Spring管理Service和DAO，Struts 2 Action通过Spring注入Service。通过登录案例和产品管理案例，演示如何修改Action以支持注入，以及在Spring配置文件中定义Bean依赖关系，最终实现Struts 2与Spring的无缝集成。

### 经典金句/数据
> “通过添加一个Listener，使得Web应用启动时会自动查找WEB-INF目录下的applicationContext.xml配置文件，并根据该配置文件来创建Spring容器。” (p.20-2)

## 第21章：SSH整合开发用户管理系统
### 核心论点
本章解决“如何综合运用Struts 2、Spring、Hibernate构建企业级用户管理系统”的问题。核心观点是：SSH整合集成了Struts 2的MVC控制、Spring的IoC/AOP管理及Hibernate的ORM持久化，形成了成熟、分层清晰、低耦合的企业级开发解决方案。

### 关键概念/事件
- **SSH架构分工**：Struts 2（表现/控制）、Spring（业务组装/事务）、Hibernate（数据持久）。
- **模块化开发**：将系统拆分为查看、添加、删除、更新等模块，分别实现各层代码。
- **完整流程**：JSP表单 -> Struts Action -> Spring Service -> Hibernate DAO -> Database。

### 逻辑推演/叙事脉络
本章首先介绍用户管理系统的功能及SSH分层结构。接着按模块逐步实现：首先设计Hibernate持久层（PO类/映射），然后设计DAO层（接口/实现/Spring配置），再设计业务逻辑层。最后整合Struts 2，编写Action及JSP页面，配置Struts与Spring的交互，完成查看所有用户、详情、添加、删除及更新用户的全功能系统开发。

### 经典金句/数据
> “通过Struts 2框架负责与用户进行交互，并通过业务逻辑组件完成业务逻辑判断。通过Struts 2整合Spring，从而为Struts 2中的Action注入业务逻辑组件。同时整合Hibernate框架进行持久化访问操作。” (p.21-13)