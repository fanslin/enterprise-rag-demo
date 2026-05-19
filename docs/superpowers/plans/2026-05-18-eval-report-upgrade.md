# Eval Report Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade retrieval evaluation from fixed keyword checks to an external-case, multi-parameter report with failure reasons.

**Architecture:** Keep `/api/eval` as the single evaluation endpoint, but return an `EvalReport` object containing summary, per-parameter runs, and per-case results. Load default cases from `src/main/resources/eval-cases.json`, parse comma-separated query parameters in the controller, and keep scoring logic inside `EvalService`.

**Tech Stack:** Java 17 records, Spring Boot MVC, Jackson resource loading, Spring AI `Document`, vanilla JavaScript frontend, Maven tests.

---

### Task 1: Evaluation Data Model And Case Loading

**Files:**
- Create: `src/main/java/com/example/enterpriserag/dto/EvalReport.java`
- Create: `src/main/java/com/example/enterpriserag/dto/EvalSummary.java`
- Create: `src/main/java/com/example/enterpriserag/dto/EvalRunResult.java`
- Create: `src/main/java/com/example/enterpriserag/dto/EvalFailureReason.java`
- Modify: `src/main/java/com/example/enterpriserag/dto/EvalCaseResult.java`
- Create: `src/main/java/com/example/enterpriserag/service/EvalCase.java`
- Create: `src/main/java/com/example/enterpriserag/service/EvalCaseLoader.java`
- Create: `src/main/resources/eval-cases.json`
- Test: `src/test/java/com/example/enterpriserag/service/EvalCaseLoaderTest.java`

- [ ] **Step 1: Write failing loader tests**

Create `EvalCaseLoaderTest` with tests that construct `EvalCaseLoader` using an `ObjectMapper` and a `ByteArrayResource`.

Expected behaviors:
- `loadsCasesFromJsonResource` loads one case and preserves `id`, `question`, `expectedKeywords`, `expectedSources`, and `expectedAnswer`.
- `rejectsEmptyCaseList` throws `IllegalArgumentException` with `评测集不能为空`.
- `rejectsCaseWithoutKeywords` throws `IllegalArgumentException` with `评测用例缺少期望关键词：case-1`.

- [ ] **Step 2: Run loader tests and verify RED**

Run:

```bash
./mvnw -Dtest=EvalCaseLoaderTest test
```

Expected: compilation fails because `EvalCaseLoader` and `EvalCase` do not exist.

- [ ] **Step 3: Add DTO records and failure enum**

Create report DTOs:

```java
public record EvalReport(EvalSummary summary, List<EvalRunResult> runs) {}
public record EvalSummary(int totalRuns, int totalCases, int passedCases, int failedCases, double passRate) {}
public record EvalRunResult(Integer topK, Double similarityThreshold, int totalCases, int passedCases, int failedCases, double passRate, List<EvalCaseResult> results) {}
public enum EvalFailureReason { PASSED, NO_RETRIEVED_DOCUMENTS, MISSING_KEYWORDS, MISSING_SOURCE }
```

Update `EvalCaseResult` to include:

```java
String id,
String question,
List<String> expectedKeywords,
List<String> expectedSources,
String expectedAnswer,
boolean passed,
EvalFailureReason failureReason,
String failureMessage,
List<Citation> retrieved
```

- [ ] **Step 4: Add case model and loader**

Create `EvalCase` as a service record with `id`, `question`, `expectedKeywords`, `expectedSources`, and `expectedAnswer`.

Create `EvalCaseLoader` as a Spring component that reads `classpath:eval-cases.json` through Jackson and validates:
- case list is not empty
- `id` is not blank
- `question` is not blank
- `expectedKeywords` has at least one nonblank item
- `expectedSources` defaults to an empty list when null
- `expectedAnswer` defaults to an empty string when null

- [ ] **Step 5: Add default `eval-cases.json`**

Move the existing four hardcoded cases into JSON. Use `company-policy.md` as `expectedSources` for all default cases.

- [ ] **Step 6: Run loader tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=EvalCaseLoaderTest test
```

Expected: tests pass.

### Task 2: Evaluation Report Service

**Files:**
- Modify: `src/main/java/com/example/enterpriserag/service/EvalService.java`
- Modify: `src/test/java/com/example/enterpriserag/service/EvalServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Replace the old `EvalServiceTest` with tests that instantiate `EvalService` from a mocked `RagChatService` and fixed cases.

Required tests:
- `runRetrievalEvalBuildsCartesianParameterRuns` calls `runRetrievalEval(List.of(2, 4), List.of(0.0, 0.3))` and expects 4 runs.
- `runRetrievalEvalMarksNoRetrievedDocuments` returns `NO_RETRIEVED_DOCUMENTS`.
- `runRetrievalEvalMarksMissingKeywords` returns `MISSING_KEYWORDS`.
- `runRetrievalEvalMarksMissingSource` returns `MISSING_SOURCE`.
- `runRetrievalEvalMarksPassedWhenKeywordAndSourceMatch` returns `PASSED`.
- `runRetrievalEvalAggregatesSummary` verifies total runs, total cases, passed cases, failed cases, and pass rate.

- [ ] **Step 2: Run service tests and verify RED**

Run:

```bash
./mvnw -Dtest=EvalServiceTest test
```

Expected: compilation fails because the new service API and report DTOs are not wired yet.

- [ ] **Step 3: Implement report generation**

Update `EvalService` constructor to accept `RagChatService` and `EvalCaseLoader`. Add package-private constructor overload for tests that accepts a `List<EvalCase>`.

Implement:

```java
public EvalReport runRetrievalEval(List<Integer> topKValues, List<Double> similarityThresholdValues)
```

For each combination, call `ragChatService.retrieve(case.question(), topK, threshold)`, score the result, and aggregate run/report summaries.

- [ ] **Step 4: Implement failure messages**

Use Chinese messages:
- `PASSED` -> `通过`
- `NO_RETRIEVED_DOCUMENTS` -> `没有召回任何片段`
- `MISSING_KEYWORDS` -> `召回片段未命中期望关键词`
- `MISSING_SOURCE` -> `召回来源未命中期望来源`

- [ ] **Step 5: Run service tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=EvalServiceTest test
```

Expected: tests pass.

### Task 3: Controller Parameter Matrix

**Files:**
- Modify: `src/main/java/com/example/enterpriserag/controller/EvalController.java`
- Create: `src/test/java/com/example/enterpriserag/controller/EvalControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create WebMvc tests for:
- `runReturnsReportForSingleParameterSet`: `GET /api/eval?topK=4&similarityThreshold=0.0` returns `summary.totalRuns`.
- `runAcceptsCommaSeparatedParameters`: `GET /api/eval?topK=2,4&similarityThreshold=0,0.3` passes two topK values and two threshold values to service.
- `runRejectsInvalidTopKFormat`: `GET /api/eval?topK=abc` returns 400.
- `runRejectsInvalidThresholdFormat`: `GET /api/eval?similarityThreshold=abc` returns 400.

- [ ] **Step 2: Run controller tests and verify RED**

Run:

```bash
./mvnw -Dtest=EvalControllerTest test
```

Expected: compilation fails because controller still accepts `Integer` and `Double`.

- [ ] **Step 3: Implement comma parser**

Change controller query parameters to `String topK` and `String similarityThreshold`.

Add private parser methods:
- blank `topK` -> `List.of((Integer) null)` or service-compatible singleton null
- comma split and trim
- parse integers/doubles
- reject empty tokens
- validate `topK` range 1 to 20
- validate threshold range 0.0 to 1.0

Use error messages from the design.

- [ ] **Step 4: Run controller tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=EvalControllerTest test
```

Expected: tests pass.

### Task 4: Frontend Report Display

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`

- [ ] **Step 1: Update controls to allow comma input**

Change `topKInput` and `similarityThresholdInput` from `type="number"` to `type="text"` so values like `2,4,6` and `0,0.3` can be typed.

- [ ] **Step 2: Update request parameter construction**

Keep chat behavior single-value only. For chat, use the first comma-separated value. For eval, send the raw comma-separated values.

- [ ] **Step 3: Render report summary and grouped runs**

Update the eval click handler to expect:

```javascript
{
  summary: {},
  runs: []
}
```

Render:
- report summary
- each run header with `topK` and `similarityThreshold`
- each case result with status, failure message, expected keywords, expected sources, and citations

- [ ] **Step 4: Add compact CSS classes**

Add classes for `.eval-summary`, `.eval-run`, `.eval-case-meta`, and `.eval-citations` using the existing restrained sidebar style.

- [ ] **Step 5: Run frontend syntax check**

Run:

```bash
node --check src/main/resources/static/app.js
```

Expected: command exits 0.

### Task 5: Documentation And Full Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/api.md`
- Modify: `docs/rag-implementation.md`
- Modify: `docs/architecture.md`

- [ ] **Step 1: Update public docs**

Document:
- `src/main/resources/eval-cases.json`
- report response shape
- parameter matrix examples
- failure reason meanings
- frontend comma input behavior

- [ ] **Step 2: Run full verification**

Run:

```bash
make verify
make package
```

Expected:
- `make verify` exits 0
- `make package` exits 0
- Maven reports 0 test failures
- `node --check src/main/resources/static/app.js` exits 0

- [ ] **Step 3: Commit completed feature**

Stage only intended files, excluding `docs/CODEX_HANDOFF.md`.

```bash
git add README.md docs/api.md docs/architecture.md docs/rag-implementation.md docs/superpowers/specs/2026-05-18-eval-report-upgrade-design.md docs/superpowers/plans/2026-05-18-eval-report-upgrade.md src/main src/test
git commit -m "Upgrade evaluation reporting"
```
