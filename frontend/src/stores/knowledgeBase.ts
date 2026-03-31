import { ref } from "vue";
import { defineStore } from "pinia";
import * as kbApi from "@/api/knowledgeBase";
import type { KnowledgeBase } from "@/api/knowledgeBase";
import { USER_KEY } from "@/api/request";

export const useKnowledgeBaseStore = defineStore("knowledgeBase", () => {
  const list = ref<KnowledgeBase[]>([]);
  const current = ref<KnowledgeBase | null>(null);
  const editingKnowledgeBase = ref<KnowledgeBase | null>(null);
  const isInitialized = ref(false);

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

  function getStorageKey(): string {
    return `llm.knowledgeBases.${getUserPrefix()}`;
  }

  function init() {
    if (isInitialized.value) return;
    const stored = sessionStorage.getItem(getStorageKey());
    if (stored) {
      try {
        list.value = JSON.parse(stored) as KnowledgeBase[];
      } catch {
        list.value = [];
      }
    }
    isInitialized.value = true;
  }

  function saveToStorage() {
    sessionStorage.setItem(getStorageKey(), JSON.stringify(list.value));
  }

  async function fetchAll() {
    init();
    try {
      list.value = await kbApi.getKnowledgeBases();
      saveToStorage();
    } catch (error) {
      // 如果未登录或其他错误，保持列表为空
      console.error("获取知识库列表失败:", error);
    }
  }

  async function fetchList() {
    init();
    try {
      list.value = await kbApi.getKnowledgeBases();
      saveToStorage();
    } catch (error) {
      // 如果未登录或其他错误，保持列表为空
      console.error("获取知识库列表失败:", error);
    }
  }

  async function getDetail(id: number) {
    current.value = await kbApi.getKnowledgeBase(id);
  }

  async function create(data: Partial<KnowledgeBase>) {
    const item = await kbApi.createKnowledgeBase(data);
    list.value.unshift(item);
    saveToStorage();
    return item;
  }

  async function update(data: KnowledgeBase) {
    const item = await kbApi.updateKnowledgeBase(data);
    const index = list.value.findIndex((x) => x.id === data.id);
    if (index !== -1) {
      list.value[index] = item;
    }
    saveToStorage();
    return item;
  }

  async function remove(id: number) {
    await kbApi.deleteKnowledgeBase(id);
    list.value = list.value.filter((x) => x.id !== id);
    saveToStorage();
  }

  function setEditingKnowledgeBase(kb: KnowledgeBase | null) {
    editingKnowledgeBase.value = kb;
  }

  function clearAll() {
    list.value = [];
    current.value = null;
    editingKnowledgeBase.value = null;
    isInitialized.value = false;
    sessionStorage.removeItem(getStorageKey());
  }

  return {
    list,
    current,
    editingKnowledgeBase,
    fetchAll,
    fetchList,
    getDetail,
    create,
    update,
    remove,
    setEditingKnowledgeBase,
    clearAll,
  };
});
