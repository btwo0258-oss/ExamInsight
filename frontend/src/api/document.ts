import { request, USER_KEY } from "./request";
import { mockEnabled } from "@/mock";

export type Document = {
  id: number;
  kbId: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  chunkCount: number;
  status: "pending" | "completed" | "failed";
  errorMsg?: string;
  createTime: string;
};

function getMockDocsKey(): string {
  const userStr = sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY);
  let userPrefix = "guest";
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      if (user && user.id) userPrefix = String(user.id);
    } catch {}
  }
  return `llm.mock.docs.${userPrefix}`;
}

function getMockDocs(): Document[] {
  const raw = sessionStorage.getItem(getMockDocsKey());
  if (raw) return JSON.parse(raw) as Document[];
  return [];
}

function saveMockDocs(list: Document[]) {
  sessionStorage.setItem(getMockDocsKey(), JSON.stringify(list));
}

function normalizeDocument(doc: any): Document {
  let s: "pending" | "completed" | "failed" = "pending";
  if (doc.status === 1) s = "completed";
  else if (doc.status === 2) s = "failed";
  else if (typeof doc.status === "string") s = doc.status; // For mock data

  return {
    id: doc.id,
    kbId: doc.kbId,
    fileName: doc.fileName,
    fileType: doc.fileType,
    fileSize: doc.fileSize,
    chunkCount: doc.chunkCount ?? 0,
    status: s,
    errorMsg: doc.errorMsg,
    createTime: doc.createTime,
  };
}

export async function getDocuments(kbId: number): Promise<Document[]> {
  if (mockEnabled.value) {
    return getMockDocs().filter((x) => x.kbId === kbId);
  }
  try {
    const res = await request.get("/api/doc/list", { params: { kbId } });
    const data = res.data?.data ?? res.data;
    return data.map(normalizeDocument);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    )
      return getMockDocs().filter((x) => x.kbId === kbId);
    throw err;
  }
}

export async function uploadDocument(kbId: number, file: File): Promise<Document> {
  if (mockEnabled.value) {
    const list = getMockDocs();
    const next: Document = {
      id: Date.now(),
      kbId,
      fileName: file.name,
      fileType: file.name.split(".").pop() || "unknown",
      fileSize: file.size,
      chunkCount: Math.floor(Math.random() * 10) + 1,
      status: "pending",
      createTime: new Date().toISOString(),
    };
    list.unshift(next);
    saveMockDocs(list);
    return next;
  }
  try {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("kbId", kbId.toString());
    const res = await request.post("/api/doc/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return normalizeDocument(res.data?.data ?? res.data);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    ) {
      const list = getMockDocs();
      const next: Document = {
        id: Date.now(),
        kbId,
        fileName: file.name,
        fileType: file.name.split(".").pop() || "unknown",
        fileSize: file.size,
        chunkCount: Math.floor(Math.random() * 10) + 1,
        status: "pending",
        createTime: new Date().toISOString(),
      };
      list.unshift(next);
      saveMockDocs(list);
      return next;
    }
    throw err;
  }
}

export async function deleteDocument(id: number): Promise<void> {
  if (mockEnabled.value) {
    const list = getMockDocs().filter((x) => x.id !== id);
    saveMockDocs(list);
    return;
  }
  try {
    await request.delete(`/api/doc/${id}`);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    ) {
      const list = getMockDocs().filter((x) => x.id !== id);
      saveMockDocs(list);
      return;
    }
    throw err;
  }
}

export async function getDocumentStatus(
  id: number,
): Promise<{ status: "pending" | "completed" | "failed"; errorMsg?: string; chunkCount?: number }> {
  if (mockEnabled.value) {
    const doc = getMockDocs().find((x) => x.id === id);
    if (!doc) throw new Error("Not found");
    // Simulate processing completion
    if (doc.status === "pending" && Math.random() > 0.5) {
      doc.status = "completed";
      saveMockDocs(getMockDocs().map((d) => (d.id === id ? doc : d)));
    }
    return { status: doc.status, errorMsg: doc.errorMsg };
  }
  try {
    const res = await request.get(`/api/doc/status/${id}`);
    const data = res.data?.data ?? res.data;
    let s: "pending" | "completed" | "failed" = "pending";
    if (data?.status === 1) s = "completed";
    else if (data?.status === 2) s = "failed";
    return { status: s, errorMsg: data?.errorMsg, chunkCount: data?.chunkCount };
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    ) {
      const doc = getMockDocs().find((x) => x.id === id);
      if (!doc) throw new Error("Not found");
      if (doc.status === "pending") {
        doc.status = "completed";
        saveMockDocs(getMockDocs().map((d) => (d.id === id ? doc : d)));
      }
      return { status: doc.status, errorMsg: doc.errorMsg };
    }
    throw err;
  }
}

export async function downloadDocument(id: number, fileName: string): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL ?? "";
  const token = sessionStorage.getItem("llm.token") || localStorage.getItem("llm.token");
  const headers = new Headers();
  if (token) headers.append("Authorization", `Bearer ${token}`);
  const response = await fetch(`${base}/api/doc/download/${id}`, { headers });
  if (!response.ok) throw new Error("下载失败");
  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.style.display = "none";
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
}

export async function getDocumentPreview(
  id: number,
): Promise<{ type: string; content: string | Blob | null }> {
  let doc, ext;
  if (mockEnabled.value) {
    doc = { fileName: "mock.txt" };
    ext = ".txt";
  } else {
    const res = await request.get(`/api/doc/${id}`);
    doc = res.data?.data ?? res.data;
    ext = doc.fileName.substring(doc.fileName.lastIndexOf(".")).toLowerCase();
  }

  const base = import.meta.env.VITE_API_BASE_URL ?? "";
  const token = sessionStorage.getItem("llm.token") || localStorage.getItem("llm.token");
  const headers = new Headers();
  if (token) headers.append("Authorization", `Bearer ${token}`);

  const response = await fetch(`${base}/api/doc/download/${id}`, { headers });
  if (!response.ok) throw new Error("获取预览失败");

  if (ext === ".pdf") {
    const blob = await response.blob();
    return { type: "pdf", content: blob };
  } else if (ext === ".docx" || ext === ".doc") {
    const blob = await response.blob();
    return { type: "docx", content: blob };
  } else {
    const text = await response.text();
    return { type: "text", content: text };
  }
}

export async function saveDocumentContent(_id: number, _content: string | Blob): Promise<void> {
  console.warn("保存到后端功能尚未实现，目前仅在前端生效。");
  return Promise.resolve();
}
