# Enterprise RAG Demo

一个面向 Java 工程师学习 AI 应用开发的 Spring AI + RAG MVP。

## 能学到什么

- Spring AI 接入大模型与 Embedding 模型
- 文档切分、向量化、相似度检索、上下文组装
- 基于 Prompt 约束模型只根据企业知识库回答
- 用固定问题集做最小化检索评测

## 运行前准备

需要 Java 17，并配置 Groq Chat API 和智谱 AI Embedding API。Groq 负责回答生成，智谱 Embedding 负责文档入库和检索向量化：

```bash
export GROQ_API_KEY="你的 Groq API Key"
export GROQ_BASE_URL="https://api.groq.com/openai"
export GROQ_CHAT_MODEL="llama-3.3-70b-versatile"

export ZAI_API_KEY="你的智谱 AI API Key"
export ZAI_BASE_URL="https://open.bigmodel.cn/api/paas"
export ZAI_EMBEDDING_MODEL="embedding-3"

export APP_RAG_SIMILARITY_THRESHOLD="0.0"
```

注意：项目仍使用 Spring AI OpenAI starter 的配置命名空间，因为 Groq 和智谱都提供 OpenAI 兼容接口。`GROQ_BASE_URL` 配到 `https://api.groq.com/openai`，Chat 路径由项目配置为 `/v1/chat/completions`；`ZAI_BASE_URL` 配到 `https://open.bigmodel.cn/api/paas`，Embedding 路径由项目配置为 `/v4/embeddings`。

`APP_RAG_SIMILARITY_THRESHOLD` 控制检索相似度阈值。不同 Embedding 模型的分数分布不同，MVP 默认设为 `0.0`，先保证能看到召回结果；调优时可以逐步提高。

项目已内置 Maven Wrapper 和项目级 Maven 配置，会优先使用 Maven Central，避免本机全局 Maven 镜像配置不可用时影响构建。也可以使用本机 Maven 3.9+ 运行同样命令。

## 验证

```bash
./mvnw test
./mvnw package
```

## 启动

```bash
./mvnw spring-boot:run
```

打开：

```text
http://localhost:8080
```

如果只是本地体验 UI、入库流程和 RAG 主链路形态，可以启用本地 Mock 模式，不需要配置外部模型 API Key：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

本地 Mock 模式会使用项目内置的确定性 Embedding 和固定 Chat 回答，不会调用 Groq 或智谱 AI。

## 使用流程

1. 点击“导入样例制度”，系统会把样例 Markdown 切分并写入内存向量库。
2. 在右侧提问，例如“试用期请假会影响转正吗？”。
3. 点击“运行评测”，查看标准问题是否检索到了包含期望关键词的片段。

## 项目文档

- [文档索引](docs/README.md)
- [架构说明](docs/architecture.md)
- [RAG 实现原理](docs/rag-implementation.md)
- [API 说明](docs/api.md)
- [开发与运行手册](docs/development.md)
- [向量库选型](docs/vector-store-options.md)
- [演进路线](docs/roadmap.md)

## API

```http
POST /api/documents
Content-Type: multipart/form-data
file=<.md 或 .txt 文件>
```

```http
POST /api/documents/sample
```

```http
POST /api/chat
Content-Type: application/json

{
  "question": "报销发票最晚什么时候提交？",
  "topK": 4,
  "similarityThreshold": 0.0
}
```

```http
GET /api/eval?topK=4&similarityThreshold=0.0
```

## 后续升级方向

- 把内存向量库替换为 ChromaDB、PostgreSQL + pgvector、Milvus 等持久化向量库
- 增加 PDF、Word 解析
- 增加多轮对话记忆
- 增加人工标注评测集，计算召回率和引用准确率
- 扩展检索参数调优能力，对比不同 Embedding 模型和模型组合
