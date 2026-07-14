import { ref } from "vue";
import { defineStore } from "pinia";
import type { KnowledgeBase } from "@/api/knowledgeBase";
import { USER_KEY } from "@/api/request";
import { courseLibraries } from "@/mock";

export const useKnowledgeBaseStore = defineStore("knowledgeBase", () => {
  const SHARED_LIBRARY_KEY = "examinsight.library.catalog";
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
      } catch { }
    }
    return "guest";
  }

  function getStorageKey(): string {
    return `llm.knowledgeBases.${getUserPrefix()}`;
  }

  function defaultKnowledgeBases(): KnowledgeBase[] {
    return courseLibraries.map((library) => ({
      id: library.id,
      name: library.name,
      description: library.description,
      icon: "folder",
      color: "#71717a",
      documentCount: library.fileCount,
      mindMapCount: 0,
      createTime: new Date().toISOString(),
      updateTime: library.updatedAt,
    }));
  }

  function mergeKnowledgeBases(items: KnowledgeBase[]) {
    const merged = items.filter((item, index) => items.findIndex((candidate) => candidate.id === item.id) === index);
    for (const fallback of defaultKnowledgeBases()) {
      if (!merged.some((item) => item.id === fallback.id)) merged.push(fallback);
    }
    return merged;
  }

  function readSharedCatalog(): KnowledgeBase[] {
    try {
      return JSON.parse(localStorage.getItem(SHARED_LIBRARY_KEY) || "[]") as KnowledgeBase[];
    } catch {
      return [];
    }
  }

  function init() {
    if (isInitialized.value) return;
    const stored = sessionStorage.getItem(getStorageKey());
    if (stored) {
      try {
        list.value = mergeKnowledgeBases([...(JSON.parse(stored) as KnowledgeBase[]), ...readSharedCatalog()]);
      } catch {
        list.value = defaultKnowledgeBases();
      }
    } else {
      list.value = mergeKnowledgeBases(readSharedCatalog());
    }
    isInitialized.value = true;
  }

  function saveToStorage() {
    sessionStorage.setItem(getStorageKey(), JSON.stringify(list.value));
    localStorage.setItem(SHARED_LIBRARY_KEY, JSON.stringify(list.value));
  }

  async function fetchAll() {
    init();
    // 当前阶段使用纯前端 Mock，后续在此处替换为真实列表接口。
    list.value = mergeKnowledgeBases([...list.value, ...readSharedCatalog()]);
    saveToStorage();
  }

  async function fetchList() {
    init();
    // 当前阶段使用纯前端 Mock，后续在此处替换为真实列表接口。
    list.value = mergeKnowledgeBases([...list.value, ...readSharedCatalog()]);
    saveToStorage();
  }

  async function getDetail(id: number) {
    init();
    current.value = list.value.find((item) => item.id === id) || null;
    if (!current.value) throw new Error("知识库不存在");
  }

  async function create(data: Partial<KnowledgeBase>) {
    init();
    // 纯前端 Mock 创建：立即写入共享资料库，不发起网络请求。
    const now = new Date().toISOString();
    const item: KnowledgeBase = {
      id: Date.now(),
      name: data.name || "未命名知识库",
      description: data.description,
      icon: data.icon || "folder",
      color: data.color || "#71717a",
      documentCount: 0,
      mindMapCount: 0,
      createTime: now,
      updateTime: now,
    };
    list.value = list.value.filter((existing) => existing.id !== item.id);
    list.value.unshift(item);
    saveToStorage();
    return item;
  }

  async function update(data: KnowledgeBase) {
    init();
    const item = { ...data, updateTime: new Date().toISOString() };
    const index = list.value.findIndex((x) => x.id === data.id);
    if (index !== -1) {
      list.value[index] = item;
    }
    saveToStorage();
    return item;
  }

  async function remove(id: number) {
    init();
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
