import { request } from "./request";

export interface MindMap {
  id: number;
  userId: number;
  kbId?: number;
  title: string;
  content: string;
  createTime: string;
  updateTime: string;
}

export interface MindMapCreateReq {
  title: string;
  kbId?: number | null;
  content: string;
}

export interface MindMapUpdateReq {
  id: number;
  title?: string;
  kbId?: number | null;
  content?: string;
}

export async function createMindMap(data: MindMapCreateReq): Promise<number> {
  const res = await request.post("/api/mindmap/create", data);
  const result = res.data?.data ?? res.data;
  if (typeof result === 'number') return result;
  if (result && typeof result === 'object' && 'id' in result) return Number((result as any).id);
  throw new Error('创建思维导图失败：服务器返回格式错误');
}

export async function updateMindMap(data: MindMapUpdateReq): Promise<void> {
  await request.post("/api/mindmap/update", data);
}

export async function deleteMindMap(id: number): Promise<void> {
  await request.post(`/api/mindmap/delete/${id}`);
}

export async function getMindMapList(kbId?: number | null): Promise<MindMap[]> {
  const res = await request.get("/api/mindmap/list", { params: { kbId } });
  return res.data?.data ?? res.data ?? [];
}

export async function getMindMapDetail(id: number): Promise<MindMap> {
  const res = await request.get(`/api/mindmap/detail/${id}`);
  return res.data?.data ?? res.data;
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
