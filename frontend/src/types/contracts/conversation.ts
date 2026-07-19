import type { EntityId } from "./common";

export type ConversationType = "general" | "learning-setup" | "learning-tutor";

export type ConversationDto = {
  id: EntityId;
  title: string | null;
  kbId?: EntityId | null;
  knowledgeBaseId?: EntityId | null;
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
  kbId?: EntityId | null;
  knowledgeBaseId?: EntityId | null;
  projectId?: EntityId | null;
  learningProjectName?: string;
  projectName?: string;
  conversationType?: ConversationType;
};

export type UpdateConversationRequest = {
  title?: string;
  isPinned?: boolean;
  knowledgeBaseId?: EntityId | null;
  projectId?: EntityId | null;
  learningProjectName?: string;
  projectName?: string;
  conversationType?: ConversationType;
};
