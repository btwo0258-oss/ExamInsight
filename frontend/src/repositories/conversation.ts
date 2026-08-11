import { request } from "@/api/request";
import { isMockDataSource } from "@/config/dataSource";
import { mockSession } from "@/mock/storage";
import type {
  ConversationId,
  ConversationDto,
  ConversationType,
  CreateConversationRequest,
  UpdateConversationRequest,
} from "@/types/contracts/conversation";

export interface ConversationRepository {
  list(): Promise<ConversationDto[]>;
  create(payload?: CreateConversationRequest): Promise<ConversationDto>;
  update(id: ConversationId, payload: UpdateConversationRequest): Promise<ConversationDto>;
  remove(id: ConversationId): Promise<void>;
}

const DOMAIN = "conversations";

function normalizeConversation(item: Record<string, unknown>): ConversationDto {
  const rawKnowledgeBaseId = item.knowledgeBaseId ?? item.kbId;
  const knowledgeBaseId = rawKnowledgeBaseId == null ? null : String(rawKnowledgeBaseId);
  const rawProjectId = item.projectId ?? item.learningProjectId;
  const projectId = rawProjectId == null ? null : Number(rawProjectId);
  const rawType = String(item.type ?? item.conversationType ?? "GENERAL").toLowerCase();
  const conversationType: ConversationType = rawType === "learning-setup"
    ? "learning-setup"
    : rawType === "learning-tutor"
      ? "learning-tutor"
      : "general";

  return {
    id: String(item.id),
    title: typeof item.title === "string" ? item.title : null,
    kbId: knowledgeBaseId,
    knowledgeBaseId,
    kbName: typeof item.kbName === "string" ? item.kbName : null,
    isPinned: Boolean(item.isPinned),
    messageCount: Number(item.messageCount ?? 0),
    totalTokens: item.totalTokens === undefined ? undefined : Number(item.totalTokens),
    updateTime: String(item.updatedAt ?? item.lastMessageAt ?? item.updateTime ?? ""),
    createTime: String(item.createdAt ?? item.createTime ?? ""),
    projectId,
    learningProjectName:
      typeof item.learningProjectName === "string" ? item.learningProjectName : null,
    projectName: typeof item.projectName === "string" ? item.projectName : undefined,
    conversationType,
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
    const item = list.find((conversation) => String(conversation.id) === String(id));
    if (!item) throw new Error("Conversation not found");
    Object.assign(item, payload, { updateTime: new Date().toISOString() });
    mockSession.set(DOMAIN, list);
    return item;
  },

  async remove(id) {
    const list = mockSession.get<ConversationDto[]>(DOMAIN, []).filter(
      (item) => String(item.id) !== String(id),
    );
    mockSession.set(DOMAIN, list);
  },
};

const apiConversationRepository: ConversationRepository = {
  async list() {
    const response = await request.get("/api/v2/conversations", { params: { limit: 100 } });
    const page = response.data as { items?: Record<string, unknown>[] };
    return (page.items ?? []).map(normalizeConversation);
  },

  async create(payload = {}) {
    const response = await request.post("/api/v2/conversations", {
      title: payload.title,
      knowledgeBaseId: payload.knowledgeBaseId == null
        ? undefined
        : String(payload.knowledgeBaseId),
    });
    return normalizeConversation(response.data as Record<string, unknown>);
  },

  async update(id, payload) {
    const body: Record<string, unknown> = {};
    if (payload.title !== undefined) body.title = payload.title;
    if (payload.knowledgeBaseId === null) body.clearKnowledgeBase = true;
    else if (payload.knowledgeBaseId !== undefined) {
      body.knowledgeBaseId = String(payload.knowledgeBaseId);
    }
    const response = await request.patch(
      `/api/v2/conversations/${encodeURIComponent(String(id))}`,
      body,
    );
    return normalizeConversation(response.data as Record<string, unknown>);
  },

  async remove(id) {
    await request.delete(`/api/v2/conversations/${encodeURIComponent(String(id))}`);
  },
};

export const conversationRepository = isMockDataSource
  ? mockConversationRepository
  : apiConversationRepository;
