import { request } from "./request";

export interface MindMap {
  id: number;
  userId: number;
  knowledgeBaseId?: number;
  title: string;
  content: string;
  createTime: string;
  updateTime: string;
}

export interface MindMapCreateReq {
  title: string;
  knowledgeBaseId?: number | null;
  content: string;
}

export interface MindMapUpdateReq {
  id: number;
  title?: string;
  knowledgeBaseId?: number | null;
  content?: string;
}

export async function createMindMap(data: MindMapCreateReq): Promise<number> {
  const res = await request.post("/api/mindmap/create", { ...data, kbId: data.knowledgeBaseId, knowledgeBaseId: undefined });
  return res.data?.data ?? res.data;
}

export async function updateMindMap(data: MindMapUpdateReq): Promise<void> {
  await request.post("/api/mindmap/update", { ...data, kbId: data.knowledgeBaseId, knowledgeBaseId: undefined });
}

export async function deleteMindMap(id: number): Promise<void> {
  await request.post(`/api/mindmap/delete/${id}`);
}

export async function getMindMapList(knowledgeBaseId?: number | null): Promise<MindMap[]> {
  const res = await request.get("/api/mindmap/list", { params: { kbId: knowledgeBaseId } });
  const items = res.data?.data ?? res.data ?? [];
  return items.map((item: MindMap & { kbId?: number }) => ({ ...item, knowledgeBaseId: item.knowledgeBaseId ?? item.kbId }));
}

export async function getMindMapDetail(id: number): Promise<MindMap> {
  const res = await request.get(`/api/mindmap/detail/${id}`);
  const item = (res.data?.data ?? res.data) as MindMap & { kbId?: number };
  return { ...item, knowledgeBaseId: item.knowledgeBaseId ?? item.kbId };
}

export interface MindMapGenerateResult {
  id: number;
  title: string;
  treeData: any;
}

export async function generateMindMapFromAi(
  content: string,
  title?: string,
): Promise<MindMapGenerateResult> {
  const res = await request.post("/api/mindmap/generate-from-ai", { content, title });
  return res.data?.data ?? res.data;
}
