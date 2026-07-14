<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useMindMapStore } from "@/stores/mindmap";
import AppIcon from "@/components/common/AppIcon.vue";
import AppButton from "@/components/common/AppButton.vue";
import AppInput from "@/components/common/AppInput.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import PromptModal from "@/components/common/PromptModal.vue";
import AppModal from "@/components/common/AppModal.vue";
import TheSidebar from "@/components/sidebar/TheSidebar.vue";
import { useAuthStore } from "@/stores/auth";
import { getKnowledgeBases, type KnowledgeBase } from "@/api/knowledgeBase";

const store = useMindMapStore();
const router = useRouter();
const authStore = useAuthStore();

const sidebarOpen = ref(false);

onMounted(() => {
  const raw = localStorage.getItem("llm.sidebar.open");
  // 如果之前打开了，这里保持打开状态，否则默认关闭
  sidebarOpen.value = raw === "1";
});

watch(sidebarOpen, (open) => {
  localStorage.setItem("llm.sidebar.open", open ? "1" : "0");
});

const searchQuery = ref("");
const activeMenuId = ref<number | null>(null);
const showDeleteConfirm = ref(false);
const deletingId = ref<number | null>(null);
const deletingTitle = ref("");

const showMoveModal = ref(false);
const movingMapId = ref<number | null>(null);
const selectedKbId = ref<number | null>(null);
const knowledgeBases = ref<KnowledgeBase[]>([]);

const showSortMenu = ref(false);
const viewMode = ref<"grid" | "list">(
  (localStorage.getItem("llm.mindmap.viewMode") as any) || "grid",
);
const sortMode = ref<"time" | "name">(
  (localStorage.getItem("llm.mindmap.sortMode") as any) || "time",
);

watch(viewMode, (val) => localStorage.setItem("llm.mindmap.viewMode", val));
watch(sortMode, (val) => localStorage.setItem("llm.mindmap.sortMode", val));

const filteredList = computed(() => {
  let result = [...store.mindMapList];
  if (searchQuery.value) {
    result = result.filter((map) =>
      map.title.toLowerCase().includes(searchQuery.value.toLowerCase()),
    );
  }
  result.sort((a, b) => {
    if (sortMode.value === "time") {
      return new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime();
    } else {
      return a.title.localeCompare(b.title);
    }
  });
  return result;
});

onMounted(async () => {
  if (authStore.isAuthed) {
    await store.fetchList();
  }
});

// Prompt Modal State
const promptState = ref({
  open: false,
  title: "",
  defaultValue: "",
  onConfirm: (val: string) => {},
});

function openPrompt(title: string, defaultValue: string, onConfirm: (val: string) => void) {
  promptState.value = {
    open: true,
    title,
    defaultValue,
    onConfirm,
  };
}

const showSearch = ref(false);

function toggleSearch() {
  showSearch.value = !showSearch.value;
  if (!showSearch.value) searchQuery.value = "";
}

function handlePromptConfirm(value: string) {
  promptState.value.onConfirm(value);
}

function handleCreate() {
  if (!authStore.isAuthed) return authStore.openAuthModal();
  openPrompt("新建思维导图", "未命名思维导图", async (title) => {
    if (title) {
      const id = await store.createMap(title);
      router.push(`/mindmap/${id}`);
    }
  });
}

function handleEdit(id: number) {
  router.push(`/mindmap/${id}`);
}

function handleRename(id: number, oldTitle: string) {
  openPrompt("重命名思维导图", oldTitle, (newTitle) => {
    if (newTitle && newTitle !== oldTitle) {
      store.renameMap(id, newTitle);
    }
  });
}

function handleDelete(id: number, title: string) {
  deletingId.value = id;
  deletingTitle.value = title;
  showDeleteConfirm.value = true;
}

async function confirmDelete() {
  if (deletingId.value !== null) {
    await store.deleteMap(deletingId.value);
    showDeleteConfirm.value = false;
    deletingId.value = null;
  }
}

async function handleMoveToKB(id: number) {
  movingMapId.value = id;
  selectedKbId.value = null;
  try {
    const data = await getKnowledgeBases();
    knowledgeBases.value = data;
  } catch (error) {
    console.error("Failed to fetch knowledge bases:", error);
  }
  showMoveModal.value = true;
}

async function confirmMove() {
  if (movingMapId.value !== null && selectedKbId.value !== null) {
    await store.moveToKB(movingMapId.value, selectedKbId.value);
    showMoveModal.value = false;
    movingMapId.value = null;
    selectedKbId.value = null;
  }
}
</script>

<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <div class="mindmap-list-view">
        <div class="header">
          <div class="header__info">
            <h1 class="title">思维导图</h1>
            <p class="subtitle">管理您的所有知识脉络与思维导图</p>
          </div>
          <div class="actions">
            <div class="search-expand" v-if="showSearch">
              <input v-model="searchQuery" placeholder="搜索思维导图..." autofocus />
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
                <div class="dropdown-menu ui-menu-panel" v-if="showSortMenu">
                  <div
                    class="dropdown-item ui-menu-item"
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
                    class="dropdown-item ui-menu-item"
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
                  <div class="ui-menu-divider"></div>
                  <div
                    class="dropdown-item ui-menu-item"
                    @click="
                      sortMode = 'time';
                      showSortMenu = false;
                    "
                  >
                    <AppIcon name="clock" :size="16" />
                    <span class="dropdown-text">按更新时间排序</span>
                    <AppIcon
                      v-if="sortMode === 'time'"
                      name="check"
                      :size="16"
                      color="var(--color-primary)"
                    />
                  </div>
                  <div
                    class="dropdown-item ui-menu-item"
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

        <div v-if="filteredList.length === 0" class="empty-state">
          <AppIcon name="layers" :size="64" color="var(--color-text-muted)" />
          <h3>暂无思维导图</h3>
          <p>点击“新建导图”开始记录您的灵感</p>
        </div>

        <div v-else class="grid" :class="`grid--${viewMode}`">
          <!-- 新建卡片 -->
          <div class="new-card" @click="handleCreate">
            <div class="new-card__icon">
              <div class="plus-circle">
                <AppIcon name="plus" :size="24" class="plus-icon" />
              </div>
            </div>
            <span class="new-card__text">新建</span>
          </div>

          <div
            v-for="map in filteredList"
            :key="map.id"
            class="map-card"
            :class="{ 'map-card--active': activeMenuId === map.id }"
            @click="handleEdit(map.id)"
          >
            <div class="map-card__icon">
              <AppIcon name="layers" :size="20" color="#8b5cf6" />
            </div>
            <div class="map-card__main">
              <div class="map-card__title">{{ map.title }}</div>
              <div class="map-card__desc">可视化知识图谱</div>
            </div>
            <div class="map-card__meta">
              <span>更新于 {{ new Date(map.updateTime).toISOString().split("T")[0] }}</span>
            </div>
            <div class="map-card__actions">
              <div class="actions-wrapper">
                <button
                  class="dots-btn"
                  @click.stop="activeMenuId = activeMenuId === map.id ? null : map.id"
                >
                  <AppIcon name="more-horizontal" :size="20" />
                </button>
                <div
                  class="overlay"
                  v-if="activeMenuId === map.id"
                  @click.stop="activeMenuId = null"
                ></div>
                <div class="actions-menu ui-menu-panel" v-if="activeMenuId === map.id">
                  <div
                    class="menu-item ui-menu-item"
                    @click.stop="
                      handleRename(map.id, map.title);
                      activeMenuId = null;
                    "
                  >
                    <AppIcon name="edit" :size="16" />
                    <span>重命名</span>
                  </div>
                  <div
                    class="menu-item ui-menu-item"
                    @click.stop="
                      handleMoveToKB(map.id);
                      activeMenuId = null;
                    "
                  >
                    <AppIcon name="folder" :size="16" />
                    <span>移动到知识库</span>
                  </div>
                  <div
                    class="menu-item danger ui-menu-item ui-menu-item--danger"
                    @click.stop="
                      handleDelete(map.id, map.title);
                      activeMenuId = null;
                    "
                  >
                    <AppIcon name="trash" :size="16" />
                    <span>删除导图</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
    </div>

    <ConfirmDialog
      :open="showDeleteConfirm"
      title="确认删除"
      :message="`确定要删除思维导图'${deletingTitle}'吗？`"
      confirm-text="删除"
      confirm-variant="primary"
      @close="showDeleteConfirm = false"
      @confirm="confirmDelete"
    />

    <PromptModal
      :open="promptState.open"
      :title="promptState.title"
      :default-value="promptState.defaultValue"
      label="名称"
      placeholder="请输入思维导图名称"
      @close="promptState.open = false"
      @confirm="handlePromptConfirm"
    />

    <AppModal :open="showMoveModal" @close="showMoveModal = false">
      <div class="move-modal">
        <h3>移动到知识库</h3>
        <p class="move-modal-desc">选择目标知识库：</p>
        <div class="move-modal-list">
          <div
            v-for="kb in knowledgeBases"
            :key="kb.id"
            class="move-modal-item"
            :class="{ 'move-modal-item--selected': selectedKbId === kb.id }"
            @click="selectedKbId = kb.id"
          >
            <AppIcon name="book" :size="20" :color="kb.color || 'var(--color-primary)'" />
            <span>{{ kb.name }}</span>
          </div>
        </div>
        <div class="move-modal-actions">
          <AppButton variant="secondary" @click="showMoveModal = false">取消</AppButton>
          <AppButton variant="primary" @click="confirmMove" :disabled="!selectedKbId"
            >确认移动</AppButton
          >
        </div>
      </div>
    </AppModal>
  </div>
</template>

<style scoped>
.plain-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-input-bg, #ffffff);
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.plain-input:focus {
  border-color: var(--color-primary);
}

.layout {
  height: 100vh;
  position: relative;
  display: flex;
  transition: padding-left 180ms ease;
  padding-left: 0;
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
  overflow-y: auto;
  min-width: 0;
}

.mini {
  position: fixed;
  top: 12px;
  left: 12px;
  display: inline-flex;
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
  background: var(--color-hover);
  color: var(--color-text);
}

.mindmap-list-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 32px;
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
  min-width: 180px;
  z-index: 50;
  display: flex;
  flex-direction: column;
}
.dropdown-item {
  gap: var(--ui-menu-gap);
}
.dropdown-text {
  flex: 1;
}
.grid {
  transition: all 0.3s ease;
}

.grid--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

.grid--list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.grid--list .new-card,
.grid--list .map-card {
  flex-direction: row;
  align-items: center;
  min-height: auto;
  padding: 16px 24px;
}

.grid--list .new-card__icon,
.grid--list .map-card__icon {
  margin-bottom: 0;
  margin-right: 16px;
}

.grid--list .map-card__content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  text-align: left;
}

.grid--list .map-card__title {
  margin-bottom: 0;
}

.grid--list .map-card__meta {
  margin-bottom: 0;
}

.grid--list .map-card__actions {
  position: static;
  opacity: 1;
  background: transparent;
  flex-direction: row;
  height: auto;
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
  color: var(--color-on-primary);
}

.new-card__text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-primary);
}

.map-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  display: flex;
  align-items: center;
  padding: 16px 20px;
  gap: 16px;
  overflow: visible;
}

.map-card--active {
  z-index: 200;
}

.map-card:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.map-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(139, 92, 246, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.map-card__main {
  flex: 1;
  min-width: 0;
}

.map-card__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 4px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.map-card__desc {
  font-size: 13px;
  color: var(--color-text-muted);
}

.map-card__meta {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-right: 12px;
}

.map-card__actions {
  display: flex;
  align-items: center;
}

.actions-wrapper {
  position: relative;
}

.dots-btn {
  background: transparent;
  border: none;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--color-text-muted);
  transition: all 0.2s;
}

.dots-btn:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.actions-menu {
  position: absolute;
  top: 100%;
  right: 0;
  min-width: 140px;
  z-index: 100;
  margin-top: 4px;
}

.grid--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.grid--grid .map-card {
  flex-direction: column;
  align-items: flex-start;
  min-height: 160px;
  padding: 24px;
}

.grid--grid .map-card__meta {
  margin-top: auto;
  margin-right: 0;
}

.grid--grid .map-card__actions {
  position: absolute;
  top: 16px;
  right: 16px;
}

.grid--list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.new-card {
  background: rgba(59, 130, 246, 0.05);
  border: 1px dashed rgba(59, 130, 246, 0.3);
  border-radius: 16px;
  display: flex;
  align-items: center;
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s;
  gap: 16px;
}

.grid--grid .new-card {
  flex-direction: column;
  justify-content: center;
  min-height: 160px;
  padding: 24px;
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

.confirm-input-box {
  padding: 16px 0;
}

.confirm-label {
  font-size: 14px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.move-modal {
  padding: 8px 0;
}

.move-modal h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 8px 0;
}

.move-modal-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0 0 16px 0;
}

.move-modal-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 20px;
}

.move-modal-item {
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

.move-modal-item:hover {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.05);
}

.move-modal-item--selected {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.1);
}

.move-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
