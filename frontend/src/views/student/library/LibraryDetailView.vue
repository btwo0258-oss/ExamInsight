<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import StudentShell from "@/components/layout/StudentShell.vue";
import UploadMaterialModal from "@/components/library/UploadMaterialModal.vue";
import { courseKnowledgeBases } from "@/mock";
import { isMockDataSource } from "@/config/dataSource";
import { useLibraryResourceStore } from "@/stores/libraryResource";
import { useKnowledgeBaseStore } from "@/stores/knowledgeBase";
import type { LibraryResource } from "@/stores/libraryResource";
import { presentationRepository } from "@/repositories/presentation";
import { spreadsheetRepository } from "@/repositories/spreadsheet";
import { resourcePreviewRoute } from "@/utils/resourcePreview";
import { downloadBlob } from "@/utils/download";

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
const knowledgeBaseId = computed(() => Number(route.params.id));
const library = computed(() => {
  if (!Number.isFinite(knowledgeBaseId.value) || knowledgeBaseId.value <= 0) return null;
  const stored =
    knowledgeBaseStore.current?.id === knowledgeBaseId.value
      ? knowledgeBaseStore.current
      : knowledgeBaseStore.list.find((item) => item.id === knowledgeBaseId.value);
  if (!stored) return null;
  const preset = isMockDataSource
    ? courseKnowledgeBases.find((item) => item.id === knowledgeBaseId.value)
    : undefined;
  return {
    id: knowledgeBaseId.value,
    name: stored.name,
    description: stored.description || "暂无说明",
    tags: preset?.tags || [],
    knowledgePoints: stored.knowledgePoints || [],
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
    if (item.knowledgeBaseId !== library.value?.id) return false;
    return (
      !query ||
      `${item.name} ${item.format} ${fileStatusLabel(item.status)}`
        .toLocaleLowerCase()
        .includes(query)
    );
  });
});
const fileCount = computed(() =>
  library.value
    ? Math.max(
        library.value.fileCount,
        libraryResourceStore.resources.filter((item) => item.knowledgeBaseId === library.value?.id)
          .length,
      )
    : 0,
);

function fileStatusLabel(status: LibraryResource["status"]) {
  return { waiting: "等待解析", processing: "向量化中", ready: "解析完成", failed: "解析失败" }[
    status
  ];
}

function getRecommendedUses(
  lib: {
    name: string;
    description: string;
    tags: string[];
    fileCount: number;
    chunkCount: number;
  } | null,
): string {
  if (!lib) return "待上传资料后识别";
  if (lib.fileCount === 0) return "待上传资料后识别";
  if (lib.chunkCount === 0) return "资料处理中，请稍后再试";

  // 根据知识库名称和描述动态生成推荐用途
  const name = lib.name.toLowerCase();
  const desc = lib.description.toLowerCase();
  const tags = lib.tags.map((t) => t.toLowerCase());

  if (name.includes("考试") || desc.includes("考试") || tags.some((t) => t.includes("考试"))) {
    return "期末复习、错题强化、模拟考试";
  }
  if (name.includes("项目") || desc.includes("项目") || tags.some((t) => t.includes("项目"))) {
    return "项目实操、代码案例、技术文档";
  }
  if (name.includes("课程") || desc.includes("课程") || tags.some((t) => t.includes("课程"))) {
    return "课程学习、知识问答、思维导图";
  }
  if (name.includes("论文") || desc.includes("论文") || tags.some((t) => t.includes("论文"))) {
    return "文献调研、论文写作、知识梳理";
  }

  return "知识问答、思维导图、个性化学习";
}

function presentationId(file: LibraryResource) {
  return file.externalKey?.startsWith("presentation:")
    ? file.externalKey.slice("presentation:".length)
    : "";
}

function spreadsheetId(file: LibraryResource) {
  return file.externalKey?.startsWith("spreadsheet:")
    ? file.externalKey.slice("spreadsheet:".length)
    : "";
}

function openFile(file: LibraryResource) {
  if (file.status !== "ready") return;
  void router.push(resourcePreviewRoute(file.resourceId, route.fullPath, "knowledge"));
}

async function loadDetail() {
  detailLoading.value = true;
  detailError.value = "";
  if (!Number.isFinite(knowledgeBaseId.value) || knowledgeBaseId.value <= 0) {
    detailLoading.value = false;
    return;
  }
  try {
    await Promise.all([
      knowledgeBaseStore.getDetail(knowledgeBaseId.value),
      libraryResourceStore.fetchList(knowledgeBaseId.value),
    ]);
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : "获取知识库详情失败";
  } finally {
    detailLoading.value = false;
  }
}

async function retryFile(file: LibraryResource) {
  try {
    actionError.value = "";
    await libraryResourceStore.retry(file.resourceId);
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "重试解析失败";
  }
}

async function downloadFile(file: LibraryResource) {
  try {
    actionError.value = "";
    const id = presentationId(file);
    const sheetId = spreadsheetId(file);
    if (!id) {
      if (sheetId) {
        const blob = await spreadsheetRepository.download(sheetId);
        downloadBlob(blob, file.name);
        return;
      }
      await libraryResourceStore.download(file.resourceId, file.name);
      return;
    }
    const blob = await presentationRepository.download(id);
    downloadBlob(blob, file.name);
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
    await libraryResourceStore.remove(target.resourceId);
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "删除失败";
  }
}

function handleUploadClose() {
  uploadOpen.value = false;
  // 刷新知识库详情和资源列表
  void loadDetail();
}

onMounted(() => {
  void loadDetail();
});

watch(knowledgeBaseId, () => void loadDetail());
</script>

<template>
  <StudentShell>
    <div class="detail-page">
      <section v-if="detailLoading" class="detail-state" aria-live="polite">
        <strong>正在加载知识库…</strong>
      </section>
      <section v-else-if="detailError" class="detail-state detail-state--error" role="alert">
        <strong>知识库加载失败</strong>
        <span>{{ detailError }}</span>
        <button type="button" @click="loadDetail">重试</button>
      </section>
      <section v-else-if="!library" class="detail-state">
        <strong>知识库不存在或已被删除</strong>
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
                @click="
                  router.push({ path: '/learning/new', query: { knowledgeBaseId: library.id } })
                "
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
                <tr
                  v-for="file in files"
                  :key="file.resourceId"
                  :class="{ 'file-row--ready': file.status === 'ready' }"
                  @click="openFile(file)"
                >
                  <td>
                    <AppIcon
                      :name="
                        presentationId(file)
                          ? 'presentation'
                          : spreadsheetId(file)
                            ? 'grid'
                            : 'file'
                      "
                      :size="18"
                    />
                    {{ file.name }}
                  </td>
                  <td>{{ file.format }}</td>
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
                  <td @click.stop>
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
                      title="预览文件"
                      :disabled="file.status !== 'ready'"
                      @click="openFile(file)"
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
              <h2>知识库摘要</h2>
            </div>
            <p>
              {{
                library.description ||
                "该知识库包含上传的学习资料，可用于知识问答、个性化学习手册生成、思维导图和代码案例生成。"
              }}
            </p>
            <div class="summary-list">
              <article>
                <span>主要知识点</span>
                <strong>{{ library.knowledgePoints.join("、") || "待上传资料后识别" }}</strong>
              </article>
              <article>
                <span>推荐用途</span>
                <strong>{{ getRecommendedUses(library) }}</strong>
              </article>
              <article>
                <span>最近更新</span>
                <strong>{{ library.updatedAt }}</strong>
              </article>
            </div>
          </aside>
        </div>
      </template>
    </div>

    <UploadMaterialModal
      :open="uploadOpen"
      :knowledge-base-id="library?.id ?? null"
      @close="handleUploadClose"
    />

    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除文件"
      :message="deleteTarget ? `确认删除“${deleteTarget.name}”？此操作无法撤销。` : ''"
      confirm-text="删除"
      confirm-variant="danger"
      @close="deleteTarget = null"
      @confirm="confirmDeleteFile"
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

.file-row--ready {
  cursor: pointer;
}
.file-row--ready:hover td {
  background: var(--color-hover);
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
