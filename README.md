# Enterprise RAG Demo

一个面向 Java 工程师学习 AI 应用开发的 Spring AI + RAG MVP。

## 能学到什么

- Spring AI 接入大模型与 Embedding 模型
- 文档切分、向量化、相似度检索、上下文组装
- 基于 Prompt 约束模型只根据企业知识库回答
- 用固定问题集做最小化检索评测

## 运行前准备

需要 Java 17，并配置 OpenAI 或兼容 OpenAI API 的服务：

```bash
export OPENAI_API_KEY="你的 key"
export OPENAI_BASE_URL="https://api.openai.com"
export OPENAI_CHAT_MODEL="gpt-4o-mini"
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"
```

如果使用兼容 OpenAI API 的国内模型服务，把 `OPENAI_BASE_URL`、`OPENAI_CHAT_MODEL`、`OPENAI_EMBEDDING_MODEL` 改成对应值即可。

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

## 使用流程

1. 点击“导入样例制度”，系统会把样例 Markdown 切分并写入内存向量库。
2. 在右侧提问，例如“试用期请假会影响转正吗？”。
3. 点击“运行评测”，查看标准问题是否检索到了包含期望关键词的片段。

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
  "question": "报销发票最晚什么时候提交？"
}
```

```http
GET /api/eval
```

## 后续升级方向

- 把内存向量库替换为 PostgreSQL + pgvector
- 增加 PDF、Word 解析
- 增加多轮对话记忆
- 增加人工标注评测集，计算召回率和引用准确率
- 增加检索参数调优页面，对比 topK、相似度阈值、不同 Embedding 模型
