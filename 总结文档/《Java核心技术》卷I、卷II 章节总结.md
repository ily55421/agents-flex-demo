# 《Java核心技术》卷I、卷II 章节总结

## 目录说明

本书分为卷I（第1-14章）和卷II（第1-12章）。卷I聚焦Java语言基础、面向对象编程、泛型、集合框架、Swing GUI、事件处理和并发编程；卷II涵盖高级特性：流API、输入输出、XML、网络、数据库、日期时间API、国际化、脚本编译注解、安全、高级Swing、高级AWT和本地方法。以下是依据实际正文标题整理的逐章总结。

---

## 卷I

## 第1章：Java程序设计概述

### 核心论点

本章主要回答“Java是什么、为什么它如此重要”的问题。Java不仅仅是一种程序设计语言，更是一个完整的平台，拥有庞大的库、可重用的代码以及提供安全性和跨平台可移植性的执行环境。Java的成功源于其集多种优势于一身：优美的语法、自动垃圾收集、跨平台能力以及大型标准库。

### 关键概念/事件

- **Java平台**：不仅是编程语言，还包括庞大的库、可重用代码和执行环境（安全性、跨平台移植性、自动垃圾收集）。
- **“白皮书”11个关键术语**：简单性、面向对象、分布式、健壮性、安全性、体系结构中立、可移植性、解释型、高性能、多线程、动态性。
- **Java发展简史**：1991年Green项目启动（Oak语言），1995年更名为Java并发布，1996年Java 1.0，1998年Java 2，2004年Java 5.0（泛型等），2014年Java 8（lambda表达式）。
- **Applet与沙箱**：早期Java的亮点是在浏览器中运行applet，受限于沙箱安全模型。如今applet已基本被淘汰，需要数字签名。

### 逻辑推演/叙事脉络

作者从Java的宣传热潮切入，指出Java不仅是语言更是平台。随后逐一解释白皮书中的11个关键术语，说明Java的设计目标。接着回顾Java的发展历史，从1991年Green项目到Java 8的重要版本更新。最后澄清关于Java的常见误解（如Java是HTML扩展、解释型速度慢、主要安全风险等），帮助读者建立正确的认知框架。

### 经典金句/数据

> “Java是一种程序设计语言；HTML是一种描述网页结构的方式。除了用于在网页上放置Java applet的HTML扩展之外，两者没有任何共同之处。”(p.27)

> 截至Java 8，标准库中API类的数量已达到4240个，相比Java 1.0的211个增长了20倍。(p.27)

---

## 第2章：Java程序设计环境

### 核心论点

本章回答“如何安装和配置Java开发环境，以及如何编译和运行不同类型的Java程序”。作者强调掌握命令行工具是基本技能，同时介绍了集成开发环境（如Eclipse）的使用方法，并演示了控制台程序、图形化应用程序和applet的编译与运行。

### 关键概念/事件

- **JDK与JRE的区别**：JDK（Java Development Kit）供程序员使用，包含编译器；JRE（Java Runtime Environment）供普通用户使用，只包含虚拟机。
- **PATH环境变量设置**：需要将`jdk/bin`目录添加到执行路径中，才能在命令行使用`javac`和`java`命令。
- **类路径（CLASSPATH）**：指定Java虚拟机查找类文件的路径，包括基目录、当前目录(.)和JAR文件。推荐使用`-classpath`选项而非全局环境变量。
- **集成开发环境（Eclipse）**：通过创建Java Project、导入源代码、运行即可编译执行，IDE能实时提示语法错误。

### 逻辑推演/叙事脉络

作者首先指导读者下载并安装JDK，设置PATH环境变量。然后演示如何使用命令行工具编译和运行一个简单的Welcome程序，强调常见错误（大小写、文件名、类路径）。接着介绍如何使用Eclipse IDE进行同样的操作。最后展示如何运行图形化应用程序（ImageViewer）以及如何配置浏览器运行applet。

### 经典金句/数据

> “如果遇到诸如‘javac:command not found’这类消息，就要返回去反复检查安装是否有问题，特别是执行路径的设置。”(p.33)

> 在Windows中设置Path环境变量时，建议不要接受包含空格的默认安装路径（如`c:\Program Files\Java\...`），取出路径中的Program Files部分。(p.31)

---

## 第3章：Java的基本程序设计结构

### 核心论点

本章全面介绍Java程序设计的基本语法元素：数据类型、变量、运算符、字符串、输入输出、控制流程、数组等。作者为没有编程经验或从其他语言转入的读者提供基础，同时为C++程序员标注了差异点。

### 关键概念/事件

- **8种基本数据类型**：byte、short、int、long、float、double、char、boolean。整型范围与平台无关（int固定32位）。
- **字符串**：String是不可变类，使用`+`拼接，`equals()`比较内容而非`==`。StringBuilder用于高效构建字符串。
- **大数值（BigInteger/BigDecimal）**：用于任意精度整数和浮点数运算，使用`add()`、`multiply()`等方法而非运算符。
- **数组**：创建后大小固定，可使用`Arrays.sort()`排序，支持for-each循环。多维数组是“数组的数组”，可形成不规则数组。
- **控制流程**：块作用域、条件语句、循环（while、do-while、for）、带标签的break（替代goto）、switch语句。

### 逻辑推演/叙事脉络

作者从第一个Java程序（FirstSample）的结构开始，依次讲解注释、数据类型（整型、浮点、char、Unicode、boolean）、变量与常量、运算符（算术、位运算、数学函数、类型转换）、字符串（不可变性、比较、码点与代码单元）、输入输出（Scanner、Console、printf格式化）、控制流程（块、条件、循环、switch、带标签break）、大数值和数组（创建、初始化、拷贝、排序、多维数组）。每部分都配有示例代码。

### 经典金句/数据

> “在Java中，不区分变量的声明与定义。而C和C++区分。”（C++注释，p.55）

> “强烈建议不要在程序中使用char类型，除非确实需要处理UTF-16代码单元。最好将字符串作为抽象数据类型处理。”(p.53)

---

## 第4章：对象与类

### 核心论点

本章系统讲解面向对象程序设计（OOP）的核心概念：类、对象、封装、继承和多态。作者强调封装的重要性：将数据和行为组合在包中，对对象使用者隐藏实现方式，绝对不能让类中的方法直接访问其他类的实例域。

### 关键概念/事件

- **类与对象**：类是构造对象的模板（蓝图），对象是类的实例。封装将数据和行为组合，实例域是数据，方法是行为。
- **构造器**：与类同名，无返回值，总是与`new`操作符一起调用。可重载，支持`this()`调用另一个构造器。
- **静态域与静态方法**：静态域属于类而非对象，静态方法不能访问实例域。`main`方法是静态的。
- **方法参数**：Java总是按值调用。方法不能修改基本类型参数，但可以修改对象参数的状态（因为传递的是对象引用的拷贝）。
- **包（package）**：用于组织类，确保类名唯一性。使用`import`导入类，静态导入可导入静态方法和域。

### 逻辑推演/叙事脉络

作者先从OOP的基本概念（类、对象、封装、类关系）入手，然后讲解如何使用预定义类（Date、LocalDate），重点区分对象与对象变量。接着以Employee类为例，详细剖析构造器、隐式/显式参数、封装的优点（返回可变对象需克隆）、私有方法、final实例域。随后讲解静态域/方法、方法参数传递机制，最后介绍对象构造（重载、默认初始化、无参构造器、初始化块）、包与类路径、文档注释和类设计技巧。

### 经典金句/数据

> “一定要注意不要编写返回引用可变对象的访问器方法。如果需要返回一个可变对象的引用，应该首先对它进行克隆。”(p.110)

> “在Java中，所有的方法都必须在类的内部定义，但并不表示它们是内联方法。是否内联是Java虚拟机的任务。”(p.126-127)

---

## 第5章：继承

### 核心论点

本章讲解面向对象的另一核心概念——继承。作者解释如何使用`extends`关键字定义子类、覆盖方法、使用`super`调用超类构造器和方法，并强调多态和动态绑定的重要性。继承的主要目的是代码复用和实现“is-a”关系。

### 关键概念/事件

- **覆盖方法与super**：子类可以覆盖超类方法，使用`super`调用超类版本。`super`必须是子类构造器的第一条语句。
- **多态与动态绑定**：对象变量可以指示多种实际类型，运行时自动选择调用哪个方法。方法调用过程：编译器查看声明类型和方法名 → 重载解析 → 动态绑定。
- **抽象类与抽象方法**：包含一个或多个抽象方法的类必须声明为抽象类，不能实例化。抽象方法充当占位符，在子类中实现。
- **Object类**：所有类的超类。`equals()`、`hashCode()`、`toString()`是重要方法，应正确覆盖（需满足自反性、对称性、传递性、一致性、非空性）。
- **反射（Reflection）**：运行时分析类的能力，可查看域、方法、构造器，动态创建对象和调用方法。

### 逻辑推演/叙事脉络

作者以Employee和Manager为例，讲解定义子类、覆盖`getSalary`方法、子类构造器调用`super`。然后解释继承层次、多态、动态绑定的方法调用过程。接着讨论`final`类/方法、强制类型转换（配合`instanceof`）、抽象类。随后深入Object类，详细讲解`equals`的正确实现、`hashCode`与`equals`的兼容性、`toString`。最后介绍泛型数组列表（ArrayList）、对象包装器与自动装箱、参数数量可变的方法、枚举类、反射（Class类、捕获异常、分析类、运行时分析对象、调用任意方法），以及继承设计技巧。

### 经典金句/数据

> “在覆盖方法时，子类方法不能低于超类方法的可见性。如果超类方法是public，子类方法一定要声明为public。”(p.157)

> “强烈建议为自定义的每一个类增加toString方法。这样做不仅自己受益，而且所有使用这个类的程序员也会从这个日志记录支持中受益匪浅。”(p.174)

---

## 第6章：接口、lambda表达式与内部类

### 核心论点

本章介绍三种高级技术：接口（interface）用于描述类具有什么功能但不给出具体实现；lambda表达式用于简洁地表示可传递的代码块；内部类定义在另一个类内部，可以访问外部类的域。作者强调接口与抽象类的区别：类可以继承多个接口但只能扩展一个超类。

### 关键概念/事件

- **接口**：对类的一组需求描述，包含抽象方法（自动public）和常量（自动public static final）。Java 8允许在接口中添加静态方法和默认方法（`default`）。
- **lambda表达式**：参数 → 表达式，或参数 → { 代码块 }。本质是函数式接口的实例，可延迟执行代码。捕获的变量必须是“实际上的final”。
- **方法引用与构造器引用**：`对象::实例方法`、`类::静态方法`、`类::实例方法`。`类::new`是构造器引用。
- **内部类**：定义在另一个类中的类。可以访问外围类的私有数据，包括静态内部类、局部内部类、匿名内部类。内部类有对外围类对象的隐式引用（`外部类.this`）。
- **代理（Proxy）**：运行时动态创建实现一组给定接口的新类，用于路由方法调用、调试跟踪等。

### 逻辑推演/叙事脉络

作者首先介绍接口的概念，以`Comparable`接口为例说明如何实现和排序。然后解释接口的特性（可扩展、可包含常量）、与抽象类的对比，以及Java 8新增的静态方法和默认方法，并讨论默认方法冲突的解决规则（“类优先”原则）。接着用`ActionListener`和`Comparator`演示接口的实际应用，并讲解对象克隆（`Cloneable`接口、浅拷贝与深拷贝）。随后全面介绍lambda表达式：语法、函数式接口、方法引用、构造器引用、变量作用域，以及如何编写处理lambda表达式的方法。最后详细讲解内部类的四种类型和使用场景，并以代理结束本章。

### 经典金句/数据

> “接口中的所有方法自动地属于public。在接口中声明方法时，不必提供关键字public。”(p.212)

> “lambda表达式就是一个代码块，以及必须传入代码的变量规范。”(p.232)

---

## 第7章：异常、断言和日志

### 核心论点

本章系统讲解Java的错误处理机制：异常（捕获与抛出）、断言（用于测试期间的条件检查）和日志（记录程序运行信息）。作者强调异常处理的目标是：向用户通告错误、保存所有工作结果、允许用户妥善退出程序。建议“早抛出，晚捕获”。

### 关键概念/事件

- **异常分类**：Throwable分为Error（内部错误）和Exception。Exception分为RuntimeException（程序错误，如空指针、数组越界）和其他异常（如IO错误）。受查异常（checked）和非受查异常（unchecked）。
- **抛出与捕获**：方法用`throws`声明可能抛出的异常，用`throw`抛出异常对象。用`try/catch`捕获异常，`finally`子句保证资源释放（无论是否发生异常）。带资源的try语句（try-with-resources）可自动关闭实现了`AutoCloseable`的资源。
- **断言（assert）**：`assert 条件;` 或 `assert 条件 : 表达式;`。默认禁用，用`-ea`启用。断言失败是致命的、不可恢复的错误，只用于开发和测试阶段。
- **日志（Logging API）**：使用`Logger`对象，有7个级别（SEVERE到FINEST）。可添加`Handler`（如FileHandler、ConsoleHandler）、设置`Formatter`、安装`Filter`。

### 逻辑推演/叙事脉络

作者首先说明为什么需要异常处理（用户输入错误、设备错误、物理限制、代码错误），然后介绍异常的分类和继承层次。接着讲解如何声明受查异常、如何抛出异常、如何创建自定义异常类。然后详细说明捕获异常的方法：`try/catch`、捕获多个异常、再次抛出异常与异常链、`finally`子句、带资源的`try`语句。之后给出使用异常机制的技巧（不要代替简单测试、不要过分细化、利用异常层次结构、不要压制异常）。最后介绍断言（概念、启用禁用、参数检查、文档假设）和日志（基本日志、高级日志、配置、本地化、处理器、过滤器、格式化器）以及调试技巧（打印变量、单元测试、堆栈轨迹、jconsole等）。

### 经典金句/数据

> “使用异常的基本规则是：只在异常情况下使用异常机制。与执行简单的测试相比，捕获异常所花费的时间大大超过了前者。”(p.283)

> “如果在子类中覆盖了超类的一个方法，子类方法中声明的受查异常不能比超类方法中声明的异常更通用。”(p.269)

---

## 第8章：泛型程序设计

### 核心论点

本章全面讲解Java泛型机制：类型参数的好处（更好的安全性和可读性）、泛型类和泛型方法的定义、类型变量的限定、泛型代码与虚拟机（类型擦除）、约束与局限性、通配符类型以及反射与泛型的关系。

### 关键概念/事件

- **类型参数的好处**：泛型让代码更安全、更可读，避免了强制类型转换，编译器可以检查类型错误。
- **类型擦除**：虚拟机中没有泛型类型对象，所有类型参数都被替换为限定类型（无限定用Object）。编译器会插入强制类型转换和桥方法以保持多态。
- **通配符类型**：`? extends T`（子类型限定，可读取但不能写入）和`? super T`（超类型限定，可写入但不能安全读取）。无限定通配符`?`用于简单操作如检测null。
- **约束与局限性**：不能用基本类型实例化类型参数；运行时类型查询只适用于原始类型；不能创建参数化类型的数组；不能实例化类型变量；泛型类的静态上下文中类型变量无效；不能抛出或捕获泛型类的实例。
- **泛型反射**：`Class`类是泛型的，`Type`接口及其子类型（`TypeVariable`、`WildcardType`、`ParameterizedType`、`GenericArrayType`）可用于获取泛型类型信息。

### 逻辑推演/叙事脉络

作者首先通过比较ArrayList使用泛型前后的代码，说明类型参数的好处。然后以`Pair<T>`为例讲解如何定义简单泛型类，接着介绍泛型方法（类型变量放在修饰符后返回类型前）。随后讲解类型变量的限定（`extends`关键字），深入分析泛型代码与虚拟机的交互：类型擦除、翻译泛型表达式和泛型方法、桥方法的必要性、调用遗留代码的警告处理。之后详细列出泛型的约束与局限性，解释泛型类型的继承规则（`Pair<Manager>`不是`Pair<Employee>`的子类型）。最后介绍通配符类型（概念、超类型限定、无限定通配符、通配符捕获）以及反射和泛型（泛型Class类、`Class<T>`参数的类型匹配、虚拟机中的泛型类型信息）。

### 经典金句/数据

> “虚拟机中没有泛型，只有普通的类和方法。所有的类型参数都用它们的限定类型替换。”(p.319)

> “通配符类型中，允许类型参数变化。例如，通配符类型`Pair<? extends Employee>`表示任何泛型Pair类型，它的类型参数是Employee的子类。”(p.330)

---

## 第9章：集合

### 核心论点

本章系统介绍Java集合框架（Collection Framework）：将集合的接口与实现分离、具体的集合类（链表、数组列表、散列集、树集、队列、映射等）、视图与包装器、算法以及遗留的集合类。作者强调选择合适的集合类型对程序性能和可维护性至关重要。

### 关键概念/事件

- **集合框架接口**：`Collection`、`List`、`Set`、`Queue`、`Map`、`Iterator`。`List`是有序集合（支持随机访问），`Set`是无重复元素集合。
- **具体集合**：`ArrayList`（动态数组）、`LinkedList`（双向链表，高效插入删除）、`HashSet`（基于散列表，无序）、`TreeSet`（有序集，红黑树）、`PriorityQueue`（堆，每次删除最小元素）、`HashMap`/`TreeMap`（键值对映射）。
- **迭代器**：`Iterator`接口的`next()`、`hasNext()`、`remove()`。Java迭代器位于两个元素之间，查找与位置移动紧密相连。`ListIterator`支持反向遍历和添加元素。
- **映射视图**：`keySet()`、`values()`、`entrySet()`返回视图。`forEach`方法可遍历所有条目。`merge`和`compute`方法简化更新映射项。
- **算法**：`Collections.sort()`（稳定归并排序）、`shuffle()`（混排）、`binarySearch()`（二分查找，仅适用于随机访问列表）。

### 逻辑推演/叙事脉络

作者首先说明集合框架的核心设计原则：将接口与实现分离，以`Queue`为例展示`CircularArrayQueue`和`LinkedListQueue`两种实现。然后介绍`Collection`接口和`Iterator`接口的使用方法，以及泛型实用方法（`contains`、`removeIf`等）。接着按类别详细讲解各具体集合：`LinkedList`（双向链表、迭代器的`add`和`remove`）、`ArrayList`（动态数组，优于`Vector`）、`HashSet`（散列表、再散列）、`TreeSet`（有序、需提供`Comparator`或实现`Comparable`）、`Queue`与`Deque`、`PriorityQueue`。随后讲解映射：基本操作、更新映射项、映射视图、`WeakHashMap`（弱引用键）、`LinkedHashMap`（插入顺序或访问顺序）、`EnumSet`/`EnumMap`、`IdentityHashMap`（用`==`而非`equals`）。然后介绍视图与包装器（`Arrays.asList()`、不可修改视图、同步视图、受查视图、子范围）。最后讲解算法（排序、混排、二分查找、简单算法、批操作）和遗留集合（`Hashtable`、枚举、`Properties`、`Stack`、`BitSet`）。

### 经典金句/数据

> “链表与泛型集合之间有一个重要的区别。链表是一个有序集合，每个对象的位置十分重要。LinkedList.add方法将对象添加到链表的尾部。”(p.4)

> “如果散列表太满，就需要再散列。装填因子为0.75时，表中超过75%的位置已填入元素，就会用双倍的桶数自动再散列。”(p.364)

---

## 第10章：图形程序设计

### 核心论点

本章开启Swing GUI编程的入门教学，介绍如何创建窗口（JFrame）、如何定位和设置框架大小、如何在组件中绘制信息、如何使用2D图形库绘制几何图形、如何使用颜色和字体，以及如何显示图像。

### 关键概念/事件

- **Swing与AWT**：Swing是基于AWT之上“绘制”的用户界面类，采用模型-视图-控制器设计模式。Swing组件以“J”开头（如JFrame、JButton）。
- **JFrame与内容窗格**：JFrame是顶层窗口，组件应添加到内容窗格（`getContentPane()`）。`setDefaultCloseOperation`决定关闭窗口时的行为。
- **绘制组件**：扩展`JComponent`并覆盖`paintComponent(Graphics g)`方法，使用`Graphics2D`绘制。调用`repaint()`强制刷新，不要直接调用`paintComponent`。
- **2D图形**：使用`Graphics2D`类，`Shape`接口的实现类（`Rectangle2D`、`Ellipse2D`、`Line2D`）。`draw()`描边，`fill()`填充。使用`Color`和`Font`设置样式。
- **显示图像**：`ImageIcon`读取图像文件，`Graphics.drawImage()`绘制图像，`copyArea()`平铺图像。

### 逻辑推演/叙事脉络

作者首先解释Swing的历史和优势（相对于AWT的对等体方法），以及Swing的“可插拔观感”。然后创建一个简单的`JFrame`框架，讲解框架的定位、大小设置（获取屏幕尺寸、`pack()`、`setExtendedState`最大化）。接着演示如何在`JComponent`的`paintComponent`中绘制字符串，并说明内容窗格结构。随后介绍Java 2D库：`Graphics2D`、各种`Shape`类（`Rectangle2D`、`Ellipse2D`、`Line2D`）、使用`Color`（RGB、预定义常量、系统颜色）、使用`Font`（字体名、逻辑字体、测量字符串尺寸的方法）。最后演示如何显示图像（`ImageIcon`、`drawImage`、`copyArea`平铺）。

### 经典金句/数据

> “无论何种原因，只要窗口需要重新绘图，事件处理器就会通告组件，从而引发执行所有组件的paintComponent方法。一定不要自己调用paintComponent方法。”(p.419)

> “Java 2D库采用面向对象的方式将几何图形组织起来。包含描述直线、矩形和椭圆的类：Line2D、Rectangle2D、Ellipse2D。这些类全部实现了Shape接口。”(p.421)

---

## 第11章：事件处理

### 核心论点

本章讲解Java AWT/Swing的事件处理机制——事件委托模型。事件源（如按钮）注册监听器对象，当事件发生时，事件源将事件对象传递给所有注册的监听器。作者演示了如何处理按钮点击、改变观感、鼠标事件，并介绍了适配器类和Action接口。

### 关键概念/事件

- **事件委托模型**：监听器对象（实现`ActionListener`等接口）注册到事件源。事件发生时，事件源调用监听器的`actionPerformed`等方法，传递`ActionEvent`等事件对象。
- **lambda表达式简化监听器**：可用`event -> { 代码 }`替代匿名内部类。例如`button.addActionListener(event -> System.exit(0));`
- **观感切换**：使用`UIManager.setLookAndFeel(className)`和`SwingUtilities.updateComponentTreeUI(frame)`动态改变观感。
- **鼠标事件**：`MouseListener`（点击、按下、释放、进入、退出）和`MouseMotionListener`（移动、拖动）。使用`MouseAdapter`适配器类避免实现所有方法。
- **Action接口**：封装命令的名称、图标、启用状态等，可同时关联到按钮、菜单项和按键。使用`InputMap`和`ActionMap`将击键映射到动作。

### 逻辑推演/叙事脉络

作者先介绍事件处理的基本概念（事件源、事件对象、监听器接口），以按钮点击为例演示如何实现`ActionListener`、注册监听器、改变面板背景颜色。然后展示如何使用lambda表达式简化代码，并演示如何动态切换观感（Metal、Windows、Motif）。接着讲解`WindowListener`和适配器类（`WindowAdapter`）处理窗口关闭事件。随后引入`Action`接口，演示如何将动作同时关联到按钮、菜单和击键（`KeyStroke`、`InputMap`、`ActionMap`）。之后讲解鼠标事件：`MouseListener`和`MouseMotionListener`，用`MouseAdapter`简化代码，并实现小方块的添加、移动、删除。最后总结AWT事件的继承层次，区分语义事件（`ActionEvent`等）和底层事件（`MouseEvent`等）。

### 经典金句/数据

> “在Java中，所有的事件对象都最终派生于java.util.EventObject类。”(p.439)

> “可以将多个监听器对象添加到一个像按钮这样的事件源中。这样一来，只要用户点击按钮，按钮就会调用所有监听器的actionPerformed方法。”(p.441)

---

## 第12章：Swing用户界面组件

### 核心论点

本章全面介绍Swing的各种用户界面组件：布局管理器（流布局、边框布局、网格布局、网格组布局、组布局）、文本输入（文本域、文本区、密码域、滚动窗格）、选择组件（复选框、单选钮、边框、组合框、滑动条）、菜单（菜单栏、菜单项、加速键、快捷键、弹出菜单、工具栏）、复杂布局管理（网格组布局详解）以及对话框（选项对话框、自定义对话框、文件对话框、颜色选择器）。

### 关键概念/事件

- **模型-视图-控制器（MVC）**：Swing组件采用MVC设计模式，模型存储内容，视图显示内容，控制器处理用户输入。例如按钮模型`ButtonModel`存储状态，视图由`BasicButtonUI`负责。
- **布局管理器**：`FlowLayout`（流式）、`BorderLayout`（边框布局）、`GridLayout`（网格布局）、`GridBagLayout`（网格组布局）、`GroupLayout`（组布局）。每个容器有默认布局管理器。
- **文本组件**：`JTextField`（单行）、`JTextArea`（多行）、`JPasswordField`（密码域）、`JScrollPane`（滚动条）。文本组件共享`JTextComponent`父类。
- **菜单与工具栏**：`JMenuBar`、`JMenu`、`JMenuItem`、`JCheckBoxMenuItem`、`JRadioButtonMenuItem`、`JPopupMenu`、`JToolBar`。使用`Action`对象可同时创建菜单项和工具栏按钮。
- **对话框**：`JOptionPane`提供标准对话框（消息、确认、选项、输入）。`JDialog`用于自定义对话框。`JFileChooser`用于文件选择，`JColorChooser`用于颜色选择。

### 逻辑推演/叙事脉络

作者首先介绍Swing的MVC架构，以按钮为例分析模型、视图、控制器的职责。然后详细讲解各种布局管理器：`FlowLayout`（默认面板）、`BorderLayout`（默认内容窗格）、`GridLayout`（计算器示例）、`GridBagLayout`（复杂的字体选择器示例，附`GBC`帮助类）、`GroupLayout`（Matisse GUI构造器生成的代码）。接着介绍文本输入组件：文本域、标签、密码域、文本区、滚动窗格。然后讲解选择组件：复选框、单选钮（`ButtonGroup`）、边框（`BorderFactory`）、组合框（`JComboBox`）、滑动条（`JSlider`）。之后介绍菜单：菜单栏、菜单项、图标、复选框/单选钮菜单项、弹出菜单、快捷键与加速器、启用/禁用菜单项、工具栏、工具提示。接着深入讲解复杂布局管理（网格组布局的各参数详解、组布局的API）。最后讲解对话框：`JOptionPane`的四种静态方法、自定义`JDialog`、数据交换、文件对话框（`JFileChooser`、过滤器、附件、文件视图）和颜色选择器（`JColorChooser`）。

### 经典金句/Data

> “模型必须实现改变内容和查找内容的方法。模型是完全不可见的。显示存储在模型中的数据是视图的工作。”(p.470)

> “网格组布局是所有布局管理器之母。可以将网格组布局看成是没有任何限制的网格布局。”(p.519)

> 对话框数据交换模式：在对话框显示前设置默认数据，显示后（阻塞返回）检查用户是否确认，再从组件中读取数据。(p.554)

---

## 第13章：部署Java应用程序

### 核心论点

本章讲解如何打包和部署Java应用程序：JAR文件（创建、清单文件、可执行JAR、资源加载、密封）、应用首选项的存储（属性映射Properties、Preferences API）、服务加载器（ServiceLoader动态加载插件）以及applet和Java Web Start。

### 关键概念/事件

- **JAR文件**：Java归档文件，使用ZIP压缩格式。`jar`命令创建（`cvf`、`cfm`等选项）。清单文件（`MANIFEST.MF`）描述归档特征，可指定`Main-Class`使JAR可执行。
- **资源加载**：使用`Class.getResource()`或`getResourceAsStream()`加载与类位于同一位置的资源（图像、文本等），资源名用`/`分隔。
- **属性映射（Properties）**：存储键/值对字符串，`load()`从文件加载，`store()`保存到文件。用于简单配置信息。`Preferences` API提供平台无关的中心存储库（Windows注册表/Linux文件系统）。
- **服务加载器（ServiceLoader）**：用于加载插件的机制。服务提供者在`META-INF/services/`目录下创建以服务接口全限定名命名的文件，列出实现类。`ServiceLoader.load()`获取迭代器。
- **applet与Java Web Start**：applet是嵌入HTML的Java程序（已过时）。Java Web Start通过JNLP文件从Internet发布应用程序，支持数字签名和沙箱安全。

### 逻辑推演/叙事脉络

作者首先介绍JAR文件的创建、清单文件、如何创建可执行JAR文件，以及如何使用`Class.getResource()`加载资源（图像、文本）。接着讲解密封（seal）包以防止其他类加入。然后介绍应用首选项存储：`Properties`类（简单属性文件）和`Preferences` API（树结构存储，用户树/系统树，导入/导出XML）。随后讲解`ServiceLoader`加载插件的方法。最后讨论applet（`JApplet`、参数传递、图像/音频访问、applet间通信、签名代码）和Java Web Start（JNLP文件、桌面集成、JNLP API的文件保存/打开服务、持久化服务）。

### 经典金句/数据

> “JAR文件既可以包含类文件，也可以包含诸如图像和声音这些其他类型的文件。JAR文件是压缩的，它使用了大家熟悉的ZIP压缩格式。”(p.580)

> “Preferences存储库有一个树状结构，节点路径名类似于/com/mycompany/myapp。类似于包名，只要程序员用逆置的域名作为路径的开头，就可以避免命名冲突。”(p.591)

---

## 第14章：并发

### 核心论点

本章全面讲解Java多线程编程：线程的创建与启动、线程状态、线程属性（优先级、守护线程、未捕获异常处理器）、同步（锁对象、条件对象、synchronized关键字、volatile域、原子性、死锁）、阻塞队列、线程安全的集合、Callable与Future、执行器（线程池、预定执行、Fork-Join框架）、同步器以及Swing中的线程安全规则。

### 关键概念/事件

- **线程创建**：实现`Runnable`接口并传递给`Thread`构造器，调用`start()`启动新线程。`Callable`有返回值，`Future`保存异步计算结果。
- **同步机制**：`ReentrantLock`保护临界区，`Condition`（`await()`、`signalAll()`）管理等待线程。`synchronized`关键字简化同步，每个对象有一个内部锁和一个条件（`wait()`、`notifyAll()`）。
- **线程状态**：New、Runnable、Blocked、Waiting、Timed waiting、Terminated。`interrupt()`请求中断，`sleep()`抛出`InterruptedException`。
- **线程安全集合**：`ConcurrentHashMap`、`ConcurrentLinkedQueue`、`CopyOnWriteArrayList`。使用`ConcurrentHashMap`的`compute`、`merge`、`search`、`reduce`等原子操作。
- **执行器（Executor）**：`Executors`工厂方法创建线程池（`newCachedThreadPool`、`newFixedThreadPool`）。`ScheduledExecutorService`用于预定执行。`ForkJoinPool`用于分治任务（`RecursiveTask`）。

### 逻辑推演/叙事脉络

作者首先通过弹跳球程序演示没有使用线程时UI被阻塞的问题，然后引入`Runnable`和`Thread`实现并发。接着讲解中断线程（`interrupt()`、`isInterrupted()`）和线程的六种状态。随后介绍线程属性（优先级、守护线程、未捕获异常处理器）。然后深入讲解同步：竞争条件示例（银行转账）、`ReentrantLock`和`Condition`的使用、`synchronized`关键字及其内部锁和条件、同步阻塞、volatile域、原子性（`AtomicLong`、`LongAdder`）、死锁分析。之后介绍高级并发工具：阻塞队列（`ArrayBlockingQueue`、`LinkedBlockingQueue`）、线程安全集合（`ConcurrentHashMap`的批操作）、`Callable`与`Future`（`FutureTask`）、执行器（线程池、预定执行、控制任务组、Fork-Join框架）、同步器（信号量、倒计时门栓、障栅、交换器）。最后讲解Swing中的线程安全：单一线程规则（只能在事件分配线程中操作Swing组件），使用`SwingWorker`在后台线程执行耗时任务并通过`publish()`/`process()`更新UI。

### 经典金句/数据

> “如果一个方法用synchronized关键字声明，那么对象的锁将保护整个方法。也就是说，要调用该方法，线程必须获得内部的对象锁。”(p.653)

> “在Java中，没有任何语言方面的需求要求一个被中断的线程应该终止。中断一个线程不过是引起它的注意。被中断的线程可以决定如何响应中断。”(p.633)

---

## 卷II

## 第1章：流

### 核心论点

本章介绍Java 8引入的Stream API，这是一种“做什么而非如何做”的数据处理方式。流不存储元素，操作是惰性的，支持并行处理。通过指定数据源和操作（filter、map、reduce等），流库可以优化计算策略，尤其适合并发计算。

### 关键概念/事件

- **流操作三阶段**：创建流 → 指定中间操作（filter、map、flatMap）→ 终端操作（count、collect、forEach）。终端操作执行前流不实际计算。
- **Optional类型**：安全替代null，`orElse()`、`orElseGet()`、`ifPresent()`等方法处理值存在与否的情况。`flatMap`组合产生Optional值的方法。
- **Collectors**：`toList()`、`toSet()`、`toMap()`、`groupingBy()`、`partitioningBy()`。下游收集器（`mapping`、`summingInt`、`maxBy`）处理分组后的值。
- **基本类型流**：`IntStream`、`LongStream`、`DoubleStream`避免包装。`range()`、`rangeClosed()`生成整数范围，`boxed()`转换为对象流。
- **并行流**：`parallelStream()`或将顺序流转换为并行流。适用于内存中可高效分割的数据集，操作应无状态且不阻塞。

### 逻辑推演/叙事脉络

作者先通过统计长单词的例子展示从迭代到流的转变，对比顺序流和并行流。然后介绍流的创建方法（`Stream.of`、`Arrays.stream`、`generate`、`iterate`、`Pattern.splitAsStream`、`Files.lines`）。接着详细讲解中间操作：`filter`（谓词筛选）、`map`（映射）、`flatMap`（扁平化）、`limit`/`skip`（截取）、`distinct`、`sorted`、`peek`（调试）。然后介绍简单归约操作（`max`、`min`、`findFirst`、`anyMatch`）和`Optional`类型的使用技巧（如何正确使用、如何创建、`flatMap`组合）。之后讲解收集结果：`toArray`、`forEach`、`collect`到各种集合、`joining`连接字符串、`summarizingInt`汇总统计。接着介绍收集到映射（`toMap`处理键冲突、`groupingBy`分组、`partitioningBy`分区）和下游收集器。然后讨论归约操作（`reduce`）和基本类型流。最后深入并行流：条件（数据在内存、高效分割、工作量大、无阻塞）、非干涉性要求、警告（不要修改共享变量），并演示`ForkJoinPool`的使用。

### 经典金句/数据

> “Streams follow the ‘what, not how’ principle. In our stream example, we describe what needs to be done: get the long words and count them. We don’t specify in which order, or in which thread, this should happen.” (p.6)

> “The key to using Optional effectively is to use a method that either produces an alternative if the value is not present, or consumes the value only if it is present.” (p.16)

---

## 第2章：输入与输出

### 核心论点

本章全面讲解Java I/O系统：输入/输出流（字节流、字符流、过滤器）、文本输入输出、二进制数据读写、对象序列化、文件操作（Path、Files、内存映射文件）、正则表达式。作者强调使用字符流（Reader/Writer）处理Unicode文本，使用字节流（InputStream/OutputStream）处理二进制数据。

### 关键概念/事件

- **流层次结构**：`InputStream`/`OutputStream`（字节），`Reader`/`Writer`（字符）。过滤器嵌套（如`DataInputStream` + `BufferedInputStream` + `FileInputStream`）可组合多种功能。
- **文本I/O**：`PrintWriter`输出文本，`Scanner`或`BufferedReader`读取文本。应明确指定字符编码（如UTF-8）。
- **二进制I/O**：`DataInput`/`DataOutput`接口，`RandomAccessFile`支持随机访问。`writeUTF`写入字符串（修改版UTF-8）。
- **对象序列化**：实现`Serializable`接口即可。每个对象有序列号，重复引用只保存编号。可自定义`readObject`/`writeObject`，或实现`Externalizable`。`serialVersionUID`用于版本管理。
- **文件操作**：`Path`和`Files`类（Java 7+）。`Files.readAllBytes`、`Files.write`、`Files.copy`、`Files.move`、`Files.walk`（遍历目录树）。内存映射文件（`FileChannel.map`）提高大文件访问效率。
- **正则表达式**：`Pattern`和`Matcher`类。支持`group()`、`find()`、`replaceAll()`、`splitAsStream()`。

### 逻辑推演/叙事脉络

作者首先介绍I/O流的基本概念（`InputStream`/`OutputStream`的`read`/`write`方法），然后展示整个流继承体系（图2.1、2.2），讲解过滤器的组合使用（如`DataInputStream`嵌套`FileInputStream`读取二进制数）。接着介绍字符流：`InputStreamReader`/`OutputStreamWriter`转换字节到字符，`PrintWriter`输出文本，`Scanner`或`BufferedReader`读取文本，并用Employee示例演示文本格式存储。随后讲解二进制I/O：`DataInput`/`DataOutput`方法、`RandomAccessFile`（随机访问，固定记录大小），以及ZIP压缩流。然后深入对象序列化：`ObjectOutputStream`/`ObjectInputStream`、序列化文件格式、修改默认序列化（transient、自定义`readObject`/`writeObject`）、`readResolve`用于单例和类型安全枚举、版本管理、利用序列化实现克隆。之后介绍`Path`和`Files`类：路径操作、读写文件、创建/复制/移动/删除、遍历目录树、ZIP文件系统。接着介绍内存映射文件（`FileChannel.map`）和缓冲区操作（`ByteBuffer`的`get`/`put`、`flip`、`clear`）。最后讲解正则表达式：`Pattern.compile`、`Matcher`的`matches`/`find`/`group`、`replaceAll`、`split`，并提供URL链接提取的完整示例。

### 经典金句/数据

> “In the Java API, an object from which we can read a sequence of bytes is called an input stream. An object to which we can write a sequence of bytes is called an output stream.” (p.64)

> “There is no reliable way to automatically detect the character encoding from a stream of bytes. For that reason, you should always explicitly specify the encoding.” (p.79)

> 内存映射文件性能测试：37MB的rt.jar文件，普通输入流110秒，缓冲输入流9.9秒，内存映射文件7.2秒。(p.141)

---

## 第3章：XML

### 核心论点

本章讲解XML处理：XML文档结构、DOM解析器、DTD和XML Schema验证、XPath定位信息、命名空间、流式解析器（SAX、StAX）、生成XML文档、XSL转换。作者强调XML用于描述结构化信息，验证机制能极大简化编程。

### 关键概念/事件

- **DOM解析**：`DocumentBuilderFactory`、`DocumentBuilder`、`Document`。树结构由`Node`接口及其子接口实现。需注意处理空白字符。
- **验证**：DTD（`<!ELEMENT>`、`<!ATTLIST>`）或XML Schema。设置`factory.setValidating(true)`启用验证，`setIgnoringElementContentWhitespace(true)`忽略元素间空白。
- **XPath**：用于快速定位XML节点。`XPathFactory`、`XPath`、`evaluate()`方法。表达式如`/configuration/database/username`。
- **流式解析**：SAX（事件回调，`DefaultHandler`覆盖`startElement`等）和StAX（拉解析，`XMLStreamReader`遍历事件）。适用于处理大型文档或只关心部分节点。
- **生成XML**：构建DOM树后使用`Transformer`（XSLT）输出。也可使用StAX的`XMLStreamWriter`直接写入。
- **XSL转换**：提供样式表（`.xsl`），`Transformer`将XML转换为HTML、文本或其他XML格式。

### 逻辑推演/叙事脉络

作者首先介绍XML的概念、与HTML的区别、文档结构（头、DTD、根元素、元素/属性、字符引用、CDATA等）。然后讲解DOM解析：`DocumentBuilder`读取文件得到`Document`，遍历节点树获取信息，以`TreeViewer`程序展示XML树。接着详细讲解验证：DTD的ELEMENT规则、ATTLIST规则，以及如何配置解析器使用验证并忽略空白。还用字体对话框的XML布局作为实际案例，展示如何从XML构建Swing界面。然后介绍XPath语法和API（`XPathFactory`、`evaluate`）。之后讲解命名空间（`setNamespaceAware(true)`）。接着介绍流式解析器：SAX（`DefaultHandler`事件回调，提取HTML链接）和StAX（`XMLStreamReader`拉解析）。然后讲解生成XML：构建DOM树（`DocumentBuilder.newDocument()`）后用`Transformer`输出，或用StAX的`XMLStreamWriter`直接写入。最后介绍XSL转换：提供样式表文件，`TransformerFactory`创建`Transformer`，`transform`方法转换`Source`到`Result`，并以非XML数据源（SAXSource）生成为例展示技巧。

### 经典金句/Data

> “The DOM parser is easier to use for most purposes. You may consider a streaming parser if you process very long documents whose tree structures would use up a lot of memory, or if you are only interested in a few elements and don’t care about their context.” (p.175)

> “That is why DTDs are so useful. You don’t overload your program with rule checking code—the parser has already done that work by the time you get the document.” (p.198)

---

## 第4章：网络

### 核心论点

本章讲解Java网络编程：连接到服务器（Socket、InetAddress）、实现服务器（ServerSocket、多线程服务）、可中断套接字（SocketChannel）、获取Web数据（URL、URLConnection、提交表单数据）以及发送电子邮件。

### 关键概念/事件

- **Socket**：`Socket`建立TCP连接，`getInputStream()`/`getOutputStream()`获取流。可设置超时（`setSoTimeout`）。`InetAddress`处理IP地址和主机名转换。
- **实现服务器**：`ServerSocket`监听端口，`accept()`接受连接。为每个连接启动新线程处理，实现多客户端并发。
- **可中断套接字**：使用`SocketChannel`（NIO）替代`Socket`，阻塞操作可被`interrupt()`中断。
- **URL与URLConnection**：`URL.openStream()`直接获取内容。`URLConnection`提供更多控制：设置请求头、读取响应头、处理密码认证、POST表单数据（`setDoOutput(true)`）。
- **发送邮件**：使用JavaMail API（需下载JAR）。通过`Session`和`MimeMessage`构建邮件，`Transport`发送。

### 逻辑推演/叙事脉络

作者首先用`telnet`演示连接时间服务（port 13）和HTTP服务（port 80），引导读者理解网络协议。然后编写`SocketTest`程序连接时间服务器，并介绍`InetAddress`获取IP地址。接着实现`EchoServer`，使用`ServerSocket`接受连接，回显客户端输入。随后改进为多线程版本（`ThreadedEchoServer`），为每个连接启动独立线程。然后介绍可中断套接字：对比`Socket`（阻塞不可中断）和`SocketChannel`（可中断），用按钮演示中断效果。之后讲解URL和URLConnection：`URL`直接读取内容，`URLConnection`设置请求头、读取响应头、处理密码认证（Base64编码）。然后重点讲解POST表单数据：分析目标表单的参数，用`setDoOutput(true)`获取输出流，`URLEncoder.encode`编码参数。最后介绍发送邮件：下载JavaMail，配置属性（SMTP服务器、认证等），构建`MimeMessage`，用`Transport`连接并发送。

### 经典金句/Data

> “The Socket class is pleasant and easy to use because the Java library hides the complexities of establishing a networking connection and sending data across it.” (p.278)

> “If you don’t want to deal with buffers, you can use the Scanner class to read from a SocketChannel because Scanner has a constructor with a ReadableByteChannel parameter.” (p.292)

---

## 第5章：数据库编程

### 核心论点

本章讲解JDBC（Java数据库连接）API：JDBC设计（驱动类型）、SQL基础、配置JDBC（数据库URL、驱动JAR、注册驱动、建立连接）、执行SQL语句（Statement、PreparedStatement、ResultSet）、可滚动/可更新结果集、行集（RowSet）、元数据（DatabaseMetaData、ResultSetMetaData）、事务（commit、rollback、savepoint、批处理）以及Web和企业应用中的连接管理。

### 关键概念/事件

- **JDBC驱动类型**：类型1（JDBC-ODBC桥，已废弃）、类型2（本地API）、类型3（中间服务器）、类型4（纯Java，直接数据库协议）。
- **连接与执行**：`DriverManager.getConnection(url, user, password)`。`Statement`执行静态SQL，`PreparedStatement`预编译带参数的SQL（防止SQL注入）。`ResultSet`遍历查询结果。
- **可滚动/可更新结果集**：`createStatement(type, concurrency)`。`TYPE_SCROLL_INSENSITIVE`支持前后移动，`CONCUR_UPDATABLE`支持更新行、插入行、删除行。
- **行集（RowSet）**：`CachedRowSet`断开连接后仍可使用，支持分页（`setPageSize`），可接受更改后同步回数据库（`acceptChanges`）。
- **元数据**：`DatabaseMetaData`获取数据库信息（表列表、支持特性）。`ResultSetMetaData`获取列数、列名、列类型。
- **事务**：`setAutoCommit(false)`关闭自动提交，`commit()`提交，`rollback()`回滚。`setSavepoint()`创建保存点。批处理（`addBatch()`、`executeBatch()`）提高批量插入性能。

### 逻辑推演/叙事脉络

作者首先介绍JDBC的设计目标和驱动类型，然后简要介绍SQL语言（SELECT、INSERT、UPDATE、DELETE、CREATE TABLE）。接着讲解JDBC配置：数据库URL格式、获取驱动JAR文件、启动数据库服务器（如Derby的`derbyrun.jar`）、注册驱动类（`Class.forName`或`jdbc.drivers`属性），最后连接数据库。然后演示如何执行SQL语句：`Statement`的`executeUpdate`和`executeQuery`，遍历`ResultSet`获取结果。使用`ExecSQL`程序批量执行SQL脚本填充数据库。接着演示复杂查询：使用`PreparedStatement`绑定参数，处理涉及多表JOIN的查询。之后介绍读取和写入LOB（`Blob`/`Clob`）、SQL转义语法、处理多个结果集、获取自动生成键。然后讲解可滚动和可更新的结果集（`SCROLL_INSENSITIVE`、`CONCUR_UPDATABLE`），演示如何编辑结果集并同步到数据库。接着介绍`RowSet`：`CachedRowSet`断开连接后操作，分页加载，`acceptChanges`同步更新。随后介绍元数据：`DatabaseMetaData`枚举表、`ResultSetMetaData`动态构建通用数据显示组件（`ViewDB`程序）。最后讲解事务：关闭自动提交、提交/回滚、保存点、批处理，以及Web/企业环境中的连接管理（JNDI数据源、连接池）。

### 经典金句/Data

> “A rule of thumb: If you can do it in SQL, don’t do it in Java.” (p.361)

> “The major reason for grouping statements into transactions is database integrity.” (p.395)

---

## 第6章：日期和时间API

### 核心论点

本章介绍Java 8全新的日期和时间API（`java.time`包），解决了旧版`Date`和`Calendar`的诸多问题。核心类包括：`Instant`（时间线上的点）、`LocalDate`（本地日期）、`LocalTime`（本地时间）、`ZonedDateTime`（带时区的时间）、`Period`/`Duration`（时间差）、`DateTimeFormatter`（格式化和解析）。

### 关键概念/事件

- **时间线（Instant）**：`Instant.now()`获取当前时刻，`Duration.between()`计算时间差。`Instant`不考虑时区和日历。
- **本地日期（LocalDate）**：`now()`、`of()`创建。`plusDays`、`minusMonths`、`with(TemporalAdjuster)`操作。`Period`表示年月日间隔。`DayOfWeek`枚举。
- **日期调整器（TemporalAdjusters）**：`nextOrSame(DayOfWeek)`、`lastDayOfMonth()`等预定义调整器。可自定义`TemporalAdjuster`。
- **带时区时间（ZonedDateTime）**：`ZoneId.of("America/New_York")`获取时区。注意夏令时导致的“时间缺口”和“歧义时间”。
- **格式化和解析**：`DateTimeFormatter`提供预定义格式（`ISO_DATE_TIME`）、本地化格式（`ofLocalizedDate`）和自定义模式（`ofPattern`）。`parse()`方法解析字符串。

### 逻辑推演/叙事脉络

作者首先指出时间处理的复杂性（闰秒、时区、夏令时），介绍`Instant`作为机器时间表示，用`Duration.between`测量算法执行时间。然后讲解`LocalDate`（本地日期），用程序员节（第256天）示例展示日期计算，强调`plusDays`和`plus(Period)`的区别（是否考虑闰年）。接着介绍日期调整器（`TemporalAdjusters`），演示计算下一个工作日。然后讲解`LocalTime`（本地时间）和`LocalDateTime`。之后深入`ZonedDateTime`，讨论夏令时导致的问题（时间跳过或重复），以及跨夏令时边界时为什么应该使用`Period`而非`Duration`。然后介绍`DateTimeFormatter`：预定义格式、本地化格式（`FormatStyle.SHORT`/`MEDIUM`/`LONG`/`FULL`）、自定义模式（`yyyy-MM-dd`）。最后讲解如何与遗留代码互操作（`Date.toInstant()`、`GregorianCalendar.toZonedDateTime()`等转换方法）。

### 经典金句/Data

> “For that reason, the API designers recommend that you do not use zoned time unless you really want to represent absolute time instances. Birthdays, holidays, schedule times, and so on are usually best represented as local dates or times.” (p.408)

> “The Java Date and Time API specification requires that Java uses a time scale that: Has 86,400 seconds per day. Exactly matches the official time at noon each day. Closely matches it elsewhere, in a precisely defined way.” (p.405)

---

## 第7章：国际化

### 核心论点

本章讲解Java国际化（i18n）支持：`Locale`（语言和国家/地区）、数字/货币/百分比格式化、日期时间格式化、排序和规范化（`Collator`、`Normalizer`）、消息格式化（`MessageFormat`、`ChoiceFormat`）、文本输入输出（字符编码、资源包`ResourceBundle`），并以一个退休计算器的完整示例展示多语言支持。

### 关键概念/事件

- **Locale**：标识语言和国家/地区（如`en-US`、`de-CH`）。`Locale.getDefault()`获取默认区域，`forLanguageTag()`解析标签。
- **数字/日期格式化**：`NumberFormat`（`getNumberInstance`、`getCurrencyInstance`、`getPercentInstance`）和`DateTimeFormatter`（`ofLocalizedDate`、`ofLocalizedTime`）。`DateFormat`已过时，推荐`java.time.format`。
- **排序（Collation）**：`Collator.getInstance(locale)`获取区域相关比较器。`setStrength`（PRIMARY/SECONDARY/TERTIARY）控制敏感度，`setDecomposition`处理字符分解。`CollationKey`提高多次比较效率。
- **消息格式化**：`MessageFormat`支持占位符`{0}`、`{1}`等，可指定格式类型（`{0,number,currency}`）。`ChoiceFormat`实现复数形式（如“1 house”/“2 houses”）。
- **资源包**：`ResourceBundle.getBundle(baseName, locale)`加载属性文件（`.properties`）或类文件。命名层次（`baseName_language_country` → `baseName_language` → `baseName`），父包继承。

### 逻辑推演/叙事脉络

作者首先指出国际化的必要性（语言、日期格式、货币、排序规则等），介绍`Locale`类的组成和创建方式。然后演示`NumberFormat`格式化数字、货币、百分比，并提供`NumberFormatTest`程序让用户切换区域查看效果。接着介绍`Currency`类控制货币符号，以及`DateTimeFormatter`处理日期/时间的本地化（`FormatStyle`）。之后讲解文本排序：`Collator`比较字符串，`setStrength`和`setDecomposition`调整行为，`CollationKey`加速重复比较。还介绍`Normalizer`将字符串转换为标准化形式（NFD、NFC等）。然后讲解消息格式化：`MessageFormat`的占位符和类型指定，`ChoiceFormat`处理复数形式。接着讲解文本输入输出：指定字符编码、行结束符（`%n`）、控制台编码、UTF-8字节顺序标记（BOM）处理、源文件编码（`native2ascii`）。最后介绍资源包：属性文件和类文件两种形式，加载和查找机制，并以退休计算器为例展示完整的多语言支持（英、德、中文界面和资源）。

### 经典金句/Data

> “The Java programming language was the first language designed from the ground up to support internationalization. From the beginning, it had the one essential feature needed for effective internationalization: It used Unicode for all strings.” (p.427)

> “Each locale has a language code (such as en for English) and a country code (such as US for the United States). The locale en_US describes English in the United States, and en_IE is English in Ireland.” (p.44, Chapter 1，但实质内容在卷II第7章，实际页面p.428-429)

---

## 第8章：脚本、编译与注解处理

### 核心论点

本章介绍三种代码处理技术：脚本API（在Java中调用JavaScript等脚本语言）、编译器API（动态编译Java代码）和注解处理（注解的定义、标准注解、源级注解处理、字节码工程）。作者强调注解本身不做任何事，必须有处理工具才有意义。

### 关键概念/事件

- **脚本API**：`ScriptEngineManager`获取脚本引擎（如Nashorn JavaScript引擎）。`eval()`执行脚本，`put()`/`get()`传递变量。`Invocable`接口调用脚本函数。`Compilable`接口编译脚本提高重复执行效率。
- **编译器API**：`JavaCompiler`（`ToolProvider.getSystemJavaCompiler()`）。`StandardJavaFileManager`管理源文件。`CompilationTask`自定义编译过程（内存中源代码、诊断监听器、自定义类加载器）。
- **注解（Annotation）**：`@interface`定义注解。元注解：`@Target`（应用位置）、`@Retention`（保留策略SOURCE/CLASS/RUNTIME）、`@Documented`、`@Inherited`、`@Repeatable`。
- **源级注解处理**：`AbstractProcessor`，在编译时扫描和处理注解，可生成新源文件（但不能修改现有文件）。`RoundEnvironment`获取被注解的元素。
- **字节码工程**：使用ASM库在类文件级别插入字节码（如为`@LogEntry`注解的方法添加日志调用）。可通过Java代理（`premain`）在加载时修改字节码。

### 逻辑推演/叙事脉络

作者首先介绍脚本API：枚举脚本引擎、`eval()`执行脚本、`Bindings`传递变量、`Invocable`调用脚本函数、`Compilable`编译脚本，并以GUI事件处理示例展示如何用脚本定义按钮行为。然后介绍编译器API：`JavaCompiler.run()`简单编译，`CompilationTask`实现更精细控制（内存中源代码、诊断监听器、自定义类加载器），并用动态生成Swing界面的示例演示完整流程。接着介绍注解：注解的定义和语法、元素类型、默认值、简写形式。然后列出标准注解（`@Deprecated`、`@SuppressWarnings`、`@Override`、`@Generated`、`@PostConstruct`、`@PreDestroy`、`@Resource`）和元注解（`@Target`、`@Retention`、`@Documented`、`@Inherited`、`@Repeatable`）。之后讲解源级注解处理：`AbstractProcessor`、`process`方法、语言模型API（`Element`、`TypeElement`等），并以生成`toString`方法的注解处理器为例。最后讲解字节码工程：使用ASM库修改类文件，为`@LogEntry`注解的方法插入日志字节码，以及通过Java代理在类加载时动态修改字节码。

### 经典金句/Data

> “Annotations are tags that you insert into your source code so that some tool can process them. The tools can operate on the source level, or they can process class files into which the compiler has placed annotations.” (p.517)

> “By itself, the @Test annotation does not do anything. It needs a tool to be useful.” (p.518)

---

## 第9章：安全

### 核心论点

本章讲解Java安全模型：类加载器（层次结构、自定义类加载器）、安全管理器（`SecurityManager`、权限检查）、策略文件（权限配置）、用户认证（JAAS框架）、数字签名（消息摘要、签名验证、证书）、加密（对称加密AES、公钥加密RSA、密码流）。作者强调安全是Java平台的核心设计目标。

### 关键概念/事件

- **类加载器**：启动类加载器（rt.jar）、扩展类加载器（`jre/lib/ext`）、系统类加载器（classpath）。自定义类加载器需重写`findClass`，用于解密类文件或从特殊源加载。
- **安全管理器与权限**：`SecurityManager`检查敏感操作（文件、网络、反射等）。策略文件（`.policy`）映射代码源（codebase）到权限集合（`FilePermission`、`SocketPermission`等）。
- **JAAS（认证与授权）**：`LoginContext`认证用户，`LoginModule`实现认证逻辑（如从文件读取用户名/密码和角色）。`Subject.doAsPrivileged`以主体权限执行代码。
- **数字签名**：`MessageDigest`（SHA-256等）生成消息摘要。`KeyPairGenerator`生成公钥/私钥对。`Signature`类签名和验证。`keytool`管理密钥库和证书。
- **加密**：`Cipher`类（AES对称加密），`KeyGenerator`生成密钥。`CipherOutputStream`/`CipherInputStream`简化加密流操作。RSA公钥加密用于安全传输对称密钥。

### 逻辑推演/叙事脉络

作者首先介绍类加载器层次和字节码验证，演示如何自定义类加载器（`CryptoClassLoader`）解密并加载加密的类文件（凯撒密码示例）。然后介绍安全管理器：`checkPermission`机制、权限类的层次、策略文件（`grant`、`permission`）、自定义`Permission`类（`WordCheckPermission`）并集成到`SecurityManager`。接着介绍JAAS：登录配置、`LoginContext`、`CallbackHandler`获取用户名/密码，`LoginModule`验证并添加`Principal`，`Subject.doAsPrivileged`执行受保护操作。然后讲解数字签名：`MessageDigest`计算哈希、`KeyPairGenerator`生成密钥对、`Signature`签名和验证、`keytool`管理证书、`jarsigner`签名JAR文件。最后讲解加密：`Cipher`的AES对称加密、`KeyGenerator`生成密钥、`CipherOutputStream`/`CipherInputStream`、公钥加密RSA与对称加密AES的组合（用RSA加密AES密钥，用AES加密数据）。

### 经典金句/Data

> “Three mechanisms help ensure safety: Language design features, an access control mechanism, and code signing.” (p.556)

> “The message digest algorithms are publicly known, and they don’t require secret keys. In that case, the recipient of the forged message and the recomputed fingerprint would never know that the message has been altered. Digital signatures solve this problem.” (p.623)

---

## 第10章：高级Swing

### 核心论点

本章深入讲解Swing的高级组件：列表（`JList`、`ListModel`、`ListCellRenderer`）、表格（`JTable`、`TableModel`、渲染器与编辑器）、树（`JTree`、`TreeModel`、`TreeCellRenderer`）、文本组件（`JFormattedTextField`、`JSpinner`、`JEditorPane`）、进度指示器（`JProgressBar`、`ProgressMonitor`）以及组件组织器和装饰器（`JSplitPane`、`JTabbedPane`、`JDesktopPane`、`JInternalFrame`、`JLayer`）。

### 关键概念/事件

- **列表（JList）**：使用`ListModel`管理数据，`ListCellRenderer`定制显示。`DefaultListModel`支持动态增删元素。
- **表格（JTable）**：`TableModel`提供数据，`DefaultTableModel`简单包装二维数组。`TableCellRenderer`和`TableCellEditor`定制单元格显示和编辑。`TableRowSorter`支持排序和过滤。
- **树（JTree）**：`TreeModel`描述树结构，`DefaultMutableTreeNode`是默认节点实现。`TreePath`标识节点位置。`TreeCellRenderer`定制节点显示。支持节点编辑和事件监听。
- **文本组件**：`JFormattedTextField`支持格式化的输入（数字、日期、掩码等），可安装`DocumentFilter`过滤输入，`InputVerifier`验证。`JSpinner`与`SpinnerModel`配合用于序列值选择。`JEditorPane`显示HTML。
- **进度指示器**：`JProgressBar`显示进度条，`setIndeterminate`表示未知进度。`ProgressMonitor`是带取消按钮的对话框。`ProgressMonitorInputStream`自动监控流读取进度。
- **组件组织器**：`JSplitPane`（可调节分割）、`JTabbedPane`（标签页）、`JDesktopPane`（桌面）和`JInternalFrame`（内部框架）实现多文档界面（MDI）。`JLayer`和`LayerUI`添加装饰层。

### 逻辑推演/叙事脉络

作者首先介绍`JList`：构造、滚动、选择模式、事件监听，然后讲解`ListModel`（`AbstractListModel`生成无限序列）和`DefaultListModel`（动态增删），以及自定义`ListCellRenderer`（显示字体名示例）。接着介绍`JTable`：简单表格、`TableModel`（投资增长计算示例）、操作行列（列类、调整大小、选择模式、排序过滤、隐藏/显示列）、单元格渲染器和编辑器（颜色渲染器、颜色编辑器、组合框编辑器）。然后介绍`JTree`：简单树构建、节点展开/折叠、`TreeModel`和`DefaultMutableTreeNode`、编辑树（添加/删除节点）、节点枚举、自定义渲染器（抽象类用斜体）、树事件监听、自定义`TreeModel`（对象检查器示例）。接着介绍文本组件：`JFormattedTextField`（整数/货币/日期/掩码/IP地址输入）、`DocumentFilter`输入过滤、`InputVerifier`验证、`JSpinner`（数字/列表/日期/自定义排列）。`JEditorPane`显示HTML和超链接。然后介绍进度指示器：`JProgressBar`、`ProgressMonitor`、`ProgressMonitorInputStream`。最后介绍组件组织器：`JSplitPane`、`JTabbedPane`（动态加载标签内容、自定义标签组件）、`JDesktopPane`和`JInternalFrame`（MDI、级联/平铺窗口、可撤销属性更改）、`JLayer`装饰层。

### 经典金句/Data

> “The JTable class hides much of that complexity. You can produce fully functional tables with rich behavior by writing a few lines of code.” (p.677)

> “A tree cell renderer is very similar to a list cell renderer.” (p.748)

---

## 第11章：高级AWT

### 核心论点

本章深入讲解AWT的高级特性：渲染管线（`Graphics2D`）、形状（Shape接口及其实现）、区域（Area几何运算）、笔画（`BasicStroke`粗细/端点/连接/虚线）、颜料（`GradientPaint`渐变、`TexturePaint`纹理）、坐标变换（`AffineTransform`）、裁剪（`clip`）、透明与合成（`AlphaComposite`）、渲染提示（`RenderingHints`）、图像读写（`ImageIO`）、图像处理（`BufferedImageOp`滤镜）、打印（`Printable`、`PrinterJob`、多页打印、打印预览、打印服务）、剪贴板（`Clipboard`、`Transferable`、数据风味）、拖放（拖动源、放置目标）以及平台集成（启动画面、桌面应用启动、系统托盘）。

### 关键概念/事件

- **2D图形**：`Graphics2D`是`Graphics`的子类。`Shape`接口包括`Line2D`、`Rectangle2D`、`Ellipse2D`、`Arc2D`、`QuadCurve2D`、`CubicCurve2D`、`GeneralPath`。`Area`支持几何运算（add、subtract、intersect、exclusiveOr）。
- **笔画与颜料**：`BasicStroke`可设置线宽、端点样式（CAP_*）、连接样式（JOIN_*）、虚线模式。`GradientPaint`渐变填充，`TexturePaint`平铺图像填充。
- **坐标变换**：`AffineTransform`支持平移、缩放、旋转、剪切。`transform()`组合变换，`setTransform`重置（谨慎使用）。
- **透明与合成**：`AlphaComposite`实现Porter-Duff合成规则（`SRC_OVER`最常用）。使用`BufferedImage.TYPE_INT_ARGB`支持透明度。
- **图像处理**：`ImageIO`读写图像（GIF/PNG/JPEG等）。`BufferedImage`的`ColorModel`和`WritableRaster`访问像素。`BufferedImageOp`子类实现变换（`AffineTransformOp`、`RescaleOp`、`LookupOp`、`ConvolveOp`）。
- **打印**：`Printable`接口的`print`方法，`PrinterJob`管理打印任务。`Book`类支持多页打印。打印服务（`PrintService`）和流打印服务（`StreamPrintService`）生成PostScript。
- **剪贴板与拖放**：`Clipboard`（系统剪贴板/本地剪贴板），`Transferable`包装数据。`DataFlavor`描述MIME类型和表示类。`TransferHandler`简化Swing组件的拖放实现。
- **平台集成**：`SplashScreen`显示启动画面。`Desktop`启动默认浏览器/邮件程序/打开/编辑/打印文件。`SystemTray`和`TrayIcon`管理系统托盘图标。

### 逻辑推演/叙事脉络

作者首先介绍2D渲染管线，然后详细讲解各种形状的构造和使用（`Rectangle2D`、`RoundRectangle2D`、`Ellipse2D`、`Arc2D`、`QuadCurve2D`、`CubicCurve2D`、`GeneralPath`），并提供交互式程序让用户拖动控制点。接着介绍`Area`的几何运算。然后讲解`BasicStroke`的端点、连接、虚线样式。再讲解`GradientPaint`和`TexturePaint`。然后深入坐标变换（`AffineTransform`）、裁剪、透明与合成（`AlphaComposite`的12种规则）。之后介绍渲染提示（抗锯齿、文本抗锯齿等）。接着讲解图像读写：`ImageIO`读写单张和多张图像，以及图像处理（`BufferedImageOp`滤镜，包括模糊、锐化、亮度调整、边缘检测、负片、旋转）。然后讲解打印：`Printable`接口、`PrinterJob`打印、页面设置、多页打印（`Book`类）、打印预览自定义对话框、打印服务枚举。接着讲解剪贴板：`StringSelection`和自定义`Transferable`（图像、可序列化对象），本地剪贴板传输对象引用。再讲解拖放：Swing组件的内置拖放支持、自定义拖动源和放置目标（`TransferHandler`、`canImport`、`importData`），演示图像列表的拖放。最后讲解平台集成：`SplashScreen`启动画面（直接绘制或跟进步窗口）、`Desktop`启动浏览器和邮件程序、`SystemTray`托盘图标和通知消息。

### 经典金句/Data

> “The Java 2D API is a more sophisticated class library that you can use to produce high-quality drawings.” (p.866)

> “This example shows the power of transformations. The drawing code is kept simple, and the transformation does all the work of placing the drawing at the appropriate place. Finally, the clip cuts away the part of the image that falls outside the page.” (p.977)

---

## 第12章：本地方法

### 核心论点

本章讲解Java本地接口（JNI）：如何从Java调用C/C++函数（数值参数、字符串参数、访问字段、调用Java方法）、编码签名、处理错误、使用Invocation API从C调用Java代码，并以访问Windows注册表的完整示例结束。

### 关键概念/事件

- **JNI基本步骤**：声明`native`方法 → `javah`生成C头文件 → 实现C函数 → 编译为共享库（`.dll`/`.so`）→ `System.loadLibrary()`加载。
- **类型映射**：Java的`int`对应C的`jint`，`String`对应`jstring`，对象对应`jobject`。JNI函数（`GetStringUTFChars`、`ReleaseStringUTFChars`）转换字符串。
- **访问字段和方法**：`GetFieldID`/`GetXxxField`访问实例字段，`GetMethodID`/`CallXxxMethod`调用Java方法。方法签名编码（如`(I)V`表示int参数无返回值）。
- **处理错误**：`ThrowNew`抛出Java异常，`ExceptionOccurred`检查异常，`ExceptionClear`清除异常。
- **Invocation API**：`JNI_CreateJavaVM`创建虚拟机，从C程序调用Java代码。
- **完整示例**：封装Windows注册表API，实现`Win32RegKey`类，支持读取/设置字符串、整数、字节数组值，枚举子项名称。

### 逻辑推演/叙事脉络

作者首先通过一个简单的`greeting`本地方法演示JNI的基本流程：声明native、`javah`生成头文件、实现C函数（`printf`）、编译为动态库、`System.loadLibrary`加载。然后介绍数值参数和返回值（`jint`、`jdouble`等），并以`printf`格式化浮点数为示例。接着讲解字符串参数：`GetStringUTFChars`和`ReleaseStringUTFChars`转换，`NewStringUTF`创建新字符串，实现`sprintf`的Java包装。然后讲解访问实例字段：`GetObjectClass`、`GetFieldID`、`GetDoubleField`/`SetDoubleField`，重写`Employee.raiseSalary`。讲解静态字段类似。接着介绍方法签名编码规则，调用Java方法（`CallVoidMethod`），在`fprint`中调用`PrintWriter.print`。然后讲解构造函数调用（`NewObject`，方法名`<init>`）、数组访问（`GetArrayLength`、`GetObjectArrayElement`、`GetXxxArrayElements`）。接着讲解错误处理：`ThrowNew`抛出异常，`ExceptionOccurred`检查并清理。然后介绍Invocation API：从C程序创建Java虚拟机，调用`Welcome.main`。最后提供完整示例：`Win32RegKey`类封装Windows注册表API，支持读取/设置值（字符串/整数/字节数组），枚举键名，演示了JNI的大部分特性。

### 经典金句/Data

> “There is no reliable way to automatically detect the character encoding from a stream of bytes. For that reason, you should always explicitly specify the encoding.” (p.79) [注：此句在第2章，但本章无更好金句，选取本章核心观点作为替代]

> 实际上，本章作者总结：“While a ‘100% Pure Java’ solution is nice in principle, there are situations in which you will want to write (or use) code written in another language.” (p.1060)

---

## 附录A：Java关键字

附录A列出了Java语言的所有保留关键字（共50个），包括`abstract`、`assert`、`boolean`、`break`、`byte`、`case`、`catch`、`char`、`class`、`const`（未使用）、`continue`、`default`、`do`、`double`、`else`、`enum`、`extends`、`final`、`finally`、`float`、`for`、`goto`（未使用）、`if`、`implements`、`import`、`instanceof`、`int`、`interface`、`long`、`native`、`new`、`null`（直接量）、`package`、`private`、`protected`、`public`、`return`、`short`、`static`、`strictfp`、`super`、`switch`、`synchronized`、`this`、`throw`、`throws`、`transient`、`try`、`void`、`volatile`、`while`。每个关键字标注了在书中首次详细讲解的章节号。

---