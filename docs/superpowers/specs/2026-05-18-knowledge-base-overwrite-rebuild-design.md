# Knowledge Base Overwrite Rebuild Design

## Goal

Make the MVP knowledge base manageable during local demos and iterative testing. Users should be able to re-upload the same source without stale chunks lingering, remove a document from the index, and rebuild the in-memory vector index from the currently known document contents.

## Scope

This design covers the in-memory `SimpleVectorStore` phase only. It keeps the existing upload, sample import, document list, chat, and eval flows, and adds management behavior around the current `KnowledgeBaseService`.

In scope:

- Duplicate source import uses overwrite semantics.
- Single-document deletion removes vector chunks and document summary.
- Rebuild reindexes all currently remembered documents into a clean vector store.
- The UI exposes delete and rebuild controls in the knowledge-base panel.
- API and service tests cover the new behavior.

Out of scope:

- Persistent document storage across application restarts.
- Document version history.
- Bulk selection or multi-document operations.
- Switching to Chroma or another persistent vector store.

## Behavior

When a document is ingested, the service normalizes the source name as it does today. If that source already exists, the old chunk ids for that source are deleted from the vector store before the new chunks are added. The response still returns the source, chunk count, and a success message. The summary for that source is replaced with the new chunk count and indexed timestamp.

Deleting a document by source removes every chunk id known for that source from the vector store and removes its summary and cached source content. Deleting an unknown source returns a client error with a clear message.

Rebuilding the index deletes all currently known chunk ids from the vector store, re-splits the cached source contents, adds fresh vector documents, and refreshes summaries. If no documents are known, rebuild is a no-op that returns a zero-document result.

## Data Model

`KnowledgeBaseService` should keep an in-memory map keyed by source. Each entry stores:

- source name
- original content
- current vector document ids
- current chunk count
- indexed timestamp

Vector document ids should be deterministic for a source and chunk number, for example `source + "::chunk-" + chunkNumber`, using a sanitized or encoded source segment so that ids are stable and safe to pass to `VectorStore.delete(List<String>)`.

Metadata continues to include `source`, `chunk`, and `indexedAt`, so existing citation and eval behavior remains unchanged.

## API

Existing endpoints stay compatible:

- `POST /api/documents`
- `POST /api/documents/sample`
- `GET /api/documents`

New endpoints:

- `DELETE /api/documents/{source}` deletes one document by source.
- `POST /api/documents/rebuild` rebuilds the in-memory vector index.

Path variables should accept filename-like source names such as `company-policy.md`; the frontend will URL-encode source values.

Rebuild returns a small response with document count, chunk count, and a message. Delete can return the removed document summary or a simple response; the implementation should prefer a DTO over plain text for consistency with the existing API.

## Frontend

The knowledge-base panel should remain compact. Each document row shows the source, chunk count, and a delete control. The panel also gets one rebuild control below the import controls.

After upload, sample import, delete, or rebuild, the UI refreshes the document list and shows a chat-area status message. Failed delete or rebuild requests should surface the API error message.

## Error Handling

Business validation errors use the existing `IllegalArgumentException` path and return HTTP 400 through `ApiExceptionHandler`.

Expected errors:

- Unsupported upload type keeps the current message.
- Empty document keeps the current message.
- Delete unknown source returns `文档不存在：<source>`.

If `VectorStore.delete` or `VectorStore.add` throws unexpectedly, the existing 500 handling remains responsible for hiding internals from the frontend.

## Testing

Service tests should verify:

- Re-importing the same source deletes old chunk ids before adding new documents.
- Deleting a known source removes ids, summary, and cached content.
- Deleting an unknown source fails before touching the vector store.
- Rebuild deletes known ids and adds fresh documents from cached content.

Controller tests should verify:

- `DELETE /api/documents/{source}` delegates and returns success.
- `POST /api/documents/rebuild` delegates and returns document and chunk counts.

Existing package verification remains `./mvnw package`.
