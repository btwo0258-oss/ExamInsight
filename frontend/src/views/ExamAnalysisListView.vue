<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <div class="exam-list-container">
        <div class="header">
          <div class="header__info">
            <h1 class="title">考试分析</h1>
            <p class="subtitle">管理您的试卷分析记录与复习建议</p>
          </div>
          <div class="actions">
            <div class="search-expand" v-if="showSearch">
              <input v-model="searchQuery" placeholder="搜索试卷分析..." autofocus />
            </div>
            <div class="action-pill">
              <button class="pill-btn" @click="toggleSearch">
                <AppIcon name="search" :size="20" color="var(--color-primary)" />
              </button>

              <div class="dropdown-wrapper">
                <button class="pill-btn" @click="showSortMenu = !showSortMenu">
                  <AppIcon
                    :name="viewMode === 'grid' ? 'grid' : 'list'"
                    :size="20"
                    color="var(--color-primary)"
                  />
                </button>
                <div class="overlay" v-if="showSortMenu" @click="showSortMenu = false"></div>
                <div class="dropdown-menu" v-if="showSortMenu">
                  <div
                    class="dropdown-item"
                    @click="
                      viewMode = 'grid';
                      showSortMenu = false;
                    "
                  >
                    <AppIcon name="grid" :size="16" />
                    <span class="dropdown-text">图标</span>
                    <AppIcon
                      v-if="viewMode === 'grid'"
                      name="check"
                      :size="16"
                      color="var(--color-primary)"
                    />
                  </div>
                  <div
                    class="dropdown-item"
                    @click="
                      viewMode = 'list';
                      showSortMenu = false;
                    "
                  >
                    <AppIcon name="list" :size="16" />
                    <span class="dropdown-text">列表</span>
                    <AppIcon
                      v-if="viewMode === 'list'"
                      name="check"
                      :size="16"
                      color="var(--color-primary)"
                    />
                  </div>
                  <div class="dropdown-divider"></div>
                  <div
                    class="dropdown-item"
                    @click="
                      sortMode = 'time';
                      showSortMenu = false;
                    "
                  >
                    <AppIcon name="clock" :size="16" />
                    <span class="dropdown-text">按创建时间排序</span>
                    <AppIcon
                      v-if="sortMode === 'time'"
                      name="check"
                      :size="16"
                      color="var(--color-primary)"
                    />
                  </div>
                  <div
                    class="dropdown-item"
                    @click="
                      sortMode = 'name';
                      showSortMenu = false;
                    "
                  >
                    <AppIcon name="user" :size="16" />
                    <span class="dropdown-text">按名称排序</span>
                    <AppIcon
                      v-if="sortMode === 'name'"
                      name="check"
                      :size="16"
                      color="var(--color-primary)"
                    />
                  </div>
                </div>
              </div>

              <button class="pill-btn" @click="handleCreate">
                <AppIcon name="plus" :size="20" color="var(--color-primary)" />
              </button>
            </div>
          </div>
        </div>

        <div class="list" :class="`list--${viewMode}`" v-if="filteredList.length > 0">
          <div class="new-card" @click="handleCreate">
            <div class="new-card__icon">
              <div class="plus-circle">
                <AppIcon name="plus" :size="24" class="plus-icon" />
              </div>
            </div>
            <span class="new-card__text">新建分析</span>
          </div>

          <div
            class="list-item"
            v-for="item in filteredList"
            :key="item.id"
            :class="{ 'list-item--active': activeMenuId === item.id }"
            @click="router.push(`/exam-analysis/${item.id}`)"
          >
            <div class="item-icon">
              <AppIcon name="pie-chart" :size="20" color="var(--color-text)" />
            </div>
            <div class="item-main">
              <div class="item-title" :title="item.title">{{ item.title }}</div>
              <div class="item-desc">试卷深度分析与考点提取</div>
            </div>
            <div class="item-meta">
              <span>考试类型：{{ item.type }}</span>
              <span
                >创建时间：{{ new Date(item.date).getFullYear() }}-{{
                  String(new Date(item.date).getMonth() + 1).padStart(2, "0")
                }}-{{ String(new Date(item.date).getDate()).padStart(2, "0") }}</span
              >
            </div>
            <div class="item-actions">
              <div class="actions-wrapper">
                <button
                  class="pill-btn dots-btn"
                  @click.stop="activeMenuId = activeMenuId === item.id ? null : item.id"
                >
                  <AppIcon name="more-horizontal" :size="20" />
                </button>
                <div
                  class="overlay"
                  v-if="activeMenuId === item.id"
                  @click.stop="activeMenuId = null"
                ></div>
                <div class="actions-menu" v-if="activeMenuId === item.id">
                  <div
                    class="menu-item"
                    @click.stop="
                      handleEdit(item);
                      activeMenuId = null;
                    "
                  >
                    <AppIcon name="edit" :size="16" />
                    <span>重命名</span>
                  </div>
                  <div
                    class="menu-item danger"
                    @click.stop="
                      handleDelete(item.id);
                      activeMenuId = null;
                    "
                  >
                    <AppIcon name="trash" :size="16" />
                    <span>删除记录</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <AppIcon name="pie-chart" :size="64" color="var(--color-text-muted)" />
          <h3>暂无试卷分析</h3>
          <p>点击“新建分析”获取您的第一份复习建议</p>
        </div>
      </div>
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
    </div>
  </div>

  <PromptModal
    :open="showRenamePrompt"
    title="重命名分析记录"
    :default-value="renamingTitle"
    label="名称"
    placeholder="请输入分析记录名称"
    @close="showRenamePrompt = false"
    @confirm="handleRenameConfirm"
  />

  <ConfirmDialog
    :open="showDeleteConfirm"
    title="确认删除"
    :message="`确定要删除分析记录'${deletingTitle}'吗？`"
    confirm-text="删除"
    confirm-variant="danger"
    @close="showDeleteConfirm = false"
    @confirm="confirmDelete"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from "vue";
import { useRouter } from "vue-router";
import TheSidebar from "@/components/sidebar/TheSidebar.vue";
import AppIcon from "@/components/common/AppIcon.vue";
import AppButton from "@/components/common/AppButton.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import PromptModal from "@/components/common/PromptModal.vue";
import { useAppState } from "@/stores/appState";
import { useExamAnalysisStore } from "@/stores/examAnalysis";

const router = useRouter();
const appState = useAppState();
const examStore = useExamAnalysisStore();
const sidebarOpen = ref(true);

const showSearch = ref(false);
const searchQuery = ref("");

const activeMenuId = ref<number | null>(null);

const showSortMenu = ref(false);
const viewMode = ref<"grid" | "list">(
  (localStorage.getItem("llm.examAnalysis.viewMode") as any) || "grid",
);
const sortMode = ref<"time" | "name">(
  (localStorage.getItem("llm.examAnalysis.sortMode") as any) || "time",
);

const showDeleteConfirm = ref(false);
const deletingId = ref<number | null>(null);
const deletingTitle = ref("");

const showRenamePrompt = ref(false);
const renamingId = ref<number | null>(null);
const renamingTitle = ref("");

watch(viewMode, (val) => localStorage.setItem("llm.examAnalysis.viewMode", val));
watch(sortMode, (val) => localStorage.setItem("llm.examAnalysis.sortMode", val));

const filteredList = computed(() => {
  let result = [...examStore.list];
  if (searchQuery.value) {
    result = result.filter((item) =>
      item.title.toLowerCase().includes(searchQuery.value.toLowerCase()),
    );
  }
  result.sort((a, b) => {
    if (sortMode.value === "time") {
      return new Date(b.date).getTime() - new Date(a.date).getTime();
    } else {
      return a.title.localeCompare(b.title);
    }
  });
  return result;
});

function toggleSearch() {
  showSearch.value = !showSearch.value;
  if (!showSearch.value) searchQuery.value = "";
}

function handleCreate() {
  router.push("/exam-analysis/new");
}

onMounted(async () => {
  const raw = localStorage.getItem("llm.sidebar.open");
  if (raw === "0") sidebarOpen.value = false;
  appState.setMode("exam-analysis" as any);
  await examStore.fetchList();
});

watch(sidebarOpen, (open) => {
  localStorage.setItem("llm.sidebar.open", open ? "1" : "0");
});

function handleEdit(item: any) {
  renamingId.value = item.id;
  renamingTitle.value = item.title;
  showRenamePrompt.value = true;
}

function handleRenameConfirm(value: string) {
  if (value && value.trim() && renamingId.value !== null) {
    examStore.rename(renamingId.value, value.trim());
  }
  showRenamePrompt.value = false;
  renamingId.value = null;
}

function handleDelete(id: number) {
  const item = examStore.list.find((i) => i.id === id);
  deletingId.value = id;
  deletingTitle.value = item?.title || "";
  showDeleteConfirm.value = true;
}

function confirmDelete() {
  if (deletingId.value !== null) {
    examStore.remove(deletingId.value);
    showDeleteConfirm.value = false;
    deletingId.value = null;
  }
}
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

.exam-list-container {
  width: 100%;
  max-width: 1200px;
  padding: 40px 32px;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}

.header__info {
  display: flex;
  flex-direction: column;
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

.actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-expand {
  animation: slideIn 0.2s ease;
}

@keyframes slideIn {
  from {
    width: 0;
    opacity: 0;
  }
  to {
    width: 200px;
    opacity: 1;
  }
}

.search-expand input {
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-size: 14px;
  width: 200px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: none;
}

.search-expand input:focus {
  border-color: var(--color-primary);
}

.action-pill {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px;
  box-shadow: var(--shadow-sm);
}

.pill-btn {
  background: transparent;
  border: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.pill-btn:hover {
  background: rgba(59, 130, 246, 0.1);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 0;
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state h3 {
  margin-top: 20px;
  font-size: 20px;
  color: var(--color-text);
}

.dropdown-wrapper {
  position: relative;
}
.overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
}
.dropdown-menu {
  position: absolute;
  top: 120%;
  right: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  padding: 8px;
  min-width: 180px;
  z-index: 50;
  display: flex;
  flex-direction: column;
}
.dropdown-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  color: var(--color-text);
  font-size: 14px;
}
.dropdown-item:hover {
  background: var(--color-surface-hover);
}
.dropdown-text {
  flex: 1;
}
.dropdown-divider {
  height: 1px;
  background: var(--color-border);
  margin: 4px 0;
}

.list {
  transition: all 0.3s ease;
}

.list--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.list--list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.list--list .new-card,
.list--list .list-item {
  flex-direction: row;
  align-items: center;
  min-height: auto;
  padding: 16px 24px;
}

.list--list .new-card__icon,
.list--list .item-icon {
  margin-bottom: 0;
  margin-right: 16px;
}

.list--list .item-meta {
  flex-direction: row;
  align-items: center;
  flex: 1;
  justify-content: flex-end;
  padding-right: 32px;
}

.list--list .item-actions {
  opacity: 1;
}

.list--list .item-title {
  margin-bottom: 4px;
}

.new-card {
  background: rgba(59, 130, 246, 0.05);
  border: 1px dashed rgba(59, 130, 246, 0.3);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 160px;
}

.new-card:hover {
  background: rgba(59, 130, 246, 0.1);
  border-style: solid;
  border-color: var(--color-primary);
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
}

.new-card__icon {
  margin-bottom: 16px;
}

.plus-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.plus-icon {
  color: #fff;
}

:root[data-theme="dark"] .plus-icon {
  color: #000;
}

.new-card__text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-primary);
}

.list-item {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  gap: 16px;
  overflow: hidden;
  box-sizing: border-box;
}

.list-item--active {
  z-index: 200;
}

.list-item:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.item-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--color-bg-alt);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 4px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.item-desc {
  font-size: 13px;
  color: var(--color-text-muted);
}

.item-meta {
  font-size: 13px;
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  gap: 20px;
  margin-right: 12px;
}

.item-actions {
  display: flex;
  align-items: center;
}

.actions-wrapper {
  position: relative;
}

.dots-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--color-text-muted);
}

.dots-btn:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.actions-menu {
  position: absolute;
  top: 100%;
  right: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  box-shadow: var(--shadow-lg);
  padding: 6px;
  min-width: 140px;
  z-index: 100;
  margin-top: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text);
  transition: background 0.2s;
}

.menu-item:hover {
  background: var(--color-bg-alt);
}

.menu-item.danger {
  color: #ef4444;
}

.menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.05);
}

.list--grid .list-item {
  flex-direction: column;
  align-items: flex-start;
  min-height: 160px;
  padding: 24px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.list--grid .item-main {
  width: 100%;
  min-width: 0;
}

.list--grid .item-meta {
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin-top: auto;
  margin-right: 0;
}

.list--grid .item-actions {
  position: absolute;
  top: 16px;
  right: 16px;
}

.list--list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.new-card {
  background: rgba(59, 130, 246, 0.05);
  border: 1px dashed rgba(59, 130, 246, 0.3);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 160px;
}

.list--grid .new-card {
  flex-direction: column;
  justify-content: center;
  min-height: 160px;
}

.new-card:hover {
  border-style: solid;
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.06);
}

.plus-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.new-card__text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-primary);
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
