import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { useRoute, useRouter } from "vue-router";
import * as conversationApi from "@/api/conversation";

export const useConversationStore = defineStore("conversation", () => {
  const router = useRouter();
  const route = useRoute();

  const list = ref<conversationApi.Conversation[]>([]);
  const isInitialized = ref(false);

  const currentId = computed<number | null>(() => {
    const param = route.params.id;
    if (typeof param === "string" && param) return Number(param);
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
    const idx = list.value.findIndex((x) => x.id === c.id);
    if (idx !== -1) {
      list.value[idx] = c;
    } else {
      list.value.unshift(c);
    }
  }

  function removeLocal(id: number) {
    list.value = list.value.filter((x) => x.id !== id);
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

  async function create(payload?: { knowledgeBaseId?: number | null; title?: string; navigate?: boolean; projectId?: number | null; projectName?: string; conversationType?: conversationApi.Conversation['conversationType']; localOnly?: boolean }) {
    const shouldNavigate = payload?.navigate !== false;
    const apiPayload = {
      knowledgeBaseId: payload?.knowledgeBaseId,
      title: payload?.title,
      projectId: payload?.projectId,
      projectName: payload?.projectName,
      conversationType: payload?.conversationType ?? 'general',
    };
    const created = await conversationApi.createConversation(apiPayload);
    upsertLocal(created);
    if (shouldNavigate) await router.push(`/chat/${created.id}`);
    return created.id;
  }

  async function rename(id: number, nextTitle: string) {
    await conversationApi.updateConversation(id, { title: nextTitle });
    const existing = list.value.find((x) => x.id === id);
    if (!existing) return;
    upsertLocal({ ...existing, title: nextTitle, updateTime: nowMs() });
  }

  async function moveToKnowledgeBase(id: number, knowledgeBaseId: number | null) {
    const existing = list.value.find((x) => x.id === id);
    if (!existing) return;

    await conversationApi.updateConversation(id, { knowledgeBaseId });
    upsertLocal({ ...existing, knowledgeBaseId, updateTime: nowMs(), title: existing.title });
  }

  function linkLearningProject(id: number, projectId: number, projectName: string, conversationType?: conversationApi.Conversation['conversationType']) {
    const existing = list.value.find((item) => item.id === id);
    if (!existing) return;
    if (existing.projectId === projectId && existing.projectName === projectName && (!conversationType || existing.conversationType === conversationType)) return;
    const next = { ...existing, projectId, projectName, conversationType: conversationType ?? existing.conversationType };
    upsertLocal(next);
    void conversationApi.updateConversation(id, { projectId, projectName, conversationType: next.conversationType })
      .catch((error) => {
        errorMessage.value = error instanceof Error ? error.message : '关联学习项目失败';
      });
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

  async function remove(id: number) {
    await conversationApi.deleteConversation(id);
    removeLocal(id);
    if (currentId.value === id) {
      await router.push("/chat");
    }
  }

  async function togglePin(id: number) {
    const existing = list.value.find((x) => x.id === id);
    if (!existing) return;
    const isPinned = !existing.isPinned;
    await conversationApi.updateConversation(id, { isPinned });
    existing.isPinned = isPinned;
  }

  function clearAll() {
    list.value = [];
    isInitialized.value = false;
    errorMessage.value = null;
    isLoading.value = false;
  }

  async function open(id: number) {
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
