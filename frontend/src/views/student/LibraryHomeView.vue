<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import LibraryKnowledgeCreateModal from '@/components/student/LibraryKnowledgeCreateModal.vue'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import type { LibraryResource } from '@/stores/libraryResource'
import type { KnowledgeBase } from '@/api/knowledgeBase'

type LibraryFilter = 'all' | 'knowledge' | 'mindmap' | 'image' | 'file'
type ViewMode = 'grid' | 'list'
type LibraryAsset =
  | { kind: 'knowledge'; id: string; source: KnowledgeBase }
  | { kind: 'file'; id: string; source: LibraryResource }

const router = useRouter()
const libraryResourceStore = useLibraryResourceStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const uploadOpen = ref(false)
const newKnowledgeOpen = ref(false)
const newMenuOpen = ref(false)
const activeFilter = ref<LibraryFilter>('all')
const viewMode = ref<ViewMode>('grid')
const selectedIds = ref<string[]>([])
const knowledgeMenuId = ref<string | null>(null)
const fileMenuId = ref<string | null>(null)
const moveModalOpen = ref(false)

const filters: Array<{ label: string; value: LibraryFilter }> = [
  { label: '全部', value: 'all' },
  { label: '知识库', value: 'knowledge' },
  { label: '思维导图', value: 'mindmap' },
  { label: '图片', value: 'image' },
  { label: '文件', value: 'file' },
]

const knowledgeAssets = computed<LibraryAsset[]>(() =>
  knowledgeBaseStore.list.map((source) => ({ kind: 'knowledge', id: `knowledge-${source.id}`, source })),
)

const fileAssets = computed<LibraryAsset[]>(() =>
  libraryResourceStore.resources.map((source) => ({ kind: 'file', id: `file-${source.id}`, source })),
)

const visibleAssets = computed(() => {
  if (activeFilter.value === 'knowledge') return knowledgeAssets.value
  if (activeFilter.value === 'mindmap') {
    return fileAssets.value.filter((asset) => asset.kind === 'file' && asset.source.category === 'mindmap')
  }
  if (activeFilter.value === 'image') {
    return fileAssets.value.filter((asset) => asset.kind === 'file' && asset.source.category === 'image')
  }
  if (activeFilter.value === 'file') {
    return fileAssets.value.filter((asset) => asset.kind === 'file' && asset.source.category === 'file')
  }
  return [...knowledgeAssets.value, ...fileAssets.value]
})

const firstFileAfterKnowledgeId = computed(() => {
  if (!visibleAssets.value.some((asset) => asset.kind === 'knowledge')) return null
  return visibleAssets.value.find((asset) => asset.kind === 'file')?.id ?? null
})

const selectedCount = computed(() => selectedIds.value.length)
const hasSelection = computed(() => selectedCount.value > 0)

function openNewMenu() {
  newMenuOpen.value = !newMenuOpen.value
}

function openUpload() {
  uploadOpen.value = true
  newMenuOpen.value = false
}

function openNewKnowledge() {
  newKnowledgeOpen.value = true
  newMenuOpen.value = false
}

function handleKnowledgeCreated() {
  activeFilter.value = 'knowledge'
  newKnowledgeOpen.value = false
}

function toggleSelection(id: string) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id]
}

function clearSelection() {
  selectedIds.value = []
}

function isSelected(id: string) {
  return selectedIds.value.includes(id)
}

function toggleKnowledgeMenu(id: string) {
  knowledgeMenuId.value = knowledgeMenuId.value === id ? null : id
}

function closeKnowledgeMenu() {
  knowledgeMenuId.value = null
}

function toggleFileMenu(id: string) {
  fileMenuId.value = fileMenuId.value === id ? null : id
}

function closeFileMenu() {
  fileMenuId.value = null
}

function openMoveModal() {
  moveModalOpen.value = true
  closeFileMenu()
}

function openKnowledgeFromMove() {
  moveModalOpen.value = false
  newKnowledgeOpen.value = true
}

function fileSize(file: LibraryResource) {
  return file.size
}

function fileIconName(file: LibraryResource) {
  if (file.category === 'mindmap') return 'mind-topic'
  if (file.type === 'PDF') return 'file'
  if (file.type === 'Word') return 'book'
  return 'file'
}

function startLearning(libraryId: number) {
  closeKnowledgeMenu()
  router.push({ path: '/learning', query: { libraryId } })
}

function knowledgeTitle(item: KnowledgeBase) {
  return item.name.replace('资料库', '').trim()
}

function knowledgeFileCount(item: KnowledgeBase) {
  return (item.documentCount || 0) + libraryResourceStore.resources.filter((resource) => resource.libraryId === item.id).length
}

function knowledgeUpdatedAt(item: KnowledgeBase) {
  return item.updateTime?.includes('今天') ? item.updateTime : item.updateTime || '刚刚'
}

function assetModifiedAt(asset: LibraryAsset) {
  if (asset.kind === 'knowledge') return knowledgeUpdatedAt(asset.source).includes('今天') ? '今天' : knowledgeUpdatedAt(asset.source)
  return asset.source.updatedAt.includes('今天') ? '今天' : asset.source.updatedAt
}

function assetSize(asset: LibraryAsset) {
  return asset.kind === 'knowledge' ? '—' : fileSize(asset.source)
}

onMounted(() => {
  void knowledgeBaseStore.fetchList()
})
</script>

<template>
  <StudentShell>
    <div class="library-page" @click="newMenuOpen = false; closeKnowledgeMenu(); closeFileMenu()">
      <header class="library-header">
        <h1>资料库</h1>

        <div class="header-actions" @click.stop>
          <label class="search-box">
            <AppIcon name="search" :size="18" />
            <input placeholder="搜索" />
          </label>

          <div class="new-menu-wrap">
            <button class="new-btn" type="button" @click="openNewMenu">
              新建
              <AppIcon name="chevron-down" :size="15" />
            </button>
            <div v-if="newMenuOpen" class="new-menu ui-menu-panel">
              <button class="ui-menu-item" type="button" @click="openUpload">
                <span class="ui-menu-icon"><AppIcon name="upload-cloud" :size="16" /></span>
                上传资料
              </button>
              <button class="ui-menu-item" type="button" @click="openNewKnowledge">
                <span class="ui-menu-icon"><AppIcon name="folder" :size="16" /></span>
                新建知识库
              </button>
              <button class="ui-menu-item" type="button" @click="newMenuOpen = false">
                <span class="ui-menu-icon"><AppIcon name="mindmap" :size="16" /></span>
                创建思维导图
              </button>
            </div>
          </div>
        </div>
      </header>

      <div class="library-controls">
        <div v-if="selectedCount" class="bulk-actions">
          <button class="bulk-primary" type="button">
            <AppIcon name="edit" :size="16" />
            开始聊天
          </button>
          <button type="button">
            <AppIcon name="download" :size="16" />
            下载
          </button>
          <button type="button">
            <AppIcon name="folder-move" :size="16" />
            移动
          </button>
          <button class="danger-outline" type="button">
            <AppIcon name="trash" :size="16" />
            删除
          </button>
        </div>
        <div v-else class="tabs">
          <button
            v-for="filter in filters"
            :key="filter.value"
            :class="{ active: activeFilter === filter.value }"
            type="button"
            @click="activeFilter = filter.value"
          >
            {{ filter.label }}
          </button>
        </div>

        <div class="view-tools">
          <span v-if="selectedCount">已选 {{ selectedCount }} 个</span>

          <button class="filter-btn ui-icon-action" type="button" aria-label="筛选">
            <AppIcon name="list-filter" :size="18" />
          </button>
          <span class="view-divider" />
          <button
            class="round-icon ui-icon-action"
            :class="{ active: viewMode === 'grid' }"
            type="button"
            aria-label="网格视图"
            @click="viewMode = 'grid'"
          >
            <AppIcon name="grid" :size="18" />
          </button>
          <button
            class="round-icon ui-icon-action"
            :class="{ active: viewMode === 'list' }"
            type="button"
            aria-label="列表视图"
            @click="viewMode = 'list'"
          >
            <AppIcon name="list" :size="18" />
          </button>
        </div>
      </div>

      <section v-if="viewMode === 'grid'" class="asset-grid">
        <article
          v-for="asset in visibleAssets"
          :key="asset.id"
          class="asset-card ui-hover-row"
          :class="{
            'asset-card--knowledge': asset.kind === 'knowledge',
            'asset-card--selected': isSelected(asset.id),
            'asset-card--new-row': asset.id === firstFileAfterKnowledgeId,
          }"
          @click="asset.kind === 'knowledge' ? router.push(`/library/${asset.source.id}`) : toggleSelection(asset.id)"
        >
          <template v-if="asset.kind === 'knowledge'">
            <span class="knowledge-icon">
              <AppIcon name="folder" :size="22" />
            </span>
            <strong>{{ knowledgeTitle(asset.source) }}</strong>
            <small>{{ knowledgeFileCount(asset.source) }} 个文档 · {{ knowledgeUpdatedAt(asset.source) }}</small>
            <button
              class="knowledge-more ui-icon-action"
              type="button"
              aria-label="知识库菜单"
              @click.stop="toggleKnowledgeMenu(asset.id)"
            >
              <AppIcon name="more-horizontal" :size="16" />
            </button>
            <div v-if="knowledgeMenuId === asset.id" class="floating-menu asset-floating-menu ui-menu-panel" @click.stop>
              <button class="menu-action ui-menu-item" type="button" @click="startLearning(asset.source.id)">
                <span class="ui-menu-icon"><AppIcon name="graduation" :size="16" /></span>
                开始智能学习
              </button>
              <button class="menu-action ui-menu-item" type="button" @click="closeKnowledgeMenu">
                <span class="ui-menu-icon"><AppIcon name="edit" :size="16" /></span>
                重命名
              </button>
              <div class="ui-menu-divider" />
              <button class="menu-action menu-action--danger ui-menu-item ui-menu-item--danger" type="button" @click="closeKnowledgeMenu">
                <span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>
                删除知识库
              </button>
            </div>
          </template>

          <template v-else>
            <button class="asset-check" type="button" @click.stop="toggleSelection(asset.id)">
              <span v-if="isSelected(asset.id)">✓</span>
            </button>
            <div class="asset-grid-actions" @click.stop>
              <button class="ui-icon-action" type="button" aria-label="重命名">
                <AppIcon name="edit" :size="18" />
              </button>
              <button class="ui-icon-action" type="button" aria-label="移动" @click="openMoveModal">
                <AppIcon name="folder-move" :size="18" />
              </button>
              <button class="ui-icon-action" type="button" aria-label="下载">
                <AppIcon name="download" :size="18" />
              </button>
              <button class="danger-icon ui-icon-action" type="button" aria-label="删除">
                <AppIcon name="trash" :size="18" />
              </button>
            </div>
            <strong>{{ asset.source.name }}</strong>
            <span class="file-preview-icon">
              <AppIcon :name="fileIconName(asset.source)" :size="34" />
            </span>
            <small>{{ asset.source.type }} · {{ fileSize(asset.source) }} · {{ asset.source.source }}</small>
          </template>
        </article>
      </section>

      <section v-else class="asset-list">
        <div class="asset-list-head">
          <button
            class="list-select-all"
            :class="{ 'list-select-all--active': hasSelection }"
            type="button"
            aria-label="清除选择"
            @click="clearSelection"
          >
            <span v-if="hasSelection" />
          </button>
          <span class="head-name">名称</span>
          <span>修改时间 ↓</span>
          <span>大小</span>
          <span />
        </div>
        <article
          v-for="asset in visibleAssets"
          :key="asset.id"
          class="asset-row ui-hover-row"
          :class="{ 'asset-row--selected': isSelected(asset.id) }"
          @click="asset.kind === 'knowledge' ? router.push(`/library/${asset.source.id}`) : toggleSelection(asset.id)"
        >
          <button class="asset-row-check" type="button" @click.stop="toggleSelection(asset.id)">
            <span v-if="isSelected(asset.id)">✓</span>
          </button>
          <AppIcon :name="asset.kind === 'knowledge' ? 'folder' : fileIconName(asset.source)" :size="20" />
          <strong>{{ asset.kind === 'knowledge' ? knowledgeTitle(asset.source) : asset.source.name }}</strong>
          <span>{{ assetModifiedAt(asset) }}</span>
          <span>{{ assetSize(asset) }}</span>
          <div class="row-actions">
            <button
              v-if="asset.kind === 'knowledge'"
              class="ui-icon-action"
              type="button"
              @click.stop="toggleKnowledgeMenu(asset.id)"
            >
              <AppIcon name="more-horizontal" :size="16" />
            </button>
            <template v-else>
              <button class="ui-icon-action" type="button" @click.stop="toggleFileMenu(asset.id)"><AppIcon name="more-horizontal" :size="16" /></button>
            </template>
          </div>
          <div v-if="knowledgeMenuId === asset.id" class="floating-menu asset-floating-menu menu--row ui-menu-panel" @click.stop>
            <button class="menu-action ui-menu-item" type="button" @click="startLearning(Number(asset.source.id))">
              <span class="ui-menu-icon"><AppIcon name="graduation" :size="16" /></span>
              开始智能学习
            </button>
            <button class="menu-action ui-menu-item" type="button" @click="closeKnowledgeMenu">
              <span class="ui-menu-icon"><AppIcon name="edit" :size="16" /></span>
              重命名
            </button>
            <div class="ui-menu-divider" />
            <button class="menu-action menu-action--danger ui-menu-item ui-menu-item--danger" type="button" @click="closeKnowledgeMenu">
              <span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>
              删除知识库
            </button>
          </div>
          <div v-if="fileMenuId === asset.id" class="floating-menu asset-floating-menu menu--row ui-menu-panel" @click.stop>
            <button class="menu-action ui-menu-item" type="button" @click="closeFileMenu">
              <span class="ui-menu-icon"><AppIcon name="download" :size="16" /></span>
              下载
            </button>
            <button class="menu-action ui-menu-item" type="button" @click="closeFileMenu">
              <span class="ui-menu-icon"><AppIcon name="edit" :size="16" /></span>
              重命名
            </button>
            <button class="menu-action ui-menu-item" type="button" @click="openMoveModal">
              <span class="ui-menu-icon"><AppIcon name="folder-move" :size="16" /></span>
              移动
            </button>
            <div class="ui-menu-divider" />
            <button class="menu-action menu-action--danger ui-menu-item ui-menu-item--danger" type="button" @click="closeFileMenu">
              <span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>
              删除
            </button>
          </div>
        </article>
      </section>
    </div>

    <UploadMaterialModal :open="uploadOpen" @close="uploadOpen = false" />

    <LibraryKnowledgeCreateModal
      :open="newKnowledgeOpen"
      @close="newKnowledgeOpen = false"
      @created="handleKnowledgeCreated"
    />

    <div v-if="moveModalOpen" class="modal-backdrop" @click.self="moveModalOpen = false">
      <section class="move-modal">
        <header>
          <h2>移动到...</h2>
          <button type="button" @click="moveModalOpen = false">×</button>
        </header>
        <span class="move-label">知识库</span>
        <div class="move-list">
          <button v-for="item in knowledgeBaseStore.list" :key="item.id" type="button">
            <span class="move-icon"><AppIcon name="folder" :size="20" /></span>
            <span>{{ knowledgeTitle(item) }}</span>
            <AppIcon name="chevron-right" :size="16" />
          </button>
        </div>
        <footer>
          <button class="outline-btn" type="button" @click="openKnowledgeFromMove">新建知识库</button>
          <span />
          <button class="outline-btn" type="button" @click="moveModalOpen = false">取消</button>
          <button class="move-disabled" type="button">移动这里</button>
        </footer>
      </section>
    </div>
  </StudentShell>
</template>

<style scoped>
.library-page {
  min-height: 100%;
  padding: 58px 64px 72px;
  background: var(--color-bg);
  color: var(--color-text);
}

.library-page,
.library-page * {
  box-sizing: border-box;
}

.library-header,
.library-controls,
.asset-grid,
.asset-list {
  max-width: 980px;
  margin-left: auto;
  margin-right: auto;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: 34px;
  font-weight: 800;
  color: var(--color-text);
}

.library-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 48px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  width: 240px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: var(--color-surface);
  color: var(--color-text-muted);
}

.search-box input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
}

.new-menu-wrap {
  position: relative;
}

.new-btn {
  height: 36px;
  border: 0;
  border-radius: 999px;
  padding: 0 14px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
}

.new-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  width: 178px;
}

.new-menu button {
  height: var(--ui-menu-item-height);
}

.library-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
}

.tabs {
  display: flex;
  gap: 12px;
}

.tabs button {
  height: 38px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--color-text);
  padding: 0 16px;
  cursor: pointer;
}

.tabs button.active {
  border-color: var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

.view-tools {
  display: flex;
  align-items: center;
  gap: 9px;
}

.view-tools > span {
  color: var(--color-text);
  font-size: 14px;
  margin: 0 10px;
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bulk-actions button,
.view-tools > button:not(.round-icon):not(.filter-btn),
.bulk-primary,
.danger-outline {
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}

.bulk-actions button:disabled {
  color: var(--color-text-muted);
  background: var(--color-hover);
  cursor: not-allowed;
}

.bulk-primary {
  background: var(--color-primary) !important;
  color: var(--color-on-primary) !important;
  border-color: var(--color-primary) !important;
  padding: 0 18px;
}

.bulk-primary :deep(svg) {
  color: var(--color-on-primary);
}

.danger-outline {
  color: #ff2457 !important;
  border-color: #ff2457 !important;
}

.filter-btn,
.round-icon {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
}

.view-tools .filter-btn {
  background: transparent;
  color: var(--color-text-muted);
}

.view-divider {
  width: 1px;
  height: 24px;
  background: var(--color-border);
  margin: 0 4px;
}

.round-icon.active,
.round-icon:hover,
.view-tools .filter-btn:hover,
.view-tools .filter-btn:focus-visible {
  background: var(--color-hover);
  color: var(--color-text);
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.asset-card--new-row {
  grid-column-start: 1;
}

.asset-card {
  position: relative;
  min-height: 246px;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: linear-gradient(180deg, var(--color-surface) 0%, var(--color-surface-subtle) 100%);
  padding: 18px 18px 16px;
  cursor: pointer;
  display: grid;
  grid-template-rows: auto 1fr auto;
  box-shadow: var(--shadow-sm);
}

.asset-card--knowledge {
  min-height: 96px;
  height: 96px;
  grid-template-columns: 44px minmax(0, 1fr);
  grid-template-rows: 1fr auto;
  align-items: center;
  column-gap: 12px;
  box-shadow: none;
  background: var(--color-surface);
}

.asset-card--knowledge strong {
  align-self: end;
}

.asset-card--knowledge small {
  grid-column: 2;
  align-self: start;
  margin-top: 3px;
}

.asset-card--selected {
  border-color: var(--color-text);
  box-shadow: inset 0 0 0 1px var(--color-text), var(--shadow-sm);
}

.asset-card strong {
  max-width: 78%;
  color: var(--color-text);
  font-size: 15px;
  line-height: 1.35;
}

.asset-card small {
  color: var(--color-text-muted);
  font-size: 13px;
}

.knowledge-icon {
  grid-row: 1 / 3;
  width: 34px;
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--color-text);
}

.asset-check {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-info);
  cursor: pointer;
  font-size: 14px;
  font-weight: 900;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.asset-card:hover .asset-check,
.asset-card--selected .asset-check {
  opacity: 1;
  pointer-events: auto;
}

.asset-more {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: var(--color-hover);
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  opacity: 0;
  transition: opacity 0.15s ease, background 0.15s ease;
}

.asset-card:hover .asset-more,
.asset-card--selected .asset-more {
  opacity: 1;
}

.asset-more:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.asset-grid-actions {
  position: absolute;
  right: 12px;
  top: 62px;
  z-index: 2;
  display: grid;
  gap: 8px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.asset-grid-actions button {
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  display: grid;
  place-items: center;
}

.asset-grid-actions button:hover {
  background: var(--color-hover-strong);
  color: var(--color-text);
}

.asset-card:hover .asset-grid-actions,
.asset-card--selected .asset-grid-actions {
  opacity: 1;
  pointer-events: auto;
}

.knowledge-more {
  position: absolute;
  right: 16px;
  top: 50%;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: var(--color-hover);
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity 0.15s ease, background 0.15s ease;
}

.asset-card--knowledge:hover .knowledge-more {
  opacity: 1;
}

.knowledge-more:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.floating-menu {
  position: absolute;
  right: 14px;
  top: 54px;
  z-index: 25;
  width: 166px;
}

.menu-action {
  height: var(--ui-menu-item-height);
}

.menu-action :deep(svg) {
  width: 16px;
  height: 16px;
  stroke-width: 2;
}

.danger-text {
  color: #ff2457 !important;
}

.row-actions button {
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
}

.row-actions button:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.danger-icon {
  color: #ff2457 !important;
}

.file-preview-icon {
  place-self: center;
  width: 58px;
  height: 58px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: var(--color-info);
}

.asset-list {
  display: grid;
  gap: 0;
  max-width: 820px;
}

.asset-list-head {
  min-height: 42px;
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) 140px 120px 54px;
  align-items: center;
  gap: 10px;
  padding: 0 8px;
  color: var(--color-text);
  font-size: 14px;
}

.head-name {
  padding-left: 38px;
}

.list-select-all,
.asset-row-check {
  width: 18px;
  height: 18px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-surface);
  color: var(--color-on-primary);
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 0;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.list-select-all--active,
.asset-row--selected .asset-row-check {
  border-color: var(--color-primary);
  background: var(--color-primary);
  opacity: 1;
  pointer-events: auto;
}

.list-select-all span {
  width: 10px;
  height: 2px;
  border-radius: 999px;
  background: var(--color-on-primary);
}

.asset-row {
  position: relative;
  min-height: 65px;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  border-radius: 0;
  display: grid;
  grid-template-columns: 26px 34px minmax(0, 1fr) 140px 120px 54px;
  align-items: center;
  gap: 10px;
  padding: 0 8px;
  color: var(--color-text);
  cursor: pointer;
}

.asset-row:hover,
.asset-row--selected {
  background: var(--color-hover);
  border-radius: 12px;
  border-color: transparent;
}

.asset-row strong {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--color-text);
}

.asset-row-check {
  font-size: 12px;
  font-weight: 800;
}

.asset-row:hover .asset-row-check,
.asset-row-check:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.asset-row:hover .row-actions,
.asset-row--selected .row-actions {
  opacity: 1;
}

.menu--row {
  top: 44px;
  right: 12px;
}

.outline-btn,
.primary-btn {
  height: 40px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 700;
  padding: 0 16px;
}

.outline-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

.primary-btn {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 220;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--color-overlay);
}

.new-knowledge-modal {
  width: min(620px, 100%);
  padding: 20px;
  border-radius: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-lg);
}

.new-knowledge-modal header,
.new-knowledge-modal footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.new-knowledge-modal header button {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 24px;
}

.new-knowledge-form {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

.new-knowledge-form label {
  display: grid;
  gap: 8px;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 500;
}

.new-knowledge-form input,
.new-knowledge-form textarea {
  min-width: 0;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  padding: 0 12px;
  box-sizing: border-box;
  font-size: 14px;
  font-weight: 400;
}

.new-knowledge-form input::placeholder,
.new-knowledge-form textarea::placeholder {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 500;
}

.new-knowledge-form input {
  height: 40px;
}

.new-knowledge-form textarea {
  min-height: 96px;
  resize: none;
  padding: 10px 12px;
}

.new-knowledge-modal footer {
  justify-content: flex-end;
  flex-wrap: wrap;
  margin-top: 18px;
}

.move-modal {
  width: min(872px, 100%);
  min-height: 560px;
  padding: 32px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
}

.move-modal header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.move-modal h2 {
  font-size: 20px;
  font-weight: 500;
}

.move-modal header button {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-size: 24px;
}

.move-modal header button:hover {
  background: var(--color-hover);
}

.move-label {
  color: var(--color-text);
  font-size: 13px;
  margin-bottom: 16px;
}

.move-list {
  min-height: 0;
  overflow: auto;
  display: grid;
  align-content: start;
}

.move-list button {
  height: 58px;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 12px;
  text-align: left;
}

.move-list button:hover {
  background: var(--color-hover);
}

.move-icon {
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: grid;
  place-items: center;
}

.move-modal footer {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 10px;
  padding-top: 18px;
}

.move-disabled {
  height: 34px;
  border: 0;
  border-radius: 999px;
  background: var(--color-border);
  color: var(--color-text-muted);
  padding: 0 16px;
  cursor: not-allowed;
  font-weight: 700;
}

@media (max-width: 1180px) {
  .library-page {
    padding: 42px 28px 56px;
  }

  .asset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .library-header,
  .library-controls {
    align-items: flex-start;
    flex-direction: column;
  }

  .view-tools {
    flex-wrap: wrap;
  }

  .asset-row {
    grid-template-columns: 26px 28px minmax(0, 1fr) 64px;
  }

  .asset-row > span:nth-of-type(2),
  .asset-row > span:nth-of-type(3) {
    display: none;
  }
}

@media (max-width: 760px) {
  .header-actions,
  .search-box {
    width: 100%;
  }

  .header-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .new-btn {
    width: 100%;
    justify-content: center;
  }

  .new-menu {
    left: 0;
    right: 0;
    width: 100%;
  }

  .tabs {
    width: 100%;
    overflow-x: auto;
  }

  .asset-grid {
    grid-template-columns: 1fr;
  }

  .view-tools > button:not(.round-icon):not(.filter-btn) {
    width: 100%;
    justify-content: center;
  }

  .new-knowledge-modal footer .outline-btn,
  .new-knowledge-modal footer .primary-btn {
    width: 100%;
  }
}
</style>
