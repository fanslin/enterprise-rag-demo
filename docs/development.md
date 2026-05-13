# 开发与运行手册

## 环境要求

- JDK 17
- 可访问 Groq Chat API
- 可访问智谱 AI Embedding API
- 推荐使用项目自带 Maven Wrapper：`./mvnw`

项目已内置 Maven Wrapper 和项目级 Maven settings。这样可以避免本机全局 Maven 配置把依赖请求转发到不可用镜像。

## 环境变量

```bash
export GROQ_API_KEY="你的 Groq API Key"
export GROQ_BASE_URL="https://api.groq.com/openai"
export GROQ_CHAT_MODEL="llama-3.3-70b-versatile"

export ZAI_API_KEY="你的智谱 AI API Key"
export ZAI_BASE_URL="https://open.bigmodel.cn/api/paas"
export ZAI_EMBEDDING_MODEL="embedding-3"

export APP_RAG_SIMILARITY_THRESHOLD="0.0"
```

`GROQ_CHAT_MODEL` 控制回答生成，`ZAI_EMBEDDING_MODEL` 控制文档入库和检索向量化。项目仍使用 Spring AI OpenAI starter 的配置命名空间，因为 Groq 和智谱都提供 OpenAI 兼容接口。

`APP_RAG_SIMILARITY_THRESHOLD` 控制检索相似度阈值。不同 Embedding 模型的分数分布不同，智谱 Embedding 下先用 `0.0` 验证召回链路，再根据“运行评测”的结果逐步提高。

## 启动

```bash
./mvnw spring-boot:run
```

打开：

```text
http://localhost:8080
```

## 验证

```bash
./mvnw test
./mvnw package
```

当前测试覆盖：

- `DocumentChunkerTest`
  - 长文本可切分成多个非空片段。
  - 空白文本返回空列表。

## 体验流程

1. 启动应用。
2. 打开 `http://localhost:8080`。
3. 点击“导入样例制度”。
4. 输入问题，例如：
   - `试用期请假会影响转正吗？`
   - `报销发票最晚什么时候提交？`
   - `年假没休完怎么处理？`
5. 查看回答和引用片段。
6. 点击“运行评测”，观察固定问题集是否命中关键词。

## Maven 配置说明

相关文件：

- `.mvn/maven.config`
- `.mvn/settings.xml`
- `.mvn/global-settings.xml`
- `.mvn/wrapper/maven-wrapper.properties`

作用：

- `maven.config` 让 Maven 自动使用项目内 settings。
- `settings.xml` 指定项目本地仓库和 Maven Central。
- `global-settings.xml` 覆盖本机全局 settings，避免不可用的全局 mirror 干扰。
- Maven Wrapper 固定 Maven 版本，避免本机 Maven 太旧导致 Spring Boot 插件无法运行。

## 常见问题

### 页面能打开，但导入样例失败

导入样例会触发 Embedding 调用。如果 `ZAI_API_KEY` 无效或模型服务不可访问，向量入库会失败。

检查：

```bash
echo $ZAI_API_KEY
echo $ZAI_BASE_URL
echo $ZAI_EMBEDDING_MODEL
echo $APP_RAG_SIMILARITY_THRESHOLD
```

### 提问失败

提问会先检索，再调用 Chat Model。如果知识库为空，会返回“没有检索到足够相关的资料”。如果模型服务不可用，则会返回统一错误消息。

检查：

- 是否已导入文档。
- `GROQ_API_KEY` 是否有效。
- `GROQ_CHAT_MODEL` 是否存在。
- `GROQ_BASE_URL` 是否为 Spring AI 可用的 Groq OpenAI 兼容地址。

### Maven 访问了错误镜像

优先使用：

```bash
./mvnw test
```

不要直接依赖本机旧 Maven。项目已配置 Maven Central，正常情况下不会访问本机全局 settings 中的不可用镜像。

### 端口被占用

默认端口是 8080。可临时改端口启动：

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```
