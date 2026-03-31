import { getStoredToken } from "./request";
import { parseSseTextStream } from "@/utils/stream";
import { mockEnabled } from "@/mock";

export type ChatStreamPayload = {
  conversationId: number;
  content: string;
  model?: string;
  kbId?: number | null;
  fileContext?: string;
  history?: { role: string; content: string }[];
  parentId?: number | null;
  isRegenerate?: boolean;
  editMsgId?: number | null;
  turnId?: string;
  qVersion?: number;
  aVersion?: number;
  files?: string;
};

export async function streamChat(
  payload: ChatStreamPayload,
  options?: { signal?: AbortSignal },
): Promise<AsyncGenerator<string>> {
  if (mockEnabled.value) {
    return (async function* () {
      const mockText = "这是一个模拟的回复内容。您说的是：" + payload.content;
      for (let i = 0; i < mockText.length; i++) {
        if (options?.signal?.aborted) break;
        yield mockText[i] as string;
        await new Promise((resolve) => setTimeout(resolve, 50));
      }
    })();
  }

  const base = import.meta.env.VITE_API_BASE_URL ?? "";
  const token = getStoredToken();
  const bodyPayload = {
    conversationId: payload.conversationId,
    question: payload.content,
    model: payload.model,
    kbId: payload.kbId,
    fileContext: payload.fileContext,
    history: payload.history,
    parentId: payload.parentId,
    turnId: payload.turnId,
    qVersion: payload.qVersion,
    aVersion: payload.aVersion,
    isRegenerate: payload.isRegenerate,
    editMsgId: payload.editMsgId,
    files: payload.files,
  };

  const res = await fetch(`${base}/api/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(bodyPayload),
    signal: options?.signal,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `HTTP ${res.status}`);
  }

  return parseSseTextStream(res);
}
