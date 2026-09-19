# 《JavaScript权威指南（第七版）》章节总结

## 书籍信息
- **书名**：《JavaScript权威指南（原书第7版）》（JavaScript: The Definitive Guide, Seventh Edition）
- **作者**：大卫·弗拉纳根（David Flanagan）
- **译者**：李松峰
- **出版社**：机械工业出版社 / O'Reilly
- **ISBN**：978-7-111-67722-2
- **出版年份**：2021年

## PDF 状态
- **OCR 状态**：良好，文本可识别，部分特殊符号（如箭头、代码块）需要人工校对
- **目录识别**：完整识别，共17章正文 + 前言、附录等
- **页码状态**：完整，从第1页到第839页

## 全书核心主题

本书是JavaScript语言的权威指南，全面覆盖从基础语法到高级特性的完整知识体系。全书分为三个主要部分：

1. **核心JavaScript语言**（第1-14章）：包括词法结构、类型、表达式、语句、对象、数组、函数、类、模块、标准库、迭代器、异步编程、元编程等语言核心特性。

2. **客户端JavaScript**（第15章）：详细讲解浏览器中的JavaScript，包括DOM操作、事件处理、CSS操作、图形绘制（Canvas/SVG）、网络请求（Fetch/WebSocket）、客户端存储、Web组件、工作线程等Web API。

3. **服务器端JavaScript**（第16章）：介绍Node.js编程基础，包括文件系统操作、网络编程、流处理、子进程、工作线程等。

第17章补充介绍了现代JavaScript开发工具链：ESLint、Prettier、Jest、npm、打包工具、Babel、JSX、Flow等。

作者的核心目标是帮助读者“真正掌握JavaScript”，不仅理解语法，更深入理解语言的设计原理、底层机制和最佳实践。

---

## 第1章：JavaScript简介

### 核心论点

**问题**：JavaScript是什么？它为什么如此重要？如何开始学习？
**观点**：JavaScript是Web编程语言，已发展为适合大规模软件工程的严肃通用语言。本章通过快速概览帮助读者建立对语言的整体认知。

### 关键概念

- **JavaScript vs ECMAScript**：JavaScript是注册商标名，标准版本称为ECMAScript（ES）。ES6（2015）是重要转折点，增加了类、模块等特性。
- **严格模式**：使用`"use strict"`指令可切换到严格模式，纠正早期语言缺陷。
- **宿主环境**：核心JavaScript语言不包含输入/输出，这些由浏览器或Node等宿主环境提供。
- **解释器**：可通过浏览器开发者工具控制台或Node交互式环境尝试代码。

### 逻辑推演

作者首先介绍JavaScript的历史地位和命名由来，区分JavaScript与Java的关系。接着说明语言版本的演进（ES5作为兼容性基准，ES6及之后每年发布）。然后通过代码示例快速展示变量、对象、数组、函数、方法、类等核心语法。最后给出完整的Node程序示例（字符频率柱形图），展示真实JavaScript程序的面貌。

### 经典金句

> “JavaScript是Web编程语言。绝大多数网站都使用JavaScript，所有现代Web浏览器都包含JavaScript解释器，这让JavaScript成为有史以来部署最广泛的编程语言。” (p.17)

> “JavaScript经历了很长时间才从一门脚本语言成长为一门健壮高效的通用语言，适合开发代码量巨大的重要软件工程和项目。” (p.17)

---

## 第2章：词法结构

### 核心论点

**问题**：JavaScript程序的基本书写规则是什么？
**观点**：词法结构定义了变量命名、注释、分号等最低级语法规则，是理解JavaScript代码的基础。

### 关键概念

- **区分大小写**：关键字、变量、函数名必须保持大小写一致（`while`不能写成`While`）。
- **标识符**：以字母、下划线或`$`开头，后续可以是字母、数字、下划线或`$`。
- **保留字**：`if`、`while`、`for`等不能用作标识符；`let`有复杂规则。
- **Unicode转义序列**：`\u00E9`或`\u{E9}`表示Unicode字符。
- **可选分号**：JavaScript在换行符处可自动插入分号，但有例外（`return`后不能换行）。

### 逻辑推演

作者从最基础的文本构成开始，依次讲解大小写敏感性、空格和换行的处理方式，然后介绍单行注释`//`和多行注释`/* */`。接着说明字面量的概念（如`12`、`"hello"`）。随后详细列出标识符规则和保留字列表。最后重点讨论Unicode支持和分号自动插入的微妙规则（`return`、`++`、箭头函数等特殊情况）。

### 经典金句

> “JavaScript标识符必须以字母、下划线（_）或美元符号（$）开头。后续字符可以是字母、数字、下划线或美元符号（数字不能作为第一个字符）。” (p.41)

> “JavaScript只在下一个非空格字符无法被解释为当前语句的一部分时才把换行符当作分号。” (p.45)

---

## 第3章：类型、值和变量

### 核心论点

**问题**：JavaScript支持哪些数据类型？变量如何声明和赋值？
**观点**：JavaScript类型分为原始类型（数值、字符串、布尔值、null、undefined、Symbol）和对象类型，变量使用`let`/`const`声明，具有块作用域。

### 关键概念

- **原始类型 vs 对象类型**：原始值不可修改（immutable），对象可修改（mutable）。原始值按值比较，对象按引用比较。
- **数值**：64位浮点格式（IEEE 754），可表示±5×10⁻³²⁴到±1.8×10³⁰⁸。BigInt用于任意精度整数。
- **字符串**：16位UTF-16编码序列，不可修改。模板字面量（反引号）支持表达式插值。
- **类型转换**：JavaScript会自动将值转换为所需类型（如`10 + "objects"`得到`"10 objects"`）。
- **变量声明**：`let`声明变量（块作用域），`const`声明常量，`var`（函数作用域，已过时）。
- **解构赋值**：从数组或对象中提取值赋给变量，如`let [x, y] = [1, 2]`。

### 逻辑推演

作者从类型概述开始，区分原始类型和对象类型。然后分别详细讲解数值（整数、浮点数、算术、舍入错误、BigInt）、字符串（字面量、转义序列、方法、模板字面量）、布尔值（真性值/假性值）、null/undefined、Symbol。接着讨论全局对象、可修改性差异。之后重点讲解类型转换规则（显式和隐式），包括对象到原始值的复杂转换（toString/valueOf）。最后介绍变量声明（let/const/var）和解构赋值。

### 经典金句

> “原始值是不可修改的，即没有办法改变原始值。对象不同于原始值，对象是可修改的。” (p.76)

> “JavaScript变量可以保存任何类型的值。例如，在JavaScript中，给一个变量赋一个数值，然后再给它赋一个字符串是合法的。” (p.91)

> “严格相等操作符===不进行类型转换；相等操作符==会进行类型转换。” (p.124)

---

## 第4章：表达式与操作符

### 核心论点

**问题**：JavaScript中如何通过表达式和操作符构建计算逻辑？
**观点**：表达式是可以求值的短语，操作符组合简单表达式为复杂表达式。理解操作符优先级、结合性和求值顺序是正确书写表达式的关键。

### 关键概念

- **主表达式**：字面量（`1.23`、`"hello"`）、保留字（`true`、`false`、`null`、`this`）、变量引用。
- **对象/数组初始化程序**：`{x:1, y:2}`、`[1,2,3]`。
- **函数定义表达式**：`function(x) { return x*x; }`，箭头函数`x => x*x`。
- **属性访问**：`o.x`（点语法）或`o["x"]`（方括号）。条件式访问`?.`（可选链）。
- **操作符优先级**：`*` > `+` > `=`，圆括号可改变优先级。
- **算术操作符**：`+`、`-`、`*`、`/`、`%`、`**`（幂）。
- **逻辑操作符**：`&&`（与）、`||`（或）、`!`（非），具有短路行为。
- **赋值操作符**：`=`、`+=`、`-=`等。
- **条件操作符**：`?:`（三元）。
- **`??`操作符**：空值合并，返回第一个非null/undefined的操作数。
- **`typeof`**：返回操作数类型的字符串。
- **`delete`**：删除对象属性。
- **逗号操作符**：`a, b`返回第二个操作数的值。

### 逻辑推演

作者首先区分表达式与语句的概念，然后从最简单的“主表达式”开始，逐步介绍对象/数组初始化、函数定义、属性访问、函数调用、对象创建等表达式形式。接着用表格总览所有操作符的优先级、结合性和操作数类型。之后分节详细讲解：算术表达式（包括`+`的特殊字符串拼接行为）、关系表达式（`===`、`==`、`<`、`in`、`instanceof`）、逻辑表达式（`&&`/`||`的短路特性）、赋值表达式、求值表达式（`eval()`的用法和限制）。最后介绍`?:`、`??`、`typeof`、`delete`、`await`、`void`、逗号等操作符。

### 经典金句

> “如果JavaScript表达式像短语，那JavaScript语句就像完整的句子。” (p.148)

> “+操作符优先字符串拼接：只要有操作数是字符串或可以转换为字符串的对象，另一个操作数也会被转换为字符串并执行拼接操作。” (p.118)

> “&&的这种行为有时候也被称为短路。” (p.130)

> “??操作符选择其先定义的操作数，如果其左操作数不是null或undefined，就返回该值。” (p.141)

---

## 第5章：语句

### 核心论点

**问题**：JavaScript有哪些控制程序执行流程的语句结构？
**观点**：语句是JavaScript的“句子”，包括表达式语句、声明语句、条件语句、循环语句和跳转语句。

### 关键概念

- **表达式语句**：有副效应的表达式（赋值、函数调用、`delete`）作为语句使用。
- **复合语句**：用`{}`将多条语句组合成一个语句块。
- **条件语句**：`if/else`（分支）和`switch`（多分支）。
- **循环语句**：`while`、`do/while`、`for`、`for/of`（迭代可迭代对象）、`for/in`（枚举对象属性名）。
- **跳转语句**：`break`、`continue`、`return`、`throw`、`yield`（生成器）。
- **异常处理**：`try/catch/finally`。
- **严格模式**：`"use strict"`指令，修复语言缺陷，增强错误检查。
- **声明语句**：`let`、`const`、`var`、`function`、`class`、`import`、`export`。

### 逻辑推演

作者将语句分类讲解：首先是表达式语句（赋值、函数调用等），然后是复合语句（语句块）和空语句（`;`）。接着详细讲解条件语句（`if/else`、`switch`），注意`else`的悬挂问题。然后介绍五种循环：`while`、`do/while`、`for`、`for/of`（ES6，专门用于可迭代对象）、`for/in`（枚举属性）。之后讲解跳转语句：`break`（退出循环/switch）、`continue`（跳到下一次迭代）、`return`（函数返回值）、`yield`（生成器）、`throw`（抛出异常）、`try/catch/finally`（异常处理）。最后介绍`with`（已废弃）、`debugger`、`"use strict"`指令，以及声明语句。

### 经典金句

> “语句被执行后会导致某事件发生。” (p.148)

> “for/of循环专门用于可迭代对象。数组、字符串、集合和映射都是可迭代的。” (p.164)

> “在严格模式下，所有变量都必须声明。如果把值赋给一个标识符，而这个标识符是没有声明的变量，会导致抛出一个ReferenceError。” (p.182)

---

## 第6章：对象

### 核心论点

**问题**：JavaScript中对象的核心机制是什么？如何创建、查询、设置、删除和枚举属性？
**观点**：对象是属性的无序集合，支持原型继承。对象字面量是创建对象的便捷方式，ES6增加了简写属性、计算属性名、扩展操作符等新语法。

### 关键概念

- **对象属性特性**：writable（可写）、enumerable（可枚举）、configurable（可配置）。
- **原型**：对象从原型对象继承属性。`Object.prototype`是所有普通对象的原型根。
- **创建对象**：对象字面量`{}`、`new`构造函数、`Object.create()`。
- **属性访问**：`.`和`[]`。`?.`条件式访问（可选链）。
- **继承**：查询属性时会沿原型链向上查找；设置属性只影响对象本身，不修改原型。
- **删除属性**：`delete`操作符。
- **测试属性**：`in`操作符、`hasOwnProperty()`、`propertyIsEnumerable()`。
- **枚举属性**：`for/in`循环、`Object.keys()`、`Object.getOwnPropertyNames()`。
- **扩展对象**：`Object.assign()`。
- **序列化**：`JSON.stringify()`和`JSON.parse()`。
- **对象方法**：`toString()`、`toLocaleString()`、`valueOf()`、`toJSON()`。
- **ES6扩展**：简写属性（`{x, y}`）、计算属性名（`{[propName]: value}`）、扩展操作符（`{...obj}`）、简写方法（`method() {}`）、getter/setter。

### 逻辑推演

作者首先定义对象的基本概念（属性的无序集合、原型继承）。然后介绍创建对象的三种方式，并重点解释原型链。接着详细讲解属性的查询和设置（包括关联数组用法、继承、访问错误）。之后介绍删除、测试、枚举属性的方法。然后是扩展对象（`Object.assign`）和序列化（JSON）。再讲解原型上的通用方法（`toString`等）。最后用较大篇幅介绍ES6新增的对象字面量扩展语法：简写属性、计算属性名、符号作为属性名、扩展操作符、简写方法、getter/setter。

### 经典金句

> “对象是一种复合值，它汇聚多个值并允许我们按名字存储和获取这些值。” (p.190)

> “JavaScript对象是动态的，即可以动态添加和删除属性。” (p.190)

> “如果两个对象从同一个原型继承属性，我们说这些对象是同一个类的实例。” (p.308)

---

## 第7章：数组

### 核心论点

**问题**：JavaScript数组有哪些特性？如何创建、操作和迭代数组？
**观点**：数组是值的有序集合，是特殊的对象。数组是动态的、可以稀疏，支持丰富的操作方法（迭代、栈/队列、切片、排序等）。

### 关键概念

- **创建数组**：数组字面量`[1,2,3]`、`Array()`构造函数、`Array.of()`、`Array.from()`。
- **扩展操作符**：`[...arr]`用于展开数组。
- **稀疏数组**：元素没有连续索引的数组（如`[1,,3]`），`length`大于实际元素个数。
- **数组长度**：`length`属性可读可写，设置较小值会截断数组。
- **迭代数组**：`for/of`、`forEach()`、经典`for`循环。
- **数组方法**：
  - 迭代器：`forEach()`、`map()`、`filter()`、`find()`、`findIndex()`、`every()`、`some()`、`reduce()`、`reduceRight()`
  - 打平：`flat()`、`flatMap()`
  - 添加/删除：`push()`/`pop()`（栈）、`unshift()`/`shift()`（队列）、`splice()`（通用）
  - 切片：`slice()`
  - 填充/复制：`fill()`、`copyWithin()`
  - 搜索：`indexOf()`、`lastIndexOf()`、`includes()`
  - 排序：`sort()`、`reverse()`
  - 转换：`join()`、`toString()`
- **类数组对象**：具有`length`和数值索引的对象，可通过`Array.prototype`方法调用。
- **字符串作为数组**：字符串是只读的类数组。

### 逻辑推演

作者首先定义数组的基本特性（有序集合、动态、可稀疏）。然后介绍创建数组的多种方式。接着讲解读写数组元素、稀疏数组、`length`属性的特殊行为。之后介绍添加/删除元素的方法。然后重点讲解迭代数组的多种方式（`for/of`、`forEach`）。之后用较大篇幅详细讲解数组的所有方法，分类为：迭代器方法（`map`、`filter`、`reduce`等）、打平方法（`flat`/`flatMap`）、栈/队列方法、子数组方法（`slice`/`splice`）、填充/复制方法、搜索/排序方法、转换方法。最后讨论类数组对象和字符串作为数组的特性。

### 经典金句

> “数组是值的有序集合，其中的值叫作元素，每个元素有一个数值表示的位置，叫作索引。” (p.223)

> “JavaScript数组是无类型限制的，即数组中的元素可以是任意类型，同一数组的不同元素也可以是不同的类型。” (p.223)

> “map()方法把调用它的数组的每个元素分别传给我们指定的函数，返回这个函数的返回值构成的数组。” (p.238)

---

## 第8章：函数

### 核心论点

**问题**：JavaScript中函数有哪些定义和调用方式？什么是闭包？
**观点**：函数是JavaScript的一等公民（可作为值传递），支持词法作用域和闭包。箭头函数、默认参数、剩余参数等ES6特性极大增强了函数表达能力。

### 关键概念

- **函数定义**：函数声明（`function f() {}`）、函数表达式（`const f = function() {}`）、箭头函数（`() => {}`）。
- **调用方式**：作为函数、作为方法、作为构造函数、通过`call()`/`apply()`间接调用。
- **参数处理**：可选参数（默认值）、剩余参数（`...args`）、解构参数、Arguments对象（已过时）。
- **闭包**：函数记住并访问其定义时的作用域，即使函数在其定义作用域之外执行。
- **函数属性**：`length`（形参个数）、`name`（函数名）、`prototype`（原型对象）。
- **函数方法**：`call()`、`apply()`、`bind()`。
- **函数式编程**：高阶函数、部分应用（柯里化）、记忆（memoization）。

### 逻辑推演

作者首先定义函数的概念（可调用的代码块、参数化、返回值、调用上下文`this`）。然后介绍三种定义方式（声明、表达式、箭头函数），强调箭头函数与普通函数的区别（无`prototype`、继承`this`）。接着讲解五种调用方式，重点解释方法调用中的`this`绑定和常见错误（嵌套函数丢失`this`）。之后详细讨论参数处理：默认值、剩余参数、解构、扩展操作符在调用时的使用。然后深入讲解闭包的概念和用法，包括私有变量、循环中的闭包陷阱（用`let`解决）。再介绍函数的属性和方法（`length`、`name`、`prototype`、`call/apply/bind`）。最后介绍函数式编程技巧（高阶函数、部分应用、记忆）。

### 经典金句

> “函数不仅是语法，也是值。这意味着可以把函数赋值给变量、保存为对象的属性或者数组的元素、作为参数传给其他函数。” (p.281)

> “闭包会捕获自身定义所在外部函数的局部变量（及参数）绑定。” (p.288)

> “箭头函数从定义自己的环境继承this关键字的值，而不是像以其他方式定义的函数那样定义自己的调用上下文。” (p.262)

---

## 第9章：类

### 核心论点

**问题**：JavaScript中如何定义和使用类？基于原型的继承如何工作？
**观点**：JavaScript类基于原型继承，ES6的`class`语法是底层原型机制的语法糖。类支持构造函数、实例方法、静态方法、getter/setter，以及通过`extends`实现子类。

### 关键概念

- **原型与类**：一组对象从同一个原型对象继承属性，这些对象就是同一个类的实例。
- **构造函数**：使用`new`调用的函数，其`prototype`属性成为新对象的原型。
- **`class`语法**：ES6引入，包含`constructor`方法、实例方法、`static`方法、getter/setter。
- **实例字段**：在构造函数中定义。私有字段（`#field`）提案。
- **继承**：`extends`关键字创建子类，`super`调用父类构造函数和方法。
- **`instanceof`**：检查对象是否是某个类的实例（检查原型链）。
- **组合 vs 继承**：委托（组合）通常比继承更灵活。

### 逻辑推演

作者首先回顾原型和继承的基础。然后通过一个Range类的示例，先展示不使用构造函数的“工厂函数”方式，再展示使用构造函数的传统方式，对比两者的区别。接着解释构造函数、`prototype`属性、`constructor`属性的关系，以及`instanceof`的工作原理。之后重点讲解ES6的`class`语法：如何定义类、构造函数、实例方法、静态方法、getter/setter。还介绍了实例字段和私有字段的提案。然后通过Complex类作为完整示例。接着讨论如何为已有类添加方法（包括内置类）。最后详细讲解子类的实现：先展示ES6之前的原型链继承方式，再展示ES6的`extends`和`super`语法，最后讨论“组合优于继承”的原则，并通过抽象类层次示例（各种Set类）展示完整的类设计模式。

### 经典金句

> “在JavaScript中，类使用基于原型的继承。如果两个对象从同一个原型继承属性，我们说这些对象是同一个类的实例。” (p.308)

> “新增class关键字并未改变JavaScript类基于原型的本质。新的class语法虽然明确、方便，但最好把它看成更基础的类定义机制的‘语法糖’。” (p.319)

> “应该‘能组合就不继承’（favor composition over inheritance）。” (p.335)

---

## 第10章：模块

### 核心论点

**问题**：JavaScript中如何组织代码模块？Node模块和ES6模块有什么区别？
**观点**：模块化用于封装和隐藏实现细节，避免命名冲突。Node使用CommonJS（`require`/`module.exports`），ES6使用`import`/`export`。

### 关键概念

- **模块化目标**：封装、隐藏私有实现、保证全局命名空间清洁。
- **基于闭包的模块**：使用立即调用函数表达式（IIFE）创建私有作用域，返回公共API。
- **Node模块（CommonJS）**：每个文件是独立模块，使用`require()`导入，`exports`或`module.exports`导出。
- **ES6模块**：`import`和`export`关键字，静态结构（可在编译时分析）。支持默认导出（`export default`）和命名导出。
- **动态导入**：`import()`函数返回期约，支持按需加载。
- **模块在浏览器中使用**：`<script type="module">`标签，默认延迟执行（defer行为）。
- **`import.meta.url`**：获取当前模块的URL。

### 逻辑推演

作者首先阐述模块化的意义和JavaScript模块化的历史（早期没有内置模块）。然后介绍最简单的模块化方式：基于类、对象和闭包（IIFE）的模块。接着详细讲解Node的模块系统：`require`导入、`exports`/`module.exports`导出、模块加载规则。之后重点介绍ES6模块：`export`的各种形式（命名导出、默认导出）、`import`的各种形式（默认导入、命名导入、`* as`导入）、导入/导出时重命名（`as`）、再导出（`export from`）。然后讨论在浏览器中使用ES6模块的方法（`<script type="module">`、`nomodule`回退）。最后介绍动态导入`import()`和`import.meta.url`。

### 经典金句

> “模块化的作用主要体现在封装和隐藏私有实现细节，以及保证全局命名空间清洁上。” (p.344)

> “在ES6中，JavaScript终于有了自己依托import和export关键字的模块系统。” (p.365)

> “ES6模块中的代码自动应用严格模式。这意味着在使用ES6模块时，永远不用再写use strict了。” (p.352)

---

## 第11章：JavaScript标准库

### 核心论点

**问题**：JavaScript内置了哪些重要的数据结构和API？
**观点**：标准库包括Set/Map、定型数组、正则表达式、Date、Error、JSON、国际化API、Console、URL、计时器等。

### 关键概念

- **Set/Map**：Set是值的集合（无重复），Map是键到值的映射。WeakMap/WeakSet是弱引用版本。
- **定型数组**：固定长度的数值数组，支持多种数值类型（Int8、Uint8、Float64等）。底层基于ArrayBuffer。
- **正则表达式**：`RegExp`对象，支持模式匹配。语法包括字符类、重复、分组、锚点、标志（`g`、`i`、`m`、`s`、`u`、`y`）。
- **Date**：日期和时间操作。时间戳（毫秒数，自1970-01-01起）。
- **Error**：错误对象，包含`message`和`name`属性。子类：`RangeError`、`TypeError`等。
- **JSON**：`JSON.stringify()`和`JSON.parse()`，支持序列化/反序列化。可自定义`toJSON()`。
- **国际化API**：`Intl.NumberFormat`（数值格式化）、`Intl.DateTimeFormat`（日期时间）、`Intl.Collator`（字符串比较）。
- **Console API**：`console.log()`、`console.table()`、`console.time()`等。
- **URL API**：`URL`类解析和操作URL，`URLSearchParams`处理查询参数。
- **计时器**：`setTimeout()`、`setInterval()`、`clearTimeout()`、`clearInterval()`。

### 逻辑推演

作者将标准库分类讲解。首先介绍Set和Map，包括WeakMap/WeakSet。然后详细介绍定型数组（TypedArray）：类型、创建方式、操作、DataView处理字节序。接着用较大篇幅讲解正则表达式：语法（字面量字符、字符类、重复、分组、锚点、标志）和模式匹配的字符串方法（`search`、`replace`、`match`、`matchAll`、`split`），以及`RegExp`类的方法（`test`、`exec`）。然后依次介绍Date、Error、JSON。再介绍国际化API（格式化数值、日期、比较字符串）。最后介绍Console API、URL API和计时器。

### 经典金句

> “集合就是一组值，与数组类似。但与数组不同的是，集合没有索引或顺序，也不允许重复。” (p.368)

> “定型数组的元素全部都是数值。创建定型数组时必须指定长度，且该长度不能再改变。” (p.376)

> “正则表达式是一种描述文本模式的对象。” (p.384)

> “JSON是一种非常通用的数据格式，就连很多非JavaScript程序都支持它。” (p.413)

---

## 第12章：迭代器与生成器

### 核心论点

**问题**：什么是迭代器和生成器？如何创建可迭代对象？
**观点**：迭代器是用于遍历数据结构的统一接口，生成器是创建迭代器的简化语法。可迭代对象实现了`[Symbol.iterator]()`方法。

### 关键概念

- **可迭代对象**：实现`[Symbol.iterator]()`方法的对象，返回迭代器。数组、字符串、Set、Map都是可迭代的。
- **迭代器**：有`next()`方法的对象，返回`{value, done}`。
- **迭代结果对象**：`{value: 当前值, done: boolean}`。
- **`for/of`循环**：自动调用可迭代对象的迭代器。
- **生成器函数**：`function*`定义，使用`yield`产生值。调用返回生成器对象（既是迭代器也是可迭代对象）。
- **`yield*`**：委托给另一个可迭代对象。
- **生成器高级特性**：`yield`表达式可以接收`next()`传入的值；`return()`和`throw()`方法。

### 逻辑推演

作者首先通过`for/of`、扩展操作符、解构赋值等例子展示可迭代对象的使用。然后从原理上讲解三个角色：可迭代对象、迭代器对象、迭代结果对象。接着通过实现Range类的可迭代版本来演示如何自定义可迭代对象（实现`[Symbol.iterator]`方法返回带`next()`的迭代器）。还介绍了迭代器的`return()`方法用于清理。之后介绍生成器：`function*`定义、`yield`产生值、调用返回生成器对象。通过多个示例（斐波那契数列、`take`、`zip`）展示生成器的实用性。接着介绍`yield*`语法和递归生成器。最后讨论生成器的高级特性：返回值、`yield`表达式的值、`return()`和`throw()`方法。

### 经典金句

> “可迭代对象指的是任何具有专用迭代器方法，且该方法返回迭代器对象的对象。” (p.439)

> “生成器是一种使用强大的新ES6语法定义的迭代器，特别适合要迭代的值不是某个数据结构的元素，而是计算结果的场景。” (p.445)

> “生成器的基本特性是可以暂停计算，回送中间结果，然后在某个时刻再恢复计算。” (p.450)

---

## 第13章：异步JavaScript

### 核心论点

**问题**：JavaScript如何处理异步操作？期约、async/await、异步迭代如何工作？
**观点**：异步编程从回调演进制期约，再演进到async/await语法，使异步代码可以像同步代码一样书写。

### 关键概念

- **回调**：异步操作完成时调用的函数。存在回调地狱和错误处理困难的问题。
- **期约（Promise）**：表示异步操作的未来结果。状态：pending、fulfilled、rejected。
- **期约方法**：`then()`（注册兑现回调）、`catch()`（注册拒绝回调）、`finally()`。
- **期约链**：`then()`返回新期约，可串联异步操作。
- **期约并行**：`Promise.all()`（全部兑现）、`Promise.allSettled()`（全部落定）、`Promise.race()`（最先落定）。
- **创建期约**：`Promise.resolve()`、`Promise.reject()`、`new Promise()`。
- **async/await**：`async`函数返回期约，`await`等待期约兑现。使异步代码看起来像同步代码。
- **异步迭代**：`for/await`循环、异步迭代器（`Symbol.asyncIterator`）、异步生成器（`async function*`）。

### 逻辑推演

作者首先说明异步编程在JavaScript中的普遍性（浏览器事件、网络请求、文件I/O）。然后从回调开始讲解：定时器、事件、网络请求（XMLHttpRequest）、Node回调。指出回调的两个问题：嵌套（回调地狱）和错误处理困难。

接着引入期约：定义、状态、`then`/`catch`方法、期约链（通过`fetch`示例详细分析期约解决的过程）。再讲解错误处理和`finally`。然后介绍并行期约（`Promise.all`、`Promise.race`）和创建期约的多种方式（包括`new Promise`构造器）。最后讨论串行期约（顺序执行）。

之后讲解`async/await`：`await`表达式等待期约，`async`函数返回期约。通过对比展示如何将基于期约的代码转换为`async/await`风格。

最后介绍异步迭代：`for/await`循环、异步迭代器（`Symbol.asyncIterator`）、异步生成器（`async function*`），并通过AsyncQueue类示例展示如何实现异步迭代器。

### 经典金句

> “期约是一个对象，表示异步操作的结果。这个结果可能就绪也可能未就绪。” (p.461)

> “async和await关键字极大简化了期约的使用，允许我们像编写阻塞的同步代码一样，编写基于期约的异步代码。” (p.485)

> “只能在以async关键字声明的函数内部使用await关键字。” (p.485)

---

## 第14章：元编程

### 核心论点

**问题**：如何控制JavaScript对象和类的底层行为？
**观点**：元编程允许修改对象的基础行为，包括控制属性特性、对象可扩展性、使用代理拦截操作。

### 关键概念

- **属性特性**：writable、enumerable、configurable。`Object.getOwnPropertyDescriptor()`、`Object.defineProperty()`。
- **对象可扩展性**：`Object.preventExtensions()`、`Object.seal()`（封存）、`Object.freeze()`（冻结）。
- **原型特性**：`Object.getPrototypeOf()`、`Object.setPrototypeOf()`。
- **公认符号（Well-Known Symbols）**：`Symbol.iterator`、`Symbol.asyncIterator`、`Symbol.hasInstance`、`Symbol.toStringTag`、`Symbol.species`、`Symbol.isConcatSpreadable`、`Symbol.toPrimitive`等，用于定制语言内部行为。
- **模板标签**：自定义标签函数处理模板字面量。`String.raw`。
- **反射API**：`Reflect`对象，提供与代理处理器方法一一对应的函数。
- **代理对象（Proxy）**：拦截和自定义对象的基础操作（`get`、`set`、`deleteProperty`等）。

### 逻辑推演

作者首先定义元编程的概念（操作其他代码的代码）。然后从属性特性开始：可写、可枚举、可配置，以及如何查询和设置这些特性（`Object.getOwnPropertyDescriptor`、`Object.defineProperty`）。接着讨论对象的可扩展性（`preventExtensions`、`seal`、`freeze`）。之后介绍原型特性的查询和修改。

然后重点讲解公认符号：这些符号名的方法可以定制对象与JavaScript语言特性的交互（迭代、`instanceof`、`toString`、`species`、`concat`展开、原始值转换等）。

接着介绍模板标签：如何编写自定义标签函数处理模板字面量，以及如何访问原始字符串（`strings.raw`）。

之后介绍反射API（`Reflect`对象），它与代理处理器方法一一对应。

最后详细讲解代理对象（`Proxy`）：创建代理需要目标对象和处理器对象；处理器方法可以拦截13种基础操作（`get`、`set`、`has`、`deleteProperty`、`apply`、`construct`等）；可撤销代理（`Proxy.revocable`）；代理不变式（确保不违反语言基本规则）。通过多个示例（只读代理、日志代理）展示代理的强大能力。

### 经典金句

> “元编程就是写代码去操作其他代码。” (p.498)

> “Proxy类是JavaScript中最强大的元编程特性。使用它可以修改JavaScript对象的基础行为。” (p.524)

> “公认符号可以控制JavaScript对象和类的某些底层行为。” (p.509)

---

## 第15章：浏览器中的JavaScript

### 核心论点

**问题**：如何在浏览器中编写JavaScript程序？浏览器提供了哪些API？
**观点**：浏览器是JavaScript的主要宿主环境，提供了DOM操作、事件处理、网络请求、图形绘制、客户端存储、工作线程等丰富的Web API。

### 关键概念

- **`<script>`标签**：嵌入或引用JavaScript代码。`async`/`defer`属性控制加载行为。`type="module"`用于模块。
- **事件模型**：事件类型、事件目标、事件处理程序（`addEventListener`）、事件对象、事件传播（捕获/冒泡）、事件取消（`preventDefault`/`stopPropagation`）。
- **DOM操作**：选择元素（`querySelector`、`getElementById`）、遍历（`parentNode`、`children`）、属性操作（`getAttribute`、`dataset`、`classList`）、内容操作（`innerHTML`、`textContent`）、创建/插入/删除节点（`createElement`、`append`、`remove`）。
- **CSS操作**：`classList`添加/删除类、`style`属性操作行内样式、`getComputedStyle()`获取计算样式。
- **文档几何**：`getBoundingClientRect()`、`scrollTo()`、`scrollIntoView()`。
- **Web组件**：`<template>`、自定义元素（`customElements.define`）、影子DOM（`attachShadow`）。
- **图形**：SVG（矢量图形）、`<canvas>`（位图图形，2D上下文）。
- **网络**：`fetch()`（基于期约的HTTP请求）、SSE（服务器发送事件）、WebSocket（双向消息）。
- **客户端存储**：`localStorage`/`sessionStorage`、Cookie、IndexedDB。
- **工作线程**：`Worker`类，独立线程执行JavaScript，通过消息传递通信。
- **历史管理**：`history.pushState()`和`popstate`事件。

### 逻辑推演

本章是全书最长的一章（约180页），作者从基础到高级系统讲解浏览器JavaScript编程。

首先介绍Web编程基础：`<script>`标签的使用（`async`/`defer`、模块）、文档对象模型（DOM）、全局对象（`window`）、脚本命名空间、程序执行的两个阶段（加载阶段→事件驱动阶段）、线程模型（单线程）、程序输入/输出、错误处理（`window.onerror`）、安全模型（同源策略、XSS防御）。

然后详细讲解事件机制：事件类型、注册处理程序（属性 vs `addEventListener`）、调用细节（参数、`this`、返回值）、事件传播（捕获→目标→冒泡）、事件取消（`preventDefault`、`stopPropagation`）、自定义事件（`CustomEvent`）。

接着讲解DOM操作：选择元素（`querySelector`系列）、文档结构和遍历（元素属性 vs 节点属性）、属性操作（HTML属性与JavaScript属性的映射、`classList`、`dataset`）、元素内容（`innerHTML`、`textContent`）、创建/插入/删除节点（`append`、`prepend`、`before`、`after`、`replaceWith`、`remove`）。并给出完整的示例：动态生成目录。

之后讲解CSS操作：CSS类、行内样式（`style`对象）、计算样式（`getComputedStyle`）、样式表操作、CSS动画与事件。

然后讲解文档几何与滚动：坐标系统（文档坐标 vs 视口坐标）、查询元素大小（`getBoundingClientRect`）、定位元素（`elementFromPoint`）、滚动（`scrollTo`、`scrollIntoView`）、视口大小。

接着介绍Web组件：`<template>`、自定义元素（`customElements.define`、生命周期回调）、影子DOM（`attachShadow`、封装、插槽）。给出完整的`<search-box>`组件示例。

之后讲解SVG：在HTML中使用、编程操作、动态创建。

接着用较大篇幅讲解`<canvas>` API：路径和多边形、画布大小与坐标、图形属性（线条样式、颜色、渐变、阴影、合成）、绘制操作（矩形、曲线、文本、图片）、坐标系变换、剪切区域、像素操作（`getImageData`/`putImageData`）。给出科赫雪花、运动模糊等示例。

然后介绍Audio API：`Audio()`构造函数、WebAudio API。

再讲解位置、导航与历史：`Location`对象加载新文档、`History`对象管理浏览历史（`pushState` + `popstate`）、`hashchange`事件。

之后讲解网络API：`fetch()`（请求、响应、流式访问、中断请求）、SSE（服务器发送事件）、WebSocket（双向通信）。

然后讲解客户端存储：`localStorage`/`sessionStorage`（生命期、作用域、存储事件）、Cookie（读取、设置、属性）、IndexedDB（对象存储、事务、索引）。给出邮政编码数据库示例。

接着讲解工作线程：`Worker`对象、工作线程中的全局对象（`WorkerGlobalScope`）、导入脚本（`importScripts`）、消息传递（`postMessage`、MessagePort、MessageChannel）、跨源消息传递。

最后以曼德布洛特集合查看器作为全章的综合示例，演示工作线程、`<canvas>`、历史管理、事件处理的协同使用。

### 经典金句

> “客户端JavaScript编程中最重要的一个对象就是Document对象，它代表浏览器窗口或标签页中显示的HTML文档。” (p.541)

> “JavaScript是单线程的语言，而单线程执行让编程更容易：你保证自己写的两个事件处理程序永远不会同时运行。” (p.546)

> “阻止浏览器执行默认动作的标准且推荐的方式，是调用Event对象的preventDefault()方法。” (p.562)

> “Web组件是浏览器原生支持的替代这些框架的特性。” (p.599)

> “fetch() API完全是基于期约的。” (p.662)

---

## 第16章：Node服务器端JavaScript

### 核心论点

**问题**：如何使用Node.js编写服务器端JavaScript程序？
**观点**：Node是JavaScript与操作系统的绑定，提供文件系统、网络、进程、线程等API。Node采用异步非阻塞、基于事件的单线程并发模型。

### 关键概念

- **控制台输出**：`console.log()`、`console.error()`。
- **命令行参数与环境变量**：`process.argv`、`process.env`。
- **程序生命期**：`process.exit()`、信号处理（`SIGINT`）。
- **Node模块**：CommonJS（`require`/`exports`/`module.exports`）与ES6模块（`.mjs`、`package.json`的`type`字段）。
- **npm**：包管理器，`package.json`。
- **异步默认**：Node API默认非阻塞，使用错误在先的回调（`err, result`）。`util.promisify()`转换为期约。
- **Buffer**：字节序列，用于操作二进制数据。支持多种编码（utf8、ascii、hex、base64）。
- **EventEmitter**：基于事件的API，`on()`注册事件、`emit()`触发事件。
- **流（Stream）**：可读流、可写流、双工流、转换流。`pipe()`连接流。背压处理。
- **文件操作**：`fs`模块（读/写/删除/重命名/元数据）、`path`模块（路径操作）。
- **HTTP客户端/服务器**：`http.get()`、`http.request()`、`http.createServer()`。
- **非HTTP网络**：`net`模块创建TCP服务器/客户端。
- **子进程**：`child_process.execSync()`、`spawn()`、`fork()`。
- **工作线程**：`worker_threads`模块，基于消息传递的多线程。

### 逻辑推演

作者首先介绍Node的定位和安装。然后讲解Node编程基础：控制台输出、命令行参数（`process.argv`）和环境变量（`process.env`）、程序生命期（`process.exit`、未捕获异常处理）、Node模块系统（CommonJS与ES6模块的兼容性）、npm包管理器。

接着重点强调Node的异步默认模型：非阻塞API、错误在先的回调、基于事件的事件循环。并展示如何通过`util.promisify()`和`fs.promises`转换为期约，以及同步版本（`Sync`后缀）。

然后介绍Buffer类：创建、与字符串的转换（编码/解码）、与定型数组的关系。

之后讲解EventEmitter：`on`/`emit`、错误事件处理。

然后用较大篇幅讲解流（Stream）：4种流类型、`pipe()`方法、异步迭代器读取流、写入流和背压处理（`write()`返回`false`时停止写入，等待`drain`事件）、基于事件的流读取（流动模式 vs 暂停模式）。给出grep、copyFile等示例。

接着介绍进程、CPU和操作系统细节（`process`对象、`os`模块）。

之后详细讲解文件操作：`fs`模块的路径、文件描述符、FileHandle；读文件（`readFile`、流、底层`read`）；写文件（`writeFile`、流、底层`write`）；文件操作（`copyFile`、`rename`、`link`、`unlink`）；文件元数据（`stat`、`chmod`、`chown`、`utimes`）；目录操作（`mkdir`、`readdir`、`opendir`）。

然后讲解HTTP客户端与服务器：`http.get()`、`http.request()`、`http.createServer()`，并给出静态文件服务器示例。

再介绍非HTTP网络：`net`模块创建TCP服务器和客户端（knock-knock笑话示例）。

之后讲解子进程：`execSync`/`execFileSync`（同步）、`exec`/`execFile`（异步回调）、`spawn`（流式访问）、`fork`（Node子进程，支持`send`消息通信）。

最后介绍工作线程：`worker_threads`模块，`Worker`类、消息传递（`parentPort`、`postMessage`）、MessagePort转移、共享定型数组（`SharedArrayBuffer`、`Atomics`）。

### 经典金句

> “Node是JavaScript与底层操作系统绑定的结合，因而可以让JavaScript程序读写文件、执行子进程，以及实现网络通信。” (p.726)

> “Node的典型特点是由其默认异步的API赋能的单线程基于事件的并发能力。” (p.726)

> “Node通过让其API默认异步和非阻塞实现了高层次的并发，同时保持了单线程的编程模型。” (p.734)

> “Buffer类非常类似字符串，只不过它是字节序列而非字符序列。” (p.738)

---

## 第17章：JavaScript工具和扩展

### 核心论点

**问题**：现代JavaScript开发有哪些重要的工具和语言扩展？
**观点**：ESLint、Prettier、Jest、npm、打包工具（webpack等）、Babel、JSX、Flow/TypeScript构成了现代JavaScript开发生态的核心。

### 关键概念

- **ESLint**：代码检查工具，发现潜在的bug和风格问题。可配置规则。
- **Prettier**：代码格式化工具，自动统一代码风格。
- **Jest**：单元测试框架，内置断言、模拟、覆盖率报告。
- **npm**：包管理器，管理依赖。`package.json`、`node_modules`、`npx`。
- **代码打包工具**：webpack、Rollup、Parcel。将模块打包为浏览器可用的文件。支持摇树优化（tree shaking）、代码分割、热模块替换。
- **Babel**：转译器，将现代JavaScript代码转换为兼容性更好的代码。支持预设（presets）、源码映射。
- **JSX**：JavaScript的语法扩展，在JavaScript中编写类似HTML的标记。React框架广泛使用。Babel将其编译为`React.createElement`调用。
- **Flow**：类型检查器，为JavaScript添加类型注解。`// @flow`、类型注解（`: string`）、类型别名（`type`）、联合类型、可区分联合。TypeScript是更流行的替代品。

### 逻辑推演

作者首先说明本章目的：介绍现代JavaScript开发者常用的工具和扩展。

然后逐一介绍：

1. **ESLint**：通过示例展示如何发现代码问题（未使用变量、`==` vs `===`），可配置规则。
2. **Prettier**：自动格式化代码，解决风格争议。通过示例展示格式化效果。
3. **Jest**：单元测试。通过`getTemperature()`函数示例，演示如何编写测试、模拟依赖、验证异步函数、计算覆盖率。
4. **npm**：包管理。`npm install`、`package.json`、`--save-dev`、`-g`、`npx`、`audit`。
5. **代码打包**：webpack/Rollup/Parcel。解决ES6模块在浏览器中的兼容性。支持树摇优化、代码分割、动态导入、源码映射。
6. **Babel**：转译器。将新语法转换为旧语法。预设配置。
7. **JSX**：React的语法扩展。通过大量示例展示JSX语法：元素、属性、子元素、嵌入表达式、组件（首字母大写）、Babel编译结果。
8. **Flow**：类型检查。安装、`// @flow`注释。类型注解（变量、函数参数、返回值）、类属性注解、对象类型、类型别名、数组类型（`Array<T>`、元组）、参数化类型（`Set<T>`、`Map<K,V>`）、只读类型（`$ReadOnly<T>`）、函数类型、联合类型、字面量类型（枚举）、可区分联合。

最后提到TypeScript是Flow的更流行的替代品。

### 经典金句

> “ESLint定义了很多linting规则，而且有一个插件生态，可以增加新规则。” (p.800)

> “Prettier的默认选项还是比较合适的。只要在项目中采用Prettier，就永远不用再担心代码格式化了。” (p.802)

> “JSX是对核心JavaScript的扩展，它使用HTML风格的语法定义元素树。” (p.812)

> “TypeScript与Flow是给JavaScript添加类型注解的两个最流行的解决方案。” (p.818)

---

## 关于作者/封面

### 作者信息

**David Flanagan** 从1995年起就开始使用JavaScript并写作本书的最初版。他拥有麻省理工学院计算机科学与工程学位，目前是VMware的一名软件工程师。居住在西雅图和温哥华。

### 封面信息

本书封面动物是**爪哇犀牛**（Javan Rhinoceros）。爪哇犀牛是世界上最濒危的物种之一，2020年仅幸存约70头，被保护在印度尼西亚的马戎格库龙国家公园。封面彩图由Karen Montgomery基于Dover Animals的一幅黑白雕刻绘制。