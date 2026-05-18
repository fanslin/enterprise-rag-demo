# 评测体系升级设计

## 目标

把当前固定问题、单关键词命中的检索 smoke test 升级为一个轻量但完整的评测闭环。用户应该可以使用外部评测集运行检索评测，可以一次比较多组 `topK` 和 `similarityThreshold` 参数，并在前端看到通过率、失败原因和命中引用。

## 范围

本设计覆盖当前 Spring Boot + Spring AI RAG MVP 的评测能力升级，仍以检索质量为主，不引入 LLM-as-judge 或外部评测平台。

本次包含：

- 将硬编码评测用例迁移到资源文件。
- 支持每个评测用例配置多个期望关键词、期望来源和参考答案。
- `GET /api/eval` 返回结构化评测报告，而不是单纯的结果数组。
- 支持逗号分隔的 `topK` 和 `similarityThreshold` 参数矩阵。
- 为每条失败用例返回明确失败原因。
- 前端评测区域展示汇总、参数组合、逐条结果和失败原因。
- 更新 API 文档、RAG 实现文档和 README。
- 增加服务层、控制器和前端语法验证。

本次不包含：

- 调用模型生成答案并自动判断答案质量。
- 计算成本、延迟、token 用量等运行指标。
- 保存历史评测报告。
- 上传自定义评测集。
- 独立命令行离线评测工具。

## 评测集设计

评测集放在资源文件：

```text
src/main/resources/eval-cases.json
```

文件内容是 JSON 数组。每个用例包含：

- `id`：稳定用例 id，便于报告和后续定位。
- `question`：评测问题。
- `expectedKeywords`：期望任一召回片段包含的关键词列表。
- `expectedSources`：期望召回来源列表，可以为空。
- `expectedAnswer`：人工阅读参考答案，本次不参与自动评分。

示例：

```json
[
  {
    "id": "probation-leave",
    "question": "试用期请假会影响转正吗？",
    "expectedKeywords": ["转正"],
    "expectedSources": ["company-policy.md"],
    "expectedAnswer": "试用期请假需要按制度处理，是否影响转正取决于制度约束和审批情况。"
  }
]
```

`expectedKeywords` 至少需要一个非空关键词。`expectedSources` 可以为空，表示只评关键词命中，不评来源命中。`expectedAnswer` 可以为空字符串，但字段保留，方便后续扩展生成质量评测。

## API 设计

现有接口路径保持不变：

```http
GET /api/eval?topK=4&similarityThreshold=0.0
```

新增支持参数矩阵：

```http
GET /api/eval?topK=2,4,6&similarityThreshold=0.0,0.3
```

参数规则：

- `topK` 可以是单个整数或逗号分隔整数列表。
- `similarityThreshold` 可以是单个小数或逗号分隔小数列表。
- 未传参数时沿用 `RagChatService` 默认检索参数。
- 参数值继续复用现有校验规则：`topK` 为 1 到 20，`similarityThreshold` 为 0.0 到 1.0。
- 如果任一参数格式非法，返回 HTTP 400 和明确错误消息。

响应结构升级为报告对象：

```json
{
  "summary": {
    "totalRuns": 2,
    "totalCases": 8,
    "passedCases": 6,
    "failedCases": 2,
    "passRate": 0.75
  },
  "runs": [
    {
      "topK": 4,
      "similarityThreshold": 0.0,
      "totalCases": 4,
      "passedCases": 3,
      "failedCases": 1,
      "passRate": 0.75,
      "results": []
    }
  ]
}
```

逐条结果包含：

- `id`
- `question`
- `expectedKeywords`
- `expectedSources`
- `expectedAnswer`
- `passed`
- `failureReason`
- `failureMessage`
- `retrieved`

失败原因枚举：

- `PASSED`：通过。
- `NO_RETRIEVED_DOCUMENTS`：没有召回任何片段。
- `MISSING_KEYWORDS`：召回了片段，但没有命中期望关键词。
- `MISSING_SOURCE`：关键词命中，但召回来源不符合期望。

判定顺序：

1. 如果召回为空，返回 `NO_RETRIEVED_DOCUMENTS`。
2. 如果没有命中任一期望关键词，返回 `MISSING_KEYWORDS`。
3. 如果配置了期望来源，但召回来源没有命中任一期望来源，返回 `MISSING_SOURCE`。
4. 否则返回 `PASSED`。

## 服务设计

新增一个评测集加载组件，负责从 classpath 读取 `eval-cases.json` 并解析为不可变用例列表。加载失败或配置非法时，应用启动或首次运行评测时抛出明确业务错误。

`EvalService` 负责：

- 接收解析后的参数矩阵。
- 对每个参数组合运行完整评测集。
- 调用 `RagChatService.retrieve(question, topK, similarityThreshold)` 获取召回片段。
- 根据关键词和来源规则生成逐条结果。
- 聚合每组 run 的通过数、失败数和通过率。
- 聚合整个报告的总通过率。

为了保持边界清晰，参数字符串解析放在控制器或独立 parser 中，评分逻辑放在 `EvalService` 内部。

## 前端设计

评测区继续使用当前侧栏位置，不改成独立页面。

前端变化：

- Top K 输入和相似度阈值输入允许逗号分隔值，例如 `2,4,6` 和 `0,0.3`。
- 点击“运行评测”后展示报告汇总：通过率、通过数、失败数、参数组合数。
- 多参数组合时按组合分组展示每个 run。
- 每条用例展示问题、通过/失败、失败原因、期望关键词、期望来源和命中引用。
- 失败原因使用中文展示，便于演示时快速解释。

前端不保存评测历史。每次点击运行评测都会替换当前报告。

## 错误处理

格式错误或范围错误返回 HTTP 400。

典型错误：

- `topK 参数格式错误：<value>`
- `similarityThreshold 参数格式错误：<value>`
- `topK 必须在 1 到 20 之间`
- `similarityThreshold 必须在 0.0 到 1.0 之间`
- `评测集不能为空`
- `评测用例缺少期望关键词：<id>`

未预期异常继续走现有统一异常处理，不把内部细节暴露给前端。

## 兼容性

`GET /api/eval` 路径保持不变，但响应体从数组升级为报告对象。这是面向当前 demo 的可接受破坏性变更，因为前端和文档会同步升级。

单参数调用仍然支持：

```http
GET /api/eval?topK=4&similarityThreshold=0.0
```

旧的“固定四个问题”语义通过默认 `eval-cases.json` 保留。

## 测试设计

服务层测试覆盖：

- 能从外部评测集读取用例。
- 单参数组合时生成一个 run。
- 多个 `topK` 和多个阈值时生成笛卡尔积 run。
- 没有召回片段时返回 `NO_RETRIEVED_DOCUMENTS`。
- 召回片段缺少关键词时返回 `MISSING_KEYWORDS`。
- 关键词命中但来源不匹配时返回 `MISSING_SOURCE`。
- 关键词和来源都命中时返回 `PASSED`。
- 报告 summary 正确聚合通过率。

控制器测试覆盖：

- 单参数请求返回报告结构。
- 逗号分隔参数请求传入多组参数。
- 非法参数返回 HTTP 400。

前端验证覆盖：

- `node --check src/main/resources/static/app.js`

最终验证命令：

```bash
make verify
make package
```
