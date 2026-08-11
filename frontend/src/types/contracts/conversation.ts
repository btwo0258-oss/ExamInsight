import type { EntityId } from "./common";

export type ConversationType = "general" | "learning-setup" | "learning-tutor";

// V2 general chat uses external string IDs while the temporarily retained learning flow uses local numbers.
export type ConversationId = string | number;
export type ConversationKnowledgeBaseId = string | number;

export type ConversationDto = {
  id: ConversationId;
  title: string | null;
  kbId?: ConversationKnowledgeBaseId | null;
  knowledgeBaseId?: ConversationKnowledgeBaseId | null;
  kbName?: string | null;
  isPinned: boolean;
  messageCount: number;
  totalTokens?: number;
  updateTime: string;
  createTime: string;
  projectId?: EntityId | null;
  learningProjectName?: string | null;
  projectName?: string;
  conversationType: ConversationType;
};

export type CreateConversationRequest = {
  title?: string;
  kbId?: ConversationKnowledgeBaseId | null;
  knowledgeBaseId?: ConversationKnowledgeBaseId | null;
  projectId?: EntityId | null;
  learningProjectName?: string;
  projectName?: string;
  conversationType?: ConversationType;
};

export type UpdateConversationRequest = {
  title?: string;
  isPinned?: boolean;
  knowledgeBaseId?: ConversationKnowledgeBaseId | null;
  projectId?: EntityId | null;
  learningProjectName?: string;
  projectName?: string;
  conversationType?: ConversationType;
};
