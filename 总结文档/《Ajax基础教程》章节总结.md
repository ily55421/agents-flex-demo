# 《Ajax基础教程》章节总结

## 书籍信息

- 书名：Ajax基础教程
- 作者：[美] Ryan Asleson, Nathaniel T. Schutta 著；金灵 等译
- 出版社：人民邮电出版社
- 出版年份：2006
- PDF状态：扫描版，文本层存在但中英文混杂
- OCR状态：可识别，部分页面有乱码

## 目录说明

- 目录识别情况：完整（第1章至第8章+附录）
- 章节对应依据：按原书顺序
- OCR修复说明：部分代码块有错位

## 全书核心主题

本书是Ajax入门教程，从基础概念入手，详细讲解XMLHttpRequest对象的使用、与服务器通信的方式（GET/POST、XML、JSON）、常见Ajax技术（验证、动态列表、自动刷新、进度条、工具提示、自动完成）以及开发工具（JSDoc、HTML Validator、DOM Inspector、JSLint、压缩工具）和测试框架（JsUnit）。最后介绍调试工具和综合应用示例。

---

## 第1章：Ajax简介

### 核心论点

本章回顾Web应用发展历史，介绍Ajax概念、相关技术、适用场合和设计考虑。作者认为Ajax可以突破传统同步请求/响应模式的限制。

### 关键概念/事件

- **Web简史**：从ARPANET到浏览器大战。
- **传统技术局限**：CGI、applet、JavaScript、servlet、Flash、DHTML、XML衍生语言。
- **Ajax核心**：XMLHttpRequest对象实现异步通信。
- **可用性问题**：后退按钮、书签、用户期望、过度使用。
- **设计考虑**：简单、不唐突、逐步增强。

### 逻辑推演/叙事脉络

从因特网起源讲到浏览器发展，再到动态Web技术的演进。指出传统技术都无法完美解决页面刷新问题。引出Ajax作为解决方案，列举Google Maps、Gmail等案例。最后给出使用Ajax的时机和设计原则。

### 经典金句/数据

> “因特网以请求/响应模式作为基础，由此带来的同步性对你造成了妨碍。” (p.13)

---

## 第2章：使用XMLHttpRequest对象

### 核心论点

本章详细介绍XMLHttpRequest对象的创建、方法和属性，并通过简单示例演示异步请求的完整流程。

### 关键概念/事件

- **创建XHR**：跨浏览器方式（ActiveXObject vs XMLHttpRequest）。
- **方法和属性**：open、send、setRequestHeader、onreadystatechange、readyState、status、responseText、responseXML。
- **GET vs POST**：幂等性、数据量、Content-Type设置。
- **远程脚本**：使用IFRAME模拟Ajax的历史方法。
- **安全**：同源策略限制。

### 逻辑推演/叙事脉络

先给出跨浏览器创建XHR的代码。然后列出所有方法和属性。通过一个简单示例（点击按钮获取静态文本）展示完整交互。比较GET和POST的使用场景。最后讨论安全沙箱。

### 经典金句/数据

> “函数指针与任何其他变量类似，只不过它指向的不是数据，而是指向一个函数。” (p.32)

---

## 第3章：与服务器通信：发送请求和处理响应

### 核心论点

本章深入讲解如何使用XHR发送请求参数（GET/POST、XML、JSON）和处理服务器响应（responseText、responseXML、innerHTML、DOM操作）。

### 关键概念/事件

- **处理响应**：使用innerHTML动态插入HTML片段；使用responseXML解析XML文档。
- **DOM动态编辑**：createElement、appendChild、removeChild等。
- **发送参数**：GET将参数拼接到URL；POST设置Content-Type后send参数。
- **XML作为请求体**：通过串拼接或DOM创建XML，发送到服务器。
- **JSON**：轻量级数据格式，使用eval反序列化。

### 逻辑推演/叙事脉络

通过多个示例演示：使用innerHTML创建搜索结果表；使用DOM解析XML并动态生成表格；分别用GET和POST发送表单数据；发送XML数据到服务器；使用JSON发送和接收对象。

### 经典金句/数据

> “使用responseText和innerHTML可以大大简化页面增加动态内容的工作。” (p.40)

---

## 第4章：实现基本Ajax技术

### 核心论点

本章通过实际案例展示Ajax技术的典型应用场景：表单验证、读取响应首部、动态加载列表框、自动刷新页面、进度条、工具提示、动态更新Web页面、访问Web服务、自动完成。

### 关键概念/事件

- **验证**：实时检查日期有效性。
- **读取响应首部**：使用HEAD请求获取Last-Modified、判断资源是否存在。
- **动态列表框**：根据车型年份和品牌异步加载车型列表。
- **自动刷新**：使用setTimeout轮询服务器获取新消息。
- **进度条**：轮询服务器获取任务完成百分比。
- **工具提示**：鼠标悬停时异步获取详细信息。
- **动态更新**：添加/删除员工信息，只更新表格行。
- **Web服务**：通过代理调用Yahoo! Search REST API。
- **自动完成**：类似Google Suggest，输入时提示匹配项。

### 逻辑推演/叙事脉络

每个案例都给出完整代码（HTML+JavaScript+服务器端示例），并展示运行效果。重点强调如何通过Ajax改善用户体验。

### 经典金句/数据

> “在Web应用中，应该先从简单的验证开始使用Ajax，再逐步深入。” (p.72)

---

## 第5章：构建完备的Ajax开发工具箱

### 核心论点

本章介绍辅助Ajax开发的工具：JSDoc（文档生成）、HTML Validator/Checky（HTML验证）、DOM Inspector（节点检查）、JSLint（语法检查）、JavaScript压缩/模糊工具、Firefox Web开发扩展，以及高级JavaScript技术（prototype继承、私有属性、类式继承）。

### 关键概念/事件

- **JSDoc**：类似javadoc，为JavaScript生成API文档。
- **HTML Validator**：Firefox扩展，基于Tidy验证HTML。
- **DOM Inspector**：查看和动态修改DOM树。
- **JSLint**：检查JavaScript代码质量和潜在错误。
- **压缩/模糊**：减少文件大小、保护源代码。
- **Web Developer扩展**：Firefox工具集，禁用缓存、查看CSS等。
- **高级JavaScript**：prototype链、私有变量（闭包）、模拟类式继承。

### 逻辑推演/叙事脉络

按工具类别逐一介绍安装和使用方法，给出截图示例。最后通过Vehicle/SportsCar示例演示prototype继承和类式继承的区别。

### 经典金句/数据

> “JSLint会把你认为有风险的编码实践加标志，促使你养成好的JavaScript编码习惯。” (p.137)

---

## 第6章：使用JsUnit测试JavaScript代码

### 核心论点

本章介绍测试驱动开发（TDD）和JsUnit框架，讲解如何编写和运行JavaScript单元测试。

### 关键概念/事件

- **TDD优势**：明确目标、自动化回归测试、提供文档、改善设计。
- **JsUnit**：JUnit的JavaScript移植，包含断言、setUp/tearDown、测试套件。
- **编写测试**：测试函数以test开头，使用assertEquals等断言。
- **运行测试**：testRunner.html，支持跟踪（warn/inform/debug）。
- **JsUnit服务器**：在持续集成中自动运行测试。

### 逻辑推演/叙事脉络

先介绍TDD理念和JUnit。然后详细讲解JsUnit的安装、测试页结构、断言方法、setUp/tearDown/setUpPage、测试套件。最后演示如何运行测试和解读结果。

### 经典金句/数据

> “单元测试就像原开发人员留下的记号，可以展示他们的类具体是怎么工作的。” (p.156)

---

## 第7章：分析JavaScript调试工具和技术

### 核心论点

本章介绍调试Ajax应用的常用工具：Greasemonkey（拦截XHR）、Firefox JavaScript Console、Microsoft Script Debugger、Venkman（Mozilla调试器）。

### 关键概念/事件

- **Greasemonkey**：用户脚本，可以拦截和记录所有XMLHttpRequest请求和响应。
- **Firebug**：虽然不是本章重点，但后续版本已包含。
- **Venkman**：Mozilla JavaScript调试器，支持断点、单步执行、变量检查。
- **Microsoft Script Debugger**：IE的调试工具，需启用脚本调试。
- **debugger关键字**：在代码中设置断点。

### 逻辑推演/叙事脉络

依次介绍每个调试工具的安装和基本使用方法，特别强调Greasemonkey的XHR调试脚本和Venkman的断点功能。

### 经典金句/数据

> “Venkman是Mozilla的JavaScript调试器，能够帮助处理基本语法检查，设置断点，检查变量的上下文。” (p.192)

---

## 第8章：万事俱备

### 核心论点

本章总结Ajax开发模式（褪色技术、自动刷新、部分页面绘制、可拖放DOM），避免常见陷阱，提供更多资源，并通过一个完整的Dashboard示例（使用Taconite框架）展示综合应用。

### 关键概念/事件

- **褪色技术**：黄色高亮变化区域，逐渐恢复背景色。
- **自动刷新**：定时轮询服务器。
- **部分页面绘制**：只更新变化部分。
- **可拖放DOM**：使用script.aculo.us实现。
- **常见陷阱**：DOM操作性能、内存泄漏、缺乏用户反馈。
- **Taconite**：服务器端框架，返回包含多条命令的XML，客户端顺序执行。
- **Dashboard应用**：包含天气预报组件、新闻组件，自动刷新。

### 逻辑推演/叙事脉络

先总结常见模式，然后列出陷阱和资源。重点介绍Taconite框架的工作原理，并构建一个完整的Dashboard示例，展示如何将多个异步组件整合到一个页面中。

### 经典金句/数据

> “使用Taconite，服务器可以返回一个XML文档，其中包含多个更新命令，客户端依次执行，大大简化了复杂页面的Ajax开发。” (p.217)

---

# 附录总结

## 附录A：开发跨浏览器JavaScript

- 向表中追加行、设置样式、设置class属性、创建输入元素、添加事件处理程序、创建单选钮的跨浏览器方法。

## 附录B：Ajax框架介绍

- 浏览器端框架：Dojo、Rico、qooxdoo、TIBET、Flash/JavaScript集成包、Google AJAXSLT、libXmlRequest、RSLite、SACK、sarisa、XHConn。
- 服务器端框架：CPAINT、Sajax、JSON/JSON-RPC、DWR、SWATO、Java BluePrints、Ajax.Net、Microsoft Atlas、Ruby on Rails。













