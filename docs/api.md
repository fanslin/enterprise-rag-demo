# API 说明

服务默认监听：

```text
http://localhost:8080
```

前端页面位于：

```text
GET /
```

## 统一错误格式

实现位置：`ApiExceptionHandler`

业务参数错误返回 HTTP 400：

```json
{
  "message": "问题不能为空"
}
```

未预期错误返回 HTTP 500：

```json
{
  "message": "服务处理失败：..."
}
```

## 上传文档

```http
POST /api/documents
Content-Type: multipart/form-data

file=<.md 或 .txt 文件>
```

说明：

- 仅支持 `.md` 和 `.txt`。
- 文件内容按 UTF-8 读取。
- 文件大小限制由 `application.yml` 控制，当前最大 5MB。

响应：

```json
{
  "source": "company-policy.md",
  "chunks": 4,
  "message": "已写入知识库"
}
```

## 导入样例制度

```http
POST /api/documents/sample
```

说明：

- 读取 classpath 下的 `samples/company-policy.md`。
- 用于无上传文件时快速体验 RAG 流程。

响应：

```json
{
  "source": "company-policy.md",
  "chunks": 4,
  "message": "已写入知识库"
}
```

## 查询已导入文档

```http
GET /api/documents
```

响应：

```json
[
  {
    "source": "company-policy.md",
    "chunks": 4,
    "indexedAt": "2026-05-13T10:00:00Z"
  }
]
```

注意：当前文档摘要保存在内存中，应用重启后会丢失。

## RAG 问答

```http
POST /api/chat
Content-Type: application/json

{
  "question": "报销发票最晚什么时候提交？"
}
```

响应：

```json
{
  "answer": "报销发票通常应在费用发生后 30 天内提交。引用：company-policy.md#2",
  "citations": [
    {
      "source": "company-policy.md",
      "chunk": 2,
      "preview": "费用发生后，员工应在 30 天内提交报销申请..."
    }
  ]
}
```

没有检索命中时：

```json
{
  "answer": "知识库里没有检索到足够相关的资料。请先上传制度文档，或换一种问法。",
  "citations": []
}
```

## 运行检索评测

```http
GET /api/eval
```

响应：

```json
[
  {
    "question": "试用期请假会影响转正吗？",
    "expectedKeyword": "转正",
    "passed": true,
    "retrieved": [
      {
        "source": "company-policy.md",
        "chunk": 1,
        "preview": "..."
      }
    ]
  }
]
```

说明：

- 当前评测只检查检索片段是否包含期望关键词。
- 它用于快速发现知识库未导入、Embedding 配置错误、检索阈值过高等问题。
