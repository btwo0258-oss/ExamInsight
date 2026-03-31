import { request } from "./request";

export type MessageRole = "user" | "assistant" | "system";

export type Message = {
  id: number;
  conversationId: number;
  parentId?: number | null;
  role: MessageRole;
  content: string;
  tokenCount?: number;
  model?: string | null;
  durationMs?: number | null;
  sourceChunks?: unknown;
  createTime?: string;
  turnId?: string | null;
  qVersion?: number | null;
  aVersion?: number | null;
  files?: string | null;
};

export async function listMessages(conversationId: number): Promise<Message[]> {
  const res = await request.get(`/api/conversation/${conversationId}/messages`);
  return (res.data?.data ?? res.data) as Message[];
}

export async function createMessage(payload: {
  conversationId: number;
  role: MessageRole;
  content: string;
  model?: string;
}): Promise<Message> {
  // 使用ChatController的stream接口发送消息
  const res = await request.post("/api/chat/stream", {
    conversationId: payload.conversationId,
    message: payload.content,
    model: payload.model,
  });
  return (res.data?.data ?? res.data) as Message;
}
