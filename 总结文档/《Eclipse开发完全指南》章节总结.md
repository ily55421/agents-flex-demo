# 《Eclipse开发完全指南》章节总结

## 目录说明
本书内容由三份独立文档整合而成，依次为：
1. 《eclipse基本使用操作--一定要很熟悉.doc》——涵盖Eclipse快捷键、正则表达式、界面设置等基础操作
2. 《Eclipse编辑器基本设置.docx》——涵盖字体、代码风格、内容辅助、JDK配置、编码等高级设置
3. 《eclipse插件开发.doc》——涵盖插件开发入门、SWT/JFace、事件模型、常用扩展点、项目打包、J2EE环境搭建等

由于原文档未提供统一章节目录，本总结依据各文档内部标题顺序重新编排为10章，以保持内容的逻辑递进关系。

---

## 第1章：Eclipse常用快捷键与操作

### 核心论点
本章主要解决如何通过快捷键提升Eclipse开发效率的问题。作者核心观点是：熟练掌握常用快捷键（尤其是代码补全、注释、格式化、重构等）是提高编码效率的关键，开发者应优先记忆“最常用”的快捷键组合。

### 关键概念/事件
- **代码补全（Alt+/）**：当记不全类、方法、属性名称时，使用此快捷键可触发内容辅助，自动补全代码。
- **单行/多行注释（Ctrl+/、Ctrl+Shift+/）**：快速为当前行或选中块添加/取消注释，便于调试和代码管理。
- **代码修正（Ctrl+1）**：当代码出现红叉错误时，此快捷键提供快速修复方案，如导入类、转换局部变量等。
- **快速导入包（Ctrl+Shift+O）**：自动组织并导入当前文件所需的import语句，避免手动查找。
- **代码格式化（Ctrl+Shift+F）**：对选中代码或整个Java文件进行规范化排版，提升可读性。

### 逻辑推演/叙事脉络
本章首先列出最常用的快捷键（alt+/、ctrl+/、ctrl+d等），然后分门别类介绍“查看和定位快捷键”（Ctrl+K、Ctrl+Shift+T、Ctrl+L）、“调试快捷键”（F11、F5-F8）、“重构快捷键”（Alt+Shift+R重命名、Alt+Shift+M抽取方法）等。最后补充了“编辑器快捷键”和“其他快捷键”，并强调正则表达式作为字符串处理工具的重要性。整体从操作效率切入，逐步深入到调试、重构、正则等进阶主题。

### 经典金句/数据
> “Ctrl+1 快速修复 最经典的”  
> “Alt+/ 提供内容的帮助 记不全方法 类 属性 最常用”  
> “Ctrl+Shift+R 快速查找资源 免得左边找”

---

## 第2章：正则表达式基础

### 核心论点
本章旨在解释正则表达式的基本规则及其在字符串操作中的四种功能。作者核心观点是：正则表达式通过特定符号定义规则，可对字符串进行匹配、切割、替换和获取，是简化字符串处理的强大工具。

### 关键概念/事件
- **字符类**：如`[abc]`表示a或b或c，`[a-zA-Z]`表示所有大小写字母，`[^abc]`表示非a/b/c。
- **预定义字符**：`.`表示任意字符，`\d`表示数字，`\w`表示字母/数字/下划线，`\s`表示空白字符。
- **边界符**：`^`表示行开始，`$`表示行结束。
- **数量词**：`?`（0或1次）、`*`（0或多次）、`+`（1或多次）、`{n}`（恰好n次）、`{n,m}`（n到m次）。
- **正则四大功能**：匹配（验证字符串是否符合规则）、切割（按规则拆分字符串）、替换（替换匹配部分）、获取（提取匹配的子串）。

### 逻辑推演/叙事脉络
本章先给出正则的定义（简化String操作的规则集），然后依次介绍一般规则（x、\\、[abc]等）、预定义字符、边界符、数量词。最后总结正则表达式的四种功能（匹配、切割、替换、获取）并强调正则只属于字符串使用。结构清晰，从符号到功能逐层递进。

### 经典金句/数据
> “正则表达式中只有4种功能：匹配、切割、替换、获取”  
> “正则只属于字符串使用，而正则也是对字符串进行操作（匹配、切割、替换、获取）”

---

## 第3章：Eclipse界面简单设置

### 核心论点
本章解决如何将Eclipse界面调整为个人开发习惯的问题。核心观点是：通过重置视图、配置快捷键、调整代码字体和颜色、显示行号等设置，可以显著提升代码阅读和编写舒适度。

### 关键概念/事件
- **重置为默认视图**：通过“窗口→重置透视图”可恢复界面布局，避免混乱。
- **查看某一视图**：使用“窗口→显示视图→其他”可恢复或添加特定视图（如包资源管理器、大纲）。
- **代码模板**：在Window→Preferences→Java→Editor→Templates中可自定义或删除代码模板。
- **配置快捷键**：在Window→Preferences→General→Keys中可修改或添加快捷键。
- **显示代码行号**：在编辑器左侧边缘右键勾选“显示行号”，或通过Preferences→General→Editors→Text Editors设置。

### 逻辑推演/叙事脉络
本章按操作步骤依次介绍：重置视图→查看视图→代码模板设置→快捷键配置→字体颜色调整→行号显示→导入包/资源→查看源代码→修改文件名→其他小技巧（Ctrl+E切换编辑器、Tab/Shift+Tab缩进、Ctrl+Shift+F格式化等）。最后列出22条常用操作，形成完整的基础设置指南。

### 经典金句/数据
> “Ctrl+/ 单行注释 Ctrl+/ 取消单行注释 Ctrl+shift+/ 多行注释 Ctrl+shift+\ 取消多行注释”  
> “Alt+shift+s？？？”（提示可能为生成getter/setter的快捷键）

---

## 第4章：Eclipse编辑器高级设置

### 核心论点
本章解决如何深度定制Eclipse编辑器以满足专业开发需求。核心观点是：通过修改字体、代码风格、内容辅助、JDK编译环境、编码方式等，可以使Eclipse更符合团队规范和个人习惯，提升开发效率。

### 关键概念/事件
- **字体修改**：在General→Appearance→Colors and Fonts中修改Java编辑器字体，推荐使用等宽字体。
- **去掉拼写错误检查**：在General→Editors→Text Editors→Spelling中取消“Enable spell checking”，避免红色波浪线干扰。
- **Java代码风格**：在Java→Code Style→Formatter中新建或编辑代码格式化方案，可自定义花括号位置、缩进等。
- **内容辅助（Content Assist）**：在Java→Editor→Content Assist中设置自动激活触发器，默认仅“.”，可修改为所有字母和“@”以增强提示。
- **增强代码提示**：通过导出Preferences文件，编辑后导入，实现按任意字母触发代码补全；并可修改插件源码解决空格和“=”自动上屏问题。
- **修改项目编码**：在项目属性→Resource中将文本文件编码设为UTF-8，避免中文乱码。

### 逻辑推演/叙事脉络
本章从基础设置（行号、字体）开始，逐步深入到拼写检查关闭、代码风格自定义、内容辅助增强。重点讲解了“增强Eclipse代码提示功能”的详细步骤（导出.epf→修改触发字符→导入），以及如何通过修改org.eclipse.jface.text插件源码去掉空格/等号自动上屏。最后补充了导入JUnit库、修改项目编码和MyEclipse编码设置。整个过程由浅入深，既有界面操作也有插件级修改。

### 经典金句/数据
> “默认触发代码提示的就是‘.’这个符号”  
> “设置每种文件编辑提示的时候都是window→Preferences然后搜索content Assist”  
> “if(key!=0x20 && key!='=' && key!=';' && contains(triggers,key))”（修改插件源码后的判断条件）

---

## 第5章：Eclipse插件开发简介与Hello World

### 核心论点
本章解决如何从零开始创建第一个Eclipse插件的问题。作者核心观点是：Eclipse的内核很小，所有功能都是基于插件机制实现的，开发者可以利用Eclipse提供的向导轻松创建插件，并能将应用系统写成插件形式以获得统一的界面风格。

### 关键概念/事件
- **插件概念**：Eclipse内核极小，其他功能（如JUNIT、ANT）都是插件，用户可基于开放机制开发自己的插件。
- **插件优势**：界面风格友好统一（视图、编辑窗、停泊窗），复用Eclipse操作习惯。
- **插件劣势**：必须依附Eclipse运行，原Eclipse的部分菜单和工具栏无法完全屏蔽。
- **使用向导创建HelloWorld插件**：新建插件项目→选择模板“Hello,World”→生成MypluginPlugin.java和SampleAction.java→运行“运行工作平台”启动新Eclipse实例，看到新增工具栏按钮和菜单项。
- **手工创建空白项目插件**：新建空白插件项目→创建实现IWorkbenchWindowActionDelegate的Action类→修改plugin.xml添加扩展点→运行得到同样效果。

### 逻辑推演/叙事脉络
本章首先介绍插件开发的意义（Eclipse平台的可扩展性），然后比较插件开发的优势与不足。接着通过两个完整示例（向导生成和手工创建）演示插件开发流程：新建插件项目→编写Action类→配置plugin.xml→运行调试。最后详细解释plugin.xml中`<plugin>`、`<runtime>`、`<requires>`、`<extension>`等标签的含义，为后续扩展点学习打下基础。

### 经典金句/数据
> “Eclipse的内核很小，其他功能都是基于这个内核上的插件”  
> “将软件写成插件形式也有一定的缺陷。首先插件必须依附Eclipse，如果要安装插件就得先安装Eclipse。”

---

## 第6章：常用插件扩展点实战

### 核心论点
本章解决如何在插件中添加透视图、视图、编辑器、首选项、帮助等功能的问题。核心观点是：plugin.xml中的扩展点是插件与Eclipse内核的接口，开发者只需熟悉常用扩展点（如org.eclipse.ui.perspectives、views、editors、preferencePages、help.toc等），通过XML配置即可为插件添加丰富的界面组件。

### 关键概念/事件
- **透视图扩展点（org.eclipse.ui.perspectives）**：通过`<perspective>`标签添加自定义透视图，需实现IPerspectiveFactory接口。
- **视图扩展点（org.eclipse.ui.views）**：通过`<view>`标签添加视图，需继承ViewPart抽象类并实现createPartControl方法。
- **视图间事件监听**：使用IWorkbenchPage.findView(“视图id”)获取其他视图对象，实现组件互动。
- **编辑器扩展点（org.eclipse.ui.editors）**：需实现EditorPart抽象类（至少实现init、createPartControl、doSave、isDirty等7种方法）并配套IEditorInput接口。
- **首选项扩展点（org.eclipse.ui.preferencePages）**：需继承PreferencePage并实现IWorkbenchPreferencePage，使用IPreferenceStore进行值的存取。
- **帮助扩展点（org.eclipse.help.toc）**：通过toc.xml定义帮助目录树，再关联html帮助文件。
- **弹出信息扩展点（org.eclipse.help.contexts）**：通过HelpContexts.xml定义帮助上下文id，然后用WorkbenchHelp.setHelp()关联到具体组件。

### 逻辑推演/叙事脉络
本章按照开发一个完整插件的典型顺序展开：先添加透视图，再在透视图中加入视图，然后实现视图间事件监听，接着给视图添加菜单和按钮。之后依次演示添加编辑器（含IEditorInput和EditorPart详细代码）、首选项（含IPreferenceStore的使用）、帮助目录（toc.xml）和弹出式帮助（contexts）。每个扩展点都给出了plugin.xml配置示例、Java类实现代码和运行效果图。结构非常清晰，覆盖了插件开发80%以上的常用功能。

### 经典金句/数据
> “org.eclipse.ui.perspectives是透视图的扩展点”  
> “在插件中IWorkbenchPage对象比较重要”  
> “设置弹出信息和设置帮助（toc）有其类似之处，而且它们的HTML格式的帮助文件也可以共用”

---

## 第7章：SWT/JFace事件模型与界面编程

### 核心论点
本章解决如何在SWT/JFace中处理用户交互事件的问题。核心观点是：事件代码有四种写法（匿名内部类、命名内部类、外部类、实现监听接口），推荐使用命名内部类以获得最佳的可读性和可维护性；同时需掌握addSelectionListener等常用监听器，并通过final修饰符或实例变量等方式访问类中的变量。

### 关键概念/事件
- **四种事件写法**：匿名内部类（最简洁但代码分散）、命名内部类（推荐）、外部类（便于跨类重用）、实现监听接口（适合多个组件共用同一处理逻辑）。
- **常用监听器**：addSelectionListener（最常用，响应单击/回车）、addKeyListener（按键）、addFocusListener（焦点）、addMouseListener（鼠标）。
- **访问类中变量的方法**：加final修饰符（针对局部变量）、将变量转为实例变量、通过构造函数传参（命名内部类方式）。
- **Java变量分类**：局部变量（方法内）、实例变量（类内、方法外）、类变量（static修饰）、常量（final static）。

### 逻辑推演/叙事脉络
本章先从文本框双击弹对话框的简单需求出发，依次演示四种事件写法的代码示例，并分析各自优缺点。然后列出常用监听器及其触发方法。最后重点讲解在事件代码中如何访问外部类变量，给出了三种解决方案，并补充Java变量类型的知识（局部变量、实例变量、类变量）。整体以实用为导向，兼顾代码风格和变量作用域原则。

### 经典金句/数据
> “使用匿名内部类来写事件代码简单方便，但也要注意它的一些缺点”  
> “命名内部类方式在写起来方便些，但不适合事件代码太长太多的情况”  
> “使用变量的一般原则是，尽量使变量的有效范围最小化：优先考虑用局部变量，其次是实例变量，最后才是类变量。”

---

## 第8章：SWT/JFace界面组件与布局

### 核心论点
本章解决如何利用SWT/JFace构建图形用户界面的问题。核心观点是：SWT是Eclipse图形API的基础，拥有比AWT/Swing更快的速度和更原生的外观；JFace在SWT之上提供更易用的组件；开发时应优先使用JFace组件，但通常需要混合使用两者。

### 关键概念/事件
- **SWT包结构**：org.eclipse.swt.widgets（基本组件如Button、Text、Shell）、org.eclipse.swt.layout（布局）、org.eclipse.swt.custom（扩展组件）、org.eclipse.swt.event（事件）、org.eclipse.swt.graphics（图形）、org.eclipse.swt.ole.win32（OLE调用）。
- **SWT应用程序模板**：创建Display→创建Shell→设置布局→添加组件→调用open()→事件循环（while (!shell.isDisposed())）。
- **SWT Designer工具**：可视化设计SWT/JFace界面，可自动生成代码，但建议只用于快速原型，然后手工修改优化。
- **布局管理器**：FillLayout、RowLayout、GridLayout等，用于控制组件在容器中的位置和大小。
- **组件对应关系**：Shell对应主窗口，Composite对应面板，Button、Text、Label等为基本控件。

### 逻辑推演/叙事脉络
本章先通过SWT与AWT/Swing的对比引出SWT的优势（速度快、外观原生）。然后介绍SWT的主要子包。接着用SWT Designer创建第一个SWT程序（HelloWorld），详细讲解代码结构：Display、Shell、事件循环。之后演示如何添加文本框组件并设置位置（setBounds）。最后给出一个标准界面示例，并说明Display和Shell的谱系关系。本章末尾还提供了实践建议（使用临时Application做原型再移植）和稳定性注意事项。

### 经典金句/数据
> “SWT/JFace象一股清新的风吹入了Java的GUI开发领域，为这个沉闷的领域带来了勃勃生机”  
> “SWT Designer还无法完成所有的界面设计工作，所以在界面开发中依然是以手工写代码为主”

---

## 第9章：项目打包与发行

### 核心论点
本章解决如何将Java应用程序和Eclipse插件项目打包发布的问题。核心观点是：应用程序项目可通过JAR打包+清单文件设置入口类和支持库路径来独立运行；而插件项目则需导出为可部署的插件形式；使用Ant或Fat Jar插件可以简化打包流程，甚至可以让程序在不安装JRE的电脑上运行。

### 关键概念/事件
- **应用程序打包步骤**：编写MANIFEST.MF（设置Main-Class和Class-Path）→使用Eclipse导出向导生成JAR→复制依赖包（如swt.jar、jface.jar）和本地化文件（swt-win32-*.dll）→编写批处理（javaw -jar myswt.jar）运行。
- **Fat Jar插件**：可将所有依赖包合并到一个JAR中，简化分发。
- **脱离JRE运行**：将JDK中的jre目录复制到程序目录，修改批处理指向该jre/bin/javaw，即可在不安装JRE的电脑上运行。
- **美化启动**：使用JavaLauncher（launch.exe+launcher.cfg）替代批处理文件，并用Resource Hacker替换图标为自定义图标。
- **Ant自动打包**：编写build.xml，定义编译、打包、复制依赖、生成API文档等任务，实现一键构建。

### 逻辑推演/叙事脉络
本章先分应用程序打包和插件打包两条线。对于应用程序，详细演示了清单文件编写、导出向导操作、复制支持包、编写run.bat的完整流程；然后介绍Fat Jar插件一键打包；再进一步讲解如何让用户电脑无需安装JRE（复制jre目录并修改启动路径）；最后用JavaLauncher和Resource Hacker美化启动程序。对于插件项目，推荐使用Ant进行自动化打包，给出了完整的build.xml示例及其解释（变量定义、路径引用、编译、打包、复制、生成文档）。整个过程从基础到进阶，覆盖了从开发到发行的所有环节。

### 经典金句/数据
> “Class-Path项最长...可以通过项目根目录下的‘.classpath’编辑而得”  
> “Fat Jar使用步骤如下：右键单击项目→Build Fat Jar”  
> “使用一个叫JavaLauncher的免费小程序来代替批处理文件去运行Java程序”  
> “让用户电脑不必安装JRE环境的方法：将原JDK中的‘jre’目录复制到应用程序目录”

---

## 第10章：Eclipse的J2EE开发环境搭建与Struts+Hibernate整合

### 核心论点
本章解决如何使用Eclipse + Lomboz + Tomcat + Struts + Hibernate进行J2EE开发的问题。核心观点是：通过Lomboz插件可以方便地创建J2EE项目、管理Web模块、启动Tomcat；结合数据库连接池配置、JSP动态发布技巧、以及Struts和Hibernate的集成，可以构建流行的免费J2EE开发栈。

### 关键概念/事件
- **Lomboz安装**：下载对应Eclipse 3.1版本的Lomboz和emf，使用Links方式安装，并配置Tomcat 5.0.28服务器。
- **J2EE项目创建**：新建Lomboz J2EE Project→创建Web Module→指定Targeted Servers（Tomcat v5.0.x）→生成项目结构。
- **免发布修改JSP**：修改Tomcat的conf/server.xml，添加`<Context>`并设置docBase指向Eclipse工作区中的hello模块，workDir指向bin目录，实现保存JSP后刷新浏览器即可看到效果。
- **数据库连接池配置**：通过Tomcat Admin页面配置DataSource（JNDI名、URL、驱动等），并在server.xml中添加`<ResourceLink>`，最后在JSP中用InitialContext查找数据源获取连接。
- **面向对象分析与设计（OOAD）**：本章后半部分以学生成绩管理系统为例，展示如何先设计实体对象（Student、Teacher、Course、Grade、Exam等），再根据对象关系生成数据库表结构，强调“万物皆对象”的设计理念。

### 逻辑推演/叙事脉络
本章先下载安装Lomboz和Tomcat，验证环境。然后创建第一个J2EE项目myweb，演示启动Tomcat、发布模块、访问index.jsp。重点解决了开发中“每次修改JSP都要重新发布”的痛点，通过修改server.xml实现实时更新。接着配置Tomcat数据库连接池，并给出test.jsp示例验证连接。最后转向软件工程方法：以学生成绩管理插件项目为例，详细讲解OOAD过程——先抽象IUser接口，再实现AbstractUser默认实现，然后派生出Student和Teacher，再设计Course、SchoolClass、Grade、Exam、StudentScore等实体对象。整个流程从环境搭建到实战编码，再到设计方法论，层次分明。

### 经典金句/数据
> “面向对象的分析与设计...关键点在于思维方式的转变，而不是工具的使用”  
> “徒具对象的形，而无对象的神，是现在一般程序员的最大缺陷所在”  
> “设计数据库尽量依照数据库设计范式来做，不要为了书写SQL语句方便，而将同一字段放在多个表中”  
> “实体对象是一种纯数据对象，和数据库表有着一定程度上的对应关系，但又不是完全对应。”

---

**总结完成**。以上十章覆盖了Eclipse基础操作、编辑器高级配置、插件开发入门、SWT/JFace界面编程、事件模型、项目打包发行以及J2EE环境搭建等核心内容，严格遵循用户要求的章节结构格式输出。