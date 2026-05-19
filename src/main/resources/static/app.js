const uploadForm = document.querySelector("#uploadForm");
const fileInput = document.querySelector("#fileInput");
const sampleBtn = document.querySelector("#sampleBtn");
const rebuildBtn = document.querySelector("#rebuildBtn");
const evalBtn = document.querySelector("#evalBtn");
const chatForm = document.querySelector("#chatForm");
const questionInput = document.querySelector("#questionInput");
const topKInput = document.querySelector("#topKInput");
const similarityThresholdInput = document.querySelector("#similarityThresholdInput");
const aiStatus = document.querySelector("#aiStatus");
const docList = document.querySelector("#docList");
const evalList = document.querySelector("#evalList");
const messages = document.querySelector("#messages");

uploadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!fileInput.files.length) {
        addAssistantMessage("请选择一个 .md 或 .txt 文档。");
        return;
    }
    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    try {
        const result = await request("/api/documents", { method: "POST", body: formData });
        addAssistantMessage(`${result.source} 已入库，共 ${result.chunks} 个片段。`);
        fileInput.value = "";
        await refreshDocuments();
    } catch (error) {
        addAssistantMessage(error.message);
    }
});

sampleBtn.addEventListener("click", async () => {
    try {
        const result = await request("/api/documents/sample", { method: "POST" });
        addAssistantMessage(`${result.source} 已入库，共 ${result.chunks} 个片段。现在可以提问了。`);
        await refreshDocuments();
    } catch (error) {
        addAssistantMessage(error.message);
    }
});

rebuildBtn.addEventListener("click", async () => {
    try {
        const result = await request("/api/documents/rebuild", { method: "POST" });
        addAssistantMessage(result.message);
        await refreshDocuments();
    } catch (error) {
        addAssistantMessage(error.message);
    }
});

docList.addEventListener("click", async (event) => {
    const deleteButton = event.target.closest("[data-delete-source]");
    if (!deleteButton) {
        return;
    }

    const source = deleteButton.dataset.deleteSource;
    try {
        await request(`/api/documents/${encodeURIComponent(source)}`, { method: "DELETE" });
        addAssistantMessage(`${source} 已从知识库删除。`);
        await refreshDocuments();
    } catch (error) {
        addAssistantMessage(error.message);
    }
});

evalBtn.addEventListener("click", async () => {
    evalList.innerHTML = `<div class="doc-item">评测中...</div>`;
    try {
        const report = await request(`/api/eval?${retrievalQueryParams()}`);
        evalList.innerHTML = renderEvalReport(report);
    } catch (error) {
        evalList.innerHTML = `<div class="eval-item fail">${escapeHtml(error.message)}</div>`;
    }
});

chatForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const question = questionInput.value.trim();
    if (!question) {
        return;
    }
    addUserMessage(question);
    questionInput.value = "";
    const thinking = addAssistantMessage("正在检索知识库并组织回答...");
    try {
        const result = await request("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ question, ...retrievalOptions() })
        });
        thinking.remove();
        addAssistantMessage(result.answer, result.citations);
    } catch (error) {
        thinking.remove();
        addAssistantMessage(error.message);
    }
});

async function refreshDocuments() {
    let docs;
    try {
        docs = await request("/api/documents");
    } catch (error) {
        docList.innerHTML = `<div class="doc-item">${escapeHtml(error.message)}</div>`;
        return;
    }
    if (!docs.length) {
        docList.innerHTML = `<div class="doc-item">还没有文档。</div>`;
        return;
    }
    docList.innerHTML = docs.map((doc) => `
        <div class="doc-item doc-row">
            <div class="doc-meta">
                <strong>${escapeHtml(doc.source)}</strong>
                <span>${escapeHtml(doc.chunks)} 个片段</span>
            </div>
            <button class="danger compact" type="button" data-delete-source="${escapeHtml(doc.source)}">删除</button>
        </div>
    `).join("");
}

async function refreshAiStatus() {
    try {
        const status = await request("/api/health/ai");
        aiStatus.textContent = status.message;
        aiStatus.className = `status-banner ${status.mode === "local" ? "local" : "remote"}`;
    } catch (error) {
        aiStatus.textContent = error.message;
        aiStatus.className = "status-banner fail";
    }
}

async function request(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();
    const body = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error(body?.message || "请求失败");
    }
    return body;
}

function retrievalOptions() {
    return {
        topK: Number(firstValue(topKInput.value)),
        similarityThreshold: Number(firstValue(similarityThresholdInput.value))
    };
}

function retrievalQueryParams() {
    return new URLSearchParams({
        topK: topKInput.value.trim(),
        similarityThreshold: similarityThresholdInput.value.trim()
    }).toString();
}

function firstValue(value) {
    return String(value ?? "").split(",")[0].trim();
}

function renderEvalReport(report) {
    const summary = report.summary ?? {};
    const runs = report.runs ?? [];
    return `
        <div class="eval-summary">
            <strong>${formatPercent(summary.passRate)} 通过率</strong>
            <span>${summary.passedCases ?? 0} 通过 / ${summary.failedCases ?? 0} 失败 / ${summary.totalRuns ?? 0} 组参数</span>
        </div>
        ${runs.map(renderEvalRun).join("")}
    `;
}

function renderEvalRun(run) {
    const results = run.results ?? [];
    return `
        <section class="eval-run">
            <div class="eval-run-head">
                <strong>Top K ${formatParam(run.topK)} · 阈值 ${formatParam(run.similarityThreshold)}</strong>
                <span>${formatPercent(run.passRate)} · ${run.passedCases} / ${run.totalCases}</span>
            </div>
            ${results.map(renderEvalCase).join("")}
        </section>
    `;
}

function renderEvalCase(item) {
    return `
        <div class="eval-item ${item.passed ? "pass" : "fail"}">
            <strong>${item.passed ? "通过" : "失败"} · ${escapeHtml(item.failureMessage)}</strong>
            <div>${escapeHtml(item.question)}</div>
            <div class="eval-case-meta">关键词：${escapeHtml(formatList(item.expectedKeywords))}</div>
            <div class="eval-case-meta">来源：${escapeHtml(formatList(item.expectedSources))}</div>
            ${renderEvalCitations(item.retrieved)}
        </div>
    `;
}

function renderEvalCitations(citations = []) {
    if (!citations.length) {
        return "";
    }
    return `
        <div class="eval-citations">
            ${citations.map((citation) => `
                <div>
                    <strong>${escapeHtml(citation.source)}#${escapeHtml(citation.chunk)}</strong>
                    <span>${escapeHtml(citation.preview)}</span>
                </div>
            `).join("")}
        </div>
    `;
}

function formatList(values = []) {
    if (!values.length) {
        return "未配置";
    }
    return values.join("、");
}

function formatPercent(value) {
    const percent = Number(value) * 100;
    if (!Number.isFinite(percent)) {
        return "0%";
    }
    return `${percent.toFixed(percent % 1 === 0 ? 0 : 1)}%`;
}

function formatParam(value) {
    return value === null || value === undefined ? "默认" : value;
}

function addUserMessage(text) {
    const node = document.createElement("article");
    node.className = "message user";
    node.textContent = text;
    messages.appendChild(node);
    messages.scrollTop = messages.scrollHeight;
    return node;
}

function addAssistantMessage(text, citations = []) {
    const node = document.createElement("article");
    node.className = "message assistant";
    node.innerHTML = `<p>${escapeHtml(text)}</p>`;
    if (citations.length) {
        const block = document.createElement("div");
        block.className = "citations";
        block.innerHTML = citations.map((citation) => `
            <div class="citation">
                <strong>${escapeHtml(citation.source)}#${citation.chunk}</strong><br>
                ${escapeHtml(citation.preview)}
            </div>
        `).join("");
        node.appendChild(block);
    }
    messages.appendChild(node);
    messages.scrollTop = messages.scrollHeight;
    return node;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

refreshAiStatus();
refreshDocuments();
