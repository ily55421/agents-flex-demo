# 《Kubernetes 核心概念与进阶》课程讲义章节总结

## 目录说明
本总结基于上传的五个 PDF 文件整理。这些文件并非单一书籍，而是“阿里巴巴云原生技术公开课”系列中关于 Kubernetes 的不同专题讲义。为了符合结构化总结的要求，我将这五个独立的讲义视为全书的五个主要章节，并按照通常的学习逻辑（核心概念 -> 网络基础 -> 网络进阶 -> 调度资源 -> 存储架构）进行排序和总结。

1.  **第1章**：Kubernetes 核心概念（李响）
2.  **第2章**：Kubernetes 网络概念及策略控制（叶磊）
3.  **第3章**：Kubernetes 网络模型进阶（叶磊）
4.  **第4章**：Kubernetes 调度与资源管理（子誉）
5.  **第5章**：Kubernetes 存储架构及插件使用（阚俊宝）

---

## 第1章：Kubernetes 核心概念

### 1. 核心论点
本章旨在回答“什么是 Kubernetes”以及“它由哪些基本构件组成”。作者核心观点是：Kubernetes 是一个工业级的自动化容器编排平台，其核心在于通过声明式 API 管理 Pod、Service 等资源，实现应用的自动部署、弹性伸缩和自我修复。

### 2. 关键概念/事件
-   **Kubernetes (k8s)**：源于希腊语“舵手”，是自动化容器编排平台，核心功能包括服务发现、负载均衡、自动装箱、自我恢复、滚动发布等。
-   **Pod**：Kubernetes 最小的调度和资源单元，由一个或多个容器组成，共享网络和存储环境。
-   **Deployment**：控制器，用于定义 Pod 的副本数目和版本，负责维持 Pod 状态、自动恢复失败 Pod 及管理版本滚动升级。
-   **Service**：为一组 Pod 提供稳定的访问地址（VIP），屏蔽后端 Pod 的变化，支持 ClusterIP、NodePort、LoadBalancer 等类型。
-   **Namespace**：集群内部的逻辑隔离机制，用于鉴权和资源额度管理，不同 Namespace 中的资源可重名。

### 3. 逻辑推演/叙事脉络
本章首先定义 Kubernetes 及其核心价值（自动化编排）。接着深入架构层面，区分 Master 节点（API Server, etcd, Controller, Scheduler）和 Node 节点（Kubelet, Kube-proxy, Container Runtime）的职责。随后，逐一拆解核心 API 对象：从最小的执行单元 Pod，到持久化存储抽象 Volume，再到应用管理层面的 Deployment 和服务暴露层面的 Service。最后介绍 Namespace 隔离机制和 API 的基础结构（Metadata, Spec, Label），并以 Minikube 安装和常用命令作为实践结尾。

### 4. 经典金句/数据
> “Kubernetes-工业级容器编排平台... 核心功能：服务发现与负载均衡、容器自动装箱、存储编排、自动容器恢复、自动发布与回滚、配置与密文管理、批量执行、水平伸缩。”

> “Pod 是最小的调度以及资源单元... 提供给容器共享的运行环境（网络、进程空间）。”

---

## 第2章：Kubernetes 网络概念及策略控制

### 1. 核心论点
本章解决“Kubernetes 网络模型的基本约束是什么”以及“如何实现网络隔离”的问题。核心观点是：Kubernetes 采用“每 Pod 一 IP”的扁平网络模型，摒弃 NAT，要求所有 Pod 间可直接通信；而 Network Policy 则是在此连通性基础上实现微隔离和安全控制的关键工具。

### 2. 关键概念/事件
-   **Kubernetes 网络基本法**：所有 Pod 可与其他 Pod 直接通信（无需 NAT）；所有 Node 可与所有 Pod 直接通信；Pod 看到的 IP 即通信对方使用的 IP。
-   **Network Namespace (Netns)**：Linux 内核提供的网络虚拟化基础，每个 Pod 拥有独立的 Netns，包含独立的协议栈、IP、路由表和 iptables 规则。
-   **CNI (Container Network Interface)**：容器网络接口标准，主流方案包括 Flannel（Overlay/Host-gw）、Calico（BGP 直连）、Cilium（eBPF）等。
-   **Network Policy**：基于标签选择器（Label Selector）的网络访问控制策略，用于限制 Pod 间的入站（Ingress）和出站（Egress）流量，需网络插件支持。

### 3. 逻辑推演/叙事脉络
文章首先确立 Kubernetes 网络的“约法三章”基本模型，强调 Per-Pod-Per-IP 的重要性。接着深入底层，解释 Netns 如何实现网络隔离。随后对比主流网络插件方案（Flannel, Calico 等）的实现差异（Overlay vs Underlay）。最后引入 Network Policy，说明如何在默认全连通的模型上，通过标签匹配实现精细化的流量控制和攻击面减少。

### 4. 经典金句/数据
> “Kubernetes 对于 Pod 间的网络没有任何限制，只需满足如下「三个基本条件」：所有 Pod 可以与其他 Pod 直接通信，无需显式使用 NAT... Pod 可见的 IP 地址确为其他 Pod 与其通信时所用，无需显式转换。”

> “Network Policy 提供了基于策略的网络控制，用于隔离应用并减少攻击面。它使用标签选择器模拟传统的分段网络。”

---

## 第3章：Kubernetes 网络模型进阶

### 1. 核心论点
本章深入探讨“Pod 如何上网”、“Service 如何工作”以及“负载均衡的内部机制”。核心观点是：Pod 通过 CNI 插件获得真实 IP 并经由主机路由转发；Service 本质是客户端侧的内部负载均衡，由 Kube-proxy 通过 iptables 或 IPVS 实现虚拟 IP 到后端 Pod 的流量转发。

### 2. 关键概念/事件
-   **Pod 联网流程**：数据包从容器 Netns 经 veth pair 到 CNI 桥（cni0），再经主机路由表查找，通过物理网卡发出；远端节点接收后反向查路由和桥转发表到达目标容器。
-   **Service 工作原理**：Service 是一个稳定的虚 IP（ClusterIP），Kube-proxy 监控 API Server，将 Service 的 VIP 和后端 Pod IP 的映射关系写入内核的 iptables 或 IPVS 规则中。
-   **IPVS 模式**：相比 iptables，IPVS 是基于内核的 L4 负载均衡器，性能更优（支持每秒 10 万+ 转发），通过绑定 VIP 到本地接口并创建 Virtual Server 实现。
-   **Service 类型**：ClusterIP（集群内访问）、NodePort（节点端口暴露）、LoadBalancer（云厂商 LB 集成）、ExternalName（外部 DNS 映射）。

### 3. 逻辑推演/叙事脉络
本章从数据包的生命周期入手，详细拆解了跨节点 Pod 通信的五步路由过程（容器内->宿主机->远端宿主机->远端容器）。随后转向 Service 机制，解释其作为“内部负载均衡”的本质，并对比 iptables 与 IPVS 的实现差异，重点介绍了 IPVS 的配置步骤。最后梳理了四种 Service 类型的适用场景，并简述了云上完整负载均衡链路（Service -> Ingress -> NodePort -> Cloud SLB）。

### 4. 经典金句/数据
> “一句话，让一个功能聚集小团伙（Pod）正大光明的拥有自己的身份证 —— IP... Pod 的 IP 是真身份证，通行全球就这一个号，拒绝任何变造（NAT）。”

> “Service = Internal Load Balance @ Client Side... Kube-proxy 是实现核心，隐藏了大量复杂性，通过 apiserver 监控 Pod/Service 的变化，反馈到 LB 配置中。”

---

## 第4章：Kubernetes 调度与资源管理

### 1. 核心论点
本章解决“如何将 Pod 调度到合适的节点”以及“如何高效管理集群资源”的问题。核心观点是：调度是基于资源需求（Request/Limit）、亲和性规则和节点限制的匹配过程；通过 QoS 分类和优先级抢占机制，可以在资源受限情况下保障关键业务的稳定性。

### 2. 关键概念/事件
-   **调度过程**：过滤（Predicate）-> 打分（Priority）-> 绑定（Bind）。调度器寻找满足资源、亲和性、污点等条件的最优节点。
-   **资源 QoS (Quality of Service)**：
    -   **Guaranteed**：Request == Limit，最高优先级，OOM Score 最低，CPU 绑核。
    -   **Burstable**：Request < Limit，中等优先级。
    -   **BestEffort**：未设置 Request/Limit，最低优先级，优先被驱逐。
-   **亲和性与反亲和性**：PodAffinity/AntiAffinity（Pod 间关系）、NodeAffinity/Selector（Pod 对节点偏好）、Taint/Toleration（节点排斥与 Pod 容忍）。
-   **优先级与抢占 (Priority & Preemption)**：高优先级 Pod 在资源不足时，可驱逐低优先级 Pod 以腾出空间，确保关键任务运行。

### 3. 逻辑推演/叙事脉络
本章首先概述调度器的基本工作流程。接着分为“基础调度能力”和“高级调度能力”两部分。基础部分详细讲解资源调度（Request/Limit 的作用及 QoS 分类对底层 OOM 和 CPU 管理的影响）和关系调度（亲和性、污点容忍）。高级部分引入优先级概念，解释当集群资源饱和时，如何通过 PriorityClass 定义重要性，并通过抢占算法驱逐低优先级 Pod，从而实现集群资源的合理利用和业务保障。

### 4. 经典金句/数据
> “调度过程 = 把 Pod 放到合适 Node 上去... 什么是合适？满足 Pod 资源要求、满足 Pod 的特殊关系要求、满足 Node 限制条件要求、做到集群资源合理利用。”

> “Guaranteed: CPU/Memory 必须 request==limit... Memory 按 QoS 划分 OOMScore: Guaranteed-998, Burstable 2~999, BestEffort 1000。”

---

## 第5章：Kubernetes 存储架构及插件使用

### 1. 核心论点
本章解决“Kubernetes 如何抽象和管理持久化存储”的问题。核心观点是：Kubernetes 通过 PV/PVC/StorageClass 解耦存储供给与使用，并通过卷插件（Volume Plugins）实现具体存储设备的挂载；CSI（容器存储接口）作为新一代标准，取代了早期的 Flexvolume 和 In-Tree 插件，实现了存储系统与编排系统的彻底解耦。

### 2. 关键概念/事件
-   **存储核心对象**：
    -   **PV (Persistent Volume)**：集群级的存储资源抽象，由管理员创建。
    -   **PVC (Persistent Volume Claim)**：用户级的存储请求，由用户创建，与 PV 绑定。
    -   **StorageClass**：存储类，定义 PV 的动态 Provisioning 模板。
-   **存储架构组件**：PV Controller（生命周期管理）、AD Controller（Attach/Detach）、Volume Manager（Mount/Unmount，位于 Kubelet）。
-   **插件演进**：
    -   **In-Tree**：代码在内核中，迭代慢。
    -   **Flexvolume**：可执行文件方式，由 Kubelet 调用，非守护进程。
    -   **CSI (Container Storage Interface)**：标准化接口，支持多编排系统，通过 Sidecar 组件（如 external-attacher, node-driver-registrar）与 K8s 交互，支持动态扩容、快照等高级特性。

### 3. 逻辑推演/叙事脉络
本章首先介绍 Kubernetes 存储体系的整体架构，明确 PV Controller、AD Controller 和 Volume Manager 的职责分工。接着回顾早期的 Flexvolume 机制，分析其基于可执行文件的调用模式及局限性。随后重点引入 CSI 架构，解释其为何成为标准（解耦、容器化部署、功能丰富），详细拆解 CSI 的核心组件（Controller Server, Node Server）及关键 CRD 对象（CSIDriver, CSINode, VolumeAttachment）。最后对比了静态与动态存储卷的使用方式及 CSI 的新特性（扩容、快照）。

### 4. 经典金句/数据
> “Kubernetes 挂载 Volume 过程：Provision -> Attach -> Mount; Unmount -> Detach -> Delete。”

> “有了 Flexvolume，为何还要 CSI？提供容器存储统一接口，实现编排系统与存储类型解耦，在多平台运行（K8S, Mesos, Swarm）；容器化部署，减少环境依赖，增强安全性，丰富插件功能。”