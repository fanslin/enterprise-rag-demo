const uploadForm = document.querySelector("#uploadForm");
const fileInput = document.querySelector("#fileInput");
const sampleBtn = document.querySelector("#sampleBtn");
const evalBtn = document.querySelector("#evalBtn");
const chatForm = document.querySelector("#chatForm");
const questionInput = document.querySelector("#questionInput");
const topKInput = document.querySelector("#topKInput");
const similarityThresholdInput = document.querySelector("#similarityThresholdInput");
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

evalBtn.addEventListener("click", async () => {
    evalList.innerHTML = `<div class="doc-item">评测中...</div>`;
    try {
        const results = await request(`/api/eval?${retrievalQueryParams()}`);
        evalList.innerHTML = results.map((item) => `
            <div class="eval-item ${item.passed ? "pass" : "fail"}">
                <strong>${item.passed ? "通过" : "未命中"}</strong><br>
                ${escapeHtml(item.question)}<br>
                期望关键词：${escapeHtml(item.expectedKeyword)}
            </div>
        `).join("");
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
        <div class="doc-item">
            <strong>${escapeHtml(doc.source)}</strong><br>
            ${doc.chunks} 个片段
        </div>
    `).join("");
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
        topK: Number(topKInput.value),
        similarityThreshold: Number(similarityThresholdInput.value)
    };
}

function retrievalQueryParams() {
    return new URLSearchParams(retrievalOptions()).toString();
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

refreshDocuments();
