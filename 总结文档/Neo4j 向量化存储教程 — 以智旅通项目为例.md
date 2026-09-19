# Neo4j 向量化存储教程 — 以智旅通项目为例

## 目录

- [1. 什么是向量化存储](#1-什么是向量化存储)
- [2. 项目当前技术栈](#2-项目当前技术栈)
- [3. 核心概念](#3-核心概念)
- [4. 环境准备](#4-环境准备)
- [5. Step 1：Neo4j 向量索引配置](#5-step-1neo4j-向量索引配置)
- [6. Step 2：实体类改造（支持向量字段）](#6-step-2实体类改造支持向量字段)
- [7. Step 3：Embedding 向量生成服务](#7-step-3embedding-向量生成服务)
- [8. Step 4：帖子向量化存储与检索](#8-step-4帖子向量化存储与检索)
- [9. Step 5：Controller 层对接](#9-step-5controller-层对接)
- [10. 完整流程图](#10-完整流程图)
- [11. 常见问题](#11-常见问题)

---

## 1. 什么是向量化存储

传统数据库按**精确匹配**查询（如 `WHERE title = 'xxx'`），而向量化存储将文本转换为**高维数值向量**，通过**向量相似度**实现**语义搜索**。

```
用户搜索："Q币充值"
    ↓
Embedding 模型 → [0.12, -0.34, 0.56, ..., 0.78]   （1024维向量）
    ↓
在 Neo4j 中计算余弦相似度，找到语义最接近的帖子

结果可能匹配到：
  - "冲Q币就选淘宝代充"        ← 高相似度 ✅
  - "游戏点卡充值攻略"        ← 中等相似度
  - "今天天气真好"            ← 低相似度 ❌
```

### 为什么用 Neo4j 做向量存储？

| 特性     | Neo4j                    | Redis (你项目中也在用) |
| -------- | ------------------------ | ---------------------- |
| 向量索引 | 原生支持（5.11+）        | 支持                   |
| 图关系   | **天然支持节点间关系**   | 不支持                 |
| 混合查询 | 向量 + 图属性 + 关系遍历 | 仅向量                 |
| 适用场景 | 社交网络、推荐、知识图谱 | 缓存、简单向量检索     |

本项目是**社区帖子系统**，帖子之间有评论关系（Post-[COMMENT]->Comment），用 Neo4j 可以同时做**向量语义搜索 + 图关系查询**。

---

## 2. 项目当前技术栈

```
智旅通 (ZhiLvTong)
├── Spring Boot 3.5.12 + Java 17
├── Spring Data Neo4j          ← 图数据库 ORM
├── Spring AI                  ← AI 集成框架
│   ├── spring-ai-vector-store       ← 向量存储抽象层
│   └── spring-ai-starter-vector-store-redis  ← Redis向量（已引入）
├── DJL (Deep Java Library)    ← 本地 Embedding 模型推理
│   ├── ai.djl:api:0.27.0           ← 核心 API
│   ├── ai.djl:model-zoo:0.27.0     ← 模型仓库
│   └── ai.djl.pytorch:pytorch-engine:0.27.0  ← PyTorch 引擎
├── 阿里云 DashScope            ← 远程 Embedding API（text-embedding-v4, 1024维）
├── Neo4j                       ← 图数据库（bolt://192.168.119.9:7687）
└── Redis                       ← 缓存/向量备选
```

---

## 3. 核心概念

### 3.1 向量（Vector / Embedding）

一段文本经过 Embedding 模型后，输出一个固定长度的浮点数数组：

```java
// "冲Q币就选淘宝代充" → 1024维向量
float[] embedding = {0.023f, -0.156f, 0.892f, ..., -0.041f};  // 长度=1024
```

本项目使用阿里云 **text-embedding-v4** 模型，维度为 **1024**（见 application.yml 第24-25行）。

### 3.2 相似度计算

常用算法：

| 算法                    | 公式                           | 范围    | 说明                       |
| ----------------------- | ------------------------------ | ------- | -------------------------- |
| **余弦相似度 (Cosine)** | cos(θ) = A·B / (\|A\| × \|B\|) | [-1, 1] | **最常用**，衡量方向一致性 |
| 欧氏距离                | \|A - B\|²                     | [0, ∞)  | 衡量空间距离               |
| 内积 (Dot Product)      | A · B                          | (-∞, ∞) | 计算最快                   |

Neo4j 向量索引默认使用 **余弦相似度**。

### 3.3 Neo4j 向量索引（5.11+）

从 Neo4j 5.11 开始支持原生向量索引：

```cypher
CREATE VECTOR INDEX post_embedding_index 
FOR (p:Post) ON (p.embedding) 
OPTIONS { indexConfig: { `vector.dimensions`: 1024, `vector.similarity_function`: 'cosine' } };
```

---

## 4. 环境准备

### 4.1 确认 Neo4j 版本

向量索引需要 **Neo4j 5.11+** 或 **Neo4j AuraDB**。在你的 Neo4j 浏览器中执行：

```cypher
RETURN dbms.components() AS components;
```

确认版本 ≥ 5.11。如果版本过低，需要升级或使用 AuraDB 免费实例。

### 4.2 项目依赖确认

你的 [pom.xml](pom.xml) 已包含以下关键依赖：

```xml
<!-- Spring Data Neo4j -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-neo4j</artifactId>
</dependency>

<!-- Spring AI 向量抽象层 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
</dependency>

<!-- DJL 本地推理 -->
<dependency>
    <groupId>ai.djl</groupId>
    <artifactId>api</artifactId>
    <version>0.27.0</version>
</dependency>
<dependency>
    <groupId>ai.djl.pytorch</groupId>
    <artifactId>pytorch-engine</artifactId>
    <version>0.27.0</version>
</dependency>

<!-- 阿里云 DashScope Embedding -->
<!-- 通过 spring-ai-alibaba-starter-dashscope 自动引入 -->
```

---

## 5. Step 1：Neo4j 向量索引配置

### 方式一：手动创建索引（推荐先验证）

在 Neo4j Browser 或通过代码执行：

```cypher
-- 创建帖子内容的向量索引（1024维，余弦相似度）
CREATE VECTOR INDEX post_content_vector_idx 
IF NOT EXISTS
FOR (p:Post) ON (p.contentEmbedding) 
OPTIONS {
    indexConfig: {
        `vector.dimensions`: 1024,
        `vector.similarity_function`: 'cosine'
    }
};

-- 确认索引创建成功
SHOW INDEXES WHERE name = 'post_content_vector_idx';
```

### 方式二：Java 配置类自动创建（推荐 ✅）

项目已内置配置类，启动时自动创建索引，无需手动操作。

文件位置：`src/main/java/com/woniu/zhilvtong/config/Neo4jVectorIndexConfig.java`

```java
package com.woniu.zhilvtong.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Neo4jVectorIndexConfig implements CommandLineRunner {

    @Autowired
    private Neo4jClient neo4jClient;

    @Override
    public void run(String... args) {
        initPostContentVectorIndex();
        log.info("Neo4j 向量索引初始化完成");
    }

    private void initPostContentVectorIndex() {
        String cypher = """
                CREATE VECTOR INDEX post_content_vector_idx 
                IF NOT EXISTS
                FOR (p:Post) ON (p.contentEmbedding) 
                OPTIONS {
                    indexConfig: {
                        `vector.dimensions`: 1024,
                        `vector.similarity_function`: 'cosine'
                    }
                }
                """;
        try {
            neo4jClient.query(cypher).run();
            log.info("向量索引 post_content_vector_idx 就绪（1024维, cosine）");
        } catch (Exception e) {
            log.warn("向量索引创建失败，请确认 Neo4j 版本 >= 5.11: {}", e.getMessage());
        }
    }
}
```

**工作原理**：

| 要点                           | 说明                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| `implements CommandLineRunner` | Spring Boot 启动完成后自动执行 `run()` 方法                  |
| `Neo4jClient`                  | Spring Data Neo4j 提供的 Cypher 执行客户端（项目中其他 Service 也在用） |
| `IF NOT EXISTS`                | 幂等操作，重复启动不会报错                                   |
| `try-catch`                    | 即使 Neo4j 版本不支持或连接失败，也不阻塞项目启动            |

启动日志示例：
```
INFO  c.w.z.config.Neo4jVectorIndexConfig - 向量索引 post_content_vector_idx 就绪（1024维, cosine）
INFO  c.w.z.config.Neo4jVectorIndexConfig - Neo4j 向量索引初始化完成
```

---

## 6. Step 2：实体类改造（支持向量字段）

修改现有 [Post.java](src/main/java/com/woniu/zhilvtong/entity/community/Post.java)，添加向量字段：

```java
package com.woniu.zhilvtong.entity.community;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Date;
import java.util.List;

@Data
@Node("Post")
public class Post {

    @Id
    @GeneratedValue
    private Long postId;

    private Long userId;
    private String title;
    private String content;
    private List<String> postTags;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    // ========== 新增向量字段 ==========

    /**
     * 内容向量（1024维）
     * 用于语义搜索匹配
     */
    private float[] contentEmbedding;

    /**
     * 标题向量（可选，用于标题的快速匹配）
     */
    private float[] titleEmbedding;

}
```

### 关键说明

| 字段               | 类型      | 用途                                         |
| ------------------ | --------- | -------------------------------------------- |
| `contentEmbedding` | `float[]` | 帖子正文的向量表示，用于**内容语义搜索**     |
| `titleEmbedding`   | `float[]` | 标题的向量表示（可选），用于**标题快速匹配** |

> **为什么用 `float[]` 而不是 `List<Float>`？**
> Neo4j 原生向量索引要求数组类型。`float[]` 性能更好，且与 DJL/Spring AI 的输出格式一致。

---

## 7. Step 3：Embedding 向量生成服务

### 7.1 使用阿里云 DashScope（推荐 — 项目已集成）

你的 [application.yml](src/main/resources/application.yml) 已配置了 DashScope Embedding：

```yaml
spring:
  ai:
    dashscope:
      embedding:
        enabled: true
        options:
          model: text-embedding-v4
          dimensions: 1024
```

创建 Embedding 服务类：

```java
package com.woniu.zhilvtong.service.community;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 将单段文本转换为向量
     * @param text 输入文本
     * @return 1024维浮点数组
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[1024];
        }
        EmbeddingResponse response = embeddingModel.embed(List.of(text));
        return response.getResults().get(0).getOutput().getFloatValues()
                .stream()
                .mapToDouble(d -> (float) d)
                .toArray();
    }

    /**
     * 批量转换文本为向量
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingResponse response = embeddingModel.embed(texts);
        return response.getResults().stream()
                .map(result -> result.getOutput().getFloatValues()
                        .stream()
                        .mapToDouble(d -> (float) d)
                        .toArray())
                .toList();
    }

    /**
     * 计算两个向量的余弦相似度
     * @return 相似度值，范围 [-1, 1]，越接近 1 越相似
     */
    public float cosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null || vecA.length != vecB.length) {
            return 0f;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += Math.pow(vecA[i], 2);
            normB += Math.pow(vecB[i], 2);
        }
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
```

### 7.2 使用 DJL 本地模型（离线方案）

如果不想调用远程 API，可以用 DJL 在本地跑 Embedding 模型：

```java
package com.woniu.zhilvtong.service.community;

import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslateException;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class DjlEmbeddingService {

    private ZooModel<String, NDArray> model;
    private Predictor<String, NDArray> predictor;
    private NDManager manager;

    @PostConstruct
    public void init() throws Exception, TranslateException {
        manager = NDManager.newBaseManager();

        Criteria<String, NDArray> criteria = Criteria.builder()
                .setTypes(String.class, NDArray.class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2")  // 384维轻量模型
                .optEngine("PyTorch")
                .build();

        model = criteria.loadModel();
        predictor = model.newPredictor();
    }

    public float[] embed(String text) throws TranslateException {
        NDArray array = predictor.predict(text);
        return array.toFloatArray();
    }

    @PreDestroy
    public void cleanup() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
        if (manager != null) manager.close();
    }
}
```

> **注意**：DJL 本地模型首次运行会自动下载模型文件（约 100MB+），确保网络通畅。`all-MiniLM-L6-v2` 是 384 维模型，比 DashScope 的 1024 维小但速度更快。

---

## 8. Step 4：帖子向量化存储与检索

### 8.1 改造 PostServiceImpl — 保存时自动生成向量

修改 [PostServiceImpl.java](src/main/java/com/woniu/zhilvtong/service/impl/community/PostServiceImpl.java)：

```java
package com.woniu.zhilvtong.service.impl.community;

import cn.hutool.core.util.IdUtil;
import com.woniu.zhilvtong.entity.community.Post;
import com.woniu.zhilvtong.mapper.community.PostRepository;
import com.woniu.zhilvtong.service.community.EmbeddingService;
import com.woniu.zhilvtong.service.community.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EmbeddingService embeddingService;    // 注入 Embedding 服务

    @Override
    public Post savePost(Post post) {
        post.setCreateTime(new Date());
        post.setPostId(IdUtil.getSnowflakeNextId());

        // ===== 新增：生成内容向量并存储 =====
        float[] contentVector = embeddingService.embed(post.getContent());
        post.setContentEmbedding(contentVector);

        System.out.println("拿到的PostId：" + post.getPostId());
        System.out.println("生成的向量维度：" + contentVector.length);

        return postRepository.save(post);
    }

    @Override
    public Post updatePost(Post post) {
        // 更新时重新生成向量（内容可能变了）
        if (post.getContent() != null) {
            post.setContentEmbedding(embeddingService.embed(post.getContent()));
        }
        return postRepository.save(post);
    }

    @Override
    public Post queryById(Long postId) {
        Optional<Post> optional = postRepository.findById(postId);
        return optional.orElse(null);
    }

    @Override
    public List<Post> queryByKeyWords(String keyWords) {
        return postRepository.queryByKeyWords(keyWords);
    }

    @Override
    public List<Post> queryAll() {
        return postRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        postRepository.deleteById(id);
    }
}
```

### 8.2 改造 PostRepository — 添加向量相似度查询

修改 [PostRepository.java](src/main/java/com/woniu/zhilvtong/mapper/community/PostRepository.java)：

```java
package com.woniu.zhilvtong.mapper.community;

import com.woniu.zhilvtong.entity.community.Post;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface PostRepository extends Neo4jRepository<Post, Long> {

    // ===== 原有方法 =====

    @Query("""
            MATCH (p:Post)
            WHERE p.title CONTAINS $keyWords OR p.content CONTAINS $keyWords
            RETURN p ORDER BY CASE WHEN p.title CONTAINS $keyWords THEN 0 ELSE 1 END
            """)
    List<Post> queryByKeyWords(@Param("keyWords") String keyWords);

    // ===== 新增：向量相似度搜索 =====

    /**
     * 基于内容向量的语义搜索
     * 使用 Neo4j 原生向量索引进行近似最近邻（ANN）搜索
     *
     * @param queryVector 查询向量（1024维）
     * @param limit 返回数量限制
     * @return 按相似度降序排列的帖子列表
     */
    @Query("""
            CALL db.index.vector.queryNodes(
                'post_content_vector_idx', 
                $limit, 
                $queryVector
            ) YIELD node, score
            RETURN node AS post, score
            ORDER BY score DESC
            """)
    List<Post> findByContentVectorSimilarity(
            @Param("queryVector") float[] queryVector,
            @Param("limit") int limit
    );

    /**
     * 混合搜索：关键词 + 向量相似度加权排序
     * 适合"既包含关键词又语义相近"的场景
     */
    @Query("""
            MATCH (p:Post)
            WITH p,
                 CASE WHEN p.title CONTAINS $keyWords THEN 0.3 ELSE 0 END AS keywordScore
            CALL db.index.vector.queryNodes(
                'post_content_vector_idx', 
                $limit, 
                $queryVector
            ) YIELD node, score
            WHERE id(node) = id(p)
            RETURN p AS post, (keywordScore + score * 0.7) AS finalScore
            ORDER BY finalScore DESC
            LIMIT $limit
            """)
    List<Post> hybridSearch(
            @Param("keyWords") String keyWords,
            @Param("queryVector") float[] queryVector,
            @Param("limit") int limit
    );
}
```

### 8.3 向量搜索服务方法

在 PostService 接口和 PostImpl 中添加：

```java
// PostService.java — 新增接口方法
List<Post> semanticSearch(String queryText, int limit);
List<Post> hybridSearch(String keywords, int limit);

// PostServiceImpl.java — 实现
@Override
public List<Post> semanticSearch(String queryText, int limit) {
    // 1. 将查询文本转为向量
    float[] queryVector = embeddingService.embed(queryText);
    
    // 2. 用向量去 Neo4j 搜索最相似的帖子
    return postRepository.findByContentVectorSimilarity(queryVector, limit);
}

@Override
public List<Post> hybridSearch(String keywords, int limit) {
    float[] queryVector = embeddingService.embed(keywords);
    return postRepository.hybridSearch(keywords, queryVector, limit);
}
```

---

## 9. Step 5：Controller 层对接

在 [PostController.java](src/main/java/com/woniu/zhilvtong/controller/community/PostController.java) 中新增接口：

```java
// ========== 新增向量搜索接口 ==========

/**
 * 语义搜索（纯向量）
 * 用户输入自然语言描述，返回语义最相近的帖子
 * 
 * 例：GET /community/post/semantic?query=如何充值Q币&limit=5
 */
@GetMapping("/semantic")
public ResponseEntity semanticSearch(
        @RequestParam String query,
        @RequestParam(defaultValue = "5") int limit) {
    List<Post> posts = postService.semanticSearch(query, limit);
    if (posts.isEmpty()) {
        return ResponseEntity.error("未找到相关内容！");
    }
    return ResponseEntity.ok("语义搜索完成！", posts);
}

/**
 * 混合搜索（关键词 + 向量）
 * 同时考虑关键词匹配和语义相似度
 * 
 * 例：GET /community/post/hybrid?keyWords=充值&limit=5
 */
@GetMapping("/hybrid")
public ResponseEntity hybridSearch(
        @RequestParam String keyWords,
        @RequestParam(defaultValue = "5") int limit) {
    List<Post> posts = postService.hybridSearch(keyWords, limit);
    if (posts.isEmpty()) {
        return ResponseEntity.error("未找到相关内容！");
    }
    return ResponseEntity.ok("混合搜索完成！", posts);
}
```

---

## 10. 完整流程图

### 帖子发布流程（写入向量）

```
前端提交帖子数据（title, content, tags）
    │
    ▼
PostController.addPost(post)
    │
    ▼
PostServiceImpl.savePost(post)
    │
    ├─→ post.setCreateTime(new Date())
    ├─→ post.setPostId(snowflakeId)
    │
    ├─→ embeddingService.embed(post.content)    ← 调用 DashScope/DJL
    │   └─→ 返回 float[1024]                    ← 1024维向量
    │
    └─→ post.setContentEmbedding(vector)
         │
         ▼
    postRepository.save(post)                   ← 写入 Neo4j
         │
         ▼
    Neo4j 节点：
    (:Post {
      postId: 2044701599855018000,
      title: "冲Q币就选淘宝代充",
      content: "你冲Q币吗？",
      contentEmbedding: [0.023, -0.156, 0.892, ...],  ← 1024维向量已存储
      createTime: "2026-04-16 16:57:04"
    })
```

### 帖子搜索流程（向量检索）

```
用户输入搜索词："哪里可以买Q币"
    │
    ▼
PostController.semanticSearch(query="哪里可以买Q币", limit=5)
    │
    ▼
PostServiceImpl.semanticSearch(queryText, limit)
    │
    ├─→ embeddingService.embed("哪里可以买Q币")   ← 查询文本转向量
    │   └─→ queryVector = [0.045, -0.123, 0.767, ...]
    │
    └─→ postRepository.findByContentVectorSimilarity(queryVector, 5)
         │
         ▼
    Neo4j 执行：
    CALL db.index.vector.queryNodes('post_content_vector_idx', 5, queryVector)
         │
         ▼
    返回 Top 5 最相似的帖子（含相似度分数 score）：
    1. "冲Q币就选淘宝代充"        score: 0.92  ← 高度匹配
    2. "游戏点卡充值方式汇总"      score: 0.85
    3. "虚拟货币交易注意事项"      score: 0.71
    ...
```

---

## 11. 常见问题

### Q1：向量索引报错 "Index does not exist"

**原因**：Neo4j 版本低于 5.11，或索引未创建。

**解决**：
```bash
# 查看 Neo4j 版本
RETURN version();

# 手动创建索引（见 Step 1）
CREATE VECTOR INDEX post_content_vector_idx FOR (p:Post) ON (p.contentEmbedding) OPTIONS {...};
```

### Q2：DashScope Embedding 调用失败

**排查步骤**：
1. 确认 [application.yml](src/main/resources/application.yml) 中 `api-key` 有效
2. 确认网络可访问阿里云 API（`dashscope.aliyuncs.com`）
3. 检查配额是否耗尽（免费版有 QPS 限制）

**降级方案**：改用 DJL 本地模型（见 7.2 节）

### Q3：向量维度不匹配

**错误信息**：`Expected vector of dimension X but got Y`

**原因**：DashScope 配置的是 1024 维，但索引创建时写了其他维度。

**解决**：统一所有地方为同一维度：
- `application.yml` → `dimensions: 1024`
- Cypher 索引 → `` `vector.dimensions`: 1024 ``
- 实体类 `float[]` 数组长度由模型决定（无需手动控制）

### Q4：搜索结果不够准确

**优化方向**：
1. **混合搜索**：结合关键词 + 向量（见 `hybridSearch` 方法）
2. **分块策略**：长帖子的 content 太长时，可以先分段再分别 embedding
3. **调整权重**：混合搜索中关键词权重 vs 向量权重的比例（当前 0.3 : 0.7）

### Q5：性能问题 — 全量重新生成向量

当需要为已有帖子补全向量时：

```java
@Service
public class PostVectorMigrationService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 为所有没有向量的帖子批量生成向量
     */
    public void migrateAllPosts() {
        List<Post> allPosts = postRepository.findAll();
        int count = 0;
        
        for (Post post : allPosts) {
            if (post.getContentEmbedding() == null || post.getContentEmbedding().length == 0) {
                post.setContentEmbedding(embeddingService.embed(post.getContent()));
                postRepository.save(post);
                count++;
                
                // 每 10 条打印一次进度
                if (count % 10 == 0) {
                    System.out.println("已处理 " + count + "/" + allPosts.size());
                }
            }
        }
        System.out.println("向量迁移完成！共处理 " + count + " 条帖子");
    }
}
```

可通过临时 Controller 接口或 `@PostConstruct` 触发一次。

---

## 附录：快速参考卡片

| 操作             | Cypher 命令                            | Java 方法                                 |
| ---------------- | -------------------------------------- | ----------------------------------------- |
| 创建向量索引     | `CREATE VECTOR INDEX ...`              | 初始化脚本                                |
| 单条文本转向量   | —                                      | `embeddingService.embed(text)`            |
| 保存带向量的帖子 | —                                      | `postService.savePost(post)`              |
| 语义搜索         | `CALL db.index.vector.queryNodes(...)` | `postService.semanticSearch(query, n)`    |
| 混合搜索         | 自定义 Cypher                          | `postService.hybridSearch(keywords, n)`   |
| 余弦相似度       | —                                      | `embeddingService.cosineSimilarity(a, b)` |

---

*本教程基于智旅通 (ZhiLvTong) 项目实际代码编写，技术栈为 Spring Boot 3.5 + Neo4j + Spring AI + DashScope/DJL*