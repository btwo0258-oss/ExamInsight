// @ts-nocheck
import { ref } from "vue";
import { defineStore } from "pinia";

import { streamChat } from "@/api/chat";
import { useModelStore } from "@/stores/model";
import { useConversationStore } from "@/stores/conversation";
import * as conversationApi from "@/api/conversation";
import { USER_KEY, getStoredToken } from "@/api/request";

export type ChatRole = "user" | "assistant" | "system";

export type TutorSource = {
  projectId: number;
  projectTitle: string;
  page: "study" | "detail" | "chat";
  stageId?: number | string;
  stageTitle?: string;
  taskId?: number | string;
  taskTitle?: string;
  taskType?: string;
  exerciseId?: number | string;
  exerciseTitle?: string;
  submitted?: boolean;
  label: string;
};

export type ChatMessage = {
  id: string;
  parentId?: number;
  role: ChatRole;
  content: string;
  createTime: number;
  streaming?: boolean;
  sourceChunks?: { docName: string; chunkIndex: number; content: string; _score?: number }[];
  durationMs?: number;
  // 版本控制
  turnId?: string; // 标识属于哪一轮对话
  qVersion?: number; // 问题版本 (0-based)
  aVersion?: number; // 回答版本 (0-based, 针对特定的 qVersion)
  // 附件
  files?: { name: string; type: string; size: number }[];
  kind?: "learning-profile" | "learning-document";
  learningData?: any;
  tutorContext?: string;
  tutorSource?: TutorSource;
};

function uid() {
  return `${Date.now()}_${Math.random().toString(16).slice(2)}`;
}

function getUserPrefix(): string {
  const userStr = sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY);
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      if (user && user.id) return String(user.id);
    } catch {}
  }
  return "guest";
}

function keyForConversation(conversationId: number) {
  const userPrefix = getUserPrefix();
  return `llm.messages.${userPrefix}.${conversationId}`;
}

function loadLocal(conversationId: number): ChatMessage[] {
  const key = keyForConversation(conversationId);
  const raw = localStorage.getItem(key) ?? sessionStorage.getItem(key);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as ChatMessage[];
  } catch {
    return [];
  }
}

function saveLocal(conversationId: number, items: ChatMessage[]) {
  const key = keyForConversation(conversationId);
  const value = JSON.stringify(items);
  sessionStorage.setItem(key, value);
  localStorage.setItem(key, value);
}

function keyForActiveQVersions(conversationId: number) {
  const userPrefix = getUserPrefix();
  return `llm.active_q_versions.${userPrefix}.${conversationId}`;
}

function keyForActiveAVersions(conversationId: number) {
  const userPrefix = getUserPrefix();
  return `llm.active_a_versions.${userPrefix}.${conversationId}`;
}

function loadLocalActiveQVersions(conversationId: number): Record<string, number> {
  const key = keyForActiveQVersions(conversationId);
  const raw = localStorage.getItem(key) ?? sessionStorage.getItem(key);
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Record<string, number>;
  } catch {
    return {};
  }
}

function loadLocalActiveAVersions(conversationId: number): Record<string, Record<number, number>> {
  const key = keyForActiveAVersions(conversationId);
  const raw = localStorage.getItem(key) ?? sessionStorage.getItem(key);
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Record<string, Record<number, number>>;
  } catch {
    return {};
  }
}

function saveLocalActiveQVersions(conversationId: number, versions: Record<string, number>) {
  sessionStorage.setItem(keyForActiveQVersions(conversationId), JSON.stringify(versions));
  localStorage.setItem(keyForActiveQVersions(conversationId), JSON.stringify(versions));
}

function saveLocalActiveAVersions(
  conversationId: number,
  versions: Record<string, Record<number, number>>,
) {
  sessionStorage.setItem(keyForActiveAVersions(conversationId), JSON.stringify(versions));
  localStorage.setItem(keyForActiveAVersions(conversationId), JSON.stringify(versions));
}

function parseCreateTime(createTime: any): number {
  if (!createTime) return Date.now();
  if (typeof createTime === "number") return createTime;
  if (Array.isArray(createTime)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = createTime;
    return new Date(year, month - 1, day, hour, minute, second).getTime();
  }
  const t = new Date(createTime).getTime();
  return isNaN(t) ? Date.now() : t;
}

export const useMessageStore = defineStore("message", () => {
  const byConversation = ref<Record<string, ChatMessage[]>>({});
  const fetchedFromServer = ref<Record<string, boolean>>({});

  // 记录每一轮对话当前激活的问题版本: { convId: { turnId: activeQIndex } }
  const activeQVersions = ref<Record<string, Record<string, number>>>({});
  // 记录每一轮对话下，每一个问题版本当前激活的回答版本: { convId: { turnId: { qIndex: activeAIndex } } }
  const activeAVersions = ref<Record<string, Record<string, Record<number, number>>>>({});

  const isStreaming = ref(false);
  const errorMessage = ref<string | null>(null);
  const controller = ref<AbortController | null>(null);

  const modelStore = useModelStore();

  function getMessages(conversationId: number) {
    const key = String(conversationId);
    return byConversation.value[key] ?? [];
  }

  function appendLocalMessage(
    conversationId: number,
    message: Omit<ChatMessage, "id" | "createTime"> & Partial<Pick<ChatMessage, "id" | "createTime">>,
  ) {
    initLocalIfNeeded(conversationId);
    const list = byConversation.value[String(conversationId)]!;
    const next: ChatMessage = {
      ...message,
      id: message.id || uid(),
      createTime: message.createTime || Date.now(),
    } as ChatMessage;
    list.push(next);
    saveLocal(conversationId, list);
    return next;
  }

  function updateLocalMessage(conversationId: number, messageId: string, patch: Partial<ChatMessage>) {
    initLocalIfNeeded(conversationId);
    const list = byConversation.value[String(conversationId)]!;
    const index = list.findIndex((message) => message.id === messageId);
    if (index === -1) return;
    list[index] = { ...list[index], ...patch };
    saveLocal(conversationId, list);
  }

  function initLocalIfNeeded(conversationId: number) {
    const key = String(conversationId);
    if (!byConversation.value[key]) {
      byConversation.value[key] = loadLocal(conversationId);
      activeQVersions.value[key] = loadLocalActiveQVersions(conversationId);
      activeAVersions.value[key] = loadLocalActiveAVersions(conversationId);
    }
  }

  async function ensureLoaded(conversationId: number) {
    const key = String(conversationId);

    initLocalIfNeeded(conversationId);

    if (fetchedFromServer.value[key]) return;
    fetchedFromServer.value[key] = true;

    try {
      const { listMessages } = await import("@/api/message");
      const remoteMessages = await listMessages(conversationId);
      if (remoteMessages && remoteMessages.length > 0) {
        const sortedRemote = [...remoteMessages].sort((a: any, b: any) => {
          const ta = parseCreateTime(a.createTime);
          const tb = parseCreateTime(b.createTime);
          if (ta !== tb) return ta - tb;
          return Number(a.id || 0) - Number(b.id || 0);
        });

        const userTurnIdByParent = new Map<string, string>();
        const userVersionCountByTurn = new Map<string, number>();
        const assistantVersionCountByTurnQ = new Map<string, number>();
        const derivedUserById = new Map<number, { turnId: string; qVersion: number }>();

        const formatted: ChatMessage[] = [];
        const pendingAssistants: Array<{ raw: any; message: ChatMessage }> = [];

        // 从本地尝试恢复 files、content（用于处理带文件时 [附加文件内容] 的展示）以及版本号
        const localMessages = loadLocal(conversationId);

        for (const m of sortedRemote) {
          const localMatch = localMessages.find((lm) => lm.id === String(m.id));

          // 修复：如果服务端返回了带 [附加文件内容] 的长文本，而本地存了原始输入文本和 files
          let displayContent = m.content;
          if (m.role === "user" && m.content?.includes("[附加文件内容]")) {
            displayContent =
              localMatch?.content || m.content.split("[用户输入]\n").pop() || m.content;
          }

          const parsedFiles =
            typeof m.files === "string"
              ? (() => {
                  try {
                    return JSON.parse(m.files);
                  } catch {
                    return undefined;
                  }
                })()
              : m.files;

          const message: ChatMessage = {
            id: String(m.id || Date.now() + Math.random()),
            parentId: m.parentId ?? undefined,
            role: m.role,
            content: displayContent,
            createTime: parseCreateTime(m.createTime),
            sourceChunks:
              typeof m.sourceChunks === "string"
                ? (() => {
                    try {
                      return JSON.parse(m.sourceChunks);
                    } catch {
                      return undefined;
                    }
                  })()
                : m.sourceChunks,
            durationMs: m.durationMs,
            turnId: m.turnId || localMatch?.turnId || undefined,
            qVersion: m.qVersion ?? localMatch?.qVersion ?? undefined,
            aVersion: m.aVersion ?? localMatch?.aVersion ?? undefined,
            tutorContext: m.tutorContext || localMatch?.tutorContext,
            tutorSource:
              (typeof m.learningSource === "string"
                ? (() => {
                    try {
                      return JSON.parse(m.learningSource);
                    } catch {
                      return undefined;
                    }
                  })()
                : m.learningSource) || localMatch?.tutorSource,
            //files: parsedFiles || localMatch?.files,
            files:
              parsedFiles ||
              localMatch?.files ||
              (m.role === "user" && m.content?.includes("[附加文件内容]")
                ? (() => {
                    const fileRegex = /\[文件：(.*?)\]/g;
                    const recoveredFiles = [];
                    let match;
                    while ((match = fileRegex.exec(m.content)) !== null) {
                      const fileName = match[1];
                      let type = "unknown";
                      if (fileName.endsWith(".pdf")) type = "application/pdf";
                      else if (fileName.endsWith(".txt")) type = "text/plain";
                      else if (fileName.endsWith(".docx") || fileName.endsWith(".docx"))
                        type = "application/msword";
                      else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx"))
                        type = "application/vnd.ms-excel";

                      recoveredFiles.push({ name: fileName, type, size: 0 }); // 无法恢复准确大小，只能用0
                    }
                    return recoveredFiles.length > 0 ? recoveredFiles : undefined;
                  })()
                : undefined),
          };

          if (message.role === "user") {
            const localMatch = (byConversation.value[key] || []).find(
              (lm) => lm.id === String(m.id),
            );

            // Check if we need to auto-assign qVersion
            // If the server returned undefined (old API), or if we see duplicate turnId+qVersion (corrupt old data)
            const parentKey = String(m.parentId ?? 0);
            if (!userTurnIdByParent.has(parentKey) && message.turnId) {
              userTurnIdByParent.set(parentKey, message.turnId);
            }

            const turnId =
              message.turnId || localMatch?.turnId || userTurnIdByParent.get(parentKey) || uid();
            if (!userTurnIdByParent.has(parentKey)) {
              userTurnIdByParent.set(parentKey, turnId);
            }

            const expectedNextQ = userVersionCountByTurn.get(turnId) ?? 0;

            // If message has turnId and a valid qVersion that doesn't conflict, use it
            if (
              message.turnId &&
              message.qVersion !== undefined &&
              message.qVersion >= expectedNextQ
            ) {
              userVersionCountByTurn.set(turnId, message.qVersion + 1);
            } else if (
              localMatch?.turnId &&
              localMatch?.qVersion !== undefined &&
              localMatch.qVersion >= expectedNextQ
            ) {
              message.turnId = localMatch.turnId;
              message.qVersion = localMatch.qVersion;
              userVersionCountByTurn.set(turnId, localMatch.qVersion + 1);
            } else {
              // Otherwise, auto-assign
              message.turnId = turnId;
              message.qVersion = expectedNextQ;
              userVersionCountByTurn.set(turnId, expectedNextQ + 1);
            }

            message.aVersion = 0;
            if (m.id && message.turnId) {
              derivedUserById.set(Number(m.id), {
                turnId: message.turnId,
                qVersion: message.qVersion ?? 0,
              });
            }
          } else {
            pendingAssistants.push({ raw: m, message });
          }

          formatted.push(message);
        }

        for (const { raw, message } of pendingAssistants) {
          const parentUser = raw.parentId ? derivedUserById.get(Number(raw.parentId)) : undefined;

          const localMatch = (byConversation.value[key] || []).find(
            (lm) => lm.id === String(raw.id),
          );

          const turnId = message.turnId || localMatch?.turnId || parentUser?.turnId || uid();
          const qVersion = message.qVersion ?? localMatch?.qVersion ?? parentUser?.qVersion ?? 0;

          const aKey = `${turnId}-${qVersion}`;
          const expectedNextA = assistantVersionCountByTurnQ.get(aKey) ?? 0;

          message.turnId = turnId;
          message.qVersion = qVersion;

          if (
            message.aVersion === undefined ||
            message.aVersion === null ||
            message.aVersion < expectedNextA
          ) {
            message.aVersion = localMatch?.aVersion ?? expectedNextA;
          }

          assistantVersionCountByTurnQ.set(
            aKey,
            Math.max(message.aVersion + 1, assistantVersionCountByTurnQ.get(aKey) ?? 0),
          );
        }

        const localNewMsgs = (byConversation.value[key] || []).filter(
          (localM) =>
            !formatted.some(
              (remoteM) =>
                remoteM.id === localM.id ||
                (remoteM.role === localM.role && remoteM.content === localM.content),
            ),
        );

        byConversation.value[key] = [...formatted, ...localNewMsgs];
        saveLocal(conversationId, byConversation.value[key]!);

        const rebuildQ: Record<string, number> = {};
        const rebuildA: Record<string, Record<number, number>> = {};
        const allQVersionsByTurn: Record<string, Set<number>> = {};

        for (const m of formatted) {
          if (!m.turnId) continue;
          const tid = m.turnId;
          const qv = m.qVersion ?? 0;
          const av = m.aVersion ?? 0;

          if (m.role === "user") {
            if (rebuildQ[tid] === undefined || qv > rebuildQ[tid]) {
              rebuildQ[tid] = qv;
            }
            if (!allQVersionsByTurn[tid]) allQVersionsByTurn[tid] = new Set();
            allQVersionsByTurn[tid].add(qv);
          }

          if (m.role === "assistant") {
            if (!rebuildA[tid]) rebuildA[tid] = {};
            if (rebuildA[tid][qv] === undefined || av > rebuildA[tid][qv]) {
              rebuildA[tid][qv] = av;
            }
          }
        }

        const existingQ = activeQVersions.value[key] || {};
        const localQVersions = loadLocalActiveQVersions(conversationId);
        Object.assign(existingQ, localQVersions);

        const existingA = activeAVersions.value[key] || {};
        const localAVersions = loadLocalActiveAVersions(conversationId);
        for (const tid of Object.keys(localAVersions)) {
          if (!existingA[tid]) existingA[tid] = {};
          Object.assign(existingA[tid], localAVersions[tid]);
        }

        for (const tid of Object.keys(rebuildQ)) {
          if (existingQ[tid] === undefined) {
            existingQ[tid] = rebuildQ[tid];
          }
        }

        for (const tid of Object.keys(allQVersionsByTurn)) {
          if (!existingA[tid]) existingA[tid] = {};
          for (const qv of allQVersionsByTurn[tid]) {
            if (existingA[tid][qv] === undefined) {
              if (rebuildA[tid] && rebuildA[tid][qv] !== undefined) {
                existingA[tid][qv] = rebuildA[tid][qv];
              } else {
                existingA[tid][qv] = 0;
              }
            }
          }
        }

        activeQVersions.value[key] = existingQ;
        activeAVersions.value[key] = existingA;
        saveLocalActiveQVersions(conversationId, existingQ);
        saveLocalActiveAVersions(conversationId, existingA);
      }
    } catch (err) {
      console.error("Failed to load messages from server:", err);
      fetchedFromServer.value[key] = false;
    }
  }

  function stopStreaming() {
    controller.value?.abort();
    controller.value = null;
    isStreaming.value = false;
  }

  async function sendMessage(
    conversationId: number,
    content: string,
    turnId?: string,
    qVersion?: number,
    aVersion?: number,
    files?: File[],
    skipUserMsg: boolean = false,
    extraOptions?: { isRegenerate?: boolean; editMsgId?: number; parentId?: number; tutorContext?: string; tutorSource?: TutorSource },
  ) {
    let text = content.trim();
    const hasFiles = files && files.length > 0;
    if (!text && !hasFiles && !skipUserMsg) return;

    if (!text && hasFiles) {
      text = "请分析上传的文件内容";
    }

    await ensureLoaded(conversationId);
    errorMessage.value = null;
    const convIdStr = String(conversationId);

    // 1. 确定 Turn 和版本号
    const currentTurnId = turnId || uid();
    const currentQVersion = qVersion !== undefined ? qVersion : 0;
    const currentAVersion = aVersion !== undefined ? aVersion : 0;

    // 2. 更新激活版本映射并持久化
    if (!activeQVersions.value[convIdStr]) activeQVersions.value[convIdStr] = {};
    activeQVersions.value[convIdStr]![currentTurnId] = currentQVersion;
    saveLocalActiveQVersions(conversationId, activeQVersions.value[convIdStr]!);

    if (!activeAVersions.value[convIdStr]) activeAVersions.value[convIdStr] = {};
    if (!activeAVersions.value[convIdStr]![currentTurnId])
      activeAVersions.value[convIdStr]![currentTurnId] = {};
    activeAVersions.value[convIdStr]![currentTurnId]![currentQVersion] = currentAVersion;
    saveLocalActiveAVersions(conversationId, activeAVersions.value[convIdStr]!);

    // 3. 解析文件内容 (后端逻辑)
    let fileContext = "";
    let displayContent = text;
    if (hasFiles && !skipUserMsg) {
      isStreaming.value = true;
      try {
        const extractedTexts = [];
        const fileNames = [];
        for (const file of files) {
          const formData = new FormData();
          formData.append("file", file);
          const token = getStoredToken();
          const res = await fetch((import.meta.env.VITE_API_BASE_URL || "") + "/api/doc/extract", {
            method: "POST",
            body: formData,
            headers: token ? { Authorization: `Bearer ${token}` } : {},
          });
          const json = await res.json();
          if (json.code === 200) {
            extractedTexts.push(`[文件：${file.name}]\n${json.data}`);
            fileNames.push(file.name);
          } else {
            throw new Error(json.message || "文件解析失败");
          }
        }
        fileContext = extractedTexts.join("\n\n");
        if (fileNames.length > 0) {
          // 不再向 displayContent 追加附件文本，因为 UI 已经通过 message.files 渲染了附件卡片
        }
      } catch (e) {
        errorMessage.value = "文件上传/解析失败: " + e;
        isStreaming.value = false;
        return;
      }
    }

    // 4. 记录准备发送消息状态（不覆盖后端生成的标题）
    const list = byConversation.value[convIdStr]!;

    stopStreaming();
    const nextController = new AbortController();
    controller.value = nextController;

    // 5. 创建并插入消息
    const assistantMsg: ChatMessage = {
      id: uid(),
      parentId: extraOptions?.parentId,
      role: "assistant",
      content: "",
      createTime: Date.now(),
      streaming: true,
      turnId: currentTurnId,
      qVersion: currentQVersion,
      aVersion: currentAVersion,
    };

    if (!skipUserMsg) {
      const userMsg: ChatMessage = {
        id: uid(),
        parentId: extraOptions?.parentId,
        role: "user",
        content: displayContent,
        createTime: Date.now(),
        turnId: currentTurnId,
        qVersion: currentQVersion,
        tutorContext: extraOptions?.tutorContext,
        tutorSource: extraOptions?.tutorSource,
        files: files?.map((f) => ({ name: f.name, type: f.type, size: f.size })),
      };

      const lastMsgOfTurnIdx = list.findLastIndex((m) => m.turnId === currentTurnId);
      if (lastMsgOfTurnIdx !== -1) {
        list.splice(lastMsgOfTurnIdx + 1, 0, userMsg, assistantMsg);
      } else {
        list.push(userMsg, assistantMsg);
      }
    } else {
      const lastMsgOfTurnIdx = list.findLastIndex((m) => m.turnId === currentTurnId);
      if (lastMsgOfTurnIdx !== -1) {
        list.splice(lastMsgOfTurnIdx + 1, 0, assistantMsg);
      } else {
        list.push(assistantMsg);
      }
    }
    saveLocal(conversationId, list);

    // 6. 开始流式生成
    isStreaming.value = true;
    const startTime = Date.now();
    try {
      const convStore = useConversationStore();
      const currentConv = convStore.list.find((c) => c.id === conversationId);
      const kbId = currentConv?.knowledgeBaseId;

      // 构建基于当前选中版本的历史上下文
      const historyContext: { role: string; content: string }[] = [];
      const orderedTurnIds: string[] = [];
      const turns: Record<string, ChatMessage[]> = {};

      list.forEach((m) => {
        const tid = m.turnId || `legacy-${m.id}`;
        if (!turns[tid]) {
          turns[tid] = [];
          orderedTurnIds.push(tid);
        }
        turns[tid].push(m);
      });

      for (const tid of orderedTurnIds) {
        if (tid === currentTurnId) break; // 不包含当前正在生成的这一轮

        const turnMsgs = turns[tid];
        const activeQ = activeQVersions.value[convIdStr]?.[tid] ?? 0;
        const activeA = activeAVersions.value[convIdStr]?.[tid]?.[activeQ] ?? 0;

        const userM = turnMsgs.find(
          (m) => m.role === "user" && (m.qVersion === activeQ || !m.turnId),
        );
        if (userM) historyContext.push({ role: "user", content: userM.content });

        const aiM = turnMsgs.find(
          (m) => m.role === "assistant" && m.qVersion === activeQ && m.aVersion === activeA,
        );
        if (aiM && aiM.content) historyContext.push({ role: "assistant", content: aiM.content });
      }

      // 构建发送给后端的文本，如果后端不支持 fileContext 字段，我们需要把它拼接到 question 中
      const finalQuestion = fileContext
        ? `[附加文件内容]\n${fileContext}\n\n[用户输入]\n${text}`
        : text;

      if (extraOptions?.tutorContext) {
        historyContext.unshift({ role: "system", content: extraOptions.tutorContext });
      }

      // 修复后端 bug：如果传递了 history，后端不会把当前的 question 自动加入大模型上下文中，所以前端必须手动加进去！
      historyContext.push({ role: "user", content: finalQuestion });

      const gen = await streamChat(
        {
          conversationId,
          content: finalQuestion,
          model: modelStore.currentModel,
          kbId,
          fileContext,
          history: historyContext,
          isRegenerate: extraOptions?.isRegenerate,
          editMsgId: extraOptions?.editMsgId,
          parentId: extraOptions?.parentId,
          turnId: currentTurnId,
          qVersion: currentQVersion,
          aVersion: currentAVersion,
          files:
            files && files.length > 0
              ? JSON.stringify(files.map((f) => ({ name: f.name, type: f.type, size: f.size })))
              : undefined,
        },
        { signal: nextController.signal },
      );

      let fullContent = "";
      for await (const chunk of gen) {
        fullContent += chunk;
        const targetIdx = list.findIndex((m) => m.id === assistantMsg.id);
        if (targetIdx !== -1) {
          list[targetIdx].content = fullContent;
          saveLocal(conversationId, list);
        }
      }

      const finalIdx = list.findIndex((m) => m.id === assistantMsg.id);
      if (finalIdx !== -1) {
        list[finalIdx].streaming = false;
        list[finalIdx].durationMs = Date.now() - startTime;
        saveLocal(conversationId, list);
      }

      // 如果是第一条消息，生成完毕后刷新对话列表以获取后端自动生成的标题
      const userMsgCount = list.filter((m) => m.role === "user").length;
      if (userMsgCount === 1 && !skipUserMsg) {
        const convStore = useConversationStore();
        await convStore.fetchList();
      }

      // 获取后端最终保存的状态 (sourceChunks, durationMs, 以及真实的数据库 ID)
      try {
        const { listMessages } = await import("@/api/message");
        const remoteMessages = await listMessages(conversationId);
        if (remoteMessages && remoteMessages.length > 0) {
          const localAssistantIdx = list.findIndex((m) => m.id === assistantMsg.id);

          const remoteUser = remoteMessages
            .filter((m: any) => m.role === "user")
            .find((m: any) => {
              if (m.turnId !== undefined && m.turnId !== null) {
                return (
                  m.turnId === currentTurnId &&
                  (m.qVersion ?? 0) === currentQVersion &&
                  (m.content === text || m.content === finalQuestion)
                );
              }
              return m.content === text || m.content === finalQuestion;
            });

          const remoteAssistant = remoteMessages
            .filter((m: any) => m.role === "assistant")
            .find((m: any) => {
              if (m.turnId !== undefined && m.turnId !== null) {
                return (
                  m.turnId === currentTurnId &&
                  (m.qVersion ?? 0) === currentQVersion &&
                  (m.aVersion ?? 0) === currentAVersion
                );
              }

              if (remoteUser?.id) {
                return Number(m.parentId ?? -1) === Number(remoteUser.id);
              }

              // Fallback to checking fullContent length as a heuristic if no parentId match
              return (
                m.content === fullContent ||
                (m.content && fullContent && m.content.length === fullContent.length)
              );
            });

          if (remoteUser && !skipUserMsg) {
            const localUserIdx = list.findIndex(
              (m) =>
                m.role === "user" &&
                m.turnId === currentTurnId &&
                (m.qVersion ?? 0) === currentQVersion &&
                m.content === text,
            );
            if (localUserIdx !== -1) {
              list[localUserIdx].id = String(remoteUser.id);
              list[localUserIdx].parentId = remoteUser.parentId ?? undefined;
            }
          }

          if (remoteAssistant && localAssistantIdx !== -1) {
            list[localAssistantIdx].id = String(remoteAssistant.id);
            list[localAssistantIdx].parentId = remoteAssistant.parentId ?? undefined;
            list[localAssistantIdx].turnId =
              remoteAssistant.turnId || list[localAssistantIdx].turnId;
            list[localAssistantIdx].qVersion =
              remoteAssistant.qVersion ?? list[localAssistantIdx].qVersion;
            list[localAssistantIdx].aVersion =
              remoteAssistant.aVersion ?? list[localAssistantIdx].aVersion;
            list[localAssistantIdx].sourceChunks =
              typeof remoteAssistant.sourceChunks === "string"
                ? (() => {
                    try {
                      return JSON.parse(remoteAssistant.sourceChunks);
                    } catch {
                      return undefined;
                    }
                  })()
                : remoteAssistant.sourceChunks;
            list[localAssistantIdx].durationMs = remoteAssistant.durationMs;
          }

          saveLocal(conversationId, list);
        }
      } catch (e) {
        console.error("Failed to fetch updated messages", e);
      }
    } catch (err) {
      const errorIdx = list.findIndex((m) => m.id === assistantMsg.id);
      if (errorIdx !== -1) {
        list[errorIdx].streaming = false;
        list[errorIdx].content =
          list[errorIdx].content || (err instanceof Error ? err.message : "请求失败");
        saveLocal(conversationId, list);
      }
      errorMessage.value = err instanceof Error ? err.message : "请求失败";
    } finally {
      isStreaming.value = false;
      controller.value = null;
    }
  }

  // 重新生成回答 (基于当前激活的问题版本)
  async function regenerate(conversationId: number, turnId: string) {
    await ensureLoaded(conversationId);
    const convIdStr = String(conversationId);
    const messages = byConversation.value[convIdStr] || [];

    // 如果有旧消息没有 turnId，在此处一并更新
    let hasChanges = false;
    const targetIdx = messages.findIndex(
      (m) => !m.turnId && (m.id === turnId || `turn-${m.id}` === turnId),
    );
    if (targetIdx !== -1) {
      messages[targetIdx].turnId = turnId;
      messages[targetIdx].qVersion = 0;
      if (
        targetIdx + 1 < messages.length &&
        messages[targetIdx + 1].role === "assistant" &&
        !messages[targetIdx + 1].turnId
      ) {
        messages[targetIdx + 1].turnId = turnId;
        messages[targetIdx + 1].qVersion = 0;
        messages[targetIdx + 1].aVersion = 0;
      }
      hasChanges = true;
    } else {
      // 可能是 AI 消息触发的 regenerate
      const aiIdx = messages.findIndex(
        (m) => !m.turnId && (m.id === turnId || `turn-${m.id}` === turnId),
      );
      if (aiIdx !== -1) {
        messages[aiIdx].turnId = turnId;
        messages[aiIdx].qVersion = 0;
        messages[aiIdx].aVersion = 0;
        if (aiIdx > 0 && messages[aiIdx - 1].role === "user" && !messages[aiIdx - 1].turnId) {
          messages[aiIdx - 1].turnId = turnId;
          messages[aiIdx - 1].qVersion = 0;
        }
        hasChanges = true;
      }
    }
    if (hasChanges) saveLocal(conversationId, messages);

    // 找到当前选中的问题版本
    const activeQ = activeQVersions.value[convIdStr]?.[turnId] ?? 0;
    const userMsg = messages.find(
      (m) => m.turnId === turnId && m.role === "user" && m.qVersion === activeQ,
    );
    if (!userMsg) return;

    // 计算下一个回答版本号
    const turnMessages = messages.filter((m) => m.turnId === turnId && m.qVersion === activeQ);
    const maxA = Math.max(
      -1,
      ...turnMessages.filter((m) => m.role === "assistant").map((m) => m.aVersion ?? 0),
    );
    const nextA = maxA + 1;

    const parentId = isNaN(Number(userMsg.id)) ? undefined : Number(userMsg.id);

    // 重新生成时，跳过创建 User 消息，直接创建 AI 消息
    await sendMessage(conversationId, userMsg.content, turnId, activeQ, nextA, undefined, true, {
      isRegenerate: true,
      parentId,
      tutorContext: userMsg.tutorContext,
      tutorSource: userMsg.tutorSource,
    });
  }

  // 编辑问题并重新生成 (创建新的问题版本)
  async function editAndRegenerate(conversationId: number, turnId: string, newContent: string) {
    await ensureLoaded(conversationId);
    const convIdStr = String(conversationId);
    const messages = byConversation.value[convIdStr] || [];

    // 如果有旧消息没有 turnId，在此处一并更新
    let hasChanges = false;
    const targetIdx = messages.findIndex(
      (m) => !m.turnId && (m.id === turnId || `turn-${m.id}` === turnId),
    );
    if (targetIdx !== -1) {
      messages[targetIdx].turnId = turnId;
      messages[targetIdx].qVersion = 0;
      if (
        targetIdx + 1 < messages.length &&
        messages[targetIdx + 1].role === "assistant" &&
        !messages[targetIdx + 1].turnId
      ) {
        messages[targetIdx + 1].turnId = turnId;
        messages[targetIdx + 1].qVersion = 0;
        messages[targetIdx + 1].aVersion = 0;
      }
      hasChanges = true;
    }
    if (hasChanges) saveLocal(conversationId, messages);

    // 计算下一个问题版本号
    const turnMessages = messages.filter((m) => m.turnId === turnId && m.role === "user");
    const maxQ = Math.max(-1, ...turnMessages.map((m) => m.qVersion ?? 0));
    const nextQ = maxQ + 1;

    // 找到被编辑的原始 user 消息
    const activeQ = activeQVersions.value[convIdStr]?.[turnId] ?? 0;
    const originalUserMsg = turnMessages.find((m) => m.qVersion === activeQ);
    const editMsgId =
      originalUserMsg && !isNaN(Number(originalUserMsg.id))
        ? Number(originalUserMsg.id)
        : undefined;

    const parentId = originalUserMsg?.parentId;

    await sendMessage(conversationId, newContent, turnId, nextQ, 0, undefined, false, {
      editMsgId,
      parentId,
      tutorContext: originalUserMsg?.tutorContext,
      tutorSource: originalUserMsg?.tutorSource,
    });
  }

  // 切换问题版本
  function switchQVersion(conversationId: number, turnId: string, qIndex: number) {
    const convIdStr = String(conversationId);
    if (!activeQVersions.value[convIdStr]) activeQVersions.value[convIdStr] = {};
    activeQVersions.value[convIdStr]![turnId] = qIndex;
    saveLocalActiveQVersions(conversationId, activeQVersions.value[convIdStr]!);

    if (!activeAVersions.value[convIdStr]) activeAVersions.value[convIdStr] = {};
    if (!activeAVersions.value[convIdStr]![turnId]) activeAVersions.value[convIdStr]![turnId] = {};

    const messages = byConversation.value[convIdStr] || [];
    const aVersionsForQ = messages
      .filter((m) => m.turnId === turnId && m.role === "assistant" && m.qVersion === qIndex)
      .map((m) => m.aVersion ?? 0);

    if (aVersionsForQ.length > 0) {
      const maxA = Math.max(...aVersionsForQ);
      activeAVersions.value[convIdStr]![turnId]![qIndex] = maxA;
    } else {
      activeAVersions.value[convIdStr]![turnId]![qIndex] = 0;
    }
    saveLocalActiveAVersions(conversationId, activeAVersions.value[convIdStr]!);
  }

  // 切换回答版本 (针对当前选中的问题)
  function switchAVersion(conversationId: number, turnId: string, qIndex: number, aIndex: number) {
    const convIdStr = String(conversationId);
    if (!activeAVersions.value[convIdStr]) activeAVersions.value[convIdStr] = {};
    if (!activeAVersions.value[convIdStr]![turnId]) activeAVersions.value[convIdStr]![turnId] = {};
    activeAVersions.value[convIdStr]![turnId]![qIndex] = aIndex;
    saveLocalActiveAVersions(conversationId, activeAVersions.value[convIdStr]!);
  }

  // 获取版本数
  function getQVersionCount(conversationId: number, turnId: string): number {
    const messages = byConversation.value[String(conversationId)] || [];
    const qVersions = new Set(
      messages.filter((m) => m.turnId === turnId && m.role === "user").map((m) => m.qVersion ?? 0),
    );
    return qVersions.size;
  }

  function getAVersionCount(conversationId: number, turnId: string, qIndex: number): number {
    const messages = byConversation.value[String(conversationId)] || [];
    const aVersions = new Set(
      messages
        .filter((m) => m.turnId === turnId && m.role === "assistant" && m.qVersion === qIndex)
        .map((m) => m.aVersion ?? 0),
    );
    return aVersions.size;
  }

  function getActiveQVersion(conversationId: number, turnId: string): number {
    return activeQVersions.value[String(conversationId)]?.[turnId] ?? 0;
  }

  function getActiveAVersion(conversationId: number, turnId: string, qIndex: number): number {
    return activeAVersions.value[String(conversationId)]?.[turnId]?.[qIndex] ?? 0;
  }

  async function createConversation(options: {
    knowledgeBaseId?: number;
    firstMessage?: string;
    files?: File[];
  }) {
    const conversation = await conversationApi.createConversation({
      knowledgeBaseId: options.knowledgeBaseId,
      title: "新对话",
    });

    // 不再在这里发送消息，让调用方在跳转后再发送
    return {
      id: conversation.id,
      firstMessage: options.firstMessage,
      files: options.files,
    };
  }

  function clearConversation(conversationId: number) {
    const key = String(conversationId);
    delete byConversation.value[key];
    delete activeQVersions.value[key];
    delete activeAVersions.value[key];
    sessionStorage.removeItem(keyForConversation(conversationId));
    sessionStorage.removeItem(keyForActiveQVersions(conversationId));
    sessionStorage.removeItem(keyForActiveAVersions(conversationId));
  }

  function clearError() {
    errorMessage.value = null;
  }

  function clearMemoryState() {
    byConversation.value = {};
    fetchedFromServer.value = {};
    activeQVersions.value = {};
    activeAVersions.value = {};
    isStreaming.value = false;
    errorMessage.value = null;
    if (controller.value) {
      controller.value.abort();
      controller.value = null;
    }
  }

  return {
    getMessages,
    appendLocalMessage,
    updateLocalMessage,
    isStreaming,
    errorMessage,
    byConversation,
    activeQVersions,
    activeAVersions,
    ensureLoaded,
    sendMessage,
    stopStreaming,
    createConversation,
    clearConversation,
    clearError,
    clearMemoryState,
    regenerate,
    editAndRegenerate,
    switchQVersion,
    switchAVersion,
    getQVersionCount,
    getAVersionCount,
    getActiveQVersion,
    getActiveAVersion,
  };
});
