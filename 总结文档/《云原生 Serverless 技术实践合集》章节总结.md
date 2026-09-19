# 《云原生 Serverless 技术实践合集》章节总结

## 目录说明
- **说明**：上传的文件并非单一书籍，而是由 5 份独立的技术演讲 PDF 组成的合集。因此，本总结将每一份 PDF 视为一个独立的“章节”进行处理。
- **排序依据**：按照文件上传顺序及内容逻辑（从 FaaS 应用层 -> IaC 管理层 -> Knative 编排层 -> Serverless K8s 基础设施层 -> 底层性能优化）进行结构化梳理。
- **内容来源**：严格基于提供的 5 份 PDF 文本内容提取，未包含原文件中不可识别的图表细节。

---

## 第1章：FaaS & Cloud Native 函数计算的云原生之旅
> 来源：【FaaS & Cloud Native 函数计算的云原生之旅 】+常率+2020.8.1.pdf

### 1. 核心论点
- **解决问题**：解决传统 FaaS 在交付物（代码包）上的限制（如编译环境依赖、大小限制、缺乏标准版本管理），以及分布式函数调用链路中的可观测性黑盒问题。
- **核心观点**：通过支持自定义容器镜像（Custom Container）和云原生 Tracing 能力，FaaS 能够复用容器生态的工具链与交付标准，实现真正的云原生融合，让业务更早体验 Serverless 优势。

### 2. 关键概念/事件
- **Custom Container Runtime**：函数计算新增的运行时类型，允许用户直接使用 OCI 标准容器镜像作为交付物，解除了代码包大小和特定编译环境的限制。
- **Time-to-market 全貌**：指出 CI/CD 是快速交付的关键，FaaS 虽简化了上线后运维，但曾为“上线前”引入难题；自定义镜像打通了 FaaS 与 Kubernetes 的统一交付物。
- **云原生 Tracing**：函数计算与阿里云 ARMS 及开源 Jaeger 打通，支持跨函数调用 Trace 透传、SDK 自动埋点及系统级冷启动延迟观测。
- **Vendor Lock-in 解除**：通过容器镜像标准化，用户代码不再绑定特定云厂商的 FaaS 私有格式，便于迁移和复用开源镜像。

### 3. 逻辑推演/叙事脉络
本章首先阐述云原生带来的业务价值（上市速度与成本优化），随即指出 FaaS 在实际落地中面临的“交付物困境”和“可观测性缺失”两大痛点。接着，作者提出解决方案：一是引入 Custom Container 机制，演示了如何复用 TensorFlow Serving 等开源镜像并分层构建；二是介绍 Tracing 能力，展示如何解决分布式链路追踪难题。最后总结 FaaS 正通过与容器生态、可观测体系的深度融合，迈向更完善的云原生形态。

### 4. 经典金句/数据
> “FaaS简化‘上线后’，却为‘上线前’引入新难题……难点在交付物（代码压缩包）。”

> “FaaS+容器镜像帮助业务更快、更早地体验 Serverless 优势。”

---

## 第2章：Infrastructure As Code 在阿里巴巴的初步实践
> 来源：【Infrastructure As Code 在阿里巴巴的初步实践】+许晓斌+2020.8.1.pdf

### 1. 核心论点
- **解决问题**：解决业务交付中对 PaaS/IaaS 资源依赖加重导致的管控复杂、体验不一及变更风险问题。
- **核心观点**：Infrastructure as Code (IaC) 是实现云原生的关键技术，它通过代码化定义基础设施，提升了资源的复用性、一致性和透明度，并使研发关注点从控制台操作回归到代码与架构本身。

### 2. 关键概念/事件
- **IaC (Infrastructure as Code)**：用代码化方式定义及管理 PaaS 和 IaaS 资源，替代传统 SDK/控制台手动操作，确保一致性与合规。
- **CueLang**：阿里巴巴内部采用的 IaC 描述语言，支持多层级继承、扩展限制及模块化，用于定义服务模版、容器规格、发布策略等。
- **Policy as Code**：基于 Open Policy Engine，将安全管控（如防止缩容至零、控制重启频率）编码化，嵌入 IaC 引擎执行。
- **SRE 场景化应用**：IaC 不仅用于部署，还支撑架构分析、批量操作、全局版本升级及自动化建站等运维治理场景。

### 3. 逻辑推演/叙事脉络
作者从业务交付对基础设施依赖加重的现状出发，引出 IaC 的定义及其在安全、合规、一致性上的优势。结合阿里巴巴 IaaS 全面上云但管控体验割裂的现实，论证 IaC 是云原生化的必经之路。随后展示了 IaC 如何改变研发关注点（从运维操作转向代码），并通过 CueLang 和 Policy as Code 的具体 Use Case 说明其落地实践。最后展望了工具生态建设、云服务深度集成及引擎开源的未来方向。

### 4. 经典金句/数据
> “我们认为 Infrastructure as Code 是实现云原生的关键技术。”

> “Infrastructure as Code，就是用代码化的方式，定义及管理 PaaS资源和 IaaS资源，进而提升 reusability, consistency, transparency。”

---

## 第3章：深入浅出剖析 Knative Serverless 架构
> 来源：【深入浅出剖析 Knative Serverless 架构】+牛秋霖（冬岛）+2020.8.1.pdf

### 1. 核心论点
- **解决问题**：解决在 Kubernetes 上直接构建 Serverless 应用时面临的抽象层级过低、组件繁杂（Deployment+Service+HPA+Ingress等）及弹性与灰度策略冲突的问题。
- **核心观点**：Knative 提供了面向 Serverless 工作负载的高级抽象，通过与云产品（如 SLB、ECI）深度融合及管控组件下沉，实现了免运维、低成本且具备极致弹性的 Serverless 体验。

### 2. 关键概念/事件
- **Knative Serving 抽象**：包含 Service（生命周期管理）、Configuration（期望状态）、Revision（不可变快照）、Route（流量路由）四大核心 API。
- **Gateway 与云的融合**：将 Istio/Ingress Gateway 替换为云原生 ALB/SLB，减少十几个组件，降低运维与 IaaS 成本，提升稳定性。
- **管控组件下沉**：Knative Controller 等组件部署在管控端而非用户集群，实现开箱即用、免运维及高管控。
- **弹性效率与成本平衡**：通过保留规格消除冷启动，利用突发性能实例（T系列）和 Spot 实例降低 40% 以上成本。

### 3. 逻辑推演/叙事脉络
本章先通过对比传统 K8s 资源编排的复杂性，引出“为什么需要 Knative”。接着详解 Knative Serving 的核心架构与 API 模型。重点阐述了阿里云对 Knative 的改造：将网关和控制器与云基础设施融合，解决开源版本运维重、成本高的问题。最后通过“弹性效率&成本”的分析，展示了如何利用 ECI T系列实例在保持秒级弹性的同时大幅降低成本，并给出了动手实践路径。

### 4. 经典金句/数据
> “如何才能让用户以及上层 PaaS平台以面向服务的方式使用云的能力？”

> “免冷启动：通过保留规格消除了从 0 到 1 的 30秒冷启动时间；突发性能实例成本比标准规格实例降低 40%的成本。”

---

## 第4章：Serverless Kubernetes - 理想，现实和未来
> 来源：【Serverless Kubernetes - 理想，现实和未来】+张维+2020.8.1.pdf

### 1. 核心论点
- **解决问题**：解决传统 Kubernetes 节点管理负担重、弹性扩容慢、资源闲置成本高，以及 Serverless 容器在大规模场景下的调度与网络瓶颈。
- **核心观点**：Serverless Kubernetes (ASK) 通过 Nodeless 架构和 ECI 底层技术，实现了“无限”容量与秒级弹性，同时保持了 K8s API 兼容性，是连接容器生态与 Serverless 理想的现实桥梁。

### 2. 关键概念/事件
- **ECI (Elastic Container Instance)**：基于安全沙箱的轻量级虚拟化容器，支持 0.25c-64c 规格，秒级启动，与 ECS 并池调度，是 ASK 的算力底座。
- **ASK (Serverless Kubernetes)**：Nodeless K8s 服务，无需管理节点，30秒可创建 500 Pod，支持 Spot 实例与预留券，集成 ALB Ingress 与 ARMS/SLS。
- **ACK on ECI**：混合部署模式，Long-run 应用跑在 ECS，弹性/任务型应用跑在 ECI，兼顾成本与弹性。
- **Serverless Scheduler**：专为 ECI 设计的调度器，支持批量调度、多可用区亲和性，直通 ECI Pod 生命周期管理，避免传统调度器瓶颈。

### 3. 逻辑推演/叙事脉络
作者从 Serverless 的理想（敏捷、弹性、成本）出发，指出容器是最佳载体。接着介绍 ECI 作为底层基础设施的技术选型（安全沙箱、ECS并池）。在此基础上展开 ASK 的架构设计：Nodeless、云端调度、PrivateZone 服务发现、ALB Ingress 等，解释了如何支撑万级 Pod 规模。最后总结了 Serverless 容器在在线业务、AI、大数据等场景的价值，并坦诚提出了启动效率、SLO 弹性等未来挑战。

### 4. 经典金句/数据
> “Run Containers without Managing Infrastructure……容器成为云上的一等公民。”

> “极致弹性: 30s 500 pod；单集群支持1万Pod。”

---

## 第5章：Serverless 场景下 Pod 创建效率优化
> 来源：【Serverless 场景下 Pod 创建效率优化】+张翼飞+2020.8.1.pdf

### 1. 核心论点
- **解决问题**：解决 Serverless 场景下因镜像拉取（下载+解压）耗时过长导致的 Pod 创建慢、弹性响应延迟问题。
- **核心观点**：Pod 创建效率是 Serverless 核心竞争力，需通过镜像预热、并行解压、非压缩镜像、P2P 分发及按需加载等多维度技术手段，系统性突破镜像拉取瓶颈。

### 2. 关键概念/事件
- **镜像预热**：分为调度前预热和调度中预热，提前将镜像拉取到节点，消除创建时的等待时间（OpenKruise 项目支持）。
- **并行解压 (Unpigz)**：将单线程 gunzip 替换为多线程 unpigz，使 golang:1.10 镜像解压耗时从 13.07s 降至 8.38s，提升约 35%。
- **非压缩镜像**：Push 时不压缩 tar 包，Pull 时跳过解压步骤，使 golang:1.10 拉取总耗时从 16.97s 降至 13.16s，提升约 49%。
- **按需加载镜像**：使用 stargz-snapshotter / DADI 加速器，改变“全量下载后才能启动”的模式，仅读取实际使用的 6.4% 数据即可启动容器。
- **原地升级**：通过 K8s Patch + ReadinessGates 实现容器原地更新，避免重建 Pod 带来的镜像拉取开销。

### 3. 逻辑推演/叙事脉络
本章以数据揭示镜像拉取占容器启动时间的 76%，且解压耗时往往高于下载耗时。随后逐一展开优化方案：先用 unpigz 解决解压 CPU 瓶颈；再尝试非压缩镜像牺牲存储换时间；接着引入 P2P (Dragonfly) 解决中心 Registry 带宽瓶颈；最后通过按需加载 (Lazy Pulling) 从根本上改变镜像使用范式。此外，还介绍了镜像预热和原地升级作为补充手段。每种方案均配有实测对比数据，论证严谨。

### 4. 经典金句/数据
> “Pulling packages accounts for 76% of container start time, but only 6.4% of that data is read.”

- **golang:1.10 镜像优化效果**：
    - Gunzip 解压：13.07s → Unpigz：8.38s (提升 35.88%)
    - 标准拉取：16.97s → 非压缩镜像：13.16s (提升 49.81%)