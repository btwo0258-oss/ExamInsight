<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import StudentShell from "@/components/layout/StudentShell.vue";
import UploadMaterialModal from "@/components/library/UploadMaterialModal.vue";
import FilePreviewModal from "@/components/library/FilePreviewModal.vue";
import { courseLibraries } from "@/mock";
import { isMockDataSource } from "@/config/dataSource";
import { useLibraryResourceStore } from "@/stores/libraryResource";
import { useKnowledgeBaseStore } from "@/stores/knowledgeBase";
import * as mindmapApi from "@/api/mindmap";
import type { MindMap } from "@/api/mindmap";
import type { LibraryResource } from "@/stores/libraryResource";

const route = useRoute();
const router = useRouter();
const libraryResourceStore = useLibraryResourceStore();
const knowledgeBaseStore = useKnowledgeBaseStore();
const uploadOpen = ref(false);
const detailLoading = ref(false);
const detailError = ref("");
const actionError = ref("");
const searchQuery = ref("");
const deleteTarget = ref<LibraryResource | null>(null);
const previewFile = ref<LibraryResource | null>(null);
const mindMaps = ref<MindMap[]>([]);
const mindMapsLoading = ref(false);
const libraryId = computed(() => Number(route.params.id));
const library = computed(() => {
  if (!Number.isFinite(libraryId.value) || libraryId.value <= 0) return null;
  const stored =
    knowledgeBaseStore.current?.id === libraryId.value
      ? knowledgeBaseStore.current
      : knowledgeBaseStore.list.find((item) => item.id === libraryId.value);
  if (!stored) return null;
  const preset = isMockDataSource
    ? courseLibraries.find((item) => item.id === libraryId.value)
    : undefined;
  return {
    id: libraryId.value,
    name: stored.name,
    description: stored.description || "暂无说明",
    tags: preset?.tags || [],
    fileCount: stored.documentCount || 0,
    chunkCount: stored.chunkCount || 0,
    status: stored.availableForAi === false ? "processing" : "ready",
    updatedAt: stored.updateTime || "刚刚",
  };
});
const files = computed(() => {
  if (!library.value) return [];
  const query = searchQuery.value.trim().toLocaleLowerCase();
  return libraryResourceStore.resources.filter((item) => {
    if (item.libraryId !== library.value?.id) return false;
    return (
      !query ||
      `${item.name} ${item.type} ${fileStatusLabel(item.status)}`
        .toLocaleLowerCase()
        .includes(query)
    );
  });
});
const fileCount = computed(() =>
  library.value
    ? Math.max(
        library.value.fileCount,
        libraryResourceStore.resources.filter((item) => item.libraryId === library.value?.id)
          .length,
      )
    : 0,
);

function fileStatusLabel(status: LibraryResource["status"]) {
  return { waiting: "等待解析", processing: "向量化中", ready: "解析完成", failed: "解析失败" }[
    status
  ];
}

function formatDate(dateStr: string) {
  if (!dateStr) return "未知";
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" });
  } catch {
    return dateStr;
  }
}

async function loadDetail() {
  detailLoading.value = true;
  detailError.value = "";
  if (!Number.isFinite(libraryId.value) || libraryId.value <= 0) {
    detailLoading.value = false;
    return;
  }
  try {
    await Promise.all([
      knowledgeBaseStore.getDetail(libraryId.value),
      libraryResourceStore.fetchList(libraryId.value),
    ]);
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : "获取资料库详情失败";
  } finally {
    detailLoading.value = false;
  }
}

async function loadMindMaps() {
  if (!Number.isFinite(libraryId.value) || libraryId.value <= 0) return;
  mindMapsLoading.value = true;
  try {
    mindMaps.value = await mindmapApi.getMindMapList(libraryId.value);
  } catch (error) {
    console.error("Failed to load mind maps:", error);
    mindMaps.value = [];
  } finally {
    mindMapsLoading.value = false;
  }
}

async function retryFile(file: LibraryResource) {
  try {
    actionError.value = "";
    await libraryResourceStore.retry(file.id);
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "重试解析失败";
  }
}

async function downloadFile(file: LibraryResource) {
  try {
    actionError.value = "";
    await libraryResourceStore.download(file.id, file.name);
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "下载失败";
  }
}

async function confirmDeleteFile() {
  const target = deleteTarget.value;
  deleteTarget.value = null;
  if (!target) return;
  try {
    actionError.value = "";
    await libraryResourceStore.remove(target.id);
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "删除失败";
  }
}

async function createMindMap() {
  if (!library.value) return;
  try {
    const title = `${library.value.name} - 思维导图`;
    const initialData = { data: { text: title }, children: [] };
    const id = await mindmapApi.createMindMap({
      title,
      kbId: library.value.id,
      content: JSON.stringify(initialData),
    });
    await loadMindMaps();
    router.push(`/mindmap/${id}`);
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "创建思维导图失败";
  }
}

function extractKeywords(): string[] {
  if (!library.value) return [];
  const readyFiles = files.value.filter((f) => f.status === "ready");
  if (readyFiles.length === 0) return [];

  // 从文件名中提取关键词，去除常见停用词
  const stopWords = ["的", "和", "与", "或", "是", "在", "了", "有", "一个", "这个", "那个"];
  const keywords: string[] = [];

  for (const file of readyFiles) {
    // 去除文件扩展名
    const name = file.name.replace(/\.[^/.]+$/, "");

    // 按常见分隔符拆分
    const parts = name.split(/[\s_\-.,，。、]+/);

    for (const part of parts) {
      const trimmed = part.trim();
      // 过滤掉停用词和太短的词
      if (trimmed.length >= 2 && !stopWords.includes(trimmed)) {
        keywords.push(trimmed);
      }
    }
  }

  // 去重并返回前5个
  return [...new Set(keywords)].slice(0, 5);
}

function getRecommendedUses(): string {
  const count = files.value.length;
  const hasReady = files.value.some((f) => f.status === "ready");
  const chunkCount = library.value?.chunkCount || 0;

  if (!hasReady) return "待文件解析完成后推荐";

  const uses: string[] = [];

  // 根据文件数量和分块数智能推荐
  if (count > 0 && chunkCount > 0) {
    uses.push("知识库问答");
  }
  if (count >= 2) {
    uses.push("思维导图生成");
  }
  if (chunkCount >= 10) {
    uses.push("智能学习");
  }
  if (count >= 3) {
    uses.push("错题分析");
  }

  // 如果推荐太少，添加通用推荐
  if (uses.length === 0) {
    uses.push("知识库问答");
    uses.push("智能学习");
  }

  return uses.join("、");
}

onMounted(() => {
  void loadDetail();
  void loadMindMaps();
});

watch(libraryId, () => {
  void loadDetail();
  void loadMindMaps();
});
</script>

<template>
  <StudentShell>
    <div class="detail-page">
      <section v-if="detailLoading" class="detail-state" aria-live="polite">
        <strong>正在加载资料库…</strong>
      </section>
      <section v-else-if="detailError" class="detail-state detail-state--error" role="alert">
        <strong>资料库加载失败</strong>
        <span>{{ detailError }}</span>
        <button type="button" @click="loadDetail">重试</button>
      </section>
      <section v-else-if="!library" class="detail-state">
        <strong>资料库不存在或已被删除</strong>
        <button type="button" @click="router.push('/library')">返回资料库</button>
      </section>
      <template v-else>
        <header class="hero">
          <button class="back-btn" type="button" @click="router.push('/library')">
            <AppIcon name="chevron-left" :size="18" />
            返回资料库
          </button>
          <div class="hero-card">
            <div>
              <h1>{{ library.name }}</h1>
              <p>{{ library.description }}</p>
              <div class="tags">
                <span v-for="tag in library.tags" :key="tag">{{ tag }}</span>
              </div>
            </div>
            <div class="hero-actions">
              <button class="outline-btn" type="button" @click="uploadOpen = true">
                <AppIcon name="upload-cloud" :size="18" />
                上传资料
              </button>
              <button
                class="primary-btn"
                type="button"
                @click="router.push({ path: '/learning/new', query: { libraryId: library.id } })"
              >
                <AppIcon name="graduation" :size="18" />
                用于智能学习
              </button>
            </div>
          </div>
        </header>

        <div v-if="actionError" class="action-error" role="alert">
          <span>{{ actionError }}</span>
          <button type="button" aria-label="关闭" @click="actionError = ''">
            <AppIcon name="close" :size="14" />
          </button>
        </div>

        <section class="stats">
          <article>
            <strong>{{ fileCount }}</strong>
            <span>文件</span>
          </article>
          <article>
            <strong>{{ library.chunkCount }}</strong>
            <span>知识片段</span>
          </article>
          <article>
            <strong>{{ library.status === "ready" ? "已完成" : "处理中" }}</strong>
            <span>向量化状态</span>
          </article>
        </section>

        <div class="content-grid">
          <section class="panel files-panel">
            <div class="section-head">
              <h2>文件列表</h2>
              <label>
                <AppIcon name="search" :size="18" />
                <input v-model="searchQuery" placeholder="搜索文件" />
              </label>
            </div>
            <table>
              <thead>
                <tr>
                  <th>文件名</th>
                  <th>类型</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="file in files" :key="file.id">
                  <td>
                    <AppIcon name="file" :size="18" />
                    {{ file.name }}
                  </td>
                  <td>{{ file.type }}</td>
                  <td>
                    <span
                      class="status"
                      :class="{
                        success: file.status === 'ready',
                        active: file.status === 'processing',
                        failed: file.status === 'failed',
                      }"
                    >
                      {{ fileStatusLabel(file.status) }}
                    </span>
                  </td>
                  <td>{{ file.updatedAt }}</td>
                  <td>
                    <button
                      v-if="file.status === 'failed'"
                      class="text-btn"
                      type="button"
                      :disabled="libraryResourceStore.isMutating"
                      @click="retryFile(file)"
                    >
                      重试
                    </button>
                    <button
                      class="icon-btn"
                      type="button"
                      aria-label="预览"
                      @click="previewFile = file"
                    >
                      <AppIcon name="eye" :size="17" />
                    </button>
                    <button
                      class="icon-btn"
                      type="button"
                      aria-label="下载"
                      @click="downloadFile(file)"
                    >
                      <AppIcon name="download" :size="17" />
                    </button>
                    <button
                      class="icon-btn danger"
                      type="button"
                      aria-label="删除"
                      @click="deleteTarget = file"
                    >
                      <AppIcon name="trash" :size="17" />
                    </button>
                  </td>
                </tr>
                <tr v-if="!files.length">
                  <td class="table-empty" colspan="5">
                    {{ searchQuery.trim() ? "没有匹配的文件" : "暂无文件" }}
                  </td>
                </tr>
              </tbody>
            </table>
          </section>

          <aside class="panel summary-panel">
            <div class="panel-title">
              <AppIcon name="book" :size="22" />
              <h2>资料库摘要</h2>
            </div>
            <p>该资料库包含 {{ fileCount }} 个文件，共 {{ library.chunkCount }} 个知识片段。</p>
            <div class="summary-list">
              <article>
                <span>主要知识点</span>
                <strong>{{ extractKeywords().join("、") || "待上传资料后识别" }}</strong>
              </article>
              <article>
                <span>推荐用途</span>
                <strong>{{ getRecommendedUses() }}</strong>
              </article>
              <article>
                <span>最近更新</span>
                <strong>{{ formatDate(library.updatedAt) }}</strong>
              </article>
            </div>
          </aside>
        </div>

        <section class="panel mindmaps-panel">
          <div class="section-head">
            <h2>思维导图</h2>
            <button class="outline-btn" type="button" @click="createMindMap()">
              <AppIcon name="plus" :size="16" />
              新建思维导图
            </button>
          </div>
          <div v-if="mindMapsLoading" class="mindmaps-loading">
            <AppIcon name="loader" :size="24" class="spin" />
            <span>加载思维导图...</span>
          </div>
          <div v-else-if="mindMaps.length === 0" class="mindmaps-empty">
            <AppIcon name="layers" :size="48" color="var(--color-text-muted)" />
            <p>暂无思维导图</p>
            <p class="empty-hint">基于该资料库创建思维导图，帮助您梳理知识结构</p>
          </div>
          <div v-else class="mindmaps-grid">
            <div
              v-for="mindMap in mindMaps"
              :key="mindMap.id"
              class="mindmap-card"
              @click="router.push({ path: `/mindmap/${mindMap.id}` })"
            >
              <div class="mindmap-card-header">
                <AppIcon name="layers" :size="20" color="var(--color-primary)" />
                <h3>{{ mindMap.title }}</h3>
              </div>
              <div class="mindmap-card-meta">
                <span class="meta-item">
                  <AppIcon name="calendar" :size="14" />
                  {{ formatDate(mindMap.updateTime) }}
                </span>
              </div>
            </div>
          </div>
        </section>
      </template>
    </div>

    <UploadMaterialModal
      :open="uploadOpen"
      :library-id="library?.id ?? null"
      @close="uploadOpen = false"
    />

    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除文件"
      :message="deleteTarget ? `确认删除「${deleteTarget.name}」？此操作无法撤销。` : ''"
      confirm-text="删除"
      confirm-variant="danger"
      @close="deleteTarget = null"
      @confirm="confirmDeleteFile"
    />

    <FilePreviewModal
      :open="Boolean(previewFile)"
      :file-id="previewFile?.id ?? ''"
      :file-name="previewFile?.name ?? ''"
      :file-type="previewFile?.type ?? ''"
      @close="previewFile = null"
    />
  </StudentShell>
</template>

<style scoped>
.detail-page {
  min-height: 100%;
  padding: 34px 28px 56px;
  background: var(--color-bg);
  color: var(--color-text);
}

.detail-page,
.detail-page * {
  box-sizing: border-box;
}

.hero,
.stats,
.content-grid,
.detail-state,
.action-error {
  max-width: 1180px;
  margin-left: auto;
  margin-right: auto;
}

.detail-state {
  min-height: 480px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: var(--color-text-muted);
  text-align: center;
}

.detail-state strong {
  color: var(--color-text);
  font-size: 18px;
}

.detail-state button {
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.detail-state--error span {
  color: var(--color-danger);
  overflow-wrap: anywhere;
}

.action-error {
  min-height: 38px;
  margin-top: 14px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border));
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-danger);
  font-size: 13px;
}

.action-error button {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

h1,
h2,
p {
  margin: 0;
}

.back-btn {
  height: 28px;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--ui-hover-radius);
  padding: 0 8px;
}

.back-btn:hover,
.outline-btn:hover {
  background: var(--ui-hover-bg);
}

.back-btn .icon {
  width: 14px;
  height: 14px;
}

.hero-card {
  margin-top: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 24px;
  background: var(--color-surface);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
}

h1 {
  font-size: 30px;
  color: var(--color-text);
}

.hero-card p {
  margin-top: 10px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.tags span {
  padding: 5px 10px;
  border-radius: 6px;
  background: var(--color-hover);
  color: var(--color-text-muted);
  font-size: 13px;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.outline-btn,
.primary-btn {
  height: 42px;
  border-radius: 8px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-weight: 700;
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

.stats {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stats article,
.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.stats article {
  padding: 18px;
  display: grid;
  gap: 5px;
}

.stats strong {
  font-size: 26px;
  color: var(--color-text);
}

.stats span {
  color: var(--color-text-muted);
}

.content-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
}

.panel {
  padding: 20px;
}

.section-head,
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.panel-title {
  justify-content: flex-start;
}

h2 {
  font-size: 21px;
  color: var(--color-text);
}

.section-head label {
  width: 220px;
  height: 38px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: var(--color-text-muted);
}

.section-head input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  height: 44px;
  border-top: 1px solid var(--color-border);
  text-align: left;
  color: var(--color-text);
  font-size: 14px;
}

th {
  color: var(--color-text-muted);
  font-size: 13px;
}

td:first-child {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status {
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--color-hover);
  color: var(--color-text-muted);
  font-size: 13px;
}

.status.success {
  background: color-mix(in srgb, var(--color-success) 15%, var(--color-surface));
  color: var(--color-success);
}

.status.active {
  background: color-mix(in srgb, var(--color-info) 12%, var(--color-surface));
  color: var(--color-info);
}

.status.failed {
  background: color-mix(in srgb, var(--color-danger) 12%, var(--color-surface));
  color: var(--color-danger);
}

.icon-btn {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: var(--ui-hover-radius);
  padding: 6px;
}

.icon-btn:hover {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.icon-btn:disabled:hover {
  background: transparent;
  color: var(--color-text-muted);
}

.icon-btn.danger:hover {
  color: var(--color-danger);
}

.text-btn {
  height: 28px;
  margin-right: 4px;
  padding: 0 9px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
}

.table-empty {
  height: 120px;
  display: table-cell !important;
  color: var(--color-text-muted);
  text-align: center;
}

.summary-panel {
  align-self: start;
}

.summary-panel p {
  color: var(--color-text-muted);
  line-height: 1.7;
}

.summary-list {
  margin-top: 16px;
  display: grid;
  gap: 12px;
}

.summary-list article {
  border-top: 1px solid var(--color-border);
  padding-top: 12px;
}

.summary-list span {
  display: block;
  color: var(--color-text-muted);
  font-size: 13px;
  margin-bottom: 5px;
}

.summary-list strong {
  color: var(--color-text);
  line-height: 1.5;
}

.mindmaps-panel {
  margin-top: 18px;
}

.mindmaps-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: var(--color-text-muted);
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.mindmaps-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.mindmaps-empty p {
  margin: 12px 0 0 0;
  color: var(--color-text);
  font-size: 15px;
  font-weight: 500;
}

.mindmaps-empty .empty-hint {
  margin: 8px 0 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.mindmaps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.mindmap-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
  background: var(--color-surface);
  cursor: pointer;
  transition: all 0.2s;
}

.mindmap-card:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.mindmap-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.mindmap-card-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mindmap-card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

@media (max-width: 980px) {
  .hero-card,
  .stats,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    flex-wrap: wrap;
  }
}
</style>
