# Knowledge Base Overwrite Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add overwrite, delete, and rebuild management behavior to the in-memory RAG knowledge base.

**Architecture:** Keep `KnowledgeBaseService` as the source of truth for in-memory document state. Replace the current summary-only map with document records that keep original content, generated vector ids, and summaries, then expose service methods through `DocumentController` and lightweight DTOs. The frontend stays compact and calls the new API endpoints after user actions.

**Tech Stack:** Java 17, Spring Boot MVC, Spring AI `VectorStore`, JUnit 5, Mockito, AssertJ, vanilla HTML/CSS/JavaScript.

---

### Task 1: Service State And Overwrite Semantics

**Files:**
- Modify: `src/test/java/com/example/enterpriserag/service/KnowledgeBaseServiceTest.java`
- Modify: `src/main/java/com/example/enterpriserag/service/KnowledgeBaseService.java`

- [ ] **Step 1: Write failing service test for duplicate source overwrite**

Add a test named `ingestSameSourceDeletesOldChunksBeforeAddingReplacement` to `KnowledgeBaseServiceTest`. The test should ingest `policy.md` twice, capture vector ids from the first ingest, verify `vectorStore.delete(firstIds)` is called before the second `vectorStore.add(...)`, and verify the summary has the new chunk count.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./mvnw -Dtest=KnowledgeBaseServiceTest#ingestSameSourceDeletesOldChunksBeforeAddingReplacement test
```

Expected: the test fails because duplicate ingest currently never calls `VectorStore.delete(...)`.

- [ ] **Step 3: Implement document records and deterministic ids**

In `KnowledgeBaseService`, replace `Map<String, DocumentSummary> summaries` with a `Map<String, IndexedDocument> documentsBySource`. Add a private record:

```java
private record IndexedDocument(
        String source,
        String content,
        List<String> vectorIds,
        DocumentSummary summary
) {
}
```

During ingest, create deterministic vector ids with:

```java
private static String vectorId(String source, int chunkNumber) {
    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(source.getBytes(StandardCharsets.UTF_8))
            + "::chunk-" + chunkNumber;
}
```

Build Spring AI documents with `new Document(vectorId(source, i + 1), chunks.get(i), metadata)`. If an old record exists for the source, call `vectorStore.delete(old.vectorIds())` before adding the new documents.

- [ ] **Step 4: Run the focused service test and verify GREEN**

Run:

```bash
./mvnw -Dtest=KnowledgeBaseServiceTest#ingestSameSourceDeletesOldChunksBeforeAddingReplacement test
```

Expected: test passes.

### Task 2: Delete And Rebuild Service API

**Files:**
- Modify: `src/test/java/com/example/enterpriserag/service/KnowledgeBaseServiceTest.java`
- Modify: `src/main/java/com/example/enterpriserag/service/KnowledgeBaseService.java`
- Create: `src/main/java/com/example/enterpriserag/dto/RebuildResponse.java`

- [ ] **Step 1: Write failing service tests for delete and rebuild**

Add tests:

- `deleteDocumentRemovesVectorIdsAndState`
- `deleteUnknownDocumentFailsBeforeTouchingVectorStore`
- `rebuildIndexDeletesKnownIdsAndAddsDocumentsFromCachedContent`
- `rebuildEmptyKnowledgeBaseReturnsZeroCounts`

The tests should use Mockito `InOrder` where operation order matters.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
./mvnw -Dtest=KnowledgeBaseServiceTest test
```

Expected: tests fail because `deleteDocument` and `rebuildIndex` do not exist yet.

- [ ] **Step 3: Implement delete and rebuild**

Add `public DocumentSummary deleteDocument(String source)`:

- Clean and validate the source path segment.
- Look up the record in `documentsBySource`.
- If missing, throw `IllegalArgumentException("文档不存在：" + source)`.
- Call `vectorStore.delete(record.vectorIds())`.
- Remove the record.
- Return `record.summary()`.

Add `public RebuildResponse rebuildIndex()`:

- Snapshot current records while synchronized.
- If empty, return `new RebuildResponse(0, 0, "知识库为空，无需重建")`.
- Delete all known ids once.
- Re-split each cached content, rebuild documents with deterministic ids, and add them.
- Replace records with refreshed summaries.
- Return total document and chunk counts.

- [ ] **Step 4: Run service tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=KnowledgeBaseServiceTest test
```

Expected: all `KnowledgeBaseServiceTest` tests pass.

### Task 3: Controller Endpoints

**Files:**
- Create: `src/test/java/com/example/enterpriserag/controller/DocumentControllerTest.java`
- Modify: `src/main/java/com/example/enterpriserag/controller/DocumentController.java`
- Create: `src/main/java/com/example/enterpriserag/dto/RebuildResponse.java` if not already created in Task 2

- [ ] **Step 1: Write failing controller tests**

Create standalone MockMvc tests:

- `deleteDocumentDelegatesAndReturnsDeletedSummary`
- `rebuildIndexDelegatesAndReturnsCounts`

The delete test should call `DELETE /api/documents/company-policy.md` and expect the JSON source. The rebuild test should call `POST /api/documents/rebuild` and expect `documents`, `chunks`, and `message`.

- [ ] **Step 2: Run controller tests and verify RED**

Run:

```bash
./mvnw -Dtest=DocumentControllerTest test
```

Expected: tests fail because the endpoints are missing.

- [ ] **Step 3: Add controller methods**

In `DocumentController`, add:

```java
@DeleteMapping("/{source}")
DocumentSummary deleteDocument(@PathVariable String source) {
    return knowledgeBaseService.deleteDocument(source);
}

@PostMapping("/rebuild")
RebuildResponse rebuildIndex() {
    return knowledgeBaseService.rebuildIndex();
}
```

- [ ] **Step 4: Run controller tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=DocumentControllerTest test
```

Expected: tests pass.

### Task 4: Frontend Management Controls

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`

- [ ] **Step 1: Add rebuild button markup**

Add a secondary button with id `rebuildBtn` near the sample import button:

```html
<button id="rebuildBtn" class="secondary" type="button">重建索引</button>
```

- [ ] **Step 2: Wire delete and rebuild JavaScript**

In `app.js`, add `const rebuildBtn = document.querySelector("#rebuildBtn");`, a click handler that calls `POST /api/documents/rebuild`, and document row delete buttons that call `DELETE /api/documents/${encodeURIComponent(source)}`. Refresh the document list after successful operations and show messages through `addAssistantMessage`.

- [ ] **Step 3: Add compact row styles**

In `styles.css`, add styles for `.doc-row`, `.doc-meta`, and a compact danger/delete button so source text, chunk count, and delete action fit without overlap on desktop and mobile.

### Task 5: Documentation And Full Verification

**Files:**
- Modify: `docs/api.md`
- Modify: `docs/rag-implementation.md`

- [ ] **Step 1: Update API docs**

Document:

- `DELETE /api/documents/{source}`
- `POST /api/documents/rebuild`
- duplicate imports overwrite previous chunks

- [ ] **Step 2: Update RAG implementation notes**

Mention that `KnowledgeBaseService` caches original content in memory, uses deterministic ids, and can delete/rebuild the current in-memory index.

- [ ] **Step 3: Run package verification**

Run:

```bash
./mvnw package
```

Expected: `BUILD SUCCESS` and all tests pass.

- [ ] **Step 4: Commit implementation**

Stage only feature files, leaving `docs/CODEX_HANDOFF.md` untracked unless explicitly requested:

```bash
git add docs/superpowers/plans/2026-05-18-knowledge-base-overwrite-rebuild.md docs/api.md docs/rag-implementation.md src/main/java src/test/java src/main/resources/static
git commit -m "Add knowledge base overwrite rebuild controls"
```
