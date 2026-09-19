以下是基于《Apache RocketMQ从入门到实战》PDF文件内容的详细章节分析与总结，已整理为 Markdown 文档格式。

---

# Apache RocketMQ 从入门到实战 - 核心知识点汇总

## 1. 开篇：参与开源社区的另一种方式
*   **核心理念**：参与开源不仅限于代码贡献（Committer），技术布道、社区运营、文档编写、故障案例分析同样是重要贡献。
*   **作者经历**：通过深入源码分析、撰写高质量技术文章、出版书籍《RocketMQ技术内幕》，成为 RocketMQ 优秀布道师。
*   **建议**：打牢 Java 基础（集合、并发 JUC、NIO/Netty），通过阅读源码、解决 Issues、编写单元测试等方式逐步融入开源项目。

## 2. 核心概念扫盲 (1.1)
### 2.1 部署架构
*   **Nameserver**：轻量级注册中心，无状态，节点间不通信，存储 Topic 路由信息（最终一致性）。
*   **Broker**：消息存储服务器，分 Master/Slave。Master 负责读写，Slave 主要备份及承担读负载。每 30s 向 Nameserver 发送心跳。
*   **Client**：Producer（生产者）和 Consumer（消费者）。客户端缓存路由信息，每 30s 更新。

### 2.2 消息订阅模型
*   **Topic**：消息分类集合。
*   **Consumer Group**：消费组，一个组内多个消费者共同消费或广播消费。
*   **消费模式**：
    *   **集群模式 (Clustering)**：负载均衡，一条消息只被组内一个消费者消费。
    *   **广播模式 (Broadcasting)**：每条消息被组内所有消费者消费。
*   **队列负载算法**：
    *   `AllocateMessageQueueAveragely`：平均分配（连续队列）。
    *   `AllocateMessageQueueAveragelyByCircle`：轮流平均分配（离散队列）。
*   **重平衡 (Rebalance)**：消费者增减或队列增减时，每隔 20s 触发重新分配。
*   **消费进度**：
    *   集群模式：存储在 Broker (`consumerOffset.json`)。
    *   广播模式：存储在客户端本地 (`~/.rocketmq_offsets`)。
*   **消费模型**：
    *   **并发消费**：多线程处理，无序。失败默认重试 16 次。
    *   **顺序消费**：基于 Queue 加锁，保证局部有序。失败会阻塞直到成功（需区分业务异常与系统异常）。

### 2.3 其他特性
*   **事务消息**：解决本地事务与消息发送的一致性（半消息机制）。
*   **定时消息**：开源版仅支持固定延迟级别（1s, 5s, ..., 2h）。
*   **消息过滤**：支持 Tag 过滤和 SQL92 属性过滤。

## 3. 生产环境避坑指南

### 3.1 autoCreateTopicEnable 为什么不能设为 true (1.2)
*   **现象**：开启自动创建后，新 Topic 可能只在一台 Broker 上创建队列，导致单点故障风险。
*   **原因**：
    1.  Producer 查询不到 Topic 路由，使用默认 Topic (`TBW102`) 的路由。
    2.  Producer 默认队列数为 4，轮询选择一台 Broker 发送消息。
    3.  该 Broker 收到消息后创建 Topic 路由，并异步同步给 Nameserver。
    4.  若后续消息未发送到其他 Broker，其他 Broker 不会创建该 Topic 路由。
*   **最佳实践**：生产环境关闭自动创建，通过运维平台或命令行预先创建 Topic，确保在所有 Broker 上均匀分布队列。

### 3.2 学习环境搭建 (1.3)
*   **Linux 安装**：修改 JVM 参数（降低内存占用），配置 `broker.conf`，启动 Nameserver 和 Broker。
*   **Console 安装**：编译 `rocketmq-externals` 中的 console 模块，配置 Nameserver 地址。
*   **IDEA Debug**：设置 `ROCKETMQ_HOME` 环境变量，拷贝配置文件，直接运行 `NamesrvStartup` 和 `BrokerStartup` 进行源码调试。

### 3.3 HA 核心工作机制 (1.4)
*   **主从同步**：Slave 主动拉取 Master 的 CommitLog。
*   **读写分离策略**：
    *   默认优先从 Master 拉取。
    *   当 Master 积压消息超过物理内存 40% (`accessMessageInMemoryMaxRatio`) 时，建议从 Slave 拉取。
    *   配置 `slaveReadEnable=true` 允许 Slave 读。
*   **消费进度同步**：
    *   消费进度优先汇报给 Master。
    *   Slave 定时从 Master 同步消费进度 (`syncAll`)。
    *   Master 宕机恢复后，通过客户端汇报或 Slave 同步机制保持一致。

### 3.4 监控指标异常：TPS 为 0 但积压减少 (1.5)
*   **现象**：Console 显示消费 TPS 为 0，但消息积压数在下降。
*   **原因**：发生了**主从切换读取**。
    *   Console 查询的是 Master 节点的统计信息 (`GROUP_GET_NUMS`)。
    *   由于消息堆积严重，Consumer 被引导至 Slave 节点拉取消息。
    *   Slave 节点产生了实际的消费流量，但 Master 节点统计为 0。
*   **验证**：查看 Slave 节点的 `stats.log`，会发现非零的 TPS。

### 3.5 新消费组初次启动位置 (1.6)
*   **问题**：设置 `CONSUME_FROM_LAST_OFFSET`，新消费组却从头开始消费。
*   **原因**：
    *   新消费组无历史偏移量。
    *   Broker 查询最小偏移量 (`minOffset`) 为 0（即第一个文件未被删除）。
    *   且该偏移量对应的消息在 PageCache 中。
    *   Broker 返回 offset 0，导致从头消费。
*   **解决方案**：
    *   若 Topic 数据很久，minOffset > 0，则正常从尾部消费。
    *   若需强制从尾部，可使用 `resetOffsetByTime` 命令重置偏移量为当前时间戳或最大偏移量。

### 3.6 进程自动退出排查 (1.7)
*   **现象**：Broker 进程无故退出。
*   **排查**：
    *   检查 GC 日志（排除 OOM）。
    *   检查 `broker.log` 中的 `shutdownHook`（表明是正常停止信号）。
    *   检查系统日志 `/var/log/messages` 或 `history`。
*   **常见原因**：运维人员启动时未使用 `nohup`，会话断开导致进程被杀。
*   **优雅重启步骤**：
    1.  关闭 Broker 写权限 (`updateBrokerConfig brokerPermission=4`)。
    2.  等待写入 TPS 降为 0。
    3.  Kill 进程。
    4.  重启并恢复写权限。

### 3.7 Topic 扩容后的消费陷阱 (1.8)
*   **现象**：Topic 队列扩容后，部分队列消息积压，无法消费。
*   **原因**：
    *   集群扩容增加了新的 Broker 节点。
    *   新 Broker 上没有旧 Consumer Group 的订阅关系配置 (`subscriptionGroup.json`)。
    *   若 `autoCreateSubscriptionGroup=false`，Consumer 无法从新 Broker 拉取消息。
*   **解决**：在新 Broker 上手动创建订阅组，或同步 `subscriptionGroup.json`。

### 3.8 System Busy / Broker Busy 分析 (1.9 & 1.10 & 1.17)
*   **错误类型**：
    1.  `[REJECTREQUEST]system busy`：PageCache 繁忙（写锁持有 > 1s）或 TransientStorePool 耗尽。
    2.  `RejectedExecutionException`：发送线程池队列满（默认 10000）。
    3.  `[PC_SYNCHRONIZED]broker busy`：PageCache 繁忙。
    4.  `[TIMEOUT_CLEAN_QUEUE]broker busy`：请求在队列等待超过 `waitTimeMillsInSendQueue` (默认 200ms)。
*   **根本原因**：磁盘 IO 瓶颈、PageCache 抖动、CPU 负载高。
*   **解决方案**：
    *   **开启 `transientStorePoolEnable=true`**：利用堆外内存 (DirectByteBuffer) 实现读写分离，减少 PageCache 锁竞争。**注意**：极端情况下可能丢失少量未刷盘数据。
    *   **扩容**：增加 Broker 节点或升级硬件（SSD）。
    *   **调整参数**：适当增加 `waitTimeMillsInSendQueue`（如 1000ms），避免过早快速失败。
    *   **客户端重试**：确保客户端对 `SYSTEM_BUSY` 进行重试（旧版本 Bug 可能导致不重试，需升级或自行捕获重试）。

### 3.9 NameServer 假死导致的全局故障 (1.11)
*   **现象**：单台物理机故障，导致整个集群不可用长达 10 分钟。
*   **原因**：
    *   NameServer 与 Broker 部署在同一台机器。
    *   机器故障但 TCP 连接未立即断开（假死）。
    *   Client 连接该 NameServer 超时，但未关闭连接（`clientCloseSocketIfTimeout` 默认为 false），导致无法切换到其他 NameServer。
*   **最佳实践**：
    *   **NameServer 与 Broker 物理隔离部署**。
    *   修改源码或配置，使 Client 在超时时关闭连接并切换 NameServer。

### 3.10 一行代码导致消息丢失 (1.12)
*   **Bug 描述**：RocketMQ 早期版本中，`SYSTEM_BUSY` 错误码未包含在客户端自动重试的逻辑中。
*   **后果**：Broker 快速失败返回 Busy，客户端抛出异常但不重试，导致消息丢失。
*   **解决**：
    *   升级 RocketMQ 版本（官方已修复）。
    *   业务层增加发送失败的重试补偿机制（存入 DB 或本地日志）。

## 4. 高级特性与实战

### 4.1 DLedger 多副本与主从切换 (1.13)
*   **背景**：传统主从不支持自动切换，DLedger 基于 Raft 协议实现强一致性和自动选主。
*   **核心配置**：
    *   `enableDLegerCommitLog=true`
    *   `dLegerGroup`, `dLegerPeers`, `dLegerSelfId`
*   **升级步骤**：
    1.  准备至少 3 个节点。
    2.  修改配置文件，启用 DLedger，指定 Peer 信息。
    3.  迁移原有数据目录结构（config 文件复制到 dledger_store/config）。
    4.  启动集群，验证数据兼容性和主从切换功能。

### 4.2 msgId 与 offsetMsgId 释疑 (1.14)
*   **msgId (UniqId)**：
    *   **生成端**：Client 端生成。
    *   **组成**：IP + PID + ClassLoaderHash + 时间戳 + 自增序列。
    *   **用途**：全局唯一，用于业务幂等判断。
*   **offsetMsgId**：
    *   **生成端**：Broker 端生成。
    *   **组成**：Broker IP + Port + CommitLog Offset。
    *   **用途**：物理地址定位，用于运维查询消息内容 (`queryMsgById`)。
*   **注意**：Console 查询消息时兼容两种 ID；代码中 `MessageExt.getMsgId()` 优先返回 UniqId。

### 4.3 ACL 访问控制 (1.15)
*   **功能**：基于用户名/密码和 IP 白名单的权限控制。
*   **配置**：`plain_acl.yml`。
*   **权限粒度**：
    *   Global White List (IP)。
    *   User Level: AccessKey/SecretKey。
    *   Resource Level: Topic (PUB/SUB/DENY), Consumer Group (SUB/DENY)。
    *   Admin 权限：创建/删除 Topic、Group 等。
*   **启用**：`aclEnable=true` in `broker.conf`。

### 4.4 消息轨迹设计 (1.16)
*   **目的**：追踪消息发送、存储、消费的全链路状态。
*   **记录内容**：TraceType (Pub/SubBefore/SubAfter), Time, Topic, MsgId, CostTime, Success/Fail 等。
*   **存储**：
    *   默认存储在系统 Topic `RMQ_SYS_TRACE_TOPIC`。
    *   建议独立部署一台 Broker 专门存储轨迹数据，避免影响业务性能。
*   **实现**：通过 Hook 机制异步发送轨迹消息。

### 4.5 消息发送常见问题总结 (1.17)
*   **No route info of this topic**：
    *   检查 Topic 是否创建。
    *   检查 Client 连接的 Nameserver 是否正确。
    *   生产环境禁止自动创建，需预创建。
*   **消息发送超时**：
    *   检查 Broker `store.log` 中的 PageCache RT 分布。
    *   检查客户端 GC 停顿。
    *   **优化**：缩短单次超时时间（如 500ms），增加重试次数（如 3-5 次），利用快速失败机制规避慢节点。
*   **System/Busry Busy**：参考 3.8 节，重点在于开启 `transientStorePoolEnable` 和扩容。

---

## 总结与建议

1.  **架构设计**：
    *   NameServer 与 Broker **必须物理隔离**。
    *   生产环境关闭 `autoCreateTopicEnable` 和 `autoCreateSubscriptionGroup`。
    *   高吞吐场景开启 `transientStorePoolEnable` 以优化 PageCache 性能。

2.  **运维规范**：
    *   使用 `nohup` 或 systemd 管理进程。
    *   优雅重启：先禁写，再停服。
    *   扩容时需同步 Topic 和 SubscriptionGroup 配置到新节点。

3.  **开发最佳实践**：
    *   **幂等性**：使用 `msgId` (UniqId) 或业务 Key 实现消费幂等。
    *   **重试机制**：不要完全依赖 SDK 内部重试，业务层应捕获异常并进行补偿重试（特别是针对 `SYSTEM_BUSY`）。
    *   **顺序消息**：谨慎使用，确保分区键合理，并处理好异常阻塞问题。
    *   **监控**：关注 `stats.log`，区分 Master/Slave 的消费 TPS，避免误判。

4.  **演进方向**：
    *   金融级高可用场景考虑迁移至 **DLedger** 模式，实现自动主从切换和数据强一致性。