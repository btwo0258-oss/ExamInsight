import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { useRoute, useRouter } from "vue-router";
import * as conversationApi from "@/api/conversation";
import { USER_KEY } from "@/api/request";

export const useConversationStore = defineStore("conversation", () => {
  const router = useRouter();
  const route = useRoute();

  const list = ref<conversationApi.Conversation[]>([]);
  const isInitialized = ref(false);

  function getUserPrefix(): string {
    const userStr = sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY);
    if (userStr) {
      try {
        const user = JSON.parse(userStr);
        if (user && user.id) return String(user.id);
      } catch { }
    }
    return "guest";
  }

  function getStorageKey(): string {
    return `llm.conversations.${getUserPrefix()}`;
  }

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
    const stored = sessionStorage.getItem(getStorageKey());
    if (stored) {
      try {
        list.value = JSON.parse(stored) as conversationApi.Conversation[];
      } catch {
        list.value = [];
      }
    } else {
      list.value = [];
    }
    isInitialized.value = true;
  }

  function saveToStorage() {
    sessionStorage.setItem(getStorageKey(), JSON.stringify(list.value));
  }

  function upsertLocal(c: conversationApi.Conversation) {
    const idx = list.value.findIndex((x) => x.id === c.id);
    if (idx !== -1) {
      list.value[idx] = c;
    } else {
      list.value.unshift(c);
    }
    saveToStorage();
  }

  function removeLocal(id: number) {
    list.value = list.value.filter((x) => x.id !== id);
    saveToStorage();
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
      saveToStorage();
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : "获取会话列表失败";
      // 如果未登录或其他错误，保持列表为空
      list.value = [];
      saveToStorage();
      console.error("获取会话列表失败:", err);
    } finally {
      isLoading.value = false;
    }
  }

  async function create(payload?: { kbId?: number | null; title?: string }) {
    const temp: conversationApi.Conversation = {
      id: Date.now(),
      title: payload?.title || "新对话",
      knowledgeBaseId: payload?.kbId || null,
      isPinned: false,
      messageCount: 0,
      updateTime: nowMs(),
      createTime: nowMs(),
    };
    upsertLocal(temp);

    try {
      const real = await conversationApi.createConversation(payload);
      removeLocal(temp.id);
      upsertLocal(real);
      await router.push(`/chat/${real.id}`);
    } catch {
      await router.push(`/chat/${temp.id}`);
    }
  }

  async function rename(id: number, nextTitle: string) {
    try {
      await conversationApi.updateConversation(id, { title: nextTitle });
    } catch { }
    const existing = list.value.find((x) => x.id === id);
    if (!existing) return;
    upsertLocal({ ...existing, title: nextTitle, updateTime: nowMs() });
  }

  async function moveToKnowledgeBase(id: number, knowledgeBaseId: number | null) {
    const existing = list.value.find((x) => x.id === id);
    if (!existing) return;

    try {
      await conversationApi.updateConversation(id, { knowledgeBaseId });
      upsertLocal({ ...existing, knowledgeBaseId, updateTime: nowMs(), title: existing.title });
    } catch {
      upsertLocal({ ...existing, knowledgeBaseId, updateTime: nowMs() });
    }
  }

  async function remove(id: number) {
    try {
      await conversationApi.deleteConversation(id);
    } catch { }
    removeLocal(id);
    if (currentId.value === id) {
      await router.push("/chat");
    }
  }

  async function togglePin(id: number) {
    const existing = list.value.find((x) => x.id === id);
    if (!existing) return;
    const isPinned = !existing.isPinned;
    try {
      await conversationApi.updateConversation(id, { isPinned });
    } catch { }
    existing.isPinned = isPinned;
  }

  function clearAll() {
    list.value = [];
    isInitialized.value = false;
    errorMessage.value = null;
    isLoading.value = false;
    sessionStorage.removeItem(getStorageKey());
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
    fetchList,
    create,
    rename,
    moveToKnowledgeBase,
    remove,
    togglePin,
    open,
    ensureActive,
    clearAll,
  };
});
