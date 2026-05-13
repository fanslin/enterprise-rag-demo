# Documentation Index

本目录记录 Enterprise RAG Demo 的架构、实现原理、接口与演进方向，便于复盘、面试讲解和后续迭代。

## 文档列表

- [架构说明](architecture.md)：项目边界、模块职责、运行时依赖和请求链路。
- [RAG 实现原理](rag-implementation.md)：文档切分、向量入库、检索、Prompt 组装、引用返回和评测方式。
- [API 说明](api.md)：前端调用的 REST API、请求响应结构和错误处理。
- [开发与运行手册](development.md)：环境变量、启动、验证、Maven 配置和常见问题。
- [向量库选型](vector-store-options.md)：ChromaDB 是否适合替换内存向量库，以及后续迁移路线。
- [演进路线](roadmap.md)：从 MVP 升级到企业级 RAG 应用的优先级建议。

## 当前 MVP 范围

当前项目聚焦最小闭环：

1. 使用 Spring AI 接入 Groq Chat Model 与智谱 AI Embedding Model。
2. 支持 Markdown/TXT 文档上传与样例制度导入。
3. 将文本切分成带重叠的片段，写入内存向量库。
4. 用户提问时执行相似度检索，并把检索结果作为上下文交给大模型回答。
5. 返回回答和引用片段，提供固定问题集做最小检索评测。

它不是完整生产级知识库系统。生产化还需要持久化向量库、权限隔离、文档解析管线、离线评测集、可观测性、成本治理和安全防护。
