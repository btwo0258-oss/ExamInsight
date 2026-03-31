import { request, USER_KEY } from "./request";
import { mockEnabled } from "@/mock";

export type KnowledgeBase = {
  id: number;
  name: string;
  description?: string;
  icon?: string;
  color?: string;
  documentCount?: number;
  mindMapCount?: number;
  createTime: string;
  updateTime: string;
};

function getMockKbKey(): string {
  const userStr = sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY);
  let userPrefix = "guest";
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      if (user && user.id) userPrefix = String(user.id);
    } catch {}
  }
  return `llm.mock.kb.${userPrefix}`;
}

function getMockKb(): KnowledgeBase[] {
  const key = getMockKbKey();
  const raw = sessionStorage.getItem(key);
  if (raw) return JSON.parse(raw) as KnowledgeBase[];
  return [];
}

function saveMockKb(list: KnowledgeBase[]) {
  const key = getMockKbKey();
  sessionStorage.setItem(key, JSON.stringify(list));
}

function normalizeKnowledgeBase(kb: any): KnowledgeBase {
  return {
    id: kb.id,
    name: kb.name,
    description: kb.description,
    icon: kb.avatar,
    color: kb.color,
    documentCount: kb.docCount ?? kb.documentCount ?? 0,
    mindMapCount: kb.mindMapCount ?? 0,
    createTime: kb.createTime,
    updateTime: kb.updateTime,
  };
}

export async function getKnowledgeBases(): Promise<KnowledgeBase[]> {
  if (mockEnabled.value) return getMockKb();
  try {
    const res = await request.get("/api/kb/list");
    const data = (res.data?.data ?? res.data) as any[];
    return data.map(normalizeKnowledgeBase);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    )
      return getMockKb();
    throw err;
  }
}

export async function getKnowledgeBase(id: number): Promise<KnowledgeBase> {
  if (mockEnabled.value) {
    const item = getMockKb().find((x) => x.id === id);
    if (!item) throw new Error("Not found");
    return item;
  }
  try {
    const res = await request.get(`/api/kb/${id}`);
    return normalizeKnowledgeBase(res.data?.data ?? res.data);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    ) {
      const item = getMockKb().find((x) => x.id === id);
      if (!item) throw new Error("Not found");
      return item;
    }
    throw err;
  }
}

export async function createKnowledgeBase(payload: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
  if (mockEnabled.value) {
    const list = getMockKb();
    const next: KnowledgeBase = {
      id: Date.now(),
      name: payload.name || "",
      description: payload.description,
      icon: payload.icon,
      color: payload.color,
      documentCount: 0,
      mindMapCount: 0,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    };
    list.unshift(next);
    saveMockKb(list);
    return next;
  }
  try {
    const res = await request.post("/api/kb/create", {
      ...payload,
      avatar: payload.icon,
      color: payload.color,
    });
    return normalizeKnowledgeBase(res.data?.data ?? res.data);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    ) {
      const list = getMockKb();
      const next: KnowledgeBase = {
        id: Date.now(),
        name: payload.name || "",
        description: payload.description,
        icon: payload.icon,
        color: payload.color,
        documentCount: 0,
        createTime: new Date().toISOString(),
        updateTime: new Date().toISOString(),
      };
      list.unshift(next);
      saveMockKb(list);
      return next;
    }
    throw err;
  }
}

export async function updateKnowledgeBase(data: KnowledgeBase): Promise<KnowledgeBase> {
  if (mockEnabled.value) {
    const list = getMockKb();
    const index = list.findIndex((x) => x.id === data.id);
    if (index === -1) throw new Error("Not found");
    list[index] = { ...list[index], ...data, updateTime: new Date().toISOString() };
    saveMockKb(list);
    return list[index];
  }
  try {
    const res = await request.put(`/api/kb/${data.id}`, {
      name: data.name,
      description: data.description,
      avatar: data.icon,
      color: data.color,
    });
    return normalizeKnowledgeBase(res.data?.data ?? res.data);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    ) {
      const list = getMockKb();
      const index = list.findIndex((x) => x.id === data.id);
      if (index === -1) throw new Error("Not found");
      list[index] = { ...list[index], ...data, updateTime: new Date().toISOString() };
      saveMockKb(list);
      return list[index];
    }
    throw err;
  }
}

export async function deleteKnowledgeBase(id: number): Promise<void> {
  if (mockEnabled.value) {
    const list = getMockKb().filter((x) => x.id !== id);
    saveMockKb(list);
    return;
  }
  try {
    await request.delete(`/api/kb/${id}`);
  } catch (err: unknown) {
    if (
      typeof err === "object" &&
      err !== null &&
      "response" in err &&
      (err as { response?: { status?: number } }).response?.status === 404
    ) {
      const list = getMockKb().filter((x) => x.id !== id);
      saveMockKb(list);
      return;
    }
    throw err;
  }
}
