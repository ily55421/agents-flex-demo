# 《Java 7并发编程实战手册》章节总结

## 目录说明

本书共9章，但PDF中第9章（附加信息）和附录仅提及可下载，无正文内容。以下总结基于PDF中可识别的第1-8章完整正文，以及前言、译者序等前置内容。章节顺序严格遵循书籍目录。

---

## 前言

### 核心论点

并发编程允许多个任务同时运行并相互通信。Java是一个并发平台，提供了丰富的类来执行并发任务。本书覆盖Java 7并发API中大部分重要机制，使开发者能直接在应用程序中使用这些技术。

### 关键概念/事件

- **并发 vs 并行**：并发是指一系列任务的同时运行（单核上表现为交替执行），并行是指使用多核处理器真正同时执行多个线程。
- **Java并发API演进**：从Java 5引入`java.util.concurrent`包，到Java 7新增Fork/Join框架和Phaser类。
- **本书结构**：涵盖线程管理、线程同步基础、同步辅助类、执行器、Fork/Join框架、并发集合、定制并发类、测试并发应用。

### 逻辑推演/叙事脉络

前言首先解释了并发编程的基本概念及其在Java中的重要性。随后概述了Java并发API的发展历程，特别是Java 5到Java 7的演进。最后逐章介绍了本书各章的核心内容，帮助读者理解全书的知识组织结构。

### 经典金句/数据

> “Java是一个并发平台，它提供了大量的类来执行Java程序中的并发任务。” (p.11)

> 本书适合“具有一定Java编程基础的读者”，需要“已经熟悉普通的Java开发实践”。(p.13)

---

## 第1章：线程管理

### 核心论点

本章解决如何在Java程序中创建、运行和管理线程的基本问题。核心观点是：Java提供了两种创建线程的方式（继承Thread或实现Runnable），并通过Thread类的一系列方法（sleep、interrupt、join等）来控制线程的执行状态。

### 关键概念/事件

- **线程创建**：两种方式——继承Thread并覆盖run()，或实现Runnable接口并传递给Thread构造器。推荐使用后者。
- **线程状态**：NEW、RUNNABLE、BLOCKED、WAITING、TIME_WAITING、TERMINATED六种状态。
- **线程中断**：通过`interrupt()`方法设置中断标志，线程应定期检查`isInterrupted()`并响应中断。
- **守护线程**：优先级低，当程序中只有守护线程运行时JVM退出。通过`setDaemon(true)`设置。
- **线程局部变量**：`ThreadLocal`类为每个线程存储独立的变量副本，避免共享数据问题。
- **线程组**：`ThreadGroup`类可将线程分组管理，统一操作或处理异常。

### 逻辑推演/叙事脉络

本章从最基本的线程创建和运行开始，逐步深入到线程信息的获取与设置。接着讲解线程中断机制及其控制方法，包括使用`InterruptedException`的进阶方式。然后介绍`sleep()`方法使线程休眠，以及`join()`方法等待线程终止。守护线程的概念和创建方法随后展开。最后讨论线程中不可控异常的处理、线程局部变量的使用、线程分组以及使用工厂类创建线程。整体呈现从简单到复杂、从基础到进阶的递进结构。

### 经典金句/数据

> “每个Java程序都至少有一个执行线程。当运行程序的时候，JVM将启动这个执行线程来调用程序的main()方法。” (p.20)

> “守护线程通常被用来做为同一程序中普通线程（也称为用户线程）的服务提供者。一个典型的守护线程是Java的垃圾回收器。” (p.37)

> “线程局部变量分别为每个线程存储了各自的属性值，并提供给每个线程使用。” (p.45)

---

## 第2章：线程同步基础

### 核心论点

本章解决多个线程共享资源时可能出现的数据不一致问题。核心观点是：通过`synchronized`关键字或`Lock`接口创建临界区（Critical Section），确保同一时间只有一个线程能访问共享资源，从而保证数据一致性。

### 关键概念/事件

- **临界区**：访问共享资源的代码块，同一时间只允许一个线程执行。
- **synchronized关键字**：可修饰方法或代码块，保证同一对象的同步方法/块同时只能被一个线程访问。
- **非依赖属性同步**：使用不同的对象作为锁来同步独立的属性，允许多个线程同时访问不同的属性。
- **wait/notify机制**：在同步代码块中，线程可调用`wait()`进入等待，其他线程调用`notify()`或`notifyAll()`唤醒。
- **Lock接口与ReentrantLock**：提供比synchronized更灵活的锁机制，包括`tryLock()`、可中断锁等。
- **读写锁（ReentrantReadWriteLock）**：允许多个读线程同时访问，但写线程独占访问。
- **锁的公平性**：公平模式下等待时间最长的线程优先获得锁；非公平模式则无约束选择。
- **Condition接口**：与锁绑定的条件，允许多个等待集，实现更精细的线程协调。

### 逻辑推演/叙事脉络

本章从问题出发，先介绍`synchronized`关键字的基本用法（同步方法），然后扩展到使用不同对象锁同步非依赖属性。接着引入`wait()`/`notify()`机制解决生产者-消费者问题。随后转向更强大的`Lock`接口，展示`ReentrantLock`的基本用法和读写锁的实现。最后深入讨论锁的公平性设置以及使用多条件（`Condition`）实现更复杂的线程协调。每节都配有完整的代码范例。

### 经典金句/数据

> “synchronized关键字会降低应用程序的性能，因此只能在并发情景中需要修改共享数据的方法上使用它。” (p.63)

> “当使用synchronized关键字来保护代码块时，必须把对象引用作为传入参数。” (p.64)

> “ReentrantLock类允许使用递归调用。如果一个线程获取了锁并且进行了递归调用，它将继续持有这个锁。” (p.77)

---

## 第3章：线程同步辅助类

### 核心论点

本章解决比基础同步更复杂的线程协调问题。核心观点是：Java提供了Semaphore、CountDownLatch、CyclicBarrier、Phaser、Exchanger等高级同步工具，每种工具针对特定的同步场景（资源控制、等待事件完成、集合点同步、阶段任务、数据交换）提供了专门的解决方案。

### 关键概念/事件

- **Semaphore（信号量）**：计数器保护一个或多个共享资源的访问。二进制信号量（计数器为1）保护单个资源。
- **CountDownLatch**：等待指定数量的操作完成后再继续执行，计数器只能使用一次。
- **CyclicBarrier**：多个线程在集合点相互等待，所有线程到达后执行可选的Runnable任务，可重置重复使用。
- **Phaser**：多阶段同步，可动态增减参与者，Java 7新增。
- **Exchanger**：两个线程之间的数据交换点，用于生产者-消费者场景。

### 逻辑推演/叙事脉络

本章逐一介绍五种同步辅助类。先从Semaphore开始，演示如何保护单个资源（打印队列）和多个资源（多台打印机）。接着用CountDownLatch实现视频会议等待参会者场景。然后用CyclicBarrier实现矩阵查找的分治任务，展示集合点同步。Phaser部分通过文件搜索和考试模拟两个范例，展示多阶段任务的同步控制。最后用Exchanger实现一对一的生产者-消费者数据交换。每个概念都配有完整的实际应用范例。

### 经典金句/数据

> “CountDownLatch机制不是用来保护共享资源或者临界区的，它是用来同步执行多个任务的一个或者多个线程。” (p.107)

> “CyclicBarrier类有一个很有意义的改进，即它可以传入另一个Runnable对象作为初始化参数。当所有的线程都到达集合点后，CyclicBarrier类将这个Runnable对象作为线程执行。” (p.109)

> “Phaser类机制是在每一步结束的位置对线程进行同步，当所有的线程都完成了这一步，才允许执行下一步。” (p.117)

> “Exchanger类允许在两个线程之间定义同步点。当两个线程都到达同步点时，它们交换数据结构。” (p.132)

---

## 第4章：线程执行器

### 核心论点

本章解决手动管理大量线程对象带来的复杂性和性能问题。核心观点是：执行器框架（Executor Framework）分离任务的创建和执行，通过线程池自动管理线程的生命周期，并提供Callable/Future机制支持任务返回结果，大幅简化并发编程。

### 关键概念/事件

- **Executor框架**：围绕`Executor`和`ExecutorService`接口，核心实现是`ThreadPoolExecutor`。
- **Executors工厂类**：提供`newCachedThreadPool()`、`newFixedThreadPool()`、`newSingleThreadExecutor()`等方法创建执行器。
- **Callable与Future**：`Callable.call()`可返回结果，`Future`用于控制任务状态和获取结果。
- **invokeAny() vs invokeAll()**：前者返回第一个成功完成的任务结果；后者等待所有任务完成。
- **ScheduledThreadPoolExecutor**：支持延迟执行和周期性执行任务（`schedule()`、`scheduleAtFixedRate()`）。
- **取消任务**：`Future.cancel()`方法可取消已提交的任务。
- **CompletionService**：分离任务提交与结果处理，先完成的任务结果可被优先获取。
- **拒绝策略**：`RejectedExecutionHandler`处理执行器关闭后提交的任务。

### 逻辑推演/叙事脉络

本章从执行器的创建开始，展示`newCachedThreadPool()`和`newFixedThreadPool()`两种方式。然后介绍`Callable`接口和`Future`对象，演示如何获取任务返回结果。接着讲解`invokeAny()`和`invokeAll()`处理多个任务的两种策略。之后转向`ScheduledThreadPoolExecutor`，展示延迟执行和周期性执行。再介绍如何取消任务、通过`done()`方法处理任务完成事件、使用`CompletionService`分离任务提交与结果处理。最后讨论被拒绝任务的处理机制。整章围绕执行器的完整生命周期展开。

### 经典金句/数据

> “执行器框架（Executor Framework）分离了任务的创建和执行。通过使用执行器，仅需要实现Runnable接口的对象，然后将这些对象发送给执行器即可。” (p.138)

> “newFixedThreadPool()方法创建了具有线程最大数量值的执行器。如果发送超过线程数的任务给执行器，剩余的任务将被阻塞直到线程池里有空闲的线程来处理它们。” (p.144)

> “invokeAny()方法接收到一个任务列表，然后运行任务，并返回第一个完成任务并且没有抛出异常的任务的执行结果。” (p.154)

> “当执行器接收一个任务并开始执行时，它先检查shutdown()方法是否已经被调用了。如果是，那么执行器就拒绝这个任务。” (p.184)

---

## 第5章：Fork/Join框架

### 核心论点

本章解决如何高效处理可分解为子任务的大规模问题。核心观点是：Fork/Join框架基于分治技术和“工作窃取算法”，将大任务递归拆分为小任务并行执行，再合并结果，特别适合递归分解型问题。

### 关键概念/事件

- **Fork/Join框架**：Java 7新增，用于解决可通过分治技术拆分的问题。
- **工作窃取算法**：空闲的工作者线程主动从其他线程的任务队列中“窃取”任务执行，提高利用率。
- **ForkJoinPool**：管理工作者线程的执行器，实现工作窃取算法。
- **ForkJoinTask**：任务的基类，两个子类——`RecursiveAction`（无返回值）和`RecursiveTask`（有返回值）。
- **分治结构**：`if (problem size > threshold) { 拆分子任务; 执行; 合并结果; } else { 直接解决; }`
- **fork()与join()**：`fork()`异步执行子任务，`join()`等待子任务完成并获取结果。
- **任务取消与异常**：`ForkJoinTask`提供`cancel()`方法，异常处理与非运行时异常的包装机制。

### 逻辑推演/叙事脉络

本章首先介绍Fork/Join框架的设计目标和工作窃取算法的核心优势。第一个范例使用`RecursiveAction`（无返回值）更新产品价格，展示任务拆分的基本结构。第二个范例使用`RecursiveTask`（有返回值）在文档中查找单词，演示`DocumentTask`和`LineTask`两层任务拆分及结果的合并。接着介绍异步运行任务（`fork()`不等待结果）及其应用场景。最后讨论任务中异常的处理方式和取消任务的机制。每节都完整展示了任务类设计、阈值选择、拆分逻辑和结果合并。

### 经典金句/数据

> “Fork/Join框架是用来解决能够通过分治技术（Divide and Conquer Technique）将问题拆分成小任务的问题。” (p.169)

> “Fork/Join框架和执行器框架主要的区别在于工作窃取算法（Work-Stealing Algorithm）。当使用Join操作让一个主任务等待它所创建的子任务的完成时，执行这个任务的线程称之为工作者线程。工作者线程寻找其他仍未被执行的任务，然后开始执行。” (p.169)

> “Fork/Join框架执行的任务有以下限制：任务只能使用fork()和join()操作当作同步机制；任务不能执行I/O操作；任务不能抛出非运行时异常。” (p.169-170)

> “在ForkJoinPool类中，当使用Runnable对象时，ForkJoinPool类就不采用工作窃取算法，ForkJoinPool类仅在使用ForkJoinTask类时才采用工作窃取算法。” (p.194)

---

## 第6章：并发集合

### 核心论点

本章解决在多线程环境下安全使用数据集合的问题。核心观点是：Java并发API提供了一系列线程安全的集合类（如ConcurrentHashMap、ConcurrentLinkedQueue、BlockingQueue等），使用这些集合可以避免手动编写同步代码，简化并发编程并提高性能。

### 关键概念/事件

- **非阻塞式线程安全列表**：`ConcurrentLinkedDeque`，使用无锁算法（CAS）实现高并发。
- **阻塞式线程安全列表**：`LinkedBlockingDeque`，当队列为空时获取操作阻塞，满时插入操作阻塞。
- **优先级阻塞队列**：`PriorityBlockingQueue`，元素按优先级排序而非FIFO。
- **延迟队列**：`DelayQueue`，元素只有在指定延迟时间过后才能被取出。
- **线程安全可遍历映射**：`ConcurrentHashMap`和`ConcurrentSkipListMap`，支持高并发读写。
- **并发随机数**：`ThreadLocalRandom`，每个线程独立随机数生成器，避免竞争。
- **原子变量**：`AtomicLong`、`AtomicIntegerArray`等，提供无锁的线程安全操作。

### 逻辑推演/叙事脉络

本章按集合类型组织：先介绍非阻塞和阻塞式的列表/队列（`ConcurrentLinkedDeque`、`LinkedBlockingDeque`、`PriorityBlockingQueue`、`DelayQueue`），然后介绍线程安全的映射（`ConcurrentHashMap`、`ConcurrentSkipListMap`）。接着讲解生成并发随机数的`ThreadLocalRandom`，最后介绍原子变量（`AtomicLong`、`AtomicIntegerArray`等）。每节都通过实际场景演示集合的特性和用法。

### 经典金句/数据

> “使用这些并发数据结构，可以避免在程序的实现中采用synchronized代码块。” (p.206)

> “DelayQueue类允许带有延迟元素的列表，其中的元素只有在其延迟期满后才能被取出。” (p.221)

> “原子变量提供了与其他更新相同作用域的原子操作，不需要使用同步器或其它机制。” (p.233)

---

## 第7章：定制并发类

### 核心论点

本章解决当Java并发API提供的默认类无法满足特定需求时的问题。核心观点是：通过继承和接口实现，可以对ThreadPoolExecutor、ThreadFactory、ForkJoinTask、Lock等核心并发类进行定制，以满足优先级调度、线程命名、日志记录等个性化需求。

### 关键概念/事件

- **定制ThreadPoolExecutor**：继承并覆盖`beforeExecute()`、`afterExecute()`、`terminated()`等方法。
- **优先级执行器**：定制任务队列为`PriorityBlockingQueue`实现按优先级执行。
- **定制ThreadFactory**：实现`ThreadFactory`接口，创建具有自定义名称、组或异常处理器的线程。
- **定制定时线程池任务**：继承`ScheduledThreadPoolExecutor`并覆盖`decorateTask()`。
- **定制Fork/Join任务**：继承`ForkJoinTask`或覆盖`ForkJoinWorkerThreadFactory`创建定制工作线程。
- **定制Lock**：继承`AbstractQueuedSynchronizer`实现自定义锁逻辑。
- **定制优先级传输队列**：定制`PriorityBlockingQueue`实现按优先级传输。
- **定制原子对象**：继承`AtomicInteger`或使用`AtomicReference`实现自定义原子操作。

### 逻辑推演/叙事脉络

本章按照可定制的组件逐一展开：从执行器（`ThreadPoolExecutor`）的定制开始，包括实现优先级执行器。接着定制线程工厂（`ThreadFactory`）及其在执行器中的应用。然后扩展到定时任务线程池和Fork/Join框架的定制。之后讨论锁（`Lock`）和传输队列（`TransferQueue`）的定制实现。最后介绍如何创建自己的原子对象。每节都展示完整的定制类设计和应用范例。

### 经典金句/数据

> “使用工厂类，可以将对象的创建集中化，这样做有以下的好处：更容易修改类或改变创建对象的方式；更容易为有限资源限制创建对象的数目；更容易为创建的对象生成统计数据。” (p.53)

> “ReentrantLock类基于AbstractQueuedSynchronizer类。它是一个模板方法模式的例子，其中相当数量的方法已经被实现，而其他的方法需要定制以实现我们想要的行为。” (p.279)

---

## 第8章：测试并发应用程序

### 核心论点

本章解决如何监控、调试和测试并发应用程序的问题。核心观点是：并发应用程序的调试比串行程序更困难，需要借助专门的监控方法（获取锁、Phaser、执行器的状态信息）和工具（Eclipse、NetBeans调试器、FindBugs、MultithreadedTC）来发现和解决并发问题。

### 关键概念/事件

- **监控Lock接口**：使用`ReentrantLock`的`getQueueLength()`、`getHoldCount()`等方法。
- **监控Phaser**：使用`getRegisteredParties()`、`getPhase()`、`getArrivedParties()`等方法。
- **监控执行器**：使用`getPoolSize()`、`getActiveCount()`、`getCompletedTaskCount()`等。
- **监控Fork/Join池**：使用`getStealCount()`、`getRunningThreadCount()`等方法。
- **高效日志**：使用`Thread.currentThread().getId()`和`System.currentTimeMillis()`记录线程和时间。
- **FindBugs**：静态分析工具，可检测并发代码中的常见错误模式。
- **IDE调试**：Eclipse和NetBeans的断点、线程视图功能。
- **MultithreadedTC**：用于测试多线程交互的框架，可在确定性时间点触发线程操作。

### 逻辑推演/叙事脉络

本章从监控方法入手，分别讲解如何获取`Lock`、`Phaser`、执行器、Fork/Join池的状态信息。接着讨论如何输出高效的日志信息以便事后分析。然后介绍使用FindBugs工具静态分析并发代码。最后分别配置Eclipse和NetBeans的调试功能来调试并发程序，以及使用MultithreadedTC框架进行确定性并发测试。

### 经典金句/数据

> “并发应用程序的测试比普通应用程序要复杂得多，而且许多时候连错误都很难重现。正因为并发错误的特征，必须使用额外的工具来帮助完成应用程序的测试。” (p.300)

> “监控是获取并发应用程序状态信息的有用机制，有助于发现可能存在的缺陷。” (p.300)

> “FindBugs是一个开源的分析工具，它对Java代码进行静态分析以找出可能存在的错误，包括并发代码中的错误模式。” (p.323)