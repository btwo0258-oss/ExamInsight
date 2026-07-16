import { computed, ref } from "vue";
import { defineStore } from "pinia";
import * as conversationApi from "@/api/conversation";

export const useConversationStore = defineStore("conversation", () => {
  const list = ref<conversationApi.Conversation[]>([]);
  const isInitialized = ref(false);

  // currentId 从路由参数获取
  // 注意：useRoute()不能在computed中调用，需要在组件中通过route.params获取
  const currentId = ref<number | null>(null);

  function setCurrentId(id: number | null) {
    currentId.value = id;
  }

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

  async function create(payload?: {
    kbId?: number | null;
    title?: string;
    navigate?: boolean;
    learningProjectId?: number | null;
    learningProjectName?: string;
    conversationType?: conversationApi.Conversation["conversationType"];
    localOnly?: boolean;
  }) {
    const apiPayload = {
      kbId: payload?.kbId,
      title: payload?.title,
      learningProjectId: payload?.learningProjectId,
      learningProjectName: payload?.learningProjectName,
      conversationType: payload?.conversationType ?? "general",
    };
    const created = await conversationApi.createConversation(apiPayload);
    upsertLocal(created);
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

  function linkLearningProject(
    id: number,
    learningProjectId: number,
    learningProjectName: string,
    conversationType?: conversationApi.Conversation["conversationType"],
  ) {
    const existing = list.value.find((item) => item.id === id);
    if (!existing) return;
    if (
      existing.learningProjectId === learningProjectId &&
      existing.learningProjectName === learningProjectName &&
      (!conversationType || existing.conversationType === conversationType)
    )
      return;
    const next = {
      ...existing,
      learningProjectId,
      learningProjectName,
      conversationType: conversationType ?? existing.conversationType,
    };
    upsertLocal(next);
    void conversationApi
      .updateConversation(id, {
        learningProjectId,
        learningProjectName,
        conversationType: next.conversationType,
      })
      .catch((error) => {
        errorMessage.value = error instanceof Error ? error.message : "关联学习项目失败";
      });
  }

  function restoreLearningConversation(
    id: number,
    learningProjectId: number,
    learningProjectName: string,
    knowledgeBaseId: number | null,
    title = `${learningProjectName} · AI 助教`,
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
      learningProjectId,
      learningProjectName,
      conversationType: "learning-tutor",
    });
  }

  async function remove(id: number) {
    await conversationApi.deleteConversation(id);
    removeLocal(id);
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

  async function ensureActive(createIfEmpty = true) {
    if (currentId.value) return;
    if (createIfEmpty) {
      await create();
    }
  }

  async function open(kbId: number | null) {
    const newId = await create({ kbId });
    return newId;
  }

  return {
    list,
    currentId,
    setCurrentId,
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
    clearAll,
    ensureActive,
    open,
  };
});
