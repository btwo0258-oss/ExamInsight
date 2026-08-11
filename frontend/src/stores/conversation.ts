import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { useRoute, useRouter } from "vue-router";
import * as conversationApi from "@/api/conversation";
import type { ConversationId, ConversationKnowledgeBaseId } from "@/types/contracts/conversation";

export const useConversationStore = defineStore("conversation", () => {
  const router = useRouter();
  const route = useRoute();

  const list = ref<conversationApi.Conversation[]>([]);
  const isInitialized = ref(false);

  const currentId = computed<ConversationId | null>(() => {
    const param = route.params.id;
    if (typeof param === "string" && param) return param;
    return null;
  });

  const isLoading = ref(false);
  const errorMessage = ref<string | null>(null);

  const ungroupedConversations = computed(() =>
    list.value.filter((c) => !c.isPinned && !c.knowledgeBaseId),
  );
  const pinnedConversations = computed(() => list.value.filter((c) => c.isPinned));

  function nowMs() {
    return new Date().toISOString();
  }

  function init() {
    if (isInitialized.value) return;
    isInitialized.value = true;
  }

  function upsertLocal(c: conversationApi.Conversation) {
    const idx = list.value.findIndex((x) => String(x.id) === String(c.id));
    if (idx !== -1) {
      list.value[idx] = c;
    } else {
      list.value.unshift(c);
    }
  }

  function removeLocal(id: ConversationId) {
    list.value = list.value.filter((x) => String(x.id) !== String(id));
  }

  async function fetchList() {
    init();
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const res = await conversationApi.listConversations();
      list.value = res.sort(
        (a, b) => new Date(b.updateTime || 0).getTime() - new Date(a.updateTime || 0).getTime(),
      );
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : "获取会话列表失败";
      console.error("获取会话列表失败:", err);
    } finally {
      isLoading.value = false;
    }
  }

  async function create(payload?: { knowledgeBaseId?: ConversationKnowledgeBaseId | null; title?: string; navigate?: boolean; projectId?: number | null; projectName?: string; conversationType?: conversationApi.Conversation['conversationType']; localOnly?: boolean }) {
    const shouldNavigate = payload?.navigate !== false;
    const apiPayload = {
      knowledgeBaseId: payload?.knowledgeBaseId,
      title: payload?.title,
      projectId: payload?.projectId,
      projectName: payload?.projectName,
      conversationType: payload?.conversationType ?? 'general',
    };
    const created = payload?.localOnly
      ? {
          id: Date.now(),
          title: payload.title || "新对话",
          knowledgeBaseId: payload.knowledgeBaseId ?? null,
          isPinned: false,
          messageCount: 0,
          updateTime: nowMs(),
          createTime: nowMs(),
          projectId: payload.projectId ?? null,
          projectName: payload.projectName,
          conversationType: payload.conversationType ?? "general",
        } satisfies conversationApi.Conversation
      : await conversationApi.createConversation(apiPayload);
    upsertLocal(created);
    if (shouldNavigate) await router.push(`/chat/${created.id}`);
    return created.id;
  }

  async function rename(id: ConversationId, nextTitle: string) {
    const existing = list.value.find((x) => String(x.id) === String(id));
    if (existing?.conversationType === "general") {
      await conversationApi.updateConversation(id, { title: nextTitle });
    }
    if (!existing) return;
    upsertLocal({ ...existing, title: nextTitle, updateTime: nowMs() });
  }

  function setLocalTitle(id: ConversationId, nextTitle: string) {
    const existing = list.value.find((item) => String(item.id) === String(id));
    if (!existing || !nextTitle.trim()) return;
    upsertLocal({ ...existing, title: nextTitle.trim(), updateTime: nowMs() });
  }

  async function moveToKnowledgeBase(id: ConversationId, knowledgeBaseId: ConversationKnowledgeBaseId | null) {
    const existing = list.value.find((x) => String(x.id) === String(id));
    if (!existing) return;

    if (existing.conversationType === "general") {
      await conversationApi.updateConversation(id, { knowledgeBaseId });
    }
    upsertLocal({ ...existing, knowledgeBaseId, updateTime: nowMs(), title: existing.title });
  }

  function linkLearningProject(id: ConversationId, projectId: number, projectName: string, conversationType?: conversationApi.Conversation['conversationType']) {
    const existing = list.value.find((item) => String(item.id) === String(id));
    if (!existing) return;
    if (existing.projectId === projectId && existing.projectName === projectName && (!conversationType || existing.conversationType === conversationType)) return;
    const next = { ...existing, projectId, projectName, conversationType: conversationType ?? existing.conversationType };
    upsertLocal(next);
  }

  function restoreLearningConversation(
    id: number,
    projectId: number,
    projectName: string,
    knowledgeBaseId: number | null,
    title = `${projectName} · AI 助教`,
  ) {
    if (!Number.isFinite(id) || id <= 0 || list.value.some((item) => item.id === id)) return;
    const timestamp = nowMs();
    upsertLocal({
      id,
      title,
      knowledgeBaseId,
      isPinned: false,
      messageCount: 0,
      updateTime: timestamp,
      createTime: timestamp,
      projectId,
      projectName,
      conversationType: 'learning-tutor',
    });
  }

  async function remove(id: ConversationId) {
    const existing = list.value.find((item) => String(item.id) === String(id));
    if (existing?.conversationType === "general") {
      await conversationApi.deleteConversation(id);
    }
    removeLocal(id);
    if (String(currentId.value) === String(id)) {
      await router.push("/chat");
    }
  }

  async function togglePin(id: ConversationId) {
    const existing = list.value.find((x) => String(x.id) === String(id));
    if (!existing) return;
    const isPinned = !existing.isPinned;
    existing.isPinned = isPinned;
  }

  function clearAll() {
    list.value = [];
    isInitialized.value = false;
    errorMessage.value = null;
    isLoading.value = false;
  }

  async function open(id: ConversationId) {
    await router.push(`/chat/${id}`);
  }

  async function ensureActive(createIfEmpty = true) {
    if (currentId.value) return;
    if (list.value.length > 0) {
      await router.push(`/chat/${list.value[0]?.id}`);
      return;
    }
    if (createIfEmpty) {
      await create();
    }
  }

  return {
    list,
    currentId,
    isLoading,
    errorMessage,
    ungroupedConversations,
    pinnedConversations,
    init,
    fetchList,
    create,
    rename,
    setLocalTitle,
    moveToKnowledgeBase,
    linkLearningProject,
    restoreLearningConversation,
    remove,
    togglePin,
    open,
    ensureActive,
    clearAll,
  };
});
