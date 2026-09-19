根据提供的《网站建设：（JSP+MySQL）组建动态网站》教材内容，以下是所有章节内容的详细分析与总结，已整理为 Markdown 文档格式。

---

# 《网站建设：（JSP+MySQL）组建动态网站》课程内容总结

## 第一部分：课程基础与环境搭建

### 第一章 JSP概述
*   **核心概念**：介绍了JSP（JavaServer Pages）的技术原理，即基于Java Servlet的动态网页技术。
*   **技术对比**：分析了JSP与其他动态网页技术（如ASP、PHP、CGI）的区别，强调了JSP在跨平台性、安全性及组件复用（JavaBean/Servlet）方面的优势。

### 第二章 安装、配置 JSP系统环境
*   **JDK安装**：讲解了Java Development Kit的安装及环境变量（JAVA_HOME, PATH, CLASSPATH）的配置。
*   **Tomcat服务器**：介绍了Apache Tomcat Web服务器的下载、安装、目录结构及启动/停止方法。
*   **开发工具**：以NetBeans IDE为例，演示了集成开发环境的安装与项目创建流程。
*   **Hello World**：创建了第一个JSP页面，验证环境配置是否成功。

### 第三章 JSP页面的基本结构
*   **注释**：区分了HTML注释（客户端可见）和JSP注释（`<%-- --%>`，服务端隐藏）。
*   **脚本元素**：
    *   **声明 (`<%! %>`)**：用于定义成员变量和方法。
    *   **表达式 (`<%= %>`)**：用于输出变量或表达式的值。
    *   **脚本片段 (`<% %>`)**：包含Java逻辑代码，最终转化为Servlet的`_jspService`方法内容。

### 第四章 JSP的编译指令
*   **page指令**：设置页面属性，如`contentType`（编码格式）、`import`（导入包）、`errorPage`等。
*   **include指令**：静态包含其他文件（`<%@ include file="..." %>`），在编译阶段合并文件内容。

### 第五章 JSP的动作指令
*   **`<jsp:include>`**：动态包含，在请求处理阶段包含目标资源，适合包含动态内容。
*   **`<jsp:forward>`**：请求转发，将当前请求转发到另一个JSP或Servlet。
*   **`<jsp:param>`**：配合include或forward使用，传递参数。
*   **`<jsp:useBean>` / `<jsp:setProperty>` / `<jsp:getProperty>`**：用于实例化JavaBean并操作其属性。

### 第六章 JSP的内置对象
*   **request**：封装客户端请求信息，常用方法`getParameter()`获取表单数据。
*   **response**：封装服务器响应，常用方法`sendRedirect()`进行重定向。
*   **session**：会话跟踪，用于在多个请求间保存用户状态（如登录信息）。
*   **application**：应用上下文，所有用户共享的全局变量。
*   **out**：向客户端输出内容。
*   **其他**：config, pageContext, page, exception。

---

## 第二部分：后端技术与架构

### 第七章 数据库与 JDBC技术
*   **MySQL基础**：数据库安装、SQL语句基础（INSERT, SELECT, UPDATE, DELETE）。
*   **JDBC编程步骤**:
    1.  加载驱动 (`Class.forName`)。
    2.  建立连接 (`DriverManager.getConnection`)。
    3.  创建Statement对象。
    4.  执行SQL (`executeQuery`查询, `executeUpdate`增删改)。
    5.  处理结果集 (`ResultSet`)。
    6.  关闭资源 (Connection, Statement, ResultSet)。
*   **DBHandle封装**：演示了如何编写一个通用的数据库操作类，简化JDBC调用。

### 第八章 JavaBean技术
*   **JavaBean规范**：公共类、无参构造器、私有属性、public getter/setter方法。
*   **作用域**：page, request, session, application。
*   **案例**：
    *   **计数器Bean**：利用`application`作用域实现网站访问计数。
    *   **猜数字游戏Bean**：封装游戏逻辑（生成随机数、判断大小、记录次数），实现业务逻辑与视图分离。

### 第九章 Servlet技术
*   **Servlet生命周期**：初始化 (`init`) -> 服务 (`service/doGet/doPost`) -> 销毁 (`destroy`)。
*   **MVC模式简介**：
    *   **Model**：JavaBean，处理业务逻辑和数据。
    *   **View**：JSP，负责页面展示。
    *   **Controller**：Servlet，接收请求，调用Model，选择View。
*   **Session管理**：在Servlet中通过`HttpServletRequest.getSession()`获取会话，实现用户状态保持。
*   **中文乱码处理**：演示了在GET/POST请求中处理字符编码转换（ISO8859-1转GBK/UTF-8）。
*   **请求转发与重定向**：`RequestDispatcher.forward()` vs `response.sendRedirect()`。

---

## 第三部分：综合案例 - 在线书店系统 (BookShop)

本章通过一个完整的电商网站案例，整合前述所有技术。

### 1. 项目准备
*   **数据库设计**：创建`bookshop`数据库，包含表：`admin`（管理员）、`users`（用户）、`book`（图书）、`order`（订单主表）、`orderlist`（订单详情）。
*   **静态资源导入**：导入HTML/CSS/图片模板。
*   **驱动配置**：添加MySQL JDBC驱动jar包至项目库。

### 2. 核心模块实现

#### A. 数据库操作层 (Util)
*   **`DBHandle.java`**：位于`edu.dufe.util`包。封装了获取连接、执行查询、执行更新、关闭资源的方法，作为所有数据访问的基础。

#### B. 用户模块 (User Module)
*   **JavaBeans**:
    *   `ShopUser.java`：用户实体类。
    *   `Login.java`：登录逻辑Bean，验证用户名密码，区分管理员与普通用户。
    *   `UserDB.java`：用户数据访问Bean，处理注册（Insert）、登录验证、信息修改（Update）、信息查询。
*   **JSP页面**:
    *   `login.jsp`：登录界面，提交至自身或Servlet，验证成功后跳转。
    *   `reg.jsp`：注册界面，调用`UserDB.insert`。
    *   `modimy.jsp`：个人信息修改界面。
    *   `userinfo.jsp`：用户中心，展示订单历史和个人信息。

#### C. 图书展示与购物模块 (Book & Shopping Module)
*   **JavaBeans**:
    *   `Book.java`：图书实体类。
    *   `BookDB.java`：图书数据访问Bean。提供获取图书列表（支持按类别、关键字搜索）、获取单本图书详情的方法。
    *   `Purchase.java`：购物车与订单逻辑Bean。
        *   `addnew`：添加商品到Session购物车。
        *   `modiShoper`：修改购物车数量。
        *   `delShoper`：删除购物车商品。
        *   `payout`：提交订单，生成订单号，写入`order`和`orderlist`表，扣减库存。
    *   `IndentList.java`：购物车单项实体（书ID，数量）。
    *   `Order.java`：订单实体。
*   **JSP页面**:
    *   `index.jsp`：首页，展示图书列表，提供搜索功能。
    *   `showbook.jsp`：图书详情页。
    *   `purchase.jsp`：购买确认页（弹窗），调用`Purchase.addnew`。
    *   `shoperlist.jsp`：购物车页面。展示Session中的购物车内容，支持修改、删除、清空、提交订单。
    *   `showorder.jsp`：订单详情页，查看特定订单包含的书籍。

#### D. 控制层 (Servlet - 可选/混合模式)
*   案例中主要采用 **JSP + JavaBean** 的模式（Model 2的变体），部分逻辑直接在JSP中调用Bean方法。
*   在第九章练习中展示了 **Servlet + JSP** 的MVC实现（如`LoginControlServlet`处理登录跳转），体现了控制器分离的思想。

### 3. 关键技术点总结
*   **会话管理**：大量使用`session`存储用户ID、用户名以及购物车向量（Vector）。
*   **数据持久化**：通过JDBC直接操作MySQL，注意SQL注入风险（案例中使用字符串拼接，实际生产应使用PreparedStatement）。
*   **字符编码**：统一使用GB2312/GBK或UTF-8，并在获取参数时进行转码处理。
*   **分层思想**：
    *   视图层：JSP页面。
    *   业务/数据层：JavaBean (`BookDB`, `UserDB`, `Purchase`)。
    *   工具层：`DBHandle`。

---

## 学习路线建议

1.  **基础阶段**：掌握JSP语法、内置对象、HTML表单交互。
2.  **进阶阶段**：深入理解JDBC数据库操作，学会封装DBUtils；掌握JavaBean规范，理解Scope作用域。
3.  **架构阶段**：学习Servlet生命周期，理解MVC设计模式，尝试将业务逻辑从JSP剥离到Servlet或Service层。
4.  **实战阶段**：通过“在线书店”案例，完整体验从需求分析、数据库设计、编码实现到测试部署的全过程。重点关注购物车逻辑（Session应用）和订单生成（事务一致性初步概念）。