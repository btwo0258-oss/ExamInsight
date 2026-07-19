import { request } from "@/api/request";
import { isMockDataSource } from "@/config/dataSource";
import { mockSession } from "@/mock/storage";
import type {
  ConversationDto,
  ConversationType,
  CreateConversationRequest,
  UpdateConversationRequest,
} from "@/types/contracts/conversation";

export interface ConversationRepository {
  list(): Promise<ConversationDto[]>;
  create(payload?: CreateConversationRequest): Promise<ConversationDto>;
  update(id: number, payload: UpdateConversationRequest): Promise<ConversationDto>;
  remove(id: number): Promise<void>;
}

const DOMAIN = "conversations";

function normalizeConversation(item: Record<string, unknown>): ConversationDto {
  const kbId =
    item.knowledgeBaseId === undefined
      ? item.kbId === undefined || item.kbId === null
        ? null
        : Number(item.kbId)
      : item.knowledgeBaseId === null
        ? null
        : Number(item.knowledgeBaseId);
  const rawProjectId = item.projectId ?? item.learningProjectId;
  const projectId = rawProjectId === undefined || rawProjectId === null ? null : Number(rawProjectId);
  const conversationType = (item.conversationType as ConversationType) ?? "general";
  return {
    id: Number(item.id),
    title: typeof item.title === "string" ? item.title : null,
    kbId: kbId,
    knowledgeBaseId: kbId,
    kbName: typeof item.kbName === "string" ? item.kbName : null,
    isPinned: Boolean(item.isPinned),
    messageCount: Number(item.messageCount ?? 0),
    totalTokens: item.totalTokens === undefined ? undefined : Number(item.totalTokens),
    updateTime: String(item.updateTime ?? ""),
    createTime: String(item.createTime ?? ""),
    projectId: projectId,
    learningProjectName:
      typeof item.learningProjectName === "string" ? item.learningProjectName : null,
    projectName: typeof item.projectName === "string" ? item.projectName : undefined,
    conversationType: conversationType,
  };
}

const mockConversationRepository: ConversationRepository = {
  async list() {
    return mockSession.get<ConversationDto[]>(DOMAIN, []);
  },

  async create(payload = {}) {
    const list = mockSession.get<ConversationDto[]>(DOMAIN, []);
    const now = new Date().toISOString();
    const next: ConversationDto = {
      id: Date.now(),
      title: payload.title || "新对话",
      knowledgeBaseId: payload.knowledgeBaseId ?? null,
      isPinned: false,
      messageCount: 0,
      updateTime: now,
      createTime: now,
      projectId: payload.projectId ?? null,
      projectName: payload.projectName,
      conversationType: payload.conversationType ?? "general",
    };
    list.unshift(next);
    mockSession.set(DOMAIN, list);
    return next;
  },

  async update(id, payload) {
    const list = mockSession.get<ConversationDto[]>(DOMAIN, []);
    const item = list.find((conversation) => conversation.id === id);
    if (!item) throw new Error("Conversation not found");
    Object.assign(item, payload, { updateTime: new Date().toISOString() });
    mockSession.set(DOMAIN, list);
    return item;
  },

  async remove(id) {
    const list = mockSession.get<ConversationDto[]>(DOMAIN, []).filter((item) => item.id !== id);
    mockSession.set(DOMAIN, list);
  },
};

const apiConversationRepository: ConversationRepository = {
  async list() {
    const response = await request.get("/api/conversation/list");
    const data = (response.data?.data ?? response.data) as Record<string, unknown>[];
    return data.map(normalizeConversation);
  },

  async create(payload = {}) {
    const response = await request.post("/api/conversation/create", {
      ...payload,
      kbId: payload.knowledgeBaseId,
      knowledgeBaseId: undefined,
      learningProjectName: payload.projectName,
      projectName: undefined,
    });
    return normalizeConversation((response.data?.data ?? response.data) as Record<string, unknown>);
  },

  async update(id, payload) {
    const response = await request.put(`/api/conversation/${id}`, payload);
    return normalizeConversation((response.data?.data ?? response.data) as Record<string, unknown>);
  },

  async remove(id) {
    await request.delete(`/api/conversation/${id}`);
  },
};

export const conversationRepository = isMockDataSource
  ? mockConversationRepository
  : apiConversationRepository;
