以下是基于《Java并发程序设计教程》（作者：温绍锦/温少）内容的详细分析与总结，已整理为 Markdown 格式。

---

# Java 并发程序设计教程 - 内容总结

**作者**：温绍锦 (昵称：温少)  
**版本**：2010-07-29  
**核心主旨**：从实战经验出发，讲解 Java 并发编程的核心组件、最佳实践及底层原理，强调线程安全、性能优化及死锁避免。

## 1. 使用线程的经验
在创建和管理线程时，应遵循以下最佳实践以利于监控和排错：

*   **设置线程名称**：
    *   无论何种方式启动线程，必须赋予有意义的名称。
    *   **目的**：在系统监控、Dump 线程栈或排查问题时，能直观识别线程用途。
    *   **方式**:
        ```java
        // 推荐方式
        Thread thread = new Thread(task, "thread name");
        thread.start();
        ```
*   **响应中断 (Interrupt)**：
    *   程序应对 `Thread.interrupt()` 做出恰当响应，避免线程无法停止。
    *   **处理方式**：
        1.  检查 `Thread.interrupted()` 标志位并退出循环。
        2.  捕获 `InterruptedException` 并处理（通常用于阻塞方法如 `sleep`, `wait`, `join`）。
*   **使用 ThreadLocal**：
    *   **定义**：线程局部变量，为每个线程提供独立的变量副本，避免线程间冲突。
    *   **场景**：保存线程上下文状态（如 User-ID, Transaction-ID）、缓存频繁使用的对象。
    *   **注意**：
        *   通常声明为 `static`。
        *   **内存泄露风险**：若线程池复用线程且未调用 `remove()`，可能导致内存泄露。务必在使用后清理。

## 2. Executor 框架 (☆☆☆重点)
Executor 框架将**任务提交者**与**任务执行者**解耦。

*   **核心接口**：
    *   `Executor`：执行任务的接口。
    *   `ExecutorService`：扩展了生命周期管理（shutdown等）。
    *   `Executors`：工厂类，用于创建不同类型的线程池（如 `newSingleThreadExecutor`, `newFixedThreadPool` 等）。
*   **任务类型**：
    *   `Runnable`：无返回值。
    *   `Callable<V>`：有返回值。
*   **Future 机制**：
    *   **作用**：作为提交者与执行者之间的通信手段，用于获取异步执行结果。
    *   **主要方法**：
        *   `get()`：阻塞直到任务完成。
        *   `get(long timeout, TimeUnit unit)`：带超时的阻塞获取。
        *   `cancel(boolean mayInterruptIfRunning)`：取消任务。
        *   `isDone()` / `isCancelled()`：查询状态。
    *   **异常处理`get()` 可能抛出 `ExecutionException`（任务执行异常）或 `TimeoutException`。

## 3. 阻塞队列 (BlockingQueue)
常用于**生产者-消费者**模式。

*   **常用实现**：
    *   `ArrayBlockingQueue`：基于数组的有界队列。
    *   `LinkedBlockingQueue`：基于链表的可选有界队列。
    *   `SynchronousQueue`：不存储元素，直接移交。
*   **关键方法对比**：
    | 操作     | 抛出异常    | 特殊值     | 阻塞     | 超时                   |
    | :------- | :---------- | :--------- | :------- | :--------------------- |
    | **插入** | `add(e)`    | `offer(e)` | `put(e)` | `offer(e, time, unit)` |
    | **移除** | `remove()`  | `poll()`   | `take()` | `poll(time, unit)`     |
    | **检查** | `element()` | `peek()`   | -        | -                      |
*   **最佳实践**：
    *   **推荐使用** `put()` 和 `take()`，因为它们具有阻塞特性，能自动处理队列满/空的情况。
    *   若使用 `offer/poll`，建议使用带超时参数的版本，避免忙等待或永久阻塞。
    *   **drainTo()**：批量获取队列内容，减少锁竞争次数，提高性能。
    *   **避免**直接使用继承自 `Queue` 的非阻塞方法（如 `add/remove`），否则失去阻塞特性。

## 4. 线程间的协调手段 (☆☆☆重点)
*   **传统方式 (synchronized + wait/notify)**：
    *   `wait()`：释放锁并进入等待状态，必须在 synchronized 块中调用。
    *   `notify()/notifyAll()`：唤醒等待线程，必须在 synchronized 块中调用。
    *   **缺点**：一个锁只能对应一个等待集合，灵活性差。
*   **现代方式 (Lock + Condition)**：
    *   `ReentrantLock`：显式锁，支持公平/非公平。
    *   `Condition`：一个 Lock 可以创建多个 Condition，实现更精细的线程控制（如分别等待“非空”和“非满条件”）。
    *   **映射关系**：
        *   `lock.lock()` <-> `synchronized`
        *   `condition.await()` <-> `wait()`
        *   `condition.signal()` <-> `notify()`
        *   `condition.signalAll()` <-> `notifyAll()`
    *   **警告**：**严禁**在 Lock/Condition 对象上调用 wait/notify，这会抛出 `IllegalMonitorStateException`。

## 5. Lock-free 技术 (☆☆☆重点)
利用硬件支持的原子指令（CAS, Compare-And-Swap）实现无锁并发，在高并发低竞争场景下性能优于锁。

*   **Atomic 类**：
    *   `AtomicInteger`, `AtomicLong`, `AtomicReference`, `AtomicBoolean`。
    *   基于 CAS 实现线程安全的增减操作，无需 synchronized。
*   **ConcurrentMap**：
    *   `ConcurrentHashMap`：使用分段锁（JDK7）或 CAS+synchronized（JDK8+）实现高并发读写。
    *   **putIfAbsent**：原子性的“如果不存在则放入”，常用于单例初始化或缓存加载，避免双重检查锁定（DCL）的复杂性。
*   **CopyOnWriteArrayList**：
    *   **原理**：写时复制。添加元素时复制整个数组，在新数组上修改，然后替换引用。
    *   **适用场景**：读多写少（如监听器列表）。
    *   **优点**：读操作完全无锁，性能极高。
    *   **缺点**：写操作开销大，存在内存占用翻倍风险，数据最终一致性（迭代期间可能看不到最新写入）。
*   **Lock-Free 算法三要素**：
    1.  **循环**：不断尝试。
    2.  **CAS**：比较并交换。
    3.  **回退**：失败后重试或采取其他策略。

## 6. 关于锁使用的经验
*   **死锁避免**：
    *   固定获取锁的顺序。
    *   避免嵌套锁，或使用超时获取锁。
    *   注意外部锁（如数据库锁）可能导致的死锁。
*   **诊断死锁**：
    *   Linux: `kill -3 <pid>` 打印线程栈。
    *   JDK 工具: `jstack -l <pid>` 或 JConsole/JVisualVM。
*   **乐观锁 vs 悲观锁**：
    *   **乐观锁 (CAS)**：假设冲突少，失败重试。适合读多写少或低竞争。
    *   **悲观锁 (synchronized/Lock)**：假设冲突多，直接加锁。适合高竞争或临界区代码复杂。
    *   **数据库乐观锁示例**：`UPDATE table SET val=new_val WHERE id=1 AND val=old_val`，通过影响行数判断是否成功。

## 7. 并发流程控制手段
*   **CountDownLatch**：
    *   **作用**：一个或多个线程等待其他 N 个线程完成操作。
    *   **场景**：主线程等待所有子线程初始化完成；并行计算后聚合结果。
    *   **不可重置**：计数减到 0 后无法再次使用。
*   **CyclicBarrier**：
    *   **作用**：一组线程互相等待，直到所有线程都到达某个屏障点，然后同时继续执行。
    *   **场景**：并行迭代计算（如矩阵运算），每轮计算完成后同步。
    *   **可重置**：屏障点到达后自动重置，可重复使用。
    *   **BarrierAction**：可在所有线程到达时执行一个汇总任务。

## 8. 定时器
*   **ScheduledExecutorService**：
    *   JDK 1.5+ 推荐使用的定时任务工具。
    *   **优势**：基于线程池，克服 `java.util.Timer` 的单线程缺陷（一个任务异常会导致整个 Timer 终止）和精度问题。
    *   **方法**：
        *   `schedule()`：延迟执行一次。
        *   `scheduleAtFixedRate()`：固定频率执行（按开始时间计算间隔）。
        *   `scheduleWithFixedDelay()`：固定延迟执行（按结束时间计算间隔）。
*   **TimerWheel (时间轮)**：
    *   适用于大规模定时器管理（如 Netty, Kafka 中使用的算法）。
    *   提供接近 O(1) 的添加和取消操作效率。

## 9. 并发三大定律
理论层面指导并行计算的极限和优化方向。

1.  **Amdahl 定律**：
    *   串行部分限制了并行加速的上限。即使并行部分无限快，总加速比受限于串行比例。
    *   *启示*：尽量减少串行代码。
2.  **Gustafson 定律**：
    *   随着处理器增加，问题规模也可以增加。加速比与处理器数量成正比。
    *   *启示*：通过扩大问题规模来充分利用并行能力。
3.  **Sun-Ni 定律**：
    *   受限于存储空间，尽可能增大问题规模以获得更精确的解。
    *   *启示*：内存带宽和容量也是并行计算的瓶颈。

## 10. 神人和图书
*   **Doug Lea**：Java 并发包 (`java.util.concurrent`) 的主要作者，JSR-166 规范制定者。被誉为 "Mr. Concurrency"。
*   **推荐图书**：《Concurrent Programming in Java: Design Principles and Patterns》。

## 11. 业界发展情况
*   **摩尔定律失效**：单核频率提升遇到瓶颈（功耗、散热），转向多核并行。
*   **异构计算**：
    *   **CPU**：擅长逻辑控制、串行任务。
    *   **GPU**：擅长大规模数据并行计算（SIMD）。
    *   **GPGPU/OpenCL**：通用 GPU 计算标准，允许在非图形领域利用 GPU 算力（如金融分析、科学计算、AI）。
*   **趋势**：软件开发者需要适应多核和异构架构，从串行思维转向并行思维。

---

## 复习题答案汇总

1.  **Future 是做什么用的？**
    *   Future 用于表示异步计算的结果。它提供了检查计算是否完成、等待计算完成以及检索计算结果的方法。它是任务提交者和执行者之间的通信桥梁，支持异步转同步。

2.  **Lock 和 synchronized 的区别是什么？**
    *   **实现层面**：`synchronized` 是 JVM 关键字，底层由 Monitor 实现；`Lock` 是 Java API 接口（如 `ReentrantLock`），由代码实现。
    *   **灵活性**：`Lock` 支持尝试获取锁 (`tryLock`)、超时获取、中断响应、公平锁选择；`synchronized` 只能阻塞等待，不可中断，非公平。
    *   **条件变量**：`Lock` 可以绑定多个 `Condition` 实现精准唤醒；`synchronized` 只能配合 `wait/notify` 使用，且一个锁只有一个等待集。
    *   **释放锁**：`Lock` 必须在 `finally` 块中手动释放；`synchronized` 自动释放。
    *   **性能**：在 JDK 6 之后，两者性能差距缩小，但在高竞争下 `Lock` 通常更具优势或相当。

3.  **什么是 CAS？**
    *   CAS (Compare-And-Swap) 是一种硬件原子指令。它包含三个操作数：内存位置 V、预期原值 A 和新值 B。只有当内存位置 V 的值等于预期原值 A 时，处理器才会将该位置的值更新为新值 B，否则不做任何操作。无论哪种情况，它都会返回在该位置上的真实值。CAS 是实现 Lock-Free 算法的基础。

4.  **Lock-Free 算法的三个组成部分是什么？**
    1.  **循环**：不断尝试操作。
    2.  **CAS (Compare-And-Set)**：原子性地检查并更新状态。
    3.  **回退 (Backoff/Retry)**：当 CAS 失败时，根据策略进行重试或等待，避免活锁或过度消耗 CPU。