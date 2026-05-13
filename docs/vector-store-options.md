# 向量库选型

## 当前状态

项目当前使用 Spring AI `SimpleVectorStore`。它的优点是零基础设施、容易理解，适合学习 RAG 的最小闭环；缺点是数据只在内存中，应用重启后知识库会丢失，也不适合多实例共享。

## ChromaDB 是否适合替换

ChromaDB 适合作为本项目从 MVP 进入“可重复体验”的第一步持久化向量库：

- 有独立服务进程，知识库数据不会随 Spring Boot 应用重启丢失。
- 支持向量相似度检索和 metadata，能承接当前 `source`、`chunk`、`indexedAt` 这类字段。
- Spring AI 已提供 Chroma VectorStore 集成，迁移时可以继续通过 `VectorStore` 接口访问。
- 本地 Docker 启动成本低，适合学习、演示和小规模内部工具原型。

它不一定是企业生产环境的唯一首选。生产化还需要评估备份恢复、权限隔离、多租户、水平扩展、可观测性、索引重建、版本升级和团队运维经验。如果企业已有 PostgreSQL，可以优先评估 pgvector；如果数据规模和检索吞吐更高，可以评估 Milvus、OpenSearch 或 Elasticsearch vector search。

## 推荐路线

1. 当前阶段继续保留 `SimpleVectorStore`，先把 Groq Chat 和智谱 AI Embedding 配置跑稳定。
2. 下一步增加 ChromaDB profile，例如 `spring.profiles.active=chroma`，让本地开发可以在内存向量库和 ChromaDB 之间切换。
3. 引入 ChromaDB 后补充导入、重启、再次检索的验证用例，证明数据确实持久化。
4. 如果后续目标是企业级部署，再把 ChromaDB 与 pgvector、Milvus、OpenSearch 放在同一套评测问题集下对比召回率、延迟、运维复杂度和成本。

## 迁移影响

需要改动的范围预计较小：

- Maven 增加 Spring AI Chroma VectorStore starter。
- 本地增加 ChromaDB 启动方式，例如 Docker Compose。
- 将 `AiConfig` 中手动创建的 `SimpleVectorStore` 改为 profile 条件，或交给 Spring AI Chroma auto-configuration 创建。
- 增加 ChromaDB 地址、collection name 等配置。
- 更新运行手册和验证脚本。

业务层的 `KnowledgeBaseService` 和 `RagChatService` 已经依赖 Spring AI `VectorStore` 接口，理论上不需要大改。

## Spring AI 配置要点

Spring AI 的 Chroma starter 是：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
</dependency>
```

常用配置项：

```properties
spring.ai.vectorstore.chroma.client.host=http://localhost
spring.ai.vectorstore.chroma.client.port=8000
spring.ai.vectorstore.chroma.collection-name=enterprise-rag-demo
spring.ai.vectorstore.chroma.initialize-schema=true
```

`initialize-schema` 默认不是开启状态。如果希望 Spring AI 自动创建 collection，需要显式设置为 `true`。无论使用 ChromaDB 还是其他向量库，都仍然需要一个 `EmbeddingModel` bean；向量库只负责存储和检索向量，不负责生成向量。
