# 演进路线

当前项目已经具备 RAG MVP 闭环。下面是从学习 Demo 升级到企业级智能应用时的建议路线。

## P0：让 MVP 更可靠

- 增加 controller/service 层测试。
- 为 `RagChatService` 增加 mock `VectorStore` 和 mock `ChatClient` 测试。
- 增加空知识库、空问题、非法文件类型、模型异常的测试。
- 在前端展示更明确的错误状态，例如 API Key 未配置、知识库为空、模型服务不可用。
- 扩展检索参数调优能力，对比不同 Embedding 模型和模型组合。

## P1：知识库生产化

- 将 `SimpleVectorStore` 替换为持久化向量库：
  - ChromaDB
  - PostgreSQL + pgvector
  - Milvus
  - Elasticsearch/OpenSearch vector search
  - Redis Stack
- 增加文档删除、重建索引和重复导入处理。
- 增加文档版本号和租户/部门隔离字段。
- 支持 PDF、Word、Excel、HTML 等企业常见格式。
- 抽象文档解析 pipeline：
  - upload
  - parse
  - clean
  - chunk
  - embed
  - index

## P2：检索质量提升

- 支持混合检索：向量检索 + 关键词检索。
- 增加 reranker，对初召回结果重新排序。
- 引入标题、章节、页码等结构化 metadata。
- 根据文档类型选择不同 chunk 策略。
- 记录每次问答的检索片段和模型回答，便于回放分析。

## P3：提示词工程和模型治理

- 将 prompt 模板版本化。
- 增加不同 Chat Model、Embedding Model 的配置切换。
- 引入回答格式约束，例如 JSON schema 或固定 Markdown 模板。
- 对高风险问题增加拒答策略和人工确认。
- 增加 token 用量、响应时间、模型错误率统计。

## P4：评测体系

- 建立人工标注评测集：
  - question
  - expected answer
  - expected source
  - expected keywords
- 指标拆分：
  - 检索召回率
  - 引用准确率
  - 回答忠实度
  - 拒答准确率
  - 延迟和成本
- 增加离线评测命令，支持不同 `topK`、阈值和模型组合对比。
- 将评测结果保存为报告，用于模型和参数选择。

## P5：企业级应用能力

- 用户认证与权限控制。
- 按用户权限过滤可检索文档。
- 管理后台：文档列表、索引状态、失败重试。
- 审计日志：谁问了什么、命中了哪些文档、模型回答是什么。
- 敏感信息治理：脱敏、黑名单、输出安全检查。
- 多轮对话记忆，但必须避免把旧上下文污染当前检索结果。

## 面试讲解建议

可以按以下结构介绍项目：

1. 业务目标：企业制度知识库问答，减少员工查制度成本。
2. 技术选型：Spring Boot + Spring AI + Groq Chat Model + 智谱 AI Embedding Model + 向量库。
3. RAG 主链路：上传文档、切分、Embedding、向量检索、Prompt 约束、回答引用。
4. 工程取舍：内存向量库适合 MVP，生产环境会换 pgvector 或 Milvus。
5. 质量保障：固定问题集做检索 smoke test，后续会扩展到离线评测。
6. 风险控制：Prompt 约束不编造、引用来源、无命中直接提示知识库不足。
7. 演进方向：持久化、权限过滤、混合检索、reranker、评测平台和可观测性。
