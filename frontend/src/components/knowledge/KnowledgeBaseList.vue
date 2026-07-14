<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { useKnowledgeBaseStore } from "@/stores/knowledgeBase";
import { useConversationStore } from "@/stores/conversation";
import { useMindMapStore } from "@/stores/mindmap";
import KnowledgeBaseCard from "./KnowledgeBaseCard.vue";
import KnowledgeBaseCreate from "./KnowledgeBaseCreate.vue";
import AppButton from "@/components/common/AppButton.vue";
import AppIcon from "@/components/common/AppIcon.vue";

const router = useRouter();
const kbStore = useKnowledgeBaseStore();
const conversationStore = useConversationStore();
const mindMapStore = useMindMapStore();

const searchQuery = ref("");
const viewMode = ref<"grid" | "list">(
  (localStorage.getItem("llm.kbList.viewMode") as any) || "grid",
);
const sortBy = ref<"updated_at" | "name">(
  (localStorage.getItem("llm.kbList.sortBy") as any) || "updated_at",
);
const currentPage = ref(1);
const pageSize = ref(10);
const showCreateDialog = ref(false);
const errorState = ref(false);

const filteredKnowledgeBases = computed(() => {
  let result = kbStore.list;

  // Search
  if (searchQuery.value) {
    const lowerQuery = searchQuery.value.toLowerCase();
    result = result.filter((kb) => kb.name.toLowerCase().includes(lowerQuery));
  }

  // Sort
  result = [...result].sort((a, b) => {
    if (sortBy.value === "name") {
      return a.name.localeCompare(b.name);
    } else {
      // updated_at (or updateTime)
      const timeA = new Date(a.updateTime || 0).getTime();
      const timeB = new Date(b.updateTime || 0).getTime();
      return timeB - timeA; // descending
    }
  });

  return result;
});

const totalPages = computed(() => Math.ceil(filteredKnowledgeBases.value.length / pageSize.value));

const paginatedKnowledgeBases = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredKnowledgeBases.value.slice(start, start + pageSize.value);
});

function prevPage() {
  if (currentPage.value > 1) currentPage.value--;
}

function nextPage() {
  if (currentPage.value < totalPages.value) currentPage.value++;
}

async function loadData() {
  errorState.value = false;
  try {
    await kbStore.fetchAll();
    await Promise.all([conversationStore.fetchList(), mindMapStore.fetchList()]);
  } catch (err) {
    errorState.value = true;
    console.error("Failed to load knowledge bases", err);
  }
}

onMounted(() => {
  loadData();
});

watch(viewMode, (val) => localStorage.setItem("llm.kbList.viewMode", val));
watch(sortBy, (val) => localStorage.setItem("llm.kbList.sortBy", val));

const showSearch = ref(false);
const showSortMenu = ref(false);

function toggleSearch() {
  showSearch.value = !showSearch.value;
  if (!showSearch.value) searchQuery.value = "";
}

function handleCreate() {
  showCreateDialog.value = true;
}

function handleViewDetail(id: number) {
  router.push(`/knowledge/${id}`);
}
</script>

<template>
  <div class="knowledge-base-list">
    <div class="header">
      <div class="header__info">
        <h1 class="title">知识库</h1>
        <p class="subtitle">管理您的专属知识库与文档资料</p>
      </div>
      <div class="actions">
        <div class="search-expand" v-if="showSearch">
          <input v-model="searchQuery" type="text" placeholder="搜索知识库..." autofocus />
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
                <AppIcon class="ui-menu-icon" name="grid" :size="16" />
                <span class="dropdown-text">图标排序</span>
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
                <AppIcon class="ui-menu-icon" name="list" :size="16" />
                <span class="dropdown-text">列表排序</span>
                <AppIcon
                  v-if="viewMode === 'list'"
                  name="check"
                  :size="16"
                  color="var(--color-primary)"
                />
              </div>
              <div class="dropdown-divider ui-menu-divider"></div>
              <div
                class="dropdown-item ui-menu-item"
                @click="
                  sortBy = 'updated_at';
                  showSortMenu = false;
                "
              >
                <AppIcon class="ui-menu-icon" name="clock" :size="16" />
                <span class="dropdown-text">按时间排序</span>
                <AppIcon
                  v-if="sortBy === 'updated_at'"
                  name="check"
                  :size="16"
                  color="var(--color-primary)"
                />
              </div>
              <div
                class="dropdown-item ui-menu-item"
                @click="
                  sortBy = 'name';
                  showSortMenu = false;
                "
              >
                <AppIcon class="ui-menu-icon" name="user" :size="16" />
                <span class="dropdown-text">按名称排序</span>
                <AppIcon
                  v-if="sortBy === 'name'"
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

    <div v-if="errorState" class="empty">
      <div class="empty__icon">
        <AppIcon name="alert-triangle" :size="48" />
      </div>
      <h3 class="empty__title">加载失败</h3>
      <p class="empty__description">无法获取知识库列表，请稍后重试</p>
      <AppButton @click="loadData" style="margin-top: 16px">重试</AppButton>
    </div>

    <div v-else>
      <div class="grid" :class="`grid--${viewMode}`">
        <!-- 新建卡片 -->
        <div class="new-card" @click="handleCreate">
          <div class="new-card__icon">
            <div class="plus-circle">
              <AppIcon name="plus" :size="24" class="plus-icon" />
            </div>
          </div>
          <span class="new-card__text">新建</span>
        </div>

        <!-- 知识库卡片 -->
        <KnowledgeBaseCard
          v-for="kb in paginatedKnowledgeBases"
          :key="kb.id"
          :knowledge-base="kb"
          :view-mode="viewMode"
          @click="handleViewDetail(kb.id)"
        />
      </div>
      <div class="pagination" v-if="totalPages > 1">
        <button :disabled="currentPage === 1" @click="prevPage">上一页</button>
        <span>{{ currentPage }} / {{ totalPages }}</span>
        <button :disabled="currentPage === totalPages" @click="nextPage">下一页</button>
      </div>
    </div>

    <KnowledgeBaseCreate
      v-if="showCreateDialog"
      :open="showCreateDialog"
      @close="showCreateDialog = false"
    />
  </div>
</template>

<style scoped>
.knowledge-base-list {
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
}

.search-expand input:focus {
  outline: none;
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
.dropdown-text {
  flex: 1;
}

.empty {
  text-align: center;
  padding: 80px 20px;
}

.empty__icon {
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--color-text-muted);
  margin-bottom: 24px;
}

.empty__title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}

.empty__description {
  font-size: 14px;
  color: var(--color-text-muted);
}

.grid {
  transition: all 0.3s ease;
}

.grid--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.grid--list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.grid--list .new-card {
  flex-direction: row;
  align-items: center;
  min-height: auto;
  padding: 16px 24px;
}

.grid--list .new-card__icon {
  margin-bottom: 0;
  margin-right: 16px;
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

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 40px;
}

.pagination button {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  transition: all 0.2s;
}

.pagination button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
