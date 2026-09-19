以下是《实战Java高并发程序设计》（葛一鸣、郭超 著）的全书内容分析与总结，已整理为 Markdown 文档格式。

---

# 《实战Java高并发程序设计》全书总结

## 前言与背景
本书立足于多核CPU时代，针对Java服务端编程中日益重要的并行计算需求，从基础概念、JDK并发包、锁优化、并行模式、Java 8新特性、Akka框架以及调试技巧等多个维度，系统性地介绍了Java高并发程序设计的理论与实践。

---

## 第1章：走入并行世界

### 1.1 并行计算的现状
*   **摩尔定律失效**：单核CPU主频提升遭遇瓶颈，多核成为主流。
*   **并行的必要性**：虽然Linus Torvalds曾质疑并行的普适性，但在**图像处理**和**服务端编程**领域，并行是解决高吞吐量和复杂业务模拟的唯一出路。
*   **软件开发的挑战**：硬件将摩尔定律失效的责任推给了软件开发者，程序员需面对线程安全、可见性、有序性等复杂问题。

### 1.2 核心概念
*   **同步 vs 异步**：同步需等待结果返回；异步立即返回，后续通过通知获取结果。
*   **并发 vs 并行**：并发是交替执行（单核模拟多任务），并行是真正同时执行（多核）。
*   **临界区**：共享资源区域，同一时刻只能有一个线程访问。
*   **阻塞 vs 非阻塞**：阻塞导致线程挂起；非阻塞线程尝试不断前向执行。
*   **活跃性问题**：
    *   **死锁**：互相持有对方需要的锁，无限等待。
    *   **饥饿**：低优先级线程长期无法获得资源。
    *   **活锁**：线程不断重试但始终无法成功（如两人避让相撞）。

### 1.3 并发级别
*   **阻塞 (Blocking)**：传统锁机制。
*   **无饥饿 (Starvation-Free)**：公平锁保证先来后到。
*   **无障碍 (Obstruction-Free)**：乐观策略，冲突则回滚。
*   **无锁 (Lock-Free)**：保证至少有一个线程能完成操作（CAS）。
*   **无等待 (Wait-Free)**：所有线程都能在有限步内完成（如RCU）。

### 1.4 重要定律
*   **Amdahl定律**：加速比受限于串行部分的比例。$Speedup = \frac{1}{(1-P) + P/N}$。即使增加CPU，若串行比例高，加速效果有限。
*   **Gustafson定律**：从可扩展性角度，若问题规模随处理器增加而增加，加速比可线性增长。

### 1.5 Java内存模型 (JMM)
*   **原子性**：操作不可中断。`long/double`在32位系统上非原子。
*   **可见性**：一个线程修改变量，其他线程能否立即看到。受缓存、指令重排影响。
*   **有序性**：指令重排优化性能，但可能破坏多线程语义。
*   **Happen-Before规则**：定义了指令重排的边界（如程序顺序、volatile、锁规则等）。

---

## 第2章：Java并行程序基础

### 2.1 线程基础
*   **进程与线程**：进程是资源分配单位，线程是执行单位。线程切换成本低。
*   **线程状态**：NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED。

### 2.2 线程基本操作
*   **新建**：`start()`启动新线程，`run()`仅普通方法调用。
*   **终止**：`stop()`已废弃（导致数据不一致）。推荐使用**标志位**或**中断**。
*   **中断**：`interrupt()`设置标志位，`isInterrupted()`检查，`interrupted()`检查并清除。`sleep/wait`响应中断抛出异常。
*   **等待/通知**：`wait()/notify()`必须在`synchronized`块中使用。`wait`释放锁，`notify`随机唤醒。
*   **挂起/恢复**：`suspend()/resume()`已废弃（不释放锁，易死锁）。
*   **Join/Yield**：`join()`等待线程结束；`yield()`让出CPU时间片。

### 2.3 Volatile
*   保证**可见性**和**有序性**，但**不保证原子性**（如`i++`）。
*   禁止指令重排（内存屏障）。

### 2.4 线程组与守护线程
*   **线程组**：管理线程集合。
*   **守护线程**：后台服务线程（如GC），用户线程结束后JVM退出。需在`start()`前设置。

### 2.5 线程优先级
*   1-10级，依赖操作系统调度，不建议作为业务逻辑强依赖。

### 2.6 线程安全与Synchronized
*   **Synchronized**：内置锁，保证原子性、可见性、有序性。
*   **用法**：修饰实例方法（锁对象）、静态方法（锁类）、代码块（锁指定对象）。
*   **常见错误**：锁对象不一致（如不同的Runnable实例）、锁Integer对象（自动装箱导致锁对象变化）。

### 2.7 隐蔽的错误
*   **溢出**：整数运算溢出无异常。
*   **ArrayList**：多线程add可能导致`ArrayIndexOutOfBoundsException`或数据丢失。
*   **HashMap**：JDK7中多线程扩容可能导致**死循环**（链表成环），JDK8已修复但仍非线程安全。
*   **错误加锁**：对不可变对象（如Integer）加锁无效。

---

## 第3章：JDK并发包 (JUC)

### 3.1 同步控制工具
*   **ReentrantLock**：重入锁，支持公平/非公平、中断响应 (`lockInterruptibly`)、限时等待 (`tryLock`)。需手动`unlock`。
*   **Condition**：配合Lock使用，实现精确通知（类似`wait/notify`但更灵活）。
*   **Semaphore**：信号量，控制同时访问资源的线程数。
*   **ReadWriteLock**：读写分离，读读共享，读写/写写互斥。适合读多写少场景。
*   **CountDownLatch**：倒计时器，一次性事件等待（如火箭发射前检查）。
*   **CyclicBarrier**：循环栅栏，可复用，支持 barrierAction。适合多线程分阶段任务。
*   **LockSupport**：底层阻塞原语，`park/unpark`，不依赖锁，支持中断。

### 3.2 线程池
*   **Executor框架**：`ThreadPoolExecutor`为核心。
*   **核心参数**：`corePoolSize`, `maximumPoolSize`, `keepAliveTime`, `workQueue`, `threadFactory`, `handler`。
*   **队列类型**：
    *   `SynchronousQueue`：直接提交，无容量。
    *   `LinkedBlockingQueue`：无界队列，可能OOM。
    *   `ArrayBlockingQueue`：有界队列。
    *   `PriorityBlockingQueue`：优先队列。
*   **拒绝策略**：Abort (抛异常), CallerRuns (调用者运行), DiscardOldest (丢弃最老), Discard (静默丢弃)。
*   **扩展**：继承`ThreadPoolExecutor`重写`beforeExecute/afterExecute/terminated监控任务。
*   **Fork/Join**：分治法，工作窃取算法。`RecursiveTask` (有返回值), `RecursiveAction` (无返回值)。

### 3.3 并发容器
*   **ConcurrentHashMap**：分段锁（JDK7）或 CAS + synchronized (JDK8)，高效并发Map。
*   **CopyOnWriteArrayList**：写时复制，适合读多写少，最终一致性。
*   **ConcurrentLinkedQueue**：基于CAS的无锁非阻塞队列。
*   **BlockingQueue**：阻塞队列，生产者-消费者模型核心。`ArrayBlockingQueue`, `LinkedBlockingQueue`。
*   **ConcurrentSkipListMap**：基于跳表，支持并发排序遍历。

---

## 第4章：锁的优化及注意事项

### 4.1 锁性能优化建议
*   **减小锁持有时间**：仅在必要代码段加锁。
*   **减小锁粒度**：如ConcurrentHashMap的分段锁。
*   **读写分离**：使用`ReadWriteLock`。
*   **锁分离**：如`LinkedBlockingQueue`的`takeLock`和`putLock`。
*   **锁粗化**：循环外加锁，减少频繁申请释放锁的开销。

### 4.2 JVM锁优化
*   **偏向锁**：消除无竞争下的同步开销。
*   **轻量级锁**：CAS替换对象头，无竞争时避免OS互斥量。
*   **自旋锁**：短时间等待时自旋，避免上下文切换。
*   **锁消除**：JIT编译时通过逃逸分析，消除不可能竞争的锁（如局部变量Vector）。

### 4.3 ThreadLocal
*   **原理**：每个线程拥有独立的变量副本（`ThreadLocalMap`）。
*   **内存泄漏**：Key是弱引用，Value是强引用。线程池场景下需手动`remove()`。
*   **应用**：SimpleDateFormat线程安全封装、随机数生成优化。

### 4.4 无锁 (CAS)
*   **CAS (Compare And Swap)**：乐观锁，包含V(内存值), E(预期值), N(新值)。
*   **ABA问题**：值被修改后改回原值，CAS误判。解决：`AtomicStampedReference` (版本号/时间戳)。
*   **原子类**：
    *   `AtomicInteger/Long/Reference`。
    *   `AtomicIntegerFieldUpdater`：字段更新器，反射实现，要求volatile。
    *   `AtomicIntegerArray`：原子数组。
*   **无锁Vector**：基于二维数组和CAS描述符实现动态扩容。
*   **SynchronousQueue**：基于无锁栈/队列实现的数据交换通道。

### 4.5 死锁
*   **检测**：`jstack`查看线程堆栈，寻找`BLOCKED`状态及锁依赖环。
*   **避免**：固定加锁顺序、使用`tryLock`限时、使用中断。

---

## 第5章：并行模式与算法

### 5.1 单例模式
*   **饿汉式**：类加载时初始化，线程安全。
*   **懒汉式**：双重检查锁定 (DCL) 需配合`volatile`防止指令重排。
*   **静态内部类**：推荐方式，利用类加载机制保证线程安全且延迟加载。

### 5.2 不变模式
*   **Immutable Object**：对象创建后状态不可变（final字段，无setter）。
*   **优势**：天然线程安全，无需同步。如`String`, 包装类。

### 5.3 生产者-消费者
*   **解耦**：通过`BlockingQueue`缓冲数据，平衡生产消费速度差异。

### 5.4 Disruptor (高性能无锁队列)
*   **环形缓冲区 (RingBuffer)**：预分配内存，无GC压力，索引定位快。
*   **无锁CAS**：高性能。
*   **伪共享解决**：缓存行填充 (Padding)，避免多核Cache Line冲突。
*   **等待策略**：Blocking, Sleeping, Yielding, BusySpin。

### 5.5 Future模式
*   **异步调用**：提交任务立即返回Future，后续通过`get()`获取结果。
*   **JDK实现**：`FutureTask`, `Callable`。
*   **缺点**：`get()`阻塞，无法链式处理。

### 5.6 并行流水线
*   **思想**：将依赖步骤拆分到不同线程，通过队列传递中间结果。
*   **适用**：数据流处理，如 `(B+C)*B/2` 拆分为加、乘、除三个线程。

### 5.7 并行搜索
*   **分割搜索**：将数组分段，多线程并行搜索，发现结果后通过CAS标记并通知其他线程停止。

### 5.8 并行排序
*   **奇偶交换排序**：奇交换和偶交换阶段独立，可并行化。
*   **希尔排序**：间隔排序，子数组独立，可并行化。

### 5.9 矩阵乘法
*   **分治法**：利用Fork/Join框架将大矩阵分解为子矩阵并行计算。

### 5.10 NIO (New IO)
*   **同步非阻塞**：Selector管理多个Channel，单线程处理多连接。
*   **组件**：Channel, Buffer, Selector。
*   **优势**：减少线程上下文切换，适合高并发网络IO。

### 5.11 AIO (Asynchronous IO)
*   **异步非阻塞**：OS完成IO后回调通知。
*   **组件**：`AsynchronousServerSocketChannel`, `CompletionHandler`。
*   **适用**：连接数多且长连接场景。

---

## 第6章：Java 8与并发

### 6.1 函数式编程基础
*   **Lambda表达式**：简化匿名内部类。
*   **函数式接口**：`@FunctionalInterface`，单一抽象方法。
*   **方法引用**：`Class::method`。
*   **Stream API**：声明式数据处理，支持串行/并行。

### 6.2 并行流
*   **parallelStream()**：底层使用ForkJoinPool。
*   **注意**：适合CPU密集型且无状态操作，避免共享可变状态。
*   **Arrays.parallelSort()**：并行排序。

### 6.3 CompletableFuture
*   **增强Future**：支持链式调用、组合、异常处理。
*   **常用方法**：
    *   `supplyAsync/runAsync`：异步执行。
    *   `thenApply/thenAccept/thenRun`：结果转换/消费。
    *   `thenCompose`：扁平化组合。
    *   `thenCombine`：合并两个Future结果。
    *   `exceptionally`：异常处理。

### 6.4 StampedLock
*   **乐观读锁**：提供乐观读 (`tryOptimisticRead`)，不阻塞写锁。
*   **验证**：读取后需`validate(stamp)`，失败则升级为悲观读锁。
*   **注意**：不支持重入，中断可能导致CPU飙升（死循环park）。

### 6.5 原子类增强
*   **LongAdder**：热点分离，将value分散到Cell数组，降低CAS竞争。适合高并发计数。
*   **LongAccumulator**：LongAdder的通用版，支持自定义二元运算。
*   **伪共享优化**：`@sun.misc.Contended`注解（需JVM参数开启）。

---

## 第7章：使用Akka构建高并发程序

### 7.1 Actor模型
*   **理念**：Actor是轻量级执行单元，通过消息通信，无共享状态。
*   **优势**：高并发、容错、分布式透明。

### 7.2 核心概念
*   **ActorSystem**：管理Actor的生命周期和配置。
*   **Props**：创建Actor的配置对象。
*   **ActorRef**：Actor的引用，用于发送消息。
*   **消息**：不可变对象，异步发送。

### 7.3 生命周期与监督
*   **生命周期回调**：`preStart`, `postStop`, `preRestart`, `postRestart`。
*   **监督策略 (SupervisorStrategy)**：
    *   `OneForOne`：只重启出错子Actor。
    *   `AllForOne`：重启所有子Actor。
    *   动作：Resume, Restart, Stop, Escalate。

### 7.4 高级特性
*   **Router**：消息路由（轮询、随机、广播等）。
*   **Inbox**：非Actor环境（如main方法）与Actor交互。
*   **Become/Unbecome**：动态切换Actor行为状态。
*   **Ask Pattern**：请求-响应模式，返回Future。
*   **Agent**：异步更新共享变量，保证最终一致性。
*   **STM (Software Transactional Memory)**：软件事务内存，类似数据库事务，保证原子性、隔离性。

### 7.5 案例：粒子群算法 (PSO)
*   利用Actor模拟大量粒子并行搜索最优解，Master Actor汇总全局最优。

---

## 第8章：并行程序调试

### 8.1 调试难点
*   不确定性、难以复现、线程交互复杂。

### 8.2 Eclipse调试技巧
*   **条件断点**：指定线程名称或条件触发。
*   **挂起策略**：
    *   **Suspend Thread**：仅挂起当前线程，其他线程继续运行（可能干扰调试）。
    *   **Suspend VM**：挂起所有线程，便于观察整体状态。
*   **案例重现**：通过控制线程执行顺序，重现`ArrayList`多线程扩容导致的`ArrayIndexOutOfBoundsException`。

---

## 总结与建议

1.  **基础扎实**：深入理解JMM、volatile、synchronized底层原理。
2.  **善用JUC**：优先使用JDK提供的并发工具（ConcurrentHashMap, ThreadPoolExecutor, CompletableFuture等），避免重复造轮子。
3.  **锁优化**：尽量减少锁粒度、持有时间，考虑读写分离、无锁算法（CAS）。
4.  **Java 8+**：充分利用Stream并行流、CompletableFuture进行异步编程，提高开发效率和代码可读性。
5.  **架构选型**：超高并发场景可考虑Disruptor、Akka等框架，但需权衡复杂度。
6.  **警惕陷阱**：注意线程池参数配置、ThreadLocal内存泄漏、伪共享、死锁等问题。
7.  **调试能力**：掌握多线程调试技巧，善于利用日志和线程dump分析线上问题。