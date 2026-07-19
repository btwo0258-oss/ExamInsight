import { request } from "./request";
import { isMockDataSource } from '@/config/dataSource'
import { mockSession } from '@/mock/storage'
import type { ArtifactInlinePreview, MindMapRenderConfig } from '@/types/contracts/artifact'
import { mockMindMapRenderConfig } from '@/utils/mindMapTheme'

export interface MindMap {
  id: number;
  userId: number;
  knowledgeBaseId?: number;
  title: string;
  content: string;
  renderConfig?: MindMapRenderConfig;
  createTime: string;
  updateTime: string;
}

export interface MindMapCreateReq {
  title: string;
  knowledgeBaseId?: number | null;
  content: string;
  renderConfig?: MindMapRenderConfig;
}

export interface MindMapUpdateReq {
  id: number;
  title?: string;
  knowledgeBaseId?: number | null;
  content?: string;
  renderConfig?: MindMapRenderConfig;
}

export interface MindMapUpdateResult {
  id: number;
  resourceId?: string;
  version?: number | string;
  updateTime?: string;
  updatedAt?: string;
  previewData?: ArtifactInlinePreview;
}

const MOCK_DOMAIN = 'mindmaps'

function mockMaps() {
  return mockSession.get<MindMap[]>(MOCK_DOMAIN, [])
}

function saveMockMaps(items: MindMap[]) {
  mockSession.set(MOCK_DOMAIN, items)
}

function nextMockId(items: MindMap[]) {
  return Math.max(1000, ...items.map((item) => item.id)) + 1
}

function mockTree(content: string, title = '学习主题') {
  const clean = content.replace(/\[附加文件内容\][\s\S]*?\[用户输入\]/, '').trim()
  const keywords = clean
    .replace(/[，。！？、：；,.!?;:\n]/g, ' ')
    .split(/\s+/)
    .filter((item) => item.length >= 2)
    .slice(0, 8)
  const defaults = ['核心概念', '关键方法', '实践步骤', '复习要点']
  const branches = (keywords.length ? keywords : defaults).slice(0, 4)
  return {
    data: { text: title },
    children: branches.map((text, index) => ({
      data: { text },
      children: [
        { data: { text: index % 2 ? '重点理解' : '概念说明' }, children: [] },
        { data: { text: index % 2 ? '练习应用' : '典型示例' }, children: [] },
      ],
    })),
  }
}

export async function createMindMap(data: MindMapCreateReq): Promise<number> {
  if (isMockDataSource) {
    const items = mockMaps()
    const id = nextMockId(items)
    const now = new Date().toISOString()
    items.unshift({ id, userId: 1, knowledgeBaseId: data.knowledgeBaseId ?? undefined, title: data.title, content: data.content, renderConfig: data.renderConfig, createTime: now, updateTime: now })
    saveMockMaps(items)
    return id
  }
  const res = await request.post("/api/mindmap/create", { ...data, kbId: data.knowledgeBaseId, knowledgeBaseId: undefined });
  return res.data?.data ?? res.data;
}

export async function updateMindMap(data: MindMapUpdateReq): Promise<MindMapUpdateResult> {
  if (isMockDataSource) {
    const items = mockMaps()
    const item = items.find((candidate) => candidate.id === data.id)
    if (!item) throw new Error('思维导图不存在')
    if (data.title !== undefined) item.title = data.title
    if (data.content !== undefined) item.content = data.content
    if (data.renderConfig !== undefined) item.renderConfig = structuredClone(data.renderConfig)
    if (data.knowledgeBaseId !== undefined) item.knowledgeBaseId = data.knowledgeBaseId ?? undefined
    item.updateTime = new Date().toISOString()
    saveMockMaps(items)
    const resource = mockSession
      .get<Array<{ resourceId: string; externalKey?: string }>>('resources', [])
      .find((candidate) => candidate.externalKey === `mindmap:${data.id}`)
    return {
      id: data.id,
      resourceId: resource?.resourceId,
      version: Date.now(),
      updateTime: item.updateTime,
      updatedAt: item.updateTime,
      previewData: data.content
        ? { kind: 'mindmap', mindMap: JSON.parse(data.content), mindMapConfig: item.renderConfig }
        : undefined,
    }
  }
  const res = await request.post("/api/mindmap/update", { ...data, kbId: data.knowledgeBaseId, knowledgeBaseId: undefined });
  const payload = res.data?.data ?? res.data
  return typeof payload === 'object' && payload !== null
    ? { id: data.id, ...(payload as Partial<MindMapUpdateResult>) }
    : { id: data.id }
}

export async function deleteMindMap(id: number): Promise<void> {
  if (isMockDataSource) {
    saveMockMaps(mockMaps().filter((item) => item.id !== id))
    return
  }
  await request.post(`/api/mindmap/delete/${id}`);
}

export async function getMindMapList(knowledgeBaseId?: number | null): Promise<MindMap[]> {
  if (isMockDataSource) {
    const items = mockMaps()
    return knowledgeBaseId == null ? items : items.filter((item) => item.knowledgeBaseId === knowledgeBaseId)
  }
  const res = await request.get("/api/mindmap/list", { params: { kbId: knowledgeBaseId } });
  const items = res.data?.data ?? res.data ?? [];
  return items.map((item: MindMap & { kbId?: number }) => ({ ...item, knowledgeBaseId: item.knowledgeBaseId ?? item.kbId }));
}

export async function getMindMapDetail(id: number): Promise<MindMap> {
  if (isMockDataSource) {
    const item = mockMaps().find((candidate) => candidate.id === id)
    if (!item) throw new Error('思维导图不存在')
    return structuredClone(item)
  }
  const res = await request.get(`/api/mindmap/detail/${id}`);
  const item = (res.data?.data ?? res.data) as MindMap & { kbId?: number };
  return { ...item, knowledgeBaseId: item.knowledgeBaseId ?? item.kbId };
}

export interface MindMapGenerateResult {
  id: number;
  resourceId?: string;
  title: string;
  treeData: any;
  renderConfig?: MindMapRenderConfig;
}

export async function generateMindMapFromAi(
  content: string,
  title?: string,
): Promise<MindMapGenerateResult> {
  if (isMockDataSource) {
    const resolvedTitle = title?.trim() || 'AI 思维导图'
    const treeData = mockTree(content, resolvedTitle)
    const renderConfig = mockMindMapRenderConfig()
    const id = await createMindMap({ title: resolvedTitle, content: JSON.stringify(treeData), renderConfig })
    return { id, title: resolvedTitle, treeData, renderConfig }
  }
  const res = await request.post("/api/mindmap/generate-from-ai", { content, title });
  return res.data?.data ?? res.data;
}
