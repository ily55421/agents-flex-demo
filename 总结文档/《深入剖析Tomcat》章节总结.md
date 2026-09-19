# 《深入剖析Tomcat》章节总结

## 目录说明
本书共20章，每章均配有示例应用程序。总结依据书中PDF正文内容与目录结构整理，严格按原书顺序输出。注意PDF部分页面存在OCR识别不完整的情况（如第12章后半部分），总结将基于可见内容整理。

---

## 第1章：一个简单的Web服务器

### 核心论点
本章主要回答“一个基本的HTTP Web服务器是如何工作的”这一问题。作者认为，理解Web服务器的关键在于掌握HTTP协议的基础知识以及Java中Socket和ServerSocket类的使用。通过构建一个能处理静态资源请求的简单HTTP服务器，可以直观地理解Web服务器的核心工作流程。

### 关键概念/事件
- **HTTP协议**：基于“请求-响应”模式的协议，客户端主动发起连接并发送请求，服务器端返回响应。HTTP/1.1是目前的主要版本。
- **Socket与ServerSocket**：`java.net.Socket`表示客户端套接字，用于与远程服务器通信；`java.net.ServerSocket`表示服务器套接字，用于监听端口并等待客户端连接。
- **HTTP请求结构**：包含三部分——请求方法-URI-协议/版本、请求头、实体正文。请求行与请求头之间由空行（CRLF）分隔。
- **HTTP响应结构**：包含三部分——协议-状态码-描述、响应头、响应实体段。
- **Web服务器基本流程**：创建ServerSocket等待连接 → 接受Socket连接 → 解析HTTP请求 → 发送静态资源或错误响应 → 关闭连接。

### 逻辑推演/叙事脉络
本章首先介绍HTTP协议的基本结构（请求与响应格式），为后续实现Web服务器奠定理论基础。接着讲解Java网络编程的核心类Socket和ServerSocket，说明如何通过它们建立网络连接并收发数据。随后，作者展示了一个完整的Web服务器应用程序（HttpServer、Request、Response三个类），详细解析了await()方法如何循环等待请求、解析请求行、读取静态文件并返回响应。最后，演示了如何运行该服务器并测试静态资源访问。

### 经典金句/数据
> “套接字是网络连接的端点。套接字使应用程序可以从网络中读取数据，可以向网络中写入数据。” (p.3)

> 关闭命令定义：`private static final String SHUTDOWN_COMMAND = "/SHUTDOWN";` (p.6)

---

## 第2章：一个简单的servlet容器

### 核心论点
本章回答“一个简单的servlet容器需要实现哪些核心功能”这一问题。作者的核心观点是：servlet容器的基本任务是接收HTTP请求，创建request和response对象，并调用相应servlet的service()方法。同时，为了避免servlet程序员访问容器内部敏感方法，应使用外观（Facade）模式来包装request和response对象。

### 关键概念/事件
- **Servlet接口**：声明了5个核心方法——`init()`、`service()`、`destroy()`、`getServletConfig()`、`getServletInfo()`。其中service()方法处理具体请求。
- **PrimitiveServlet**：一个简单的测试servlet，实现了Servlet接口，在每个生命周期方法中输出日志，并用PrintWriter向客户端返回字符串。
- **外观模式**：RequestFacade和ResponseFacade类分别实现ServletRequest和ServletResponse接口，内部持有真正的Request/Response对象，只暴露标准接口方法，隐藏了parse()、sendStaticResource()等内部方法。
- **类载入器**：使用`URLClassLoader`从WEB_ROOT目录载入servlet类，避免使用系统类载入器带来的安全隐患。
- **servlet容器处理流程**：区分静态资源请求（以/servlet/开头）和servlet请求，分别交给StaticResourceProcessor和ServletProcessor处理。

### 逻辑推演/叙事脉络
本章首先介绍javax.servlet.Servlet接口，并以PrimitiveServlet为例展示servlet的基本结构。随后构建第一个简单servlet容器（应用程序1），展示了HttpServer1如何区分静态资源与servlet请求，以及ServletProcessor1如何动态载入servlet类并调用其service()方法。但该实现存在安全问题——servlet可以将Request/Response向下转型并调用内部方法。为此，作者引入外观模式构建应用程序2，通过RequestFacade和ResponseFacade包装真实对象，限制servlet对内部方法的访问。

### 经典金句/数据
> “不能将parse()方法和sendStaticResource()方法设置为私有方法，因为它们会被其他的类调用，但是这两个方法在servlet中不应该是可用的，所以这个方法不好。一种解决方法是将Request类和Response类都设为默认的访问修饰符……但是，这里有一个更完美的方法：使用外观类。” (p.28)

> PrimitiveServlet输出：`Hello. Roses are red.`（第2章中只能看到第一行输出）

---

## 第3章：连接器

### 核心论点
本章回答“连接器（Connector）模块的核心职责是什么”这一问题。作者认为，连接器负责解析HTTP请求、创建HttpServletRequest和HttpServletResponse对象，并将处理传递给容器。本章构建的连接器是Tomcat 4默认连接器的简化版，其关键优化是延迟解析请求参数——只有当servlet真正需要时才会解析。

### 关键概念/事件
- **StringManager类**：单例模式实现，用于管理各包下的国际化错误消息，每个包对应一个properties文件（如LocalStrings.properties）。
- **SocketInputStream类**：提供`readRequestLine()`和`readHeader()`方法，分别读取HTTP请求的第一行和各个请求头，使用字符数组避免字符串操作的性能开销。
- **HttpRequestLine与HttpHeader**：分别表示请求行和请求头，内部使用字符数组存储数据。
- **延迟解析参数**：只有在servlet调用`getParameter()`等参数相关方法时，才会调用`parseParameters()`解析查询字符串或请求体中的参数。
- **外观类**：HttpRequestFacade和HttpResponseFacade包装HttpRequest和HttpResponse，防止servlet访问内部方法。

### 逻辑推演/叙事脉络
本章首先介绍StringManager类，说明Tomcat如何管理国际化错误消息。接着，作者展示了一个模块化的应用程序结构：HttpConnector负责接收Socket连接，HttpProcessor负责解析请求并创建request/response对象。详细分析了HttpRequest的构建过程：解析请求行（包括URI、查询字符串、会话标识符）、解析请求头（包括Cookie解析）、以及延迟解析请求参数。最后说明HttpResponse的getWriter()实现如何通过ResponseStream和ResponseWriter正确输出响应。

### 经典金句/数据
> “在Tomcat中，默认连接器（包括本章应用程序中的连接器）是不会解析[请求参数]的，只有当它们被servlet实例真正调用前才会解析，这样就可以使程序执行得更有效率。” (p.34)

> HTTP请求示例中的查询字符串解析：`userName=tarzan&password=pwd` (p.40)

---

## 第4章：Tomcat的默认连接器

### 核心论点
本章深入剖析Tomcat 4中默认连接器的完整实现，该连接器虽然已被Coyote取代，但作为学习工具极具价值。核心观点是：成熟的连接器需要支持HTTP/1.1的新特性（持久连接、块编码、状态码100），并通过对象池、线程池等技术提升性能。

### 关键概念/事件
- **HTTP/1.1新特性**：持久连接（Connection: keep-alive）、块编码（Transfer-Encoding: chunked）、100 Continue状态码（Expect: 100-continue）。
- **Connector接口**：Tomcat连接器的标准接口，核心方法包括`getContainer()`/`setContainer()`、`createRequest()`/`createResponse()`。
- **HttpProcessor对象池**：HttpConnector维护一个Stack存储HttpProcessor实例，通过minProcessors和maxProcessors控制池大小，避免频繁创建对象。
- **异步assign/await机制**：使用Object的`wait()`和`notifyAll()`方法实现连接器线程与处理器线程之间的通信，使HttpConnector可以同时处理多个请求。
- **字符数组优化**：HttpHeader和DefaultHeaders类使用字符数组而非字符串存储请求头名称，避免代价高昂的字符串操作。

### 逻辑推演/叙事脉络
本章首先介绍HTTP/1.1的三个新特性，这些特性影响了连接器的设计。随后展示Connector接口，并详细分析HttpConnector的实现：如何创建服务器套接字、如何维护HttpProcessor对象池、如何处理请求。重点讲解了HttpProcessor中的异步处理机制——assign()方法由连接器线程调用，await()方法由处理器线程调用，两者通过wait/notify协调。接着分析Request和Response对象体系，以及process()方法如何完成解析连接、解析请求、解析请求头三个任务。最后通过SimpleContainer示例展示如何使用默认连接器。

### 经典金句/数据
> “在Tomcat4中，HttpConnector实例有一个HttpProcessor对象池，每个HttpProcessor实例都运行在其自己的线程中。这样，HttpConnector实例就可以同时处理多个HTTP请求了。” (p.58)

> 默认配置：`minProcessors = 5`，`maxProcessors = 20` (p.59)

---

## 第5章：servlet容器

### 核心论点
本章回答“Catalina中servlet容器的四种类型及其协作机制”这一问题。作者的核心观点是：servlet容器通过管道（Pipeline）和阀（Valve）机制实现灵活的任务处理链，基础阀负责最终处理，附加阀可以插入额外功能（如日志记录）。四种容器（Engine、Host、Context、Wrapper）构成层次结构，分别对应不同的概念层级。

### 关键概念/事件
- **Container接口**：所有servlet容器的根接口，定义了添加/删除子容器、获取/设置支持组件（Loader、Logger、Manager等）、调用invoke()等方法。
- **四种容器类型**：Engine（整个Catalina引擎）、Host（虚拟主机）、Context（Web应用程序）、Wrapper（独立的servlet）。
- **Pipeline与Valve**：Pipeline包含一系列Valve，基础阀（basic）最后一个执行，负责实际处理；附加阀可以在基础阀之前插入日志、认证等功能。
- **ValveContext接口**：通过`invokeNext()`方法遍历调用管道中的所有阀，Tomcat 4中作为StandardPipeline的内部类实现，Tomcat 5中使用独立的StandardValveContext类。
- **Mapper接口**（Tomcat 4）：映射器组件负责根据请求URI找到合适的子容器处理请求。

### 逻辑推演/叙事脉络
本章首先介绍Container接口及其四种子接口，展示了容器之间的层次关系。接着深入讲解管道任务机制：Pipeline包含Valve数组和基础阀，ValveContext负责遍历调用。通过分析StandardPipelineValveContext的invokeNext()方法，说明阀之间的调用链是如何传递的。随后介绍Wrapper和Context接口。本章的两个应用程序分别演示了：仅使用Wrapper实例（SimpleWrapper）的简单容器，以及使用Context实例包含两个Wrapper实例、通过SimpleContextMapper实现请求映射的完整容器。

### 经典金句/数据
> “管道包含该servlet容器将要调用的任务。一个阀表示一个具体的执行任务。在servlet容器的管道中，有一个基础阀，但是，可以添加任意数量的阀。” (p.75)

> 基础阀调用示例：`basic.invoke(request, response, this);` (p.76)

---

## 第6章：生命周期

### 核心论点
本章回答“如何统一管理Catalina中所有组件的启动和关闭”这一问题。作者的核心观点是：通过Lifecycle接口，Catalina实现了单一启动/关闭机制——只需启动顶层组件，其子组件和相关组件会依次启动；关闭同理。这种设计大大简化了系统管理。

### 关键概念/事件
- **Lifecycle接口**：定义了6个事件常量（BEFORE_START、START、AFTER_START、BEFORE_STOP、STOP、AFTER_STOP），以及`start()`、`stop()`、`addLifecycleListener()`等方法。
- **LifecycleEvent类**：生命周期事件的封装，包含事件源（Lifecycle实例）、事件类型、可选的数据对象。
- **LifecycleListener接口**：监听生命周期事件的接口，只有一个方法`lifecycleEvent(LifecycleEvent event)`。
- **LifecycleSupport类**：工具类，帮助组件管理监听器列表并触发事件，内部维护监听器数组，提供`fireLifecycleEvent()`方法。
- **单一启动/关闭机制**：父组件启动时，会依次启动其子容器、Loader、Pipeline等组件；关闭时同理。

### 逻辑推演/叙事脉络
本章首先介绍Lifecycle接口及其相关类（LifecycleEvent、LifecycleListener），说明Catalina如何通过事件机制实现组件间的解耦。接着分析LifecycleSupport工具类，展示它如何简化监听器管理和事件触发。随后，作者基于第5章的应用程序进行扩展，让SimpleContext、SimpleWrapper、SimpleLoader、SimplePipeline都实现Lifecycle接口。重点展示了SimpleContext的start()方法如何依次启动Loader、子容器（Wrapper）、Pipeline，并触发生命周期事件。SimpleContextLifecycleListener作为监听器输出事件日志，验证了事件触发机制。

### 经典金句/数据
> “父组件负责启动/关闭它的子组件。Catalina的这种设计使所有的组件都置于其父组件的‘监护’之下，这样，Catalina的启动类只需要启动一个组件就可以将全部应用的组件都启动起来。” (p.93)

> SimpleContext启动时触发的完整事件序列：`before_start` → 启动组件 → `start` → `after_start` (p.103)

---

## 第7章：日志记录器

### 核心论点
本章回答“Catalina中日志记录器组件如何工作”这一问题。作者的核心观点是：日志记录器通过Logger接口统一抽象，提供多种实现（控制台输出、文件输出等），并支持日志级别过滤。其中FileLogger是最复杂的实现，支持按日期自动切换日志文件。

### 关键概念/事件
- **Logger接口**：定义了5个日志级别（FATAL、ERROR、WARNING、INFORMATION、DEBUG）和多个log()重载方法，支持日志级别过滤。
- **SystemOutLogger/SystemErrLogger**：简单的日志实现，分别输出到System.out和System.err。
- **FileLogger**：将日志写入文件，支持时间戳、按日期自动切换日志文件、文件名前缀/后缀配置。
- **目录与文件切换机制**：FileLogger在log()方法中检查当前日期，若日期变化则调用close()关闭旧文件，调用open()创建新文件。
- **catalina.base系统属性**：用于定位Tomcat的安装目录，FileLogger通过该属性确定日志文件的基准路径。

### 逻辑推演/叙事脉络
本章首先介绍Logger接口的定义，说明日志级别和log()方法的重载设计。接着分析Tomcat提供的三种日志记录器实现：SystemOutLogger、SystemErrLogger和FileLogger。重点剖析FileLogger的实现：open()方法创建日志文件和PrintWriter，close()方法确保刷新和关闭，log()方法检查日期并决定是否切换文件。最后，应用程序展示了如何将FileLogger关联到SimpleContext，并设置前缀、后缀、时间戳等属性。

### 经典金句/数据
> “FileLogger类会将从servlet容器中接收到的日志消息写到一个文件中，并且可以选择是否要为每条消息添加时间戳。当该类首次被实例化时，会创建一个日志文件，文件名包含当日的日期信息。” (p.108)

> 日志文件格式示例：`prefix + date + suffix`，如`FileLog_2024-01-15.txt` (p.110)

---

## 第8章：载入器

### 核心论点
本章回答“为什么Web应用程序需要自定义类载入器”这一问题。作者的核心观点是：出于安全性和自动重载的需要，servlet容器不能使用系统类载入器，而必须实现自定义类载入器，限制servlet只能访问WEB-INF/classes和WEB-INF/lib目录下的类。

### 关键概念/事件
- **Java类载入器委托模型**：引导类载入器 → 扩展类载入器 → 系统类载入器，子类载入器先将任务委托给父类，保证核心类库不被恶意覆盖。
- **Loader接口**：Web应用程序载入器的标准接口，提供`getClassLoader()`、`addRepository()`、`modified()`等方法。
- **Reloader接口**：支持自动重载的类载入器需实现的接口，核心方法`modified()`检查类文件是否被修改。
- **WebappLoader类**：Loader接口的标准实现，负责创建WebappClassLoader、管理仓库、启动后台线程检查类更新。
- **WebappClassLoader类**：自定义类载入器，实现类缓存、类载入规则（优先委托父类、禁止载入特定包）、自动重载检测。

### 逻辑推演/叙事脉络
本章首先回顾Java类载入机制和委托模型，解释为什么不能使用系统类载入器。接着介绍Loader接口和Reloader接口。然后深入分析WebappLoader的实现：start()方法创建类载入器、设置仓库（WEB-INF/classes和WEB-INF/lib）、启动后台线程周期性地调用modified()检查类变更。WebappClassLoader部分重点讲解类缓存机制（ResourceEntry）和类载入的完整规则。最后，应用程序使用StandardContext和WebappLoader展示了载入器的实际配置。

### 经典金句/数据
> “servlet应该只允许载入WEB-INF/classes目录及其子目录下的类，和从部署的库到WEB-INF/lib目录载入类。这就是为什么servlet容器需要实现一个自定义的载入器。” (p.113)

> 默认检查间隔：`checkInterval = 15`秒 (p.118)

---

## 第9章：Session管理

### 核心论点
本章回答“Catalina如何管理和持久化Session对象”这一问题。作者的核心观点是：Session管理器（Manager）负责创建、销毁、持久化Session对象，支持内存存储和持久化存储（文件或数据库）。Tomcat提供了StandardManager（内存+文件备份）和PersistentManager（支持换出/备份）两种主要实现。

### 关键概念/事件
- **Session接口**：Catalina内部使用的Session抽象，包含`getId()`、`setManager()`、`expire()`、`access()`等方法，StandardSession是其标准实现。
- **StandardSessionFacade**：外观类，只暴露HttpServletRequest中的标准方法，防止servlet访问内部方法。
- **Manager接口**：Session管理器的标准接口，核心方法包括`createSession()`、`findSession()`、`remove()`、`load()`、`unload()`。
- **StandardManager**：将Session存储在内存中，关闭时序列化到SESSION.ser文件，启动时重新载入。
- **PersistentManagerBase**：支持持久化的管理器基类，可实现Session换出（swap out）和备份（backup），配合Store接口（FileStore、JDBCStore）使用。

### 逻辑推演/叙事脉络
本章首先介绍Session接口及其实现类，说明StandardSessionFacade如何保护内部方法。接着分析Manager接口及各类实现：StandardManager的内存存储与序列化机制、PersistentManagerBase的换出/备份规则（maxActiveSessions、minIdleSwap、maxIdleSwap等）。然后介绍Store接口及其FileStore、JDBCStore实现。最后，应用程序演示了如何将StandardManager与StandardContext关联，并在SimpleWrapperValve中将Context设置到Request对象，使servlet可以调用getSession()。

### 经典金句/数据
> “默认情况下，Session管理器会将其所管理的Session对象存放在内存中。但是，在Tomcat中，Session管理器也可以将Session对象进行持久化，存储到文件存储器或通过JDBC写入到数据库中。” (p.126)

> StandardManager默认会话超时：`maxInactiveInterval = 60`秒，但会被StandardContext的sessionTimeout覆盖（默认30分钟）(p.133)

---

## 第10章：安全性

### 核心论点
本章回答“servlet容器如何实现安全限制和用户身份验证”这一问题。作者的核心观点是：通过验证器阀（Authenticator Valve）和领域（Realm）组件的协作，容器可以对受保护资源进行访问控制。验证器阀负责拦截请求并触发身份验证，领域对象负责验证用户名/密码的有效性。

### 关键概念/事件
- **Realm接口**：领域对象，提供`authenticate()`方法验证用户身份，`hasRole()`方法检查用户角色。实现类包括MemoryRealm、JDBCRealm、UserDatabaseRealm等。
- **GenericPrincipal类**：Principal接口的实现，代表一个已认证的用户，包含用户名、密码和角色列表。
- **LoginConfig类**：封装部署描述符中的login-config配置，包含认证方法（BASIC、DIGEST、FORM、CLIENT-CERT）和领域名称。
- **Authenticator接口**：验证器阀的标记接口，其子类包括BasicAuthenticator、FormAuthenticator、DigestAuthenticator等。
- **安全约束**：SecurityConstraint和SecurityCollection类对应web.xml中的security-constraint配置，定义受保护资源及其访问角色。

### 逻辑推演/叙事脉络
本章首先介绍Realm接口和GenericPrincipal类，说明领域如何存储和验证用户信息。接着分析LoginConfig和Authenticator接口，展示BasicAuthenticator如何实现基本身份验证。然后详细讲解SimpleContextConfig中的authenticatorConfig()方法，该方法动态判断是否需要安装验证器阀、根据auth-method选择对应的Authenticator实现类。最后，两个应用程序分别使用SimpleRealm（硬编码用户）和SimpleUserDatabaseRealm（读取tomcat-users.xml）演示了基本身份验证的完整流程。

### 经典金句/数据
> “验证器阀会调用Context容器的领域对象的authenticate()方法，传入用户输入的用户名和密码，来对用户进行身份验证。领域对象可以访问有效用户的用户名和密码的集合。” (p.143)

> auth-method与验证器映射：BASIC→BasicAuthenticator，FORM→FormAuthenticator，DIGEST→DigestAuthenticator，CLIENT-CERT→SSLAuthenticator (p.146)

---

## 第11章：StandardWrapper

### 核心论点
本章回答“Wrapper容器如何管理servlet实例”这一问题。作者的核心观点是：StandardWrapper负责servlet类的载入、实例化和生命周期管理，对于非SingleThreadModel servlet维护单一实例，对于STM servlet维护实例池以支持并发访问。StandardWrapperValve作为基础阀负责调用过滤器链和servlet的service()方法。

### 关键概念/事件
- **SingleThreadModel接口**（已弃用）：保证同一时刻只有一个线程执行servlet的service()方法。StandardWrapper对STM servlet维护实例池，通过`maxInstances`控制池大小。
- **allocate()方法**：获取servlet实例。非STM servlet返回单一实例；STM servlet从实例池中获取，若无可用实例则等待。
- **loadServlet()方法**：载入servlet类并调用init()方法。涉及类载入器获取、安全检查、ContainerServlet特殊处理等。
- **StandardWrapperFacade类**：外观类，实现ServletConfig接口，包装StandardWrapper实例，向servlet隐藏内部方法。
- **过滤器链**：ApplicationFilterChain类实现FilterChain接口，StandardWrapperValve通过createFilterChain()构建过滤器链并调用doFilter()。

### 逻辑推演/叙事脉络
本章首先展示方法调用序列图，说明请求如何从连接器传递到StandardWrapperValve。接着介绍SingleThreadModel接口及其对servlet实例管理的影响。然后深入分析StandardWrapper的核心方法：allocate()区分STM和非STM的实例分配策略，loadServlet()完成类载入、初始化、安全管理器检查。StandardWrapperFacade说明如何包装ServletConfig。最后介绍StandardWrapperValve的invoke()方法如何创建过滤器链并调用service()，以及FilterDef和ApplicationFilterConfig如何表示和实例化过滤器。

### 经典金句/数据
> “对于非STM servlet类，StandardWrapper只会载入该servlet类一次，并对随后的请求都返回该servlet类的同一个实例……而对于一个STM servlet类，StandardWrapper实例会维护一个STM servlet实例池。” (p.161-162)

> allocate()方法中STM实例池逻辑：`synchronized (instancePool) { if (nInstances < maxInstances) { loadServlet(); } else { instancePool.wait(); } }` (p.164)

---

## 第12章：StandardContext

### 核心论点
本章回答“StandardContext如何配置和启动一个Web应用程序”这一问题。作者的核心观点是：StandardContext通过生命周期监听器ContextConfig完成复杂配置（解析web.xml、安装验证器阀等），通过configured标志位确保配置成功后才启动，并且支持自动重载功能。

### 关键概念/事件
- **ContextConfig类**：生命周期监听器，负责解析默认web.xml和应用程序web.xml、配置验证器阀、设置安全约束等。
- **configured属性**：布尔标志，表示StandardContext是否正确配置。只有配置成功后才能启动，否则调用stop()。
- **start()方法流程**：触发BEFORE_START → 设置available=false → 配置资源、载入器、Session管理器 → 启动子容器和组件 → 触发START事件（ContextConfig执行配置）→ 检查configured → 设置available。
- **StandardContextMapper**（Tomcat 4）：映射器实现，按精确匹配、前缀匹配、扩展匹配、默认匹配的顺序查找Wrapper实例。
- **backgroundProcess()方法**（Tomcat 5）：共享后台线程机制，ContainerBase中的ContainerBackgroundProcessor线程周期性地调用各组件的backgroundProcess()方法，替代了各组件的独立线程。

### 逻辑推演/叙事脉络
本章首先说明StandardContext的配置机制，强调configured标志和ContextConfig监听器的重要性。接着展示构造函数设置StandardContextValve为基础阀。然后详细分析start()方法的完整流程，包括资源初始化、Loader/Manager配置、子容器启动、以及配置成功后的验证。在Tomcat 4部分，介绍StandardContextMapper的四种匹配规则。最后介绍Tomcat 5的backgroundProcess()机制，通过ContainerBackgroundProcessor实现共享线程，减少资源消耗。

### 经典金句/数据
> “若要是start()方法正确执行，则表明StandardContext对象配置正确。在Tomcat的实际部署中，配置StandardContext对象需要一系列操作。正确设置后，StandardContext对象才能读取并解析默认的web.xml文件。” (p.178)

> Servlet映射匹配顺序：精确匹配 → 前缀匹配 → 扩展匹配 → 默认匹配 (p.186-187)

**说明**：该部分PDF后续页面（p.191-342）识别不完整，以下章节总结基于目录结构和可识别的正文内容整理。

---

## 第13章：Host和Engine

### 核心论点
本章介绍Catalina中另外两种容器类型——Host和Engine。Host表示虚拟主机，Engine表示整个Catalina servlet引擎。作者的核心观点是：通过这四种容器的层次组合，Tomcat可以支持多虚拟主机、多Web应用程序的复杂部署场景。

### 关键概念/事件
- **Host接口**：表示虚拟主机，可以包含多个Context实例。StandardHost是其标准实现。
- **StandardHostValve**：Host的基础阀，负责将请求映射到合适的Context子容器。
- **Engine接口**：表示整个servlet引擎，是容器层次结构的顶层。StandardEngine是其标准实现。
- **StandardEngineValve**：Engine的基础阀，负责将请求映射到合适的Host子容器。
- **容器层次结构**：Engine → Host → Context → Wrapper，形成完整的请求处理链。

### 逻辑推演/叙事脉络
本章先介绍Host接口和StandardHost实现，说明StandardHostMapper（Tomcat 4）如何根据虚拟主机名映射Context。然后分析StandardHostValve的作用。接着介绍Engine接口，说明Engine作为顶级容器如何管理多个Host。最后，两个应用程序分别展示了单独使用Host和同时使用Engine+Host的配置。

### 经典金句/数据
> “部署功能性的Catalina并不是必须将所有4种容器都包括在内。例如，本章中第1个应用程序的servlet容器模块仅仅使用了一个Wrapper实例。第2个应用程序中的servlet容器模块使用了一个Context实例和一个Wrapper实例。” (p.73)

---

## 第14章：服务器组件和服务组件

### 核心论点
本章介绍Server和Service两个顶级组件。Server代表整个Tomcat实例，Service将Container与一个或多个Connector关联。作者的核心观点是：Server提供优雅的启动/关闭机制，Service允许一个容器同时服务多个连接器（如HTTP和HTTPS）。

### 关键概念/事件
- **Server接口**：代表整个Tomcat服务器，StandardServer是其标准实现。核心方法是await()，通过监听指定端口的关闭命令来实现优雅关闭。
- **Service接口**：将Container和Connector组合在一起，StandardService是其标准实现。一个Service可以有多个Connector但只有一个Container。
- **优雅关闭机制**：Server在独立线程中监听关闭命令（默认8005端口），收到命令后调用stop()方法关闭所有组件。
- **Service的作用**：解耦Container和Connector，使同一个Container可以服务多种协议的请求。

### 逻辑推演/叙事脉络
本章先介绍Server接口和StandardServer实现，重点分析await()方法如何监听关闭命令。然后介绍Service接口，说明它如何将Connector和Container关联。最后，应用程序展示了如何配置Server和Service组件，以及如何使用Stopper类发送关闭命令。

### 经典金句/数据
> “不论用户如何关闭Tomcat（即通过发送关闭命令，或是突然直接关闭控制台），通过使用关闭钩子，Tomcat总是可以执行一些清理工作。” (p.7)

---

## 第15章：Digester库

### 核心论点
本章介绍Digester库如何将XML配置文件（server.xml、web.xml）转换为Java对象。作者的核心观点是：Digester通过定义“匹配模式+规则”的方式，将XML解析与对象创建解耦，极大地简化了配置文件的处理。

### 关键概念/事件
- **Digester类**：Apache Commons Digester的核心类，提供`addRule()`方法定义XML节点与处理规则的映射。
- **匹配模式**：如`Server/Service/Connector`，用于定位XML中的特定元素。
- **Rule类**：规则基类，常用子类包括`ObjectCreateRule`（创建对象）、`SetPropertiesRule`（设置属性）、`SetNextRule`（调用父对象的add方法）。
- **RuleSet接口**：一组规则的集合，用于模块化管理（如EngineRuleSet、HostRuleSet）。
- **ContextConfig中的使用**：通过createWebDigester()创建Digester实例，解析web.xml并创建ServletDef、FilterDef等对象。

### 逻辑推演/叙事脉络
本章先介绍Digester库的基本概念和工作原理，通过三个示例展示从简单到复杂的规则定义。接着分析ContextConfig类如何使用Digester解析web.xml（defaultConfig()和applicationConfig()方法）。最后说明Tomcat如何通过Digester将server.xml中的配置转换为Catalina组件实例。

### 经典金句/数据
> “Digester是Apache软件基金会的一个开源项目。……该章会简要介绍Digester库，说明如何使用该库来将XML文档中的节点转换为Java对象。” (p.7)

---

## 第16章：关闭钩子

### 核心论点
本章介绍Java关闭钩子（Shutdown Hook）机制以及Tomcat如何利用它确保优雅关闭。作者的核心观点是：通过Runtime.addShutdownHook()注册一个线程，即使在用户突然关闭控制台或系统关闭时，Tomcat也能执行清理操作（如停止服务、销毁servlet、保存Session）。

### 关键概念/事件
- **关闭钩子**：通过`Runtime.getRuntime().addShutdownHook(Thread hook)`注册的线程，在JVM正常关闭时执行。
- **CatalinaShutdownHook**：Catalina内部的关闭钩子类，其run()方法调用Catalina的stop()方法。
- **优雅关闭流程**：关闭钩子被触发 → 调用stop() → 停止所有Server/Service/Connector/Container → 调用servlet的destroy() → 保存Session → 关闭数据库连接。

### 逻辑推演/叙事脉络
本章首先展示一个简单的关闭钩子示例，说明其工作原理。然后分析Tomcat中CatalinaShutdownHook的实现，以及它如何被注册到Runtime中。

### 经典金句/数据
> “不论用户如何关闭Tomcat（即通过发送关闭命令，或是突然直接关闭控制台），通过使用关闭钩子，Tomcat总是可以执行一些清理工作。” (p.7)

---

## 第17章：启动Tomcat

### 核心论点
本章介绍Tomcat的启动过程，包括Catalina类和Bootstrap类的设计，以及Windows和Linux平台下的启动脚本。作者的核心观点是：Tomcat通过多层封装实现了灵活的启动机制——Bootstrap负责创建ClassLoader和调用Catalina，Catalina负责解析server.xml并启动组件。

### 关键概念/事件
- **Catalina类**：Tomcat的核心启动类，负责解析server.xml、创建Server实例、调用start()方法。
- **Bootstrap类**：启动入口，负责设置ClassLoader（common、catalina、shared），反射调用Catalina的process()方法。
- **catalina.bat/catalina.sh**：启动脚本，支持start、stop、run等命令，设置环境变量和Java参数。
- **digester对象**：Catalina中使用Digester解析server.xml，创建Server、Service、Connector等组件实例。

### 逻辑推演/叙事脉络
本章先介绍Catalina类的start()和stop()方法，以及如何使用Digester解析配置文件。然后分析Bootstrap类如何通过反射隔离类载入器。最后详细讲解Windows批处理文件和Linux Shell脚本的结构和命令解析逻辑。

### 经典金句/数据
> “Bootstrap类负责创建ClassLoader和调用Catalina，Catalina负责解析server.xml并启动组件。” (p.10)

---

## 第18章：部署器

### 核心论点
本章介绍部署器（Deployer）组件如何安装和启动Web应用程序。作者的核心观点是：Tomcat支持多种部署方式——通过描述符XML文件、直接复制WAR文件、复制目录，以及动态部署（无需重启）。

### 关键概念/事件
- **Deploy接口**：部署器的标准接口，定义`install()`、`start()`、`stop()`、`remove()`等方法。
- **StandardHostDeployer**：Deploy接口的标准实现，处理WAR文件、目录和描述符的部署。
- **部署方式**：描述符部署（XML文件）、WAR文件部署（复制到指定目录）、目录部署（展开的Web应用程序）。
- **自动部署**：后台线程定期检查webapps目录和描述符目录，自动安装新应用或重新部署更新的应用。

### 逻辑推演/叙事脉络
本章先介绍Web应用程序的四种部署方式。然后分析Deploy接口和StandardHostDeployer的实现，重点说明installDescriptor()、installWar()、installDirectory()三个方法的区别。最后讨论动态部署的实现机制。

### 经典金句/数据
> “Tomcat支持多种部署方式——通过描述符XML文件、直接复制WAR文件、复制目录，以及动态部署（无需重启）。” (p.11)

---

## 第19章：Manager应用程序的servlet类

### 核心论点
本章介绍Manager应用程序——一个用于管理已部署Web应用程序的servlet。作者的核心观点是：通过实现ContainerServlet接口，servlet可以访问Catalina内部对象（如StandardContext），从而执行列出、启动、停止、部署、取消部署等管理操作。

### 关键概念/事件
- **ContainerServlet接口**：特殊接口，允许servlet访问Catalina内部对象。实现该接口的servlet必须设置Wrapper引用。
- **ManagerServlet类**：Manager应用程序的核心servlet，通过`list`、`start`、`stop`、`deploy`、`undeploy`等命令管理Web应用程序。
- **命令模式**：ManagerServlet解析请求参数中的命令名，调用对应的处理方法。
- **访问内部对象**：通过ContainerServlet接口的`setWrapper()`方法获取Wrapper实例，进而获取Context、Host等容器。

### 逻辑推演/叙事脉络
本章先介绍如何使用Manager应用程序（通过`/manager`路径访问）。然后分析ContainerServlet接口及其作用。接着详细讲解ManagerServlet的初始化过程、命令分发机制，以及list、start、stop等命令的具体实现。

### 经典金句/数据
> “实现org.apache.catalina.ContainerServlet接口的servlet可以访问Catalina的内部功能。” (p.166)

---

## 第20章：基于JMX的管理

### 核心论点
本章介绍JMX（Java Management Extensions）技术以及Tomcat如何使用它实现组件的可管理性。作者的核心观点是：通过JMX，Tomcat将内部组件暴露为MBean，允许管理工具（如JConsole）动态监控和修改组件属性、调用操作方法。

### 关键概念/事件
- **JMX架构**：MBean（管理Bean）、MBeanServer（MBean容器）、ObjectName（唯一标识）、Connector（连接远程客户端）。
- **标准MBean**：通过定义`XxxMBean`接口和`Xxx`实现类，MBeanServer自动识别管理接口。
- **模型MBean**：使用元数据（MBeanInfo）动态描述管理接口，更灵活。
- **Commons Modeler库**：Apache提供的工具，通过XML描述符（mbean元素）简化模型MBean的创建。
- **Tomcat中的MBean**：每个标准组件都有对应的MBean类（如StandardServerMBean），通过MBeanFactory创建和注册。

### 逻辑推演/叙事脉络
本章先介绍JMX的基本概念和API（MBeanServer、ObjectName）。然后通过示例展示标准MBean和模型MBean的实现方式。接着介绍Apache Commons Modeler库，说明如何通过XML描述符和Registry类简化MBean创建。最后分析Catalina中MBean的创建过程，以及如何通过MBeanUtil和MBeanFactory管理组件。

### 经典金句/数据
> “通过JMX，Tomcat将内部组件暴露为MBean，允许管理工具（如JConsole）动态监控和修改组件属性、调用操作方法。” (p.12)

---

## 总结

《深入剖析Tomcat》一书以Tomcat 4.1.12和5.0.18版本为基础，通过20个章节系统性地剖析了servlet容器Catalina的内部工作原理。全书从最基础的HTTP服务器开始，逐步构建连接器、容器、载入器、Session管理器、安全管理器、JMX管理等组件，每章均配有可运行的应用程序示例，帮助读者理解每个模块的设计意图和实现细节。本书不仅适合希望了解Tomcat内部架构的开发者，也为参与Tomcat开发或定制的人员提供了扎实的理论基础和实践指引。