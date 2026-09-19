# 《Java EE 6 开发手册·高级篇（第4版）》章节总结

## 目录说明
- 本书为《The Java EE 6 Tutorial: Advanced Topics, 4th Edition》的中文译本
- 全书共分为9大部分，包含27个章节及附录内容
- 总结依据书中实际目录结构与正文内容对应整理
- 说明：PDF第23页之后部分章节内容识别不完整，以下总结基于可见内容整理

---

## 第1章：概述

### 核心论点
本章主要回答：Java EE 6平台是什么？它提供了哪些核心能力和API？企业级应用程序的开发模型是怎样的？作者的核心观点是：Java EE 6平台通过简化的编程模型、注解和依赖注入，大大降低了企业级应用程序的开发复杂度，同时提供了强大的API集合和容器服务。

### 关键概念/事件
- **Java EE组件模型**：应用程序客户端、Web组件（Servlet/JSF/JSP）、业务组件（EJB）、企业信息系统层构成的四层架构
- **容器服务**：容器为组件提供安全、事务、JNDI查询、远程连接等底层服务，开发者无需自行实现
- **依赖注入**：容器自动引用其他所需组件或资源，在代码中隐藏资源创建及查找过程
- **注解（Annotation）**：直接在Java源文件中使用注解，取代XML部署描述符进行组件配置
- **Profile机制**：Java EE 6引入轻量级Web Profile和完整Full Profile两种配置

### 逻辑推演/叙事脉络
本章从Java EE平台的背景和目标入手，首先介绍平台的亮点特性（Profile、新API、注解、依赖注入等），然后详细阐释应用程序模型和分布式多层架构的各个层次（客户端层、Web层、业务层、EIS层）。随后逐层介绍各类组件的职责和交互方式，说明容器如何提供服务支持。最后系统性地列举了Java EE 6平台所包含的全部API（EJB、Servlet、JSF、JPA、JAX-RS、CDI等），并以GlassFish Server工具作为收尾。

### 经典金句/数据
> “Java EE平台的目标就是为开发人员提供一个强大的API集合，同时减少开发时间，降低应用程序复杂度并提高性能。”(p.27)

> “通过使用注解，你只需将规范信息写在代码中程序元素的旁边，就可以改变它们的行为。”(p.27)

---

## 第2章：使用本教程的示例程序

### 核心论点
本章主要回答：如何安装、构建和运行本书中的示例程序？所需软件有哪些？作者强调读者需要按照正确的步骤配置GlassFish Server、NetBeans IDE和Ant工具，才能顺利运行所有示例代码。

### 关键概念/事件
- **GlassFish Server 3.1.2**：本书示例程序的运行环境，Java EE 6平台规范的参考实现
- **Update Tool**：用于获取Java EE 6教程组件和Apache Ant的工具
- **NetBeans IDE**：支持Java EE平台的免费开源IDE，需下载含Java EE bundle的版本
- **Apache Ant 1.7.1+**：用于构建、打包和部署示例程序的构建工具
- **服务器日志调试**：位于domain-dir/logs/server.log，可通过System.out.println或Java Logging API记录调试信息

### 逻辑推演/叙事脉络
本章先列出运行示例所需的所有软件（JDK 6/7、Java EE 6 SDK、教程组件、NetBeans IDE、Ant），然后给出详细的安装提示和配置步骤。接着说明如何启动和停止GlassFish Server、启动管理控制台、启动/停止Java DB服务。之后介绍示例程序的目录结构和构建方法。最后提供了获取示例程序更新和调试Java EE应用程序的实用技巧（使用服务器日志和使用调试器）。

### 经典金句/数据
- **默认端口**：HTTP端口8080，管理端口4848
- **默认管理员**：用户名admin，无需密码
- **安装路径**（Windows）：`C:\glassfish3\glassfish`
- **日志查看**：管理控制台 → 日志查看器，默认显示最后40条记录

---

## 第II部分：Web层（简介）

### 核心论点
第II部分聚焦于Java EE Web层的高级主题，涵盖JavaServer Faces技术的高级概念、Ajax应用、复合组件、自定义组件与渲染器、应用程序配置、文件上传以及国际化/本地化等内容。

### 关键概念/事件
- **JSF生命周期**：恢复视图、应用请求值、处理校验、更新模型值、调用应用程序、渲染响应六个阶段
- **Ajax支持**：Java EE 6内置JavaScript资源库，通过`f:ajax`标签提供异步局部更新能力
- **复合组件**：一种特殊类型的JSF模板，可作为一个组件使用
- **自定义组件**：通过继承UIComponent类创建，可搭配自定义渲染器实现跨客户端渲染
- **文件上传**：Servlet 3.0通过`@MultipartConfig`注解和`Part`接口原生支持

---

## 第3章：JavaServer Faces技术：高级概念

### 核心论点
本章主要回答：JavaServer Faces应用程序的完整生命周期是怎样的？JSF的组件架构包含哪些核心模型？作者深入讲解了JSF生命周期的六个阶段，以及组件模型、渲染模型、转换模型、事件/监听器模型、校验模型和导航模型的运作机制。

### 关键概念/事件
- **生命周期六阶段**：恢复视图→应用请求值→处理校验→更新模型值→调用应用程序→渲染响应
- **起始请求vs回传请求**：起始请求只执行恢复视图和渲染响应；回传请求执行全部阶段
- **immediate属性**：设置为true时，校验和事件在“应用请求值”阶段处理而非后续阶段
- **局部处理和局部渲染**：`PartialViewContext`支持只处理和渲染部分组件（如Ajax请求）
- **组件架构五大模型**：组件类、渲染模型、转换模型、事件/监听器模型、校验模型、导航模型

### 逻辑推演/叙事脉络
本章首先概述JSF生命周期的两个主要阶段（执行和渲染）及六子阶段，然后逐一详细解释每个阶段的具体职责和事件处理机制。接着说明局部处理和局部渲染在Ajax场景下的应用。之后转入组件架构的介绍，分别阐述UIComponent类体系、Renderer的委托渲染机制、Converter的数据转换、Listener的事件处理、Validator的输入校验，以及基于XML配置的导航规则。最后以隐式导航作为补充。

### 经典金句/数据
> “一个组件可以是一个用户界面(UI)组件，或者是一个非UI组件。JSF UI组件是可配置、可重用的元素，用来组成JSF应用程序的用户界面。”(p.45)

> “按照JSF组件架构的设计，组件的功能由组件类定义，而组件如何渲染则由另外的渲染器来定义。”(p.47)

---

## 第4章：在JavaServer Faces技术中使用Ajax

### 核心论点
本章主要回答：如何在JSF应用程序中使用Ajax功能实现异步局部更新？`f:ajax`标签的各属性如何配置？作者的核心观点是：Java EE 6内置了JavaScript资源库，通过`f:ajax`标签可以为零任意UI组件添加Ajax行为，无需编写额外JavaScript代码。

### 关键概念/事件
- **`f:ajax`标签**：JSF核心标签，为任意组件提供Ajax功能，支持execute和render属性指定服务器执行和客户端渲染的组件
- **execute属性关键字**：`@all`（全部组件）、`@form`（包裹组件的表单）、`@none`（无）、`@this`（触发请求的元素）
- **onevent/onerror属性**：分别指定JavaScript方法监视Ajax请求进度和处理错误
- **ajaxguessnumber示例**：演示异步调用managed bean，在同一页面显示响应而非跳转新页面

### 逻辑推演/叙事脉络
本章先解释Ajax的概念和优点（实时校验、局部更新、减少网络消耗），然后介绍在JSF中使用Ajax的两种方式（`f:ajax`标签和JavaScript API）。接着以`f:ajax`标签为核心，详细讲解其event、execute、immediate、listener、onevent、onerror、render等属性的用法。之后说明Ajax请求在JSF生命周期中的特殊处理（PartialViewContext）。最后通过ajaxguessnumber示例程序完整演示如何将传统的guessnumber应用改造为Ajax版本。

### 经典金句/数据
> “通过使用Ajax，web应用程序不需要影响客户端的显示，就可以从服务器获取内容。”(p.55)

- **默认事件**：ActionSource组件默认`action`，EditableValueHolder组件默认`valueChange`
- **execute默认值**：`@this`
- **render默认值**：`@none`

---

## 第5章：复合组件：高级主题及示例程序

### 核心论点
本章主要回答：如何创建和使用具有高级特性的JSF复合组件？复合组件如何与Managed Bean交互和进行值校验？作者通过compositecomponentlogin示例展示了复合组件如何封装登录面板并与后台bean交互。

### 关键概念/事件
- **`composite:attribute`标签属性**：name（属性名）、default（默认值）、required（是否必须）、method-signature（方法表达式类型）、type（属性类型）
- **调用Managed Bean**：通过将bean引用传递给复合组件，或直接在复合组件中使用bean属性
- **校验器标签**：`f:validateBean`（委托Bean Validation）、`f:validateRegex`（正则校验）、`f:validateRequired`（必填校验）
- **compositecomponentlogin示例**：创建接受用户名和密码的复合组件，与MyLoginBean交互进行登录验证

### 逻辑推演/叙事脉络
本章首先介绍复合组件属性的高级配置选项，包括method-signature和type的使用。然后说明如何通过传递引用或直接使用属性两种方式调用Managed Bean。接着列举可用于复合组件的校验器标签。最后通过compositecomponentlogin示例完整展示了复合组件文件（LoginPanel.xhtml）的interface和implementation部分、Facelets页面的引用方式、MyLoginBean的实现逻辑（用户名验证为"javaee"），以及校验规则（用户名4-10字符，密码需包含数字、大小写字母）。

### 经典金句/数据
- **登录验证逻辑**：用户名必须为"javaee"才能成功
- **密码校验规则**：`f:validateRegex`要求至少包含一个数字、一个小写字母、一个大写字母，长度4-10字符
- **运行URL**：`http://localhost:8080/compositecomponentlogin/`

---

## 第6章：创建自定义UI组件以及其他自定义对象

### 核心论点
本章主要回答：如何从头创建自定义JSF组件、渲染器、转换器、监听器和校验器？如何决定是否需要自定义组件或渲染器？作者的核心观点是：JSF灵活的组件架构允许开发者将行为定义与渲染分离，通过继承UIComponent类、实现Renderer类、定义TLD标签，可以创建可复用的自定义UI组件。

### 关键概念/事件
- **何时使用自定义组件**：需要添加新行为、不同的请求处理逻辑、利用非HTML客户端特性
- **何时使用自定义渲染器**：需要为不同客户端设备提供相同的组件语义但不同的展现
- **`@FacesComponent`注解**：将组件注册到JSF实现，指定component-family和component-type
- **`@FacesRenderer`注解**：将渲染器注册到渲染套件，标识componentFamily和rendererType
- **Duke's Bookstore图像映射**：MapComponent和AreaComponent自定义组件，配合MapRenderer和AreaRenderer实现客户端图像映射

### 逻辑推演/叙事脉络
本章首先帮助读者判断是否需要自定义组件/渲染器，说明直接实现vs委托实现两种编码/解码模型。然后以Duke's Bookstore的图像映射为例，分析HTML输出、Facelets页面、模型数据配置。接着详细列出创建自定义组件的完整步骤：创建组件类、重写getFamily、实现编码/解码、允许表达式属性、保存/恢复状态、委托渲染器。之后分别讲解创建渲染器类、实现事件监听器、处理自定义组件事件、在TLD中定义标签。最后介绍自定义转换器（CreditCardConverter）和自定义校验器（FormatValidator）的创建与使用，以及组件值与实例与Managed Bean绑定的两种方式。

### 经典金句/数据
> “通过使用组件类族和渲染器类型来查找组件可使用的渲染器，JSF实现允许一个组件被多个渲染器渲染，并且允许一个渲染器渲染多个组件。”(p.140)

- **绑定方式**：value绑定（组件值与bean属性）vs binding绑定（组件实例与bean属性）
- **TLD文件命名**：必须以`taglib.xml`结尾
- **转换器注册**：`@FacesConverter("ccno")`或`@FacesConverter(forClass=Guardian.class)`

---

## 第7章：配置JavaServer Faces应用程序

### 核心论点
本章主要回答：如何配置大型JSF应用程序的Managed Bean、导航规则、消息资源、自定义组件/转换器/校验器？作者全面介绍了使用注解和faces-config.xml两种配置方式，以及各类配置元素的详细用法。

### 关键概念/事件
- **`@ManagedBean`注解**：自动将类注册为JSF管理的bean，支持@ApplicationScoped、@SessionScoped、@ViewScoped、@RequestScoped、@NoneScoped、@CustomScoped
- **eager加载**：设置`@ManagedBean(eager=true)`强制应用程序作用域bean在启动时初始化
- **应用程序配置资源文件**：faces-config.xml，用于配置导航规则、资源绑定、默认校验器、自定义转换器/校验器/组件/渲染器
- **导航规则结构**：`<navigation-rule>`包含`<from-view-id>`和`<navigation-case>`（含`<from-outcome>`和`<to-view-id>`）
- **项目阶段**：Development、UnitTest、SystemTest、Production，影响错误消息显示等行为

### 逻辑推演/叙事脉络
本章从注解配置Managed Bean入手，说明作用域类型和eager加载。然后详细介绍应用程序配置资源文件的位置、顺序（absolute-ordering和ordering）和schema结构。接着深入讲解`<managed-bean>`元素的配置（初始化Map/List/数组、引用其他bean、引用上下文初始化参数、作用域连接规则）。之后说明资源绑定的注册和FacesMessage的使用。随后分别讲解默认校验器、自定义校验器、自定义转换器的注册方法。再详细说明导航规则的XML配置和隐式导航。最后介绍渲染套件注册自定义渲染器、组件注册，以及WAR文件打包的基本要求和web.xml配置（FacesServlet、状态保存位置、项目阶段）。

### 经典金句/数据
- **状态保存**：默认服务器端，可通过`javax.faces.STATE_SAVING_METHOD`参数改为client
- **项目阶段默认值**：Development
- **允许的作用域连接**：应用程序→应用程序、none；会话→none、应用程序、会话；请求→none、应用程序、会话、请求、视图
- **faces-config位置**：`/WEB-INF/faces-config.xml`、`/META-INF/faces-config.xml`、或通过`javax.faces.CONFIG_FILES`参数指定

---

## 第8章：使用Java Servlet技术上传文件

### 核心论点
本章主要回答：Servlet 3.0如何原生支持文件上传？`@MultipartConfig`注解和`Part`接口如何使用？作者通过fileupload示例演示了从HTML表单接收multipart请求并保存文件到服务器的完整流程。

### 关键概念/事件
- **`@MultipartConfig`注解属性**：location（临时存储目录）、fileSizeThreshold（磁盘缓存阈值）、maxFileSize（单个文件最大大小）、maxRequestSize（整个请求最大大小）
- **`Part`接口方法**：getName()、getSize()、getContentType()、getInputStream()、write(String filename)、delete()、getHeader()
- **HTML表单约束**：enctype必须为`multipart/form-data`，method必须为`POST`
- **fileupload示例**：FileUploadServlet使用`@WebServlet("/upload")`和`@MultipartConfig`注解，通过request.getPart("file")获取文件Part，调用write()方法保存到destination指定目录

### 逻辑推演/叙事脉络
本章先说明Servlet 3.0之前文件上传的复杂性，引出新规范带来的简化。然后介绍`@MultipartConfig`注解的四个可选属性及在web.xml中的等效配置。接着说明`HttpServletRequest`新增的`getPart()`和`getParts()`方法。最后通过fileupload示例的完整代码（HTML表单、FileUploadServlet的processRequest方法、getFileName辅助方法）演示了从请求中提取destination参数、获取文件Part、创建FileOutputStream写入文件的完整流程，并说明了错误处理机制。

### 经典金句/数据
> “在Servlet 3.0之前，实现文件上传需要使用额外的库，或者复杂的输入处理。”(p.148)

- **enctype必须值**：`multipart/form-data`
- **method必须值**：`POST`
- **web.xml等效配置**：`<multipart-config>`子元素包含`<location>`、`<max-file-size>`、`<max-request-size>`、`<file-size-threshold>`

---

## 第9章：国际化和本地化Web应用程序

### 核心论点
本章主要回答：如何使Web应用程序支持多种语言和数据格式？Java平台提供了哪些国际化和本地化类？作者介绍了使用资源绑定（ResourceBundle）和Locale类实现Web应用程序国际化的标准方法。

### 关键概念/事件
- **Locale类**：表示特定地理位置、政治和文化属性的区域，由语言和国家两字符简写组成（如`en_US`、`de_CH`）
- **ResourceBundle**：存储语言环境敏感数据的键/值对集合，基础名称后追加语言环境字符串
- **字符编码**：ISO8859系列（Latin-1）、UTF-8（变长编码，兼容ASCII）
- **Duke's Tutoring资源绑定**：支持en（英语）、de（德语）、es（西班牙语）、pt（葡萄牙语）、zh（中文）
- **Unicode转义**：在properties文件中使用`\uXXXX`表示非ASCII字符

### 逻辑推演/叙事脉络
本章先区分国际化和本地化的概念。然后介绍Java平台提供的Locale和ResourceBundle类。接着说明两种提供本地化消息的方法（多套页面vs资源绑定），推荐使用资源绑定方式。之后讲解如何建立语言环境（从请求获取或用户显式设置）、如何在配置文件中使用`<locale-config>`和`<resource-bundle>`、如何通过`f:loadBundle`标签加载资源绑定、如何获取本地化消息。随后说明日期和数字的格式化方法。最后介绍字符集和字符编码的概念，强调UTF-8的兼容性和在properties文件中使用Unicode转义。

### 经典金句/数据
> “UTF-8兼容大量已有的web内容，并且提供了对Unicode字符集的访问。”(p.160)

- **默认语言环境**：en（英语）
- **支持的语言环境**：de（德语）、es（西班牙语）、pt（葡萄牙语）、zh（中文）
- **Duke's Tutoring示例**：`nav.main=P\u00e1gina Principal`（西班牙语）
- **字符集**：US-ASCII（美国英语）、Unicode（标准化通用字符集）

---

## 第10章：JAX-RS：高级主题和示例

### 核心论点
本章主要回答：JAX-RS提供了哪些高级注解和功能？如何与EJB、CDI集成？如何进行条件性HTTP请求和运行时内容协商？作者详细介绍了路径参数提取、子资源定位、JAXB集成等内容，并通过customer示例完整演示RESTful Web Service的开发。

### 关键概念/事件
- **参数提取注解**：`@PathParam`（路径参数）、`@QueryParam`（查询参数）、`@FormParam`（表单数据）、`@HeaderParam`（头信息）、`@CookieParam`（Cookie）、`@MatrixParam`（矩阵参数）、`@Context`（注入上下文对象）
- **子资源方法vs子资源定位符**：子资源方法直接处理HTTP请求（有`@GET`等注解）；子资源定位符返回处理HTTP请求的对象（无请求方法注解）
- **条件性HTTP请求**：使用Last-Modified和ETag头信息，GET返回304（Not Modified）、PUT返回412（Precondition Failed）
- **运行时内容协商**：`Variant`类和`Request.selectVariant()`方法根据Accept等头信息选择最合适的资源表现形式
- **JAXB集成**：通过`@XmlRootElement`等注解将Java对象与XML互相转换，JAX-RS自动提供MessageBodyReader/Writer

### 逻辑推演/叙事脉络
本章首先列出JAX-RS高级注解的用途。然后分别讲解路径参数（支持正则表达式）、查询参数（支持`@DefaultValue`）、表单数据提取的方法。接着说明`@Context`注入URIInfo和HttpHeaders的使用。之后重点讲解子资源机制：子资源方法处理URI模板匹配的请求；子资源定位符返回子资源对象，支持运行时动态解析。然后介绍JAX-RS与EJB（无状态session bean、单例bean）和CDI的集成方式。随后分别说明条件性HTTP请求的实现（使用Request.evaluatePreconditions）和运行时内容协商的实现（VariantListBuilder + selectVariant）。再详细讲解在JAX-RS中使用JAXB的三种方式（从Java类生成XSD、从XSD生成Java类、使用JAXBContext和ContextResolver）。最后通过customer示例展示完整的RESTful Web Service实现。

### 经典金句/数据
> “通过使用JAXB，你可以使用以下几种方式来操作数据对象：从XML schema定义开始创建Java类，或从Java类开始生成XML schema。”(p.171)

- **@PathParam正则示例**：`{lastname[a-zA-Z]*}`限制姓氏只能由大小写字母组成
- **selectVariant组合数量**：mediatypes和languages参数的笛卡尔积
- **customer示例**：Customer和Address实体类、CustomerService资源类、CustomerClientXML和CustomerClientJSON客户端

---

## 第11章：Message-Driven Bean示例

### 核心论点
本章主要回答：如何创建和部署Message-Driven Bean？simplemessage示例的各个组件如何协作？作者通过simplemessage示例演示了MDB的完整开发流程，包括应用程序客户端、MDB类、onMessage方法实现以及JMS资源的创建与管理。

### 关键概念/事件
- **simplemessage示例**：包含应用程序客户端（发送消息）、Message-Driven Bean（接收消息）、JMS连接工厂和目的地资源
- **onMessage方法**：MDB的核心方法，当消息到达时由容器自动调用，参数为Message对象
- **被管理对象**：JMS连接工厂（ConnectionFactory）和目的地（Queue/Topic），需要在应用服务器中创建
- **应用程序客户端**：通过JNDI查找连接工厂和目的地，创建连接、会话、消息生产者，发送TextMessage

### 逻辑推演/叙事脉络
本章先概述simplemessage示例的架构（客户端发送消息、MDB异步接收）。然后介绍应用程序客户端的实现：查找资源、创建JMS对象、发送消息。接着详细讲解MDB类的定义（`@MessageDriven`注解、activationConfig属性配置目的地类型）、onMessage方法的实现逻辑。之后说明运行示例的步骤（启动服务器、创建被管理对象、部署、运行客户端）。最后介绍如何删除JMS被管理对象。

### 经典金句/数据
- **MDB激活配置**：`destinationType`指定为`javax.jms.Queue`
- **消息类型**：TextMessage（文本消息）
- **删除命令**：`asadmin delete-jms-resource`（连接工厂）和`asadmin delete-jms-destination`（目的地）

---

## 第12章：使用嵌入式Enterprise Bean容器

### 核心论点
本章主要回答：如何使用嵌入式EJB容器在Java SE环境中测试和运行Enterprise Bean？作者通过standalone示例演示了在容器外启动EJB容器、查找Session Bean引用、关闭容器的完整流程。

### 关键概念/事件
- **嵌入式EJB容器**：允许在Java SE环境中运行EJB组件，无需完整的Java EE应用服务器
- **EJBContainer API**：`EJBContainer.createEJBContainer()`创建容器实例，`getContext()`获取JNDI上下文
- **standalone示例**：演示如何在独立Java应用程序中使用嵌入式容器调用Session Bean
- **查找Session Bean**：通过JNDI名称`java:global/模块名/Bean类名`查找

### 逻辑推演/叙事脉络
本章先介绍嵌入式EJB容器的用途（单元测试、独立运行）。然后说明开发嵌入式应用程序的步骤：编写EJB、编写客户端、启动容器、查找引用、调用方法、关闭容器。接着详细讲解`EJBContainer`的创建和配置、JNDI查找的命名规则。最后通过standalone示例展示完整的代码实现。

### 经典金句/数据
- **创建容器**：`EJBContainer.createEJBContainer()`
- **关闭容器**：`container.close()`
- **JNDI查找格式**：`java:global/ejb-module-name/BeanClassName`

---

## 第13章：在Session Bean中使用异步方法调用

### 核心论点
本章主要回答：如何在Session Bean中实现和调用异步方法？异步调用有哪些特点和约束？作者通过async示例演示了`@Asynchronous`注解的使用，以及客户端如何接收异步结果。

### 关键概念/事件
- **`@Asynchronous`注解**：标记Session Bean的业务方法为异步执行，立即返回`void`或`Future<V>`对象
- **`Future<V>`接口**：表示异步计算的结果，提供`get()`（阻塞等待）、`isDone()`（检查完成）、`cancel()`（取消）等方法
- **async示例**：演示在无状态Session Bean中使用异步方法，客户端通过`Future`获取结果
- **异步方法约束**：只能用在Session Bean（无状态、有状态、单例）中；方法参数和返回值必须是可序列化的

### 逻辑推演/叙事脉络
本章先说明异步方法调用的适用场景（长时间操作、不需要立即返回结果）。然后讲解如何通过`@Asynchronous`注解创建异步业务方法，以及两种返回类型（`void`和`Future<V>`）。接着说明客户端如何调用异步方法并使用`Future`对象获取结果（包括`get`的阻塞特性、`get(long timeout, TimeUnit unit)`的超时控制）。最后通过async示例完整演示实现和运行过程。

### 经典金句/数据
- **异步方法类型**：`void`（不返回结果）或`Future<V>`（返回结果）
- **`Future.get()`行为**：阻塞直到计算完成
- **示例架构**：AsyncBean（无状态Session Bean）+ AsyncServlet（调用异步方法）

---

## 第14章：Java EE平台上下文和依赖注入：高级篇

### 核心论点
本章主要回答：CDI提供了哪些高级特性？如何使用替代类、生产者方法、事件、拦截器和装饰器？作者系统介绍了CDI 1.0规范中的高级功能，展示如何构建松耦合、类型安全的应用程序。

### 关键概念/事件
- **替代类（Alternatives）**：使用`@Alternative`注解标记备选实现，通过`beans.xml`中的`<alternatives>`元素激活，用于不同环境（测试/生产）切换
- **生产者方法/字段（`@Produces`）**：动态生成可注入的对象，生产者方法可包含自定义逻辑，生产者字段用于生成资源（如EntityManager）
- **清理方法（`@Disposes`）**：与生产者方法配对，在对象不再使用时释放资源
- **事件（`@Observes`）**：使用`@Observes`注解的观察者方法监听事件，`Event<T>.fire()`触发事件，实现松耦合通信
- **拦截器（`@Interceptor`）**：使用方法级`@AroundInvoke`拦截方法调用，处理横切关注点（如日志、事务）
- **装饰器（`@Decorator`）**：实现业务接口并委托给被装饰对象，用于增强业务逻辑而非技术横切关注点
- **模板（Stereotypes）**：使用`@Stereotype`注解组合多个作用域和拦截器注解

### 逻辑推演/叙事脉络
本章从替代类开始，说明如何通过`@Alternative`和`beans.xml`在不同环境间切换实现。然后介绍生产者方法和生产者字段如何动态生成可注入对象，以及清理方法如何配合释放资源。接着说明预定义Bean（如`Instance<T>`）的用法。随后讲解事件机制的定义、触发和观察。最后分别介绍拦截器（横切关注点）、装饰器（业务逻辑增强）和模板（注解组合）的用法。

### 经典金句/数据
> “CDI有着更广泛的使用范围以及更好的灵活性，使得开发人员能够以松耦合但是类型安全的方式整合不同类型的组件。”(p.22)

- **替代类激活**：需要在`beans.xml`的`<alternatives>`中显式声明
- **生产者方法**：方法级注解，返回类型即为可注入类型
- **观察者方法**：参数标注`@Observes EventType event`

---

## 第15章：运行上下文和依赖注入的高级示例程序

### 核心论点
本章主要回答：CDI的高级特性在实际代码中如何应用？作者通过5个完整示例（encoder、producermethods、producerfields、billpayment、decorators）逐一演示了替代类、生产者方法、生产者字段、事件、拦截器和装饰器的实战用法。

### 关键概念/事件
- **encoder示例**：使用`@Alternative`注解，通过`beans.xml`切换Coder接口的不同实现（CoderImpl和CoderImplTwo）
- **producermethods示例**：使用生产者方法根据运行时条件动态选择Coder实现
- **producerfields示例**：使用生产者字段生成EntityManager资源，配合实体和Session Bean操作数据库
- **billpayment示例**：结合事件（PaymentEvent）和拦截器（`@Logged`）实现支付处理和日志记录
- **decorators示例**：使用`@Decorator`装饰Message接口的实现类，添加业务逻辑增强

### 逻辑推演/叙事脉络
本章按示例逐一展开。每个示例均按照“组件介绍→Facelets页面→Managed Bean→运行步骤”的结构组织。encoder示例展示替代类的切换效果；producermethods示例展示生产者方法的动态决策能力；producerfields示例展示资源生产和注入的完整流程；billpayment示例展示事件驱动和拦截器切面的协同工作；decorators示例展示装饰器如何在不修改原代码的情况下增强业务功能。

### 经典金句/数据
- **encoder运行**：按钮切换时显示不同的编码结果
- **billpayment拦截器**：`@Logged`注解触发日志记录
- **producerfields实体**：使用JPA注解标注实体类
- **decorators执行顺序**：装饰器在拦截器之后、业务方法之前执行

---

## 第16章：创建并使用基于字符串的条件（Criteria）查询

### 核心论点
本章主要回答：JPA 2.0中基于字符串的Criteria API是什么？如何使用它构建动态查询？作者介绍了与类型安全Criteria API相对的字符串形式API，用于运行时动态构建查询字符串。

### 关键概念/事件
- **字符串Criteria API**：以字符串形式表达查询条件，通过`EntityManager.createQuery()`创建，适用于动态条件拼接场景
- **查询创建**：使用JPQL语法字符串，支持`WHERE`子句的条件表达式动态添加
- **查询执行**：调用`getResultList()`或`getSingleResult()`执行查询

### 逻辑推演/叙事脉络
本章先说明字符串Criteria API的适用场景（动态条件拼接、运行时查询构建）。然后介绍如何通过字符串拼接创建查询、设置参数、执行查询。最后强调与类型安全Criteria API的取舍。

---

## 第17章：使用锁来控制对实体数据的并发访问

### 核心论点
本章主要回答：JPA如何处理多用户并发访问同一实体数据的冲突？乐观锁和悲观锁分别如何配置和使用？作者介绍了锁模式（LOCK_MODE）的设置方法和两种锁机制的应用场景。

### 关键概念/事件
- **乐观锁**：通过`@Version`注解版本字段实现，更新时检查版本号；`LockModeType.OPTIMISTIC`、`OPTIMISTIC_FORCE_INCREMENT`
- **悲观锁**：数据库层面锁定行，`LockModeType.PESSIMISTIC_READ`（共享锁）、`PESSIMISTIC_WRITE`（排他锁）、`PESSIMISTIC_FORCE_INCREMENT`
- **设置锁的方式**：`EntityManager.lock()`、`find()`的lockMode参数、`Query.setLockMode()`、`@NamedQuery`的lockMode属性
- **锁超时**：通过`javax.persistence.lock.timeout`提示属性设置

### 逻辑推演/叙事脉络
本章先说明并发访问问题的背景。然后分别介绍乐观锁（通过版本字段、适用读多写少场景）和悲观锁（数据库行锁、适用写冲突频繁场景）的原理和使用场景。接着详细讲解各种锁模式的含义和设置方法。最后说明锁超时的配置。

### 经典金句/数据
- **`@Version`类型**：int、Integer、long、Long、short、Short、Timestamp
- **OPTIMISTIC_FORCE_INCREMENT**：即使实体未修改也强制增加版本号
- **PESSIMISTIC_WRITE**：排他锁，阻止其他事务读或写

---

## 第18章：在Java持久化API应用程序中使用二级缓存

### 核心论点
本章主要回答：JPA二级缓存是什么？如何配置和使用？作者介绍了二级缓存的缓存模式（CacheModeType）和存储模式（RetrieveMode/StoreMode）的设置，以及如何通过编程方式控制缓存。

### 关键概念/事件
- **二级缓存（L2 Cache）**：跨EntityManager的共享缓存，存储在EntityManagerFactory级别
- **`@Cacheable`注解**：标记实体是否可被缓存，`true`表示允许缓存
- **缓存模式**：`CacheModeType`枚举值：GET、PUT、BOTH、REFRESH、NONE、BY_PASS
- **缓存存储/读取模式**：`javax.persistence.cache.retrieveMode`和`storeMode`属性，可用`CacheRetrieveMode.BYPASS`和`CacheStoreMode.BYPASS`跳过缓存
- **编程式控制**：`EntityManagerFactory.getCache()`获取`Cache`接口，调用`contains()`、`evict()`、`evictAll()`管理缓存

### 逻辑推演/叙事脉络
本章先说明二级缓存的作用（提高读取性能、减少数据库访问）。然后讲解`@Cacheable`注解的使用和控制实体是否可缓存的规则。接着介绍通过`persistence.xml`中的`shared-cache-mode`元素设置缓存模式（ALL、NONE、ENABLE_SELECTIVE、DISABLE_SELECTIVE、UNSPECIFIED）。之后说明如何设置缓存的读取和存储模式以覆盖默认行为。最后介绍通过`Cache`接口编程控制二级缓存的方法。

### 经典金句/数据
- **shared-cache-mode值**：ALL（全部缓存）、NONE（全部不缓存）、ENABLE_SELECTIVE（仅`@Cacheable(true)`缓存）、DISABLE_SELECTIVE（仅`@Cacheable(false)`不缓存）
- **CacheStoreMode.REFRESH**：更新数据库的同时刷新二级缓存
- **缓存失效**：`cache.evict(EntityClass.class, id)`

---

## 第19章：Java EE安全：高级篇

### 核心论点
本章主要回答：Java EE提供了哪些高级安全机制？如何使用数字签名、证书域、双向认证？如何保护Web应用程序和企业信息系统安全？作者介绍了证书管理、认证机制配置、JDBC域、程序式登录等内容。

### 关键概念/事件
- **数字签名和服务器证书**：使用keytool生成证书，配置GlassFish Server使用HTTPS和双向认证
- **证书域**：将用户添加到证书域，基于客户端证书进行认证
- **认证机制**：基本认证、表单认证、摘要认证、客户端证书认证、双向认证
- **JDBC域**：将用户信息存储在关系数据库中，通过JDBC查询进行认证
- **程序式登录**：`HttpServletRequest.login(username, password)`实现编程式认证
- **组件管理登录**：应用程序客户端和EJB组件的`LoginContext`使用

### 逻辑推演/叙事脉络
本章先介绍如何创建和使用服务器证书进行HTTPS通信。然后讲解如何在GlassFish Server中配置不同的服务器证书和认证机制。接着详细说明客户端证书认证和双向认证的配置步骤。之后介绍在JSF Web应用中使用基于表单的登录（`j_security_check`）和通过Managed Bean进行程序式认证。随后讲解如何配置JDBC域实现数据库用户认证。最后介绍如何保护HTTP资源安全、保护应用程序客户端安全、配置资源适配器安全，以及使用部署描述符配置安全选项。

### 经典金句/数据
- **keytool命令**：生成密钥对和自签名证书
- **默认HTTPS端口**：8181
- **j_security_check**：表单登录的标准action URL
- **JDBC域配置**：需要指定数据源、用户表、密码加密算法

---

## 第20章：Java消息服务概念

### 核心论点
本章主要回答：JMS API是什么？消息传递的核心概念和编程模型是怎样的？如何构建可靠的消息应用程序？作者系统介绍了JMS架构、消息传递域（点对点、发布/订阅）、编程模型（连接、会话、生产者、消费者、消息），以及可靠性机制。

### 关键概念/事件
- **JMS API**：Java平台的消息传递标准，支持松耦合、可靠、异步的分布式通信
- **消息传递域**：点对点（Queue，一消息一消费者）、发布/订阅（Topic，一消息多订阅者）
- **编程模型对象**：ConnectionFactory、Destination（Queue/Topic）、Connection、Session、MessageProducer、MessageConsumer、Message
- **消息类型**：TextMessage、MapMessage、BytesMessage、StreamMessage、ObjectMessage
- **可靠性机制**：消息应答（AUTO_ACKNOWLEDGE、CLIENT_ACKNOWLEDGE、DUPS_OK_ACKNOWLEDGE）、持久订阅、本地事务
- **Message-Driven Bean**：容器管理的JMS消息消费者，异步处理消息

### 逻辑推演/叙事脉络
本章先从消息传递的基本概念入手，说明JMS API的用途和使用场景。然后介绍JMS架构（JMS提供方、JMS客户端）和两种消息传递域的差异。接着详细讲解JMS编程模型中各个对象的作用和创建顺序。之后说明消息的结构（头、属性、体）和五种消息类型。随后重点介绍可靠性机制：基础机制（消息应答、持久性、优先级、存活时间）和高级机制（持久订阅、本地事务、异步接收）。最后说明JMS API在Java EE环境中的使用方式，包括`@Resource`注解注入JMS资源、Session Bean生产和同步接收消息、Message-Driven Bean异步接收消息。

### 经典金句/数据
> “JMS API可以用来构建松耦合的、可靠的以及异步的分布式通信。”(p.23)

- **点对点特征**：一消息只能被一个消费者接收
- **发布/订阅特征**：一消息可被多个订阅者接收
- **持久订阅**：订阅者离线时消息仍会保存

---

## 第21章：Java消息服务示例

### 核心论点
本章主要回答：JMS API的各种使用场景如何通过代码实现？作者通过一系列示例（简单同步/异步接收、队列浏览、消息应答、持久订阅、本地事务、Session Bean集成、实体集成、远程消息接收等）完整演示了JMS编程模型的实战应用。

### 关键概念/事件
- **简单JMS示例**：同步接收（生产者发送、消费者`receive()`阻塞等待）和异步接收（注册MessageListener）
- **队列浏览器（QueueBrowser）**：浏览队列中消息而不消费
- **消息应答示例**：演示CLIENT_ACKNOWLEDGE模式下需显式调用`acknowledge()`
- **持久订阅示例**：创建`createDurableSubscriber()`，订阅者离线时消息不丢失
- **本地事务示例**：Session创建时参数为true，调用`commit()`或`rollback()`
- **clientsessionmdb示例**：应用程序客户端发送消息，Session Bean生产消息，MDB消费消息
- **clientmdbentity示例**：结合JPA实体，将接收的消息持久化到数据库
- **consumeremote示例**：从一个服务器接收远程消息
- **sendremote示例**：在两个服务器上部署MDB，演示跨服务器消息传递

### 逻辑推演/叙事脉络
本章按示例逐一展开，从简单到复杂。每个示例均包含“编写组件→创建资源→运行→清理”的完整流程。先演示最基本的同步和异步接收，再介绍队列浏览功能。然后进入可靠性机制示例：消息应答、持久订阅、本地事务。接着展示JMS与EJB集成的两种模式（Session Bean生产消息+MDB消费、结合JPA实体持久化）。最后演示跨服务器的远程消息接收和双服务器MDB部署。

---

## 第22章：Bean Validation：高级主题

### 核心论点
本章主要回答：Bean Validation提供了哪些高级特性？如何创建自定义约束、定制错误消息、使用约束分组并控制校验顺序？作者介绍了`@Constraint`注解、ValidationMessages.properties资源绑定、`@GroupSequence`等高级功能。

### 关键概念/事件
- **创建自定义约束**：使用`@Constraint(validatedBy=...)`注解，实现`ConstraintValidator`接口，定义`validate()`方法
- **使用内置约束组合**：通过`@ReportAsSingleViolation`和`@Pattern.List`等组合多个内置约束
- **自定义校验器消息**：在`ValidationMessages.properties`文件中配置消息模板，使用`{}`占位符
- **约束分组**：使用`interface`定义分组，校验时指定`@Validated(GroupClass.class)`
- **分组校验顺序**：使用`@GroupSequence`定义分组的校验顺序，前面分组失败则后续分组不执行

### 逻辑推演/叙事脉络
本章先说明如何通过扩展`@Constraint`创建自定义约束注解并实现对应的`ConstraintValidator`。然后讲解如何利用内置约束组合创建新的约束。接着介绍在`ValidationMessages.properties`资源绑定中定义自定义错误消息的方法。之后说明约束分组的定义和使用场景（如将校验分为基础分组和高级分组）。最后讲解如何使用`@GroupSequence`控制多个分组的校验执行顺序。

### 经典金句/数据
- **自定义约束所需**：注解定义 + Validator实现 + ValidationMessages.properties消息
- **分组定义**：空接口作为分组标识
- **顺序校验**：`@GroupSequence({Basic.class, Advanced.class})`

---

## 第23章：使用Java EE拦截器

### 核心论点
本章主要回答：Java EE拦截器是什么？如何拦截方法调用、生命周期回调和超时事件？作者通过interceptor示例演示了`@AroundInvoke`、`@PostConstruct`等拦截器类型的使用方法以及拦截器与CDI的集成。

### 关键概念/事件
- **拦截器类**：使用`@Interceptor`注解标记，实现拦截方法（`@AroundInvoke`、`@PostConstruct`、`@PreDestroy`、`@PrePassivate`、`@PostActivate`、`@AroundTimeout`）
- **拦截方法调用**：`@AroundInvoke`注解方法，参数为`InvocationContext`，调用`proceed()`继续执行链
- **拦截生命周期回调**：`@PostConstruct`等方法级注解，拦截bean的创建、销毁等生命周期事件
- **拦截超时事件**：`@AroundTimeout`注解方法，拦截EJB定时器超时
- **拦截器与CDI**：拦截器可以作为CDI bean，支持依赖注入
- **interceptor示例**：演示如何在Session Bean上应用拦截器记录方法执行时间

### 逻辑推演/叙事脉络
本章先说明拦截器的概念和适用场景（横切关注点）。然后介绍拦截器类的定义和生命周期。接着讲解拦截器与CDI的集成方式。之后详细说明三种拦截类型：方法调用拦截（`@AroundInvoke`）、生命周期回调拦截（`@PostConstruct`等）、超时事件拦截（`@AroundTimeout`）。最后通过interceptor示例展示完整实现。

### 经典金句/数据
- **`InvocationContext.proceed()`**：调用下一个拦截器或目标方法
- **拦截器执行顺序**：`@AroundInvoke`可以调用`proceed()`控制执行链
- **生命周期拦截器**：同一个类中只能有一个`@PostConstruct`方法

---

## 第24章：资源适配器示例

### 核心论点
本章主要回答：资源适配器（Connector）是什么？如何通过mailconnector示例使用邮件资源适配器？作者展示了资源适配器如何与Message-Driven Bean和Web应用程序协同工作。

### 关键概念/事件
- **资源适配器**：Java EE连接器架构中的组件，允许应用程序访问企业信息系统（EIS）
- **mailconnector示例**：演示邮件资源适配器的使用，包含MDB（监听邮件）和Web应用程序
- **部署**：资源适配器以.rar文件打包，与应用程序一同部署

### 逻辑推演/叙事脉络
本章简要介绍资源适配器的概念和用途。然后以mailconnector示例说明其组成部分（资源适配器本身、Message-Driven Bean、Web应用程序）。最后给出运行示例的步骤。

---

## 第25章：Duke's Bookstore案例研究示例

### 核心论点
本章主要回答：Duke's Bookstore案例研究的整体设计和架构是怎样的？它使用了哪些Java EE技术？作者全面分析了该书店应用程序的接口设计、实体类、Enterprise Bean、Facelets页面、Managed Bean、自定义组件、属性文件和部署描述符。

### 关键概念/事件
- **Book实体**：使用JPA `@Entity`标注，包含id、title、price、description等属性
- **Enterprise Bean**：BookRequestBean（无状态Session Bean）提供图书查询功能
- **Facelets页面**：bookcatalog.xhtml（图书目录）、bookdetail.xhtml（图书详情）、bookcashier.xhtml（结账）、bookreceipt.xhtml（收据）
- **自定义组件**：图像映射组件（MapComponent和AreaComponent）配合自定义渲染器
- **属性文件**：messages.properties提供本地化支持
- **部署描述符**：faces-config.xml（JSF配置）、web.xml（Servlet配置）、bookstore.taglib.xml（自定义标签库）

### 逻辑推演/叙事脉络
本章先概述Duke's Bookstore的设计目标和架构分层。然后详细介绍Book实体类的JPA映射。接着说明使用的Enterprise Bean（无状态Session Bean）。之后分析Facelets页面和Managed Bean的协作关系。再讲解自定义图像映射组件的实现和TLD定义。最后说明属性文件和多语言支持、部署描述符的配置，以及应用程序的运行方法。

---

## 第26章：Duke's Tutoring案例研究示例

### 核心论点
本章主要回答：Duke's Tutoring案例研究的设计和架构是怎样的？它如何支持学生、监护人、地址等实体的管理？作者介绍了该辅导管理系统的实体设计、Enterprise Bean、Facelets页面以及主界面和管理界面的双界面架构。

### 关键概念/事件
- **主界面**：面向普通用户，包含学生状态查看、辅导会话管理、学生列表等功能
- **管理界面**：面向管理员，包含创建/编辑学生、创建/编辑监护人、创建/编辑地址、激活学生等功能
- **JPA实体**：Student、Guardian、Address、TutoringSession、StatusType
- **Enterprise Bean**：AdminBean（管理操作）、StatusManager（状态管理）
- **Facelets文件**：分别位于/admin/和/user/目录下
- **资源绑定**：messages.properties支持en、de、es、pt、zh五种语言

### 逻辑推演/叙事脉络
本章先介绍Duke's Tutoring的双界面设计（主界面+管理界面）。然后分别说明两个界面的功能模块。接着详细列出核心JPA实体的属性和关系。之后介绍Enterprise Bean的职责分配。再说明Facelets页面的组织结构和辅助类的用途。最后介绍多语言支持、部署描述符配置以及完整的运行步骤（包括GlassFish Server设置）。

---

## 第27章：Duke's Forest案例研究示例

### 核心论点
本章主要回答：Duke's Forest案例研究如何展示多个Java EE API的整合？它的模块化架构是怎样的？作者详细介绍了由events、entities、dukes-payment、dukes-resource、Duke's Store、Duke's Shipment等多个模块组成的复杂企业级应用程序。

### 关键概念/事件
- **events项目**：处理应用程序中的事件机制
- **entities项目**：定义核心JPA实体类
- **dukes-payment项目**：支付处理模块，演示与支付网关的集成
- **dukes-resource项目**：资源管理模块
- **Duke's Store项目**：商店前端，包含Facelets页面和Managed Bean
- **Duke's Shipment项目**：物流模块，演示消息驱动的订单处理

### 逻辑推演/叙事脉络
本章先概述Duke's Forest的设计目标和整体架构。然后逐一介绍各个子项目（events、entities、dukes-payment、dukes-resource、Duke's Store、Duke's Shipment）的职责和技术栈。接着说明构建和部署的前提条件。最后给出运行整个应用程序的完整步骤。

---

## 致谢与版权说明

### 核心论点
致谢部分感谢了Java EE规范领导者、SDK团队、NetBeans工程师、审阅者以及Addison-Wesley产品团队对本书的贡献。版权说明明确了英文原版版权归Pearson Education所有，中文简体版版权归电子工业出版社所有。

### 关键概念/事件
- **规范领导者感谢**：Roberto Chinnici、Bill Shannon、Ken Saks、Linda DeMichiel、Ed Burns、Gavin King等
- **审阅者**：Sivakumar Thyagarajan（CDI章节）、Tim Quinn（应用程序客户端容器）
- **NetBeans团队**：Petr Jiricka、John Jullion-Ceccarelli、Troy Giunipero
- **版权信息**：英文原版©2013 Pearson Education，中文版合同登记号图字：01-2014-2205

### 经典金句/数据
- **ISBN**：978-7-121-22911-4
- **定价**：89.00元
- **印刷时间**：2014年5月第1次印刷