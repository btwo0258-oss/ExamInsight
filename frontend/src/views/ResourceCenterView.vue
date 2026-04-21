<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <div class="resource-container">
        <div class="header">
          <h1 class="title">资料中心</h1>
          <p class="subtitle">海量真题与学习资料，一键下载或添加到知识库</p>
        </div>

        <div class="categories">
          <button
            v-for="cat in categories"
            :key="cat.key"
            class="category-btn"
            :class="{ 'category-btn--active': activeCategory === cat.key }"
            @click="activeCategory = cat.key"
          >
            {{ cat.label }}
          </button>
        </div>

        <div v-if="loading" class="loading-state">
          <div class="loading-spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="resourceList.length > 0" class="resource-list">
          <div v-for="year in availableYears" :key="year" class="year-group">
            <h2 class="year-title">{{ year }}</h2>
            <div class="papers">
              <div v-for="paper in getResourcesByYear(year)" :key="paper.id" class="paper-item">
                <div class="paper-info">
                  <AppIcon
                    :name="getFileIcon(paper.fileType)"
                    :size="24"
                    color="var(--color-primary)"
                  />
                  <div class="paper-text">
                    <span class="paper-name">{{ paper.title }}</span>
                    <span class="paper-meta" v-if="paper.fileSize"
                      >{{ formatFileSize(paper.fileSize) }} · 下载{{ paper.downloadCount }}次</span
                    >
                  </div>
                </div>
                <div class="paper-actions">
                  <button
                    class="action-btn add-btn"
                    @click="handleAddToKb(paper)"
                    :title="isAdded(paper.id) ? '移动到知识库' : '添加到知识库'"
                  >
                    <AppIcon :name="isAdded(paper.id) ? 'move' : 'plus'" :size="16" />
                    <span>{{ isAdded(paper.id) ? "移动到知识库" : "加入知识库" }}</span>
                  </button>
                  <button class="action-btn download-btn" @click="handleDownload(paper)">
                    <AppIcon name="download" :size="16" />
                    <span>下载</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <AppIcon name="folder" :size="64" color="var(--color-text-muted)" />
          <h3>暂无资料</h3>
          <p>该分类下暂无资料，请查看其他分类</p>
        </div>
      </div>
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
    </div>

    <AppModal :open="showKbModal" @close="showKbModal = false">
      <div class="kb-modal">
        <h3>添加到知识库</h3>
        <p class="kb-modal-desc">选择要添加到的知识库：</p>
        <div class="kb-list">
          <div
            v-for="kb in knowledgeBases"
            :key="kb.id"
            class="kb-item"
            :class="{ 'kb-item--selected': selectedKbId === kb.id }"
            @click="selectedKbId = kb.id"
          >
            <AppIcon name="book" :size="20" :color="kb.color || 'var(--color-primary)'" />
            <span>{{ kb.name }}</span>
          </div>
        </div>
        <div class="kb-modal-actions">
          <AppButton variant="secondary" @click="showKbModal = false">取消</AppButton>
          <AppButton variant="primary" @click="confirmAddToKb" :disabled="!selectedKbId"
            >确认添加</AppButton
          >
        </div>
      </div>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from "vue";
import TheSidebar from "@/components/sidebar/TheSidebar.vue";
import AppIcon from "@/components/common/AppIcon.vue";
import AppButton from "@/components/common/AppButton.vue";
import AppModal from "@/components/common/AppModal.vue";
import { useAppState } from "@/stores/appState";
import { useAuthStore } from "@/stores/auth";
import {
  getResourceList,
  addToKb,
  moveToKb,
  getMyResources,
  downloadResource,
  type ResourceItem,
} from "@/api/resource";
import { getKnowledgeBases, type KnowledgeBase } from "@/api/knowledgeBase";

const appState = useAppState();
const authStore = useAuthStore();
const sidebarOpen = ref(true);

const categories = [
  { key: "英语四六级", label: "英语四六级" },
  { key: "研究生考试", label: "研究生考试" },
  { key: "公务员考试", label: "公务员考试" },
  { key: "英语专四专八", label: "英语专四专八" },
  { key: "教师资格证", label: "教师资格证" },
  { key: "计算机二级", label: "计算机二级" },
  { key: "普通话等级考试", label: "普通话等级考试" },
];
const activeCategory = ref("计算机二级");

const loading = ref(false);
const resourceList = ref<ResourceItem[]>([]);
const addedResourceIds = ref<Set<number>>(new Set());
const knowledgeBases = ref<KnowledgeBase[]>([]);

const showKbModal = ref(false);
const selectedResource = ref<ResourceItem | null>(null);
const selectedKbId = ref<number | null>(null);

const availableYears = computed(() => {
  const years = new Set(resourceList.value.map((r) => r.year));
  return Array.from(years).sort((a, b) => b - a);
});

function getResourcesByYear(year: number) {
  return resourceList.value.filter((r) => r.year === year);
}

function isAdded(id: number) {
  return addedResourceIds.value.has(id);
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

function getFileIcon(fileType: string): string {
  switch (fileType?.toLowerCase()) {
    case "pdf":
      return "file-pdf";
    case "docx":
    case "doc":
      return "file-word";
    case "txt":
      return "file-text";
    default:
      return "file";
  }
}

async function fetchResources() {
  loading.value = true;
  try {
    const data = await getResourceList({ category: activeCategory.value });
    resourceList.value = data;
  } catch (error) {
    console.error("Failed to fetch resources:", error);
    const currentYear = new Date().getFullYear();
    resourceList.value = Array.from({ length: 5 }, (_, i) => currentYear - i).flatMap((year) =>
      generateMockData(year),
    );
  } finally {
    loading.value = false;
  }
}

function generateMockData(year: number): ResourceItem[] {
  if (activeCategory.value === "计算机二级") {
    return [
      {
        id: parseInt(`${year}01`),
        title: `${year}年计算机二级C语言程序设计真题及答案`,
        category: "计算机二级",
        year,
        fileName: `${year}计算机二级C语言真题.pdf`,
        fileType: "pdf",
        fileSize: 1024000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
      {
        id: parseInt(`${year}02`),
        title: `${year}年计算机二级Python程序设计真题及答案`,
        category: "计算机二级",
        year,
        fileName: `${year}计算机二级Python真题.pdf`,
        fileType: "pdf",
        fileSize: 980000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
      {
        id: parseInt(`${year}03`),
        title: `${year}年计算机二级Java程序设计真题及答案`,
        category: "计算机二级",
        year,
        fileName: `${year}计算机二级Java真题.pdf`,
        fileType: "pdf",
        fileSize: 850000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
      {
        id: parseInt(`${year}04`),
        title: `${year}年计算机二级MS Office高级应用真题及答案`,
        category: "计算机二级",
        year,
        fileName: `${year}计算机二级Office真题.pdf`,
        fileType: "pdf",
        fileSize: 780000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
    ];
  } else if (activeCategory.value === "普通话等级考试") {
    return [
      {
        id: parseInt(`${year}11`),
        title: `${year}年普通话水平测试真题（甲卷）`,
        category: "普通话等级考试",
        year,
        fileName: `${year}普通话真题甲卷.pdf`,
        fileType: "pdf",
        fileSize: 1200000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
      {
        id: parseInt(`${year}12`),
        title: `${year}年普通话水平测试真题（乙卷）`,
        category: "普通话等级考试",
        year,
        fileName: `${year}普通话真题乙卷.pdf`,
        fileType: "pdf",
        fileSize: 1150000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
      {
        id: parseInt(`${year}13`),
        title: `${year}年普通话水平测试模拟试卷及答案解析`,
        category: "普通话等级考试",
        year,
        fileName: `${year}普通话模拟试卷.pdf`,
        fileType: "pdf",
        fileSize: 1300000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
    ];
  } else if (activeCategory.value === "英语四六级") {
    return [
      {
        id: parseInt(`${year}11`),
        title: `${year}年12月英语四级真题及答案解析`,
        category: "英语四六级",
        year,
        fileName: `${year}12月四级真题.pdf`,
        fileType: "pdf",
        fileSize: 1200000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
      {
        id: parseInt(`${year}12`),
        title: `${year}年6月英语四级真题及答案解析`,
        category: "英语四六级",
        year,
        fileName: `${year}6月四级真题.pdf`,
        fileType: "pdf",
        fileSize: 1150000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
      {
        id: parseInt(`${year}13`),
        title: `${year}年12月英语六级真题及答案解析`,
        category: "英语四六级",
        year,
        fileName: `${year}12月六级真题.pdf`,
        fileType: "pdf",
        fileSize: 1300000,
        filePath: "",
        description: "",
        downloadCount: 0,
        status: 0,
        createTime: "",
        updateTime: "",
      },
    ];
  }
  return [
    {
      id: parseInt(`${year}21`),
      title: `${year}年${activeCategory.value}历年真题（一）`,
      category: activeCategory.value,
      year,
      fileName: `${year}真题一.pdf`,
      fileType: "pdf",
      fileSize: 900000,
      filePath: "",
      description: "",
      downloadCount: 0,
      status: 0,
      createTime: "",
      updateTime: "",
    },
    {
      id: parseInt(`${year}22`),
      title: `${year}年${activeCategory.value}历年真题（二）`,
      category: activeCategory.value,
      year,
      fileName: `${year}真题二.pdf`,
      fileType: "pdf",
      fileSize: 880000,
      filePath: "",
      description: "",
      downloadCount: 0,
      status: 0,
      createTime: "",
      updateTime: "",
    },
  ];
}

async function fetchMyResources() {
  try {
    const data = await getMyResources();
    addedResourceIds.value = new Set(data.map((r) => r.resourceId));
  } catch (error) {
    console.error("Failed to fetch my resources:", error);
  }
}

async function fetchKnowledgeBases() {
  try {
    const data = await getKnowledgeBases();
    knowledgeBases.value = data;
  } catch (error) {
    console.error("Failed to fetch knowledge bases:", error);
  }
}

function handleDownload(paper: ResourceItem) {
  if (paper.id > 100000) {
    alert(`开始下载：${paper.title}`);
    return;
  }
  downloadResource(paper.id);
}

function handleAddToKb(paper: ResourceItem) {
  if (!authStore.isAuthed) {
    authStore.openAuthModal();
    return;
  }
  if (knowledgeBases.value.length === 0) {
    alert("请先创建知识库");
    return;
  }
  selectedResource.value = paper;
  selectedKbId.value = null;
  showKbModal.value = true;
}

async function confirmAddToKb() {
  if (!selectedResource.value || !selectedKbId.value) return;
  try {
    if (isAdded(selectedResource.value.id)) {
      await moveToKb(selectedResource.value.id, selectedKbId.value);
    } else {
      await addToKb(selectedResource.value.id, selectedKbId.value);
      addedResourceIds.value.add(selectedResource.value.id);
    }
    showKbModal.value = false;
  } catch (error: any) {
    alert(
      error?.response?.data?.message ||
        (isAdded(selectedResource.value.id) ? "移动知识库失败" : "添加到知识库失败"),
    );
  }
}

watch(activeCategory, () => {
  fetchResources();
});

onMounted(async () => {
  const raw = localStorage.getItem("llm.sidebar.open");
  if (raw === "0") sidebarOpen.value = false;
  appState.setMode("resource");

  await Promise.all([fetchResources(), fetchMyResources(), fetchKnowledgeBases()]);
});

watch(sidebarOpen, (open) => {
  localStorage.setItem("llm.sidebar.open", open ? "1" : "0");
});
</script>

<style scoped>
.layout {
  height: 100vh;
  position: relative;
  display: flex;
  transition: padding-left 180ms ease;
  padding-left: 0;
  background-color: var(--color-bg);
}

.layout--open {
  padding-left: var(--sidebar-width);
}

.drawer {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: var(--sidebar-width);
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  transform: translateX(-100%);
  transition: transform 180ms ease;
  z-index: 30;
}

.drawer--open {
  transform: translateX(0);
}

.content {
  flex: 1;
  height: 100vh;
  display: flex;
  justify-content: center;
  min-width: 0;
  overflow-y: auto;
}

.resource-container {
  width: 100%;
  max-width: 1000px;
  padding: 40px 32px;
  display: flex;
  flex-direction: column;
}

.header {
  margin-bottom: 32px;
}

.title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 8px 0;
}

.subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.categories {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 32px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.category-btn {
  background: transparent;
  border: none;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-muted);
  padding: 8px 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.category-btn:hover {
  color: var(--color-text);
}

.category-btn--active {
  color: var(--color-primary);
}

.category-btn--active::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60%;
  height: 2px;
  background-color: var(--color-primary);
  border-radius: 2px;
}

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.year-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.year-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.papers {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--color-border);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.paper-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: var(--color-surface);
  transition: background-color 0.2s ease;
}

.paper-item:hover {
  background: var(--color-surface-hover);
}

.paper-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.paper-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.paper-name {
  font-size: 15px;
  color: var(--color-text);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.paper-meta {
  font-size: 12px;
  color: var(--color-text-muted);
}

.paper-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 500;
  padding: 6px 14px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-btn {
  color: var(--color-primary);
}

.add-btn:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.2);
}

.download-btn {
  color: var(--color-text-muted);
}

.download-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  border-color: var(--color-border);
}

:root[data-theme="dark"] .download-btn:hover {
  background: rgba(255, 255, 255, 0.06);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px;
  color: var(--color-text-muted);
  gap: 16px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px;
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state h3 {
  margin-top: 16px;
  font-size: 18px;
  color: var(--color-text);
}

.kb-modal {
  padding: 8px;
}

.kb-modal h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--color-text);
}

.kb-modal-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0 0 20px 0;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 24px;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  color: var(--color-text);
}

.kb-item:hover {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.05);
}

.kb-item--selected {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.1);
}

.kb-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.mini {
  position: fixed;
  top: 12px;
  left: 12px;
  display: inline-flex;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  z-index: 25;
}

.mini__btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  display: grid;
  place-items: center;
}

.mini__btn:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--color-text);
}

:root[data-theme="dark"] .mini__btn:hover {
  background: rgba(255, 255, 255, 0.06);
}
</style>
