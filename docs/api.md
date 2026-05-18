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
  "message": "服务暂时不可用，请稍后重试或检查模型服务配置。"
}
```

## 模型配置状态

```http
GET /api/health/ai
```

响应：

```json
{
  "mode": "local",
  "chatConfigured": true,
  "embeddingConfigured": true,
  "message": "本地 Mock 模式已启用，不会调用外部模型服务。"
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
- 如果同名文档已经存在，新导入会覆盖旧片段：服务端会先删除旧向量片段，再写入新片段。

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

## 删除文档

```http
DELETE /api/documents/{source}
```

说明：

- `source` 是文档来源文件名，例如 `company-policy.md`。
- 客户端应对 `source` 做 URL 编码。
- 删除会移除该文档的内存摘要、缓存原文和对应向量片段。

响应：

```json
{
  "source": "company-policy.md",
  "chunks": 4,
  "indexedAt": "2026-05-13T10:00:00Z"
}
```

文档不存在时返回 HTTP 400：

```json
{
  "message": "文档不存在：company-policy.md"
}
```

## 重建索引

```http
POST /api/documents/rebuild
```

说明：

- 基于当前内存中缓存的文档原文重建向量索引。
- 服务端会先删除当前已知向量片段，再重新切分并写入向量库。
- 当前阶段不做持久化，应用重启后没有可重建的历史文档。

响应：

```json
{
  "documents": 2,
  "chunks": 7,
  "message": "已重建 2 个文档，共 7 个片段"
}
```

## RAG 问答

```http
POST /api/chat
Content-Type: application/json

{
  "question": "报销发票最晚什么时候提交？",
  "topK": 4,
  "similarityThreshold": 0.0
}
```

`topK` 和 `similarityThreshold` 可省略，省略时使用 `application.yml` 中的默认值。`topK` 范围是 1 到 20，`similarityThreshold` 范围是 0.0 到 1.0。

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
GET /api/eval?topK=4&similarityThreshold=0.0
```

也可以传入逗号分隔的参数矩阵：

```http
GET /api/eval?topK=2,4,6&similarityThreshold=0.0,0.3
```

说明：

- 评测用例来自 `src/main/resources/eval-cases.json`。
- `topK` 支持单个整数或逗号分隔整数列表，范围是 1 到 20。
- `similarityThreshold` 支持单个小数或逗号分隔小数列表，范围是 0.0 到 1.0。
- 多个 `topK` 和多个 `similarityThreshold` 会组成笛卡尔积，分别运行完整评测集。
- 不传参数时使用 `application.yml` 中的默认检索参数。

响应：

```json
{
  "summary": {
    "totalRuns": 1,
    "totalCases": 4,
    "passedCases": 4,
    "failedCases": 0,
    "passRate": 1.0
  },
  "runs": [
    {
      "topK": 4,
      "similarityThreshold": 0.0,
      "totalCases": 4,
      "passedCases": 4,
      "failedCases": 0,
      "passRate": 1.0,
      "results": [
        {
          "id": "probation-leave",
          "question": "试用期请假会影响转正吗？",
          "expectedKeywords": ["转正"],
          "expectedSources": ["company-policy.md"],
          "expectedAnswer": "试用期请假应按公司制度申请，是否影响转正以制度要求和审批结果为准。",
          "passed": true,
          "failureReason": "PASSED",
          "failureMessage": "通过",
          "retrieved": [
            {
              "source": "company-policy.md",
              "chunk": 1,
              "preview": "..."
            }
          ]
        }
      ]
    }
  ]
}
```

失败原因：

| failureReason | 含义 |
| --- | --- |
| `PASSED` | 命中期望关键词，且命中期望来源或该用例未配置来源 |
| `NO_RETRIEVED_DOCUMENTS` | 没有召回任何片段 |
| `MISSING_KEYWORDS` | 召回了片段，但没有命中任一期望关键词 |
| `MISSING_SOURCE` | 关键词命中，但召回来源没有命中期望来源 |

非法参数返回 HTTP 400：

```json
{
  "message": "topK 参数格式错误：abc"
}
```

```json
{
  "message": "similarityThreshold 必须在 0.0 到 1.0 之间"
}
```

说明：

- 当前自动评分仍聚焦检索质量，不调用模型判断答案质量。
- `expectedAnswer` 是人工阅读参考答案，保留给后续生成质量评测扩展。
- 评测可用于快速发现知识库未导入、Embedding 配置错误、检索阈值过高、参数组合召回不足等问题。
