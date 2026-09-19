# 《java8从入门到精通》章节总结

## 目录说明

本书由互联网高级技术专家冰河撰写，系统讲解Java8新特性，重点涵盖Lambda表达式、函数式接口、Stream API、Optional类、新时间日期API等。全书共115页。

---

## 第1章：Java8总览

### 核心论点

本章概述Java8的新特性和优点。核心观点是：Java8中引用最广泛的新特性是Lambda表达式和Stream API，它们使代码更简洁、支持并行、便于处理集合数据。

### 关键概念/事件

- **新特性**：Lambda表达式、函数式接口、方法引用与构造器引用、Stream API、接口默认方法与静态方法、新时间日期API。
- **优点**：速度更快、代码更少、强大的Stream API、便于并行、Optional减少空指针异常。

### 逻辑推演/叙事脉络

作者通过思维导图形式列出Java8新特性，简要说明每个特性的作用，重点强调Lambda和Stream的重要性。

### 经典金句/数据

> “Java8中引用最广泛的新特性是Lambda表达式和Stream API。” (p.8)
> “最大化的减少空指针异常——Optional。” (p.9)

---

## 第2章：Lambda表达式

### 核心论点

本章系统讲解Lambda表达式的概念、语法、用法和与函数式接口的关系。核心观点是：Lambda表达式是一个匿名函数，可以像数据一样传递，使代码更简洁灵活。Lambda表达式需要函数式接口的支持。

### 关键概念/事件

- **Lambda本质**：匿名函数，可传递的代码块。
- **语法格式**：(parameters) -> expression 或 (parameters) -> { statements; }。
- **六种语法格式**：无参无返回值、单参无返回值（括号可省略）、双参有返回值、Lambda体单条语句可省略return和大括号、参数类型可省略（类型推断）。
- **函数式接口**：只包含一个抽象方法的接口，可用@FunctionalInterface注解。
- **典型案例**：集合排序、字符串处理、数值计算。
- **对比演进**：常规遍历→设计模式（策略模式）→匿名内部类→Lambda→Stream API。

### 逻辑推演/叙事脉络

作者通过对比匿名内部类与Lambda表达式的代码量差异引出Lambda。用员工过滤的需求场景，逐步展示从常规方法→设计模式（策略模式）→匿名内部类→Lambda→Stream API的演进过程。详细讲解Lambda的六种语法格式，给出三个典型案例的完整代码。

### 经典金句/数据

> “Lambda表达式是一个匿名函数，我们可以这样理解Lambda表达式：Lambda是一段可以传递的代码（能够做到将代码像数据一样进行传递）。” (p.10)
> “只包含一个抽象方法的接口，称为函数式接口。” (p.26)
> “filterEmployee(this.employees, (e) -> e.getAge() >= 30).forEach(System.out::println);”——Lambda一行代码完成过滤 (p.19)

---

## 第3章：函数式接口总览

### 核心论点

本章系统介绍Java8内置的函数式接口。核心观点是：四大核心函数式接口（Consumer、Supplier、Function、Predicate）覆盖了最常见的操作场景，掌握它们即可理解其他函数式接口。

### 关键概念/事件

- **Consumer<T>**：消费型接口，参数T，无返回值。方法：void accept(T t)。
- **Supplier<T>**：供给型接口，无参数，返回T。方法：T get()。
- **Function<T,R>**：函数型接口，参数T，返回R。方法：R apply(T t)。
- **Predicate<T>**：断言型接口，参数T，返回boolean。方法：boolean test(T t)。
- **其他接口**：BiFunction、UnaryOperator、BinaryOperator、BiConsumer、ToIntFunction等。

### 逻辑推演/叙事脉络

作者用表格形式列出四大核心函数式接口和其他函数式接口的参数类型、返回类型和使用场景。每个接口配以完整的代码示例。

### 经典金句/数据

> “只要我们学会了Java8中四大核心函数式接口的用法，其他函数式接口我们也就知道如何使用了！” (p.41)

---

## 第4章：Java7与Java8中的HashMap

### 核心论点

本章简要对比JDK7和JDK8中HashMap的差异。核心观点是：JDK7是数组+链表；JDK8引入红黑树，当链表长度≥8且总容量≥64时转为红黑树，优化查询性能。

### 关键概念/事件

- **JDK7结构**：数组+链表，新元素添加到链表开头。
- **JDK8结构**：数组+链表+红黑树，新元素添加到链表末尾。链表长度≥8且总容量≥64时转为红黑树。
- **重排序**：删除红黑树元素重排序时，不需要重新计算hashCode，直接放到（总长度+当前位置）的位置。

### 逻辑推演/叙事脉络

作者用对比方式列出JDK7和JDK8的差异，简要说明红黑树转换条件和重排序机制。

### 经典金句/数据

> “JDK7 HashMap结构为数组+链表...JDK8 HashMap结构为数组+链表+红黑树（当HashMap总容量大于等于64，并且某个链表的大小大于等于8，会将链表转化为红黑树）。” (p.42)

---

## 第5章：方法引用与构造器引用

### 核心论点

本章介绍方法引用和构造器引用的语法和使用方式。核心观点是：当Lambda体的操作已有实现方法时，可使用方法引用（操作符“::”）。构造器引用使用ClassName::new。

### 关键概念/事件

- **方法引用三种情况**：对象::实例方法、类::静态方法、类::实例方法。
- **方法引用条件**：实现抽象方法的参数列表必须与方法引用方法的参数列表保持一致。
- **构造器引用**：ClassName::new，自动与函数式接口匹配，参数列表需一致。
- **数组引用**：type::new。

### 逻辑推演/叙事脉络

作者通过代码示例对比Lambda表达式和方法引用的等价关系，分别演示对象、静态方法、实例方法三种情况，以及构造器引用和数组引用的用法。

### 经典金句/数据

> “当要传递给Lambda体的操作，已经有实现的方法了，可以使用方法引用！” (p.45)
> “实现抽象方法的参数列表，必须与方法引用方法的参数列表保持一致！” (p.45)

---

## 第6章：Java8中的Stream

### 核心论点

本章是全书核心章节，系统讲解Stream API的概念、创建方式、中间操作、终止操作、并行流。核心观点是：Stream是数据渠道，用于操作数据源所生成的元素序列；操作分三步：创建Stream→中间操作→终止操作；Stream自己不会存储元素，不会改变源对象，操作是延迟执行的。

### 关键概念/事件

- **Stream操作三步骤**：创建Stream（数据源）→中间操作（处理链）→终止操作（产生结果）。
- **创建Stream四种方式**：Collection的stream()/parallelStream()、Arrays.stream()、Stream.of()、Stream.iterate()/generate()（无限流）。
- **中间操作**：filter（过滤）、limit（截断）、skip（跳过）、distinct（去重）、map（映射）、flatMap（扁平化映射）、sorted（排序）。
- **终止操作**：allMatch/anyMatch/noneMatch（匹配）、findFirst/findAny（查找）、count/max/min（统计）、reduce（规约）、collect（收集）。
- **Collectors工具类**：toList、toSet、toCollection、counting、summingInt、averagingInt、groupingBy、partitioningBy等。
- **并行流**：通过parallel()将串行流转为并行流，Fork/Join框架实现工作窃取。

### 逻辑推演/叙事脉络

作者先定义Stream的概念和特点。详细讲解四种创建Stream的方式（含代码示例）。用表格和代码分别介绍中间操作（筛选/切片、映射、排序）和终止操作（查找/匹配、规约、收集）。深入讲解Collectors的用法和Fork/Join框架。最后通过并行流实例展示性能优势。

### 经典金句/Data

> “Stream是Java8中处理集合的关键抽象概念，它可以指定你希望对集合进行的操作，可以执行非常复杂的查找、过滤和映射数据等操作。” (p.48)
> “集合讲的是数据，流讲的是计算！” (p.48)
> “LongStream.rangeClosed(0, 10000000L).parallel().reduce(0, Long::sum);”——并行流示例 (p.85)

---

## 第7章：Optional类

### 核心论点

本章讲解Optional容器类的用法。核心观点是：Optional是一个容器类，代表一个值存在或不存在，可以更优雅地处理空值，减少NPE。

### 关键概念/事件

- **创建Optional**：of(T t)（t不能为null）、empty()、ofNullable(T t)（t可为null）。
- **判断与获取**：isPresent()（是否包含值）、ifPresent()（存在则执行）、get()（获取值，不存在抛异常）。
- **兜底方法**：orElse(T t)（存在返回值，否则返回默认值）、orElseGet(Supplier)（存在返回值，否则执行Supplier）、orElseThrow()（不存在抛异常）。
- **转换方法**：map()（有值处理返回Optional）、flatMap()（与map类似但返回值必须是Optional）、filter()（过滤）。
- **orElse与orElseGet区别**：值存在时orElse也会执行默认方法（浪费资源），orElseGet不会执行。

### 逻辑推演/叙事脉络

作者先定义Optional的概念，列出常用方法。通过代码示例逐一演示各种方法的使用。重点对比orElse和orElseGet的区别（值存在时orElse也会执行默认方法）。给出filter、map、flatMap的进阶用法。

### 经典金句/数据

> “Optional类(java.util.Optional)是一个容器类，代表一个值存在或不存在，原来用null表示一个值不存在，现在Optional可以更好的表达这个概念。并且可以避免空指针异常。” (p.86)

---

## 第8章：默认方法

### 核心论点

本章讲解接口中的默认方法和静态方法。核心观点是：Java8允许接口中包含具有具体实现的方法（默认方法），使用default关键字修饰；默认方法遵循“类优先”原则，接口冲突时必须覆盖解决。

### 关键概念/事件

- **默认方法**：接口中用default修饰的具体实现方法。
- **类优先原则**：如果父类提供了具体实现，接口中的同名默认方法被忽略。
- **接口冲突**：两个接口有同名默认方法，实现类必须覆盖该方法来解决冲突，可通过`接口名.super.方法名()`选择调用哪个接口的实现。
- **接口静态方法**：接口中允许添加静态方法，使用方式：接口名.方法名。

### 逻辑推演/叙事脉络

作者通过代码示例演示默认方法的定义。通过MyClass继承父类并实现接口的场景说明“类优先原则”。通过实现两个有同名默认方法的接口说明“接口冲突”的解决方案。最后介绍接口静态方法。

### 经典金句/Data

> “Java8中允许接口中包含具有具体实现的方法，该方法称为‘默认方法’，默认方法使用default关键字修饰。” (p.96)
> “若一个接口中定义了一个默认方法，而另外一个父类或接口中又定义了一个同名的方法时，遵循‘类优先’原则。” (p.96)

---

## 第9章：本地时间和时间戳

### 核心论点

本章系统讲解Java8新时间日期API。核心观点是：新API是线程安全的，LocalDate/LocalTime/LocalDateTime是不可变对象，使用DateTimeFormatter代替SimpleDateFormat。

### 关键概念/事件

- **核心类**：LocalDate（日期）、LocalTime（时间）、LocalDateTime（日期时间）、Instant（时间戳）、Duration（时间间隔）、Period（日期间隔）、ZonedDateTime（带时区）。
- **常用方法**：now()、of()、plusXxx()、minusXxx()、withXxx()、getXxx()、isBefore/isAfter、until()。
- **时间校正器**：TemporalAdjuster，TemporalAdjusters提供常用实现（如firstDayOfMonth、next(DayOfWeek.SUNDAY)）。
- **格式化**：DateTimeFormatter（线程安全），支持预定义格式、自定义格式（ofPattern）。
- **时区**：ZoneId.getAvailableZoneIds()获取所有时区，ZonedDateTime带时区处理。

### 逻辑推演/叙事脉络

作者用表格列出新API的核心类和常用方法。通过代码示例逐一演示LocalDate、LocalTime、LocalDateTime、Instant、Duration、Period、TemporalAdjuster、DateTimeFormatter、ZoneId等的用法。最后给出与传统日期处理的转换表。

### 经典金句/数据

> “LocalDate、LocalTime、LocalDateTime类的实例是不可变的对象，分别表示使用ISO-8601日历系统的日期、时间、日期和时间。” (p.100)
> “Java8中加入了对时区的支持，带时区的时间为分别为：ZonedDate、ZonedTime、ZonedDateTime。” (p.106)

---

## 第10章：Java8对注解的增强

### 核心论点

本章讲解Java8对注解的两点增强：可重复的注解和类型注解。核心观点是：通过@Repeatable元注解实现重复注解；类型注解可在任何使用类型的地方使用注解。

### 关键概念/事件

- **可重复注解**：使用@Repeatable注解指定容器注解，容器注解中定义注解数组。
- **类型注解**：@Target中增加ElementType.TYPE_PARAMETER和ElementType.TYPE_USE，可在类型参数和任何类型上使用注解。
- **示例**：定义@BingheAnnotation和@BingheAnnotations，通过@Repeatable关联，实现类和方法的重复注解。

### 逻辑推演/叙事脉络

作者通过完整代码示例演示如何定义可重复注解：先定义普通注解并添加@Repeatable指定容器，再定义容器注解（包含数组属性）。然后用两个@BingheAnnotation分别注解类和方法的同一位置。最后通过反射测试验证。

### 经典金句/数据

> “Java8对注解处理提供了两点改进：可重复的注解及可用于类型的注解。” (p.111)
> “@Repeatable(BingheAnnotations.class)——在BingheAnnotation注解类上比普通的注解多了一个注解，这就是Java8中定义可重复注解的关键。” (p.111)