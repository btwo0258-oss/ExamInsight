<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import StudentShell from "@/components/layout/StudentShell.vue";
import ResourcePreviewContent from "@/components/resource-preview/ResourcePreviewContent.vue";
import { downloadLibraryResource, previewLibraryResource } from "@/api/libraryResource";
import { fetchAssetContent } from "@/api/assetLibraryV2";
import { sessionFetch } from "@/api/request";
import { isMockDataSource } from "@/config/dataSource";
import { presentationRepository } from "@/repositories/presentation";
import { spreadsheetRepository } from "@/repositories/spreadsheet";
import { useLearningStore } from "@/stores/learning";
import type { LearningPlan, LearningResource } from "@/mock";
import type { ResourcePreviewDto } from "@/types/contracts/library";
import type { PresentationDto } from "@/types/contracts/presentation";
import type { SpreadsheetSheetDraft } from "@/types/contracts/spreadsheet";
import { parseDocx, parseXlsx } from "@/features/resource-preview/previewParsers";
import {
  disposePreparedV2AssetPreview,
  prepareV2AssetPreview,
} from "@/features/resource-preview/v2AssetPreview";
import { renderMarkdownToHtml } from "@/utils/markdown";
import { subscribeResourcePreviewUpdates } from "@/utils/resourcePreviewSync";
import { downloadBlob } from "@/utils/download";

const route = useRoute();
const router = useRouter();
const learningStore = useLearningStore();

const preview = ref<ResourcePreviewDto | null>(null);
const presentation = ref<PresentationDto | null>(null);
const sheets = ref<SpreadsheetSheetDraft[]>([]);
const activeSheetIndex = ref(0);
const wordHtml = ref("");
const loading = ref(true);
const downloading = ref(false);
const localError = ref("");
let requestSequence = 0;

const resourceId = computed(() =>
  typeof route.params.resourceId === "string" ? route.params.resourceId : "",
);
const isV2LibraryAsset = computed(() => route.query.source === "library-v2");
const resource = computed(() => preview.value?.resource ?? null);
const sourceLabel = computed(
  () =>
    isV2LibraryAsset.value ? "资料库" : ({
      library: "资料库",
      knowledge: "知识库",
      learning: "智能学习资源包",
      chat: "对话生成文件",
    })[String(route.query.source)] ?? "资料库",
);
const textHtml = computed(() => {
  const content = preview.value?.textContent ?? "";
  const name = resource.value?.name.toLowerCase() ?? "";
  return name.endsWith(".md") || resource.value?.format === "Markdown"
    ? renderMarkdownToHtml(content)
    : "";
});
const statusMessage = computed(() => {
  const status = preview.value?.status as string | undefined;
  if (localError.value) return localError.value;
  if (status === "processing" || status === "waiting")
    return preview.value?.errorMessage || "文件仍在处理中，请稍后重试";
  if (status === "too_large")
    return preview.value?.errorMessage || "文件较大，暂不支持在线预览";
  if (status === "unsupported")
    return preview.value?.errorMessage || "当前格式暂不支持在线预览";
  if (status === "failed") return preview.value?.errorMessage || "文件预览加载失败";
  return "";
});
const canRender = computed(() => preview.value?.status === "ready" && !localError.value);

function internalReturnPath() {
  const value = route.query.returnTo;
  return typeof value === "string" && value.startsWith("/") && !value.startsWith("//")
    ? value
    : "/library";
}

function closePreview() {
  void router.push(internalReturnPath());
}

function clearObjectUrl() {
  const url = preview.value?.previewUrl;
  if (url?.startsWith("blob:")) URL.revokeObjectURL(url);
}

function presentationIdFromPreview(value: ResourcePreviewDto) {
  if (value.presentationId) return value.presentationId;
  return value.resource.externalKey?.startsWith("presentation:")
    ? value.resource.externalKey.slice("presentation:".length)
    : "";
}

function spreadsheetIdFromPreview(value: ResourcePreviewDto) {
  if (value.spreadsheetId) return value.spreadsheetId;
  return value.resource.externalKey?.startsWith("spreadsheet:")
    ? value.resource.externalKey.slice("spreadsheet:".length)
    : "";
}

function mindMapIdFromPreview(value: ResourcePreviewDto) {
  if (value.mindMapId) return String(value.mindMapId);
  const externalKey = value.resource.externalKey ?? "";
  const externalMatch = externalKey.match(/^mindmap[:-](\d+)$/);
  if (externalMatch) return externalMatch[1];
  const resourceMatch = value.resource.resourceId.match(/^mindmap-(\d+)$/);
  return resourceMatch?.[1] ?? "";
}

function editMindMap() {
  if (!preview.value) return;
  const id = mindMapIdFromPreview(preview.value);
  if (id) void router.push(`/mindmap/${id}`);
}

function findLearningResource(
  id: string,
  externalKey?: string,
): { plan: LearningPlan; resource: LearningResource } | undefined {
  for (const plan of learningStore.plans) {
    const matched = plan.resources.find((item) => item.resourceId === id);
    if (matched) return { plan, resource: matched };
  }
  const [, rawPlanId, rawResourceId] = externalKey?.split(":") ?? [];
  const planId = Number(rawPlanId);
  const learningResourceId = Number(rawResourceId);
  const plan = learningStore.plans.find((item) => item.id === planId);
  const resource = plan?.resources.find((item) => item.id === learningResourceId);
  if (plan && resource) return { plan, resource };
  return undefined;
}

function applyLearningPreview(value: ResourcePreviewDto) {
  const matched = findLearningResource(value.resource.resourceId, value.resource.externalKey);
  if (!matched) return;
  const { plan, resource: learningResource } = matched;
  if (learningResource.content) value.textContent = learningResource.content;
  else if (learningResource.group === "学习方案") {
    value.textContent = [
      `# ${plan.title}学习方案`,
      "",
      "## 学习目标",
      plan.goal,
      "",
      "## 个性化画像",
      plan.profile.map((item) => `- ${item.label}：${item.value}`).join("\n"),
      "",
      "## 学习路径",
      plan.stages
        .map(
          (stage) =>
            `### ${stage.title}\n${stage.desc}\n\n${stage.tasks.map((task) => `- ${task.title}（${task.duration}）`).join("\n")}`,
        )
        .join("\n\n"),
    ].join("\n");
  } else if (value.previewKind === "text") value.textContent = learningResource.desc;
  if (learningResource.previewUrl) value.previewUrl = learningResource.previewUrl;
  if (value.previewKind === "mindmap") {
    value.mindMapId = learningResource.mindMapId;
    const mindMap = learningResource.mindMapTreeData ?? {
      data: { text: learningResource.title },
      children: plan.dashboard.map((item) => ({ data: { text: item.label }, children: [] })),
    };
    value.previewData = {
      kind: "mindmap",
      mindMap: mindMap as NonNullable<ResourcePreviewDto["previewData"]>["mindMap"],
      mindMapConfig: learningResource.mindMapRenderConfig,
    };
  }
}

async function loadWord(url: string, fileName: string) {
  if (!fileName.toLowerCase().endsWith(".docx")) {
    throw new Error("Mock 环境暂不转换旧版 .doc，正式环境由后端转换后预览");
  }
  const response = await sessionFetch(url);
  wordHtml.value = await parseDocx(await response.blob());
}

async function loadUploadedSpreadsheet(url: string) {
  const response = await sessionFetch(url);
  sheets.value = await parseXlsx(await response.blob());
}

async function loadPreview() {
  const sequence = ++requestSequence;
  clearObjectUrl();
  preview.value = null;
  presentation.value = null;
  sheets.value = [];
  activeSheetIndex.value = 0;
  wordHtml.value = "";
  localError.value = "";
  loading.value = true;

  try {
    if (isV2LibraryAsset.value) {
      const prepared = await prepareV2AssetPreview(resourceId.value);
      if (sequence !== requestSequence) {
        disposePreparedV2AssetPreview(prepared);
        return;
      }
      preview.value = prepared.preview;
      wordHtml.value = prepared.wordHtml;
      sheets.value = prepared.sheets;
      return;
    }
    const value = await previewLibraryResource(resourceId.value);
    if (sequence !== requestSequence) return;
    preview.value = value;
    if (value.status !== "ready") return;

    // For PDF, image, and audio previews, fetch with auth headers and create blob URL
    if (
      (value.previewKind === "pdf" ||
        value.previewKind === "image" ||
        value.previewKind === "audio") &&
      value.previewUrl
    ) {
      const response = await sessionFetch(value.previewUrl);
      if (response.ok) {
        const blob = await response.blob();
        value.previewUrl = URL.createObjectURL(blob);
      }
    }

    if (isMockDataSource && value.resource.externalKey?.startsWith("learning:")) {
      if (!learningStore.plans.length) await learningStore.fetchPlans();
      applyLearningPreview(value);
    }

    if (value.previewData?.table) {
      sheets.value = [
        {
          sheetId: "preview",
          name: value.previewData.table.sheetName || "工作表 1",
          columns: value.previewData.table.columns,
          rows: value.previewData.table.rows,
        },
      ];
    }

    if (value.previewKind === "presentation") {
      const id = presentationIdFromPreview(value);
      if (id) presentation.value = await presentationRepository.get(id);
      else if (!value.previewData?.slides?.length)
        throw new Error("上传的 PPT 需要后端转换后才能在线预览，当前可先下载查看");
    } else if (value.previewKind === "spreadsheet") {
      const id = spreadsheetIdFromPreview(value);
      if (id) sheets.value = (await spreadsheetRepository.get(id)).workbook.sheets;
      else if (value.previewUrl) await loadUploadedSpreadsheet(value.previewUrl);
      else if (!value.previewData?.table) throw new Error("电子表格预览内容不可用");
    } else if (value.previewKind === "word") {
      if (value.previewUrl) await loadWord(value.previewUrl, value.resource.name);
      else if (!value.previewData?.text) throw new Error("Word 预览内容不可用");
    }
  } catch (error) {
    if (sequence !== requestSequence) return;
    localError.value = error instanceof Error ? error.message : "文件预览加载失败";
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function download() {
  if (!resource.value || downloading.value) return;
  downloading.value = true;
  localError.value = "";
  try {
    const presentationId = preview.value ? presentationIdFromPreview(preview.value) : "";
    const spreadsheetId = preview.value ? spreadsheetIdFromPreview(preview.value) : "";
    const blob = isV2LibraryAsset.value
      ? await fetchAssetContent(resource.value.resourceId, "attachment")
      : presentationId
        ? await presentationRepository.download(presentationId)
        : spreadsheetId
          ? await spreadsheetRepository.download(spreadsheetId)
          : await downloadLibraryResource(resource.value.resourceId);
    downloadBlob(blob, resource.value.name);
  } catch (error) {
    localError.value = error instanceof Error ? error.message : "文件下载失败";
  } finally {
    downloading.value = false;
  }
}

watch([resourceId, isV2LibraryAsset], () => void loadPreview(), { immediate: true });

const unsubscribePreviewUpdates = subscribeResourcePreviewUpdates((update) => {
  if (update.resourceId === resourceId.value) void loadPreview();
});

onBeforeUnmount(() => {
  unsubscribePreviewUpdates();
  requestSequence += 1;
  clearObjectUrl();
});
</script>

<template>
  <StudentShell>
    <section class="resource-preview-workspace">
      <header class="preview-header">
        <button
          class="header-icon"
          type="button"
          aria-label="关闭预览"
          title="关闭预览"
          @click="closePreview"
        >
          <AppIcon name="close" :size="19" />
        </button>
        <div class="preview-breadcrumb">
          <span>{{ sourceLabel }}</span>
          <AppIcon name="chevron-right" :size="14" />
          <strong>{{ resource?.name || "文件预览" }}</strong>
        </div>
        <div class="preview-header-actions">
          <button
            v-if="preview?.previewKind === 'mindmap' && mindMapIdFromPreview(preview)"
            class="download-button"
            type="button"
            @click="editMindMap"
          >
            <AppIcon name="edit" :size="17" />
            编辑
          </button>
          <button
            class="download-button"
            type="button"
            :disabled="!resource || downloading"
            @click="download"
          >
            <AppIcon name="download" :size="17" />
            {{ downloading ? "下载中" : "下载" }}
          </button>
        </div>
      </header>

      <main class="preview-stage">
        <section v-if="loading" class="preview-state">
          <AppIcon class="spin" name="loading" :size="34" />
          <h1>正在加载文件</h1>
          <p>请稍候</p>
        </section>

        <section v-else-if="!canRender" class="preview-state">
          <span class="state-icon"><AppIcon name="file" :size="32" /></span>
          <h1>
            {{ preview?.status === "too_large" ? "文件较大，暂不支持在线预览" : "无法在线预览" }}
          </h1>
          <p>{{ statusMessage }}</p>
          <button v-if="resource" type="button" @click="download">
            <AppIcon name="download" :size="17" />下载文件
          </button>
        </section>

        <ResourcePreviewContent
          v-else-if="preview"
          v-model:active-sheet-index="activeSheetIndex"
          :preview="preview"
          :presentation="presentation"
          :sheets="sheets"
          :word-html="wordHtml"
          :text-html="textHtml"
          @download="download"
        />
      </main>
    </section>
  </StudentShell>
</template>

<style scoped>
.resource-preview-workspace,
.resource-preview-workspace * {
  box-sizing: border-box;
}
.resource-preview-workspace {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-hover);
  color: var(--color-text);
}
.preview-header {
  flex: 0 0 60px;
  padding: 0 20px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}
.header-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}
.header-icon:hover {
  background: var(--color-hover);
  color: var(--color-text);
}
.preview-breadcrumb {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.preview-breadcrumb span {
  color: var(--color-text-muted);
}
.preview-breadcrumb strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.download-button,
.preview-state button {
  min-height: 36px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
}
.download-button:hover:not(:disabled),
.preview-state button:hover {
  background: var(--color-hover);
}
.download-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.preview-stage {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 28px clamp(18px, 4vw, 64px) 56px;
}
.preview-state {
  width: min(520px, 100%);
  min-height: 320px;
  margin: 8vh auto 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.preview-state h1,
.preview-state p {
  margin: 0;
}
.preview-state h1 {
  margin-top: 18px;
  font-size: 20px;
}
.preview-state p {
  max-width: 460px;
  margin-top: 8px;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}
.preview-state button {
  margin-top: 20px;
}
.state-icon {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.spin {
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .preview-header {
    padding: 0 10px;
    grid-template-columns: 38px minmax(0, 1fr) auto;
  }
  .download-button {
    width: 36px;
    padding: 0;
    font-size: 0;
  }
  .preview-stage {
    padding: 16px 10px 32px;
  }
}
</style>
