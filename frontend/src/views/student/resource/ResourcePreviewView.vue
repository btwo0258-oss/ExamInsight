<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import LearningMindMapPreview from "@/components/learning/LearningMindMapPreview.vue";
import MindMapStaticPreview from "@/components/artifact/MindMapStaticPreview.vue";
import StudentShell from "@/components/layout/StudentShell.vue";
import PresentationSlidePreview from "@/components/presentation/PresentationSlidePreview.vue";
import { downloadLibraryResource, previewLibraryResource } from "@/api/libraryResource";
import { isMockDataSource } from "@/config/dataSource";
import { presentationRepository } from "@/repositories/presentation";
import { spreadsheetRepository } from "@/repositories/spreadsheet";
import { useLearningStore } from "@/stores/learning";
import type { LearningPlan, LearningResource } from "@/mock";
import type { ResourcePreviewDto } from "@/types/contracts/library";
import type { PresentationDto } from "@/types/contracts/presentation";
import type { SpreadsheetSheetDraft } from "@/types/contracts/spreadsheet";
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
const resource = computed(() => preview.value?.resource ?? null);
const activeSheet = computed(() => sheets.value[activeSheetIndex.value] ?? null);
const sourceLabel = computed(
  () =>
    ({
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
const generatedDocumentText = computed(() => preview.value?.previewData?.text ?? "");

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
  const token = localStorage.getItem("llm.token") || sessionStorage.getItem("llm.token");
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const mammoth = await import("mammoth");
  const result = await mammoth.convertToHtml({ arrayBuffer: await response.arrayBuffer() });
  wordHtml.value = result.value;
}

async function loadUploadedSpreadsheet(url: string) {
  const ExcelJS = await import("exceljs");
  const workbook = new ExcelJS.Workbook();
  const token = localStorage.getItem("llm.token") || sessionStorage.getItem("llm.token");
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  await workbook.xlsx.load(await response.arrayBuffer());
  sheets.value = workbook.worksheets.map((worksheet, sheetIndex) => {
    const rows = worksheet.getSheetValues().slice(1) as unknown[][];
    const width = Math.max(1, ...rows.map((row) => (Array.isArray(row) ? row.length - 1 : 0)));
    const normalized = rows.map((row) =>
      Array.from({ length: width }, (_, index) => {
        const value = Array.isArray(row) ? row[index + 1] : null;
        if (value == null) return null;
        if (typeof value === "string" || typeof value === "number" || typeof value === "boolean")
          return value;
        return String(value);
      }),
    );
    return {
      sheetId: String(worksheet.id || sheetIndex + 1),
      name: worksheet.name,
      columns: Array.from({ length: width }, (_, index) => String.fromCharCode(65 + (index % 26))),
      rows: normalized,
    };
  });
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
      const token = localStorage.getItem("llm.token") || sessionStorage.getItem("llm.token");
      const response = await fetch(value.previewUrl, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
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
    const blob = presentationId
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

watch(resourceId, () => void loadPreview(), { immediate: true });

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

        <div
          v-else-if="preview?.previewKind === 'presentation' && presentation"
          class="presentation-pages"
        >
          <article
            v-for="page in presentation.previewPages"
            :key="page.id"
            class="presentation-page"
          >
            <span>第 {{ page.order }} 页</span>
            <PresentationSlidePreview
              :page="page"
              :aspect-ratio="presentation.config.aspectRatio"
            />
          </article>
        </div>

        <div
          v-else-if="preview?.previewKind === 'presentation' && preview.previewData?.slides"
          class="presentation-pages generated-slides"
        >
          <article
            v-for="(slide, index) in preview.previewData.slides"
            :key="`${slide.title}-${index}`"
            class="generated-slide"
          >
            <span>第 {{ index + 1 }} 页</span>
            <div>
              <h2>{{ slide.title }}</h2>
              <ul>
                <li v-for="point in slide.points" :key="point">{{ point }}</li>
              </ul>
            </div>
          </article>
        </div>

        <section v-else-if="preview?.previewKind === 'spreadsheet'" class="spreadsheet-document">
          <div class="sheet-tabs" role="tablist" aria-label="工作表">
            <button
              v-for="(sheet, index) in sheets"
              :key="sheet.sheetId"
              type="button"
              :class="{ active: activeSheetIndex === index }"
              @click="activeSheetIndex = index"
            >
              {{ sheet.name }}
            </button>
          </div>
          <div v-if="activeSheet" class="sheet-table-wrap">
            <table>
              <thead>
                <tr>
                  <th class="row-number">#</th>
                  <th v-for="column in activeSheet.columns" :key="column">{{ column }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in activeSheet.rows" :key="rowIndex">
                  <td class="row-number">{{ rowIndex + 1 }}</td>
                  <td v-for="(_, index) in activeSheet.columns" :key="index">
                    {{ row[index] ?? "" }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="empty-preview">电子表格中没有可预览的数据</p>
        </section>

        <section v-else-if="preview?.previewKind === 'mindmap'" class="mindmap-document">
          <MindMapStaticPreview
            v-if="preview.previewData?.mindMap"
            :tree="preview.previewData.mindMap"
            :render-config="preview.previewData.mindMapConfig"
          />
          <LearningMindMapPreview
            v-else
            :title="resource?.name || '思维导图'"
            :tree-data="
              (preview as ResourcePreviewDto & { mindMapTreeData?: unknown }).mindMapTreeData
            "
          />
        </section>

        <article
          v-else-if="preview?.previewKind === 'word' && generatedDocumentText"
          class="paper-document generated-document"
        >
          <h1>{{ resource?.name }}</h1>
          <p>{{ generatedDocumentText }}</p>
        </article>
        <article
          v-else-if="preview?.previewKind === 'word'"
          class="paper-document word-document"
          v-html="wordHtml"
        />
        <article
          v-else-if="preview?.previewKind === 'text' && textHtml"
          class="paper-document markdown-document"
          v-html="textHtml"
        />
        <pre
          v-else-if="preview?.previewKind === 'text'"
          class="paper-document text-document"
        ><code>{{ preview.textContent }}</code></pre>
        <img
          v-else-if="preview?.previewKind === 'image' && preview.previewUrl"
          class="image-document"
          :src="preview.previewUrl"
          :alt="resource?.name"
        />
        <article
          v-else-if="preview?.previewKind === 'pdf' && generatedDocumentText"
          class="paper-document generated-document"
        >
          <h1>{{ resource?.name }}</h1>
          <p>{{ generatedDocumentText }}</p>
        </article>
        <iframe
          v-else-if="preview?.previewKind === 'pdf' && preview.previewUrl"
          class="pdf-document"
          :src="preview.previewUrl"
          :title="resource?.name"
        />
        <section
          v-else-if="preview?.previewKind === 'audio' && preview.previewUrl"
          class="audio-document"
        >
          <span class="state-icon"><AppIcon name="microphone" :size="30" /></span>
          <h1>{{ resource?.name }}</h1>
          <audio :src="preview.previewUrl" controls preload="metadata" />
          <article v-if="preview.transcript">
            <h2>识别文本</h2>
            <p>{{ preview.transcript }}</p>
          </article>
        </section>

        <section v-else class="preview-state">
          <span class="state-icon"><AppIcon name="file" :size="32" /></span>
          <h1>无法在线预览</h1>
          <p>当前预览内容不可用，可下载文件后查看</p>
          <button v-if="resource" type="button" @click="download">
            <AppIcon name="download" :size="17" />下载文件
          </button>
        </section>
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
.paper-document,
.spreadsheet-document,
.mindmap-document,
.audio-document {
  width: min(1040px, 100%);
  margin: 0 auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.paper-document {
  min-height: 76vh;
  padding: clamp(32px, 6vw, 88px);
  overflow-wrap: anywhere;
}
.text-document {
  margin: 0 auto;
  white-space: pre-wrap;
  color: var(--color-text);
  font:
    14px/1.75 ui-monospace,
    SFMono-Regular,
    Consolas,
    monospace;
}
.image-document {
  display: block;
  max-width: min(1200px, 100%);
  max-height: calc(100vh - 150px);
  margin: 0 auto;
  object-fit: contain;
  border-radius: 6px;
  box-shadow: var(--shadow-sm);
}
.pdf-document {
  display: block;
  width: min(1200px, 100%);
  height: calc(100vh - 145px);
  min-height: 620px;
  margin: 0 auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}
.presentation-pages {
  width: min(1100px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 34px;
}
.presentation-page > span {
  display: block;
  margin-bottom: 8px;
  color: var(--color-text-muted);
  font-size: 12px;
}
.generated-slide > div {
  aspect-ratio: 16 / 9;
  padding: clamp(28px, 5vw, 72px);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: linear-gradient(
    145deg,
    var(--color-surface),
    color-mix(in srgb, #d4552d 7%, var(--color-surface))
  );
  box-shadow: var(--shadow-sm);
}
.generated-slide h2 {
  margin: 0 0 26px;
  font-size: clamp(22px, 3vw, 38px);
}
.generated-slide li {
  margin: 10px 0;
  color: var(--color-text-muted);
}
.generated-document h1 {
  margin: 0 0 28px;
  font-size: 26px;
}
.generated-document p {
  white-space: pre-wrap;
  line-height: 1.85;
}
.spreadsheet-document {
  overflow: hidden;
}
.sheet-tabs {
  display: flex;
  gap: 2px;
  padding: 10px 12px 0;
  overflow-x: auto;
  border-bottom: 1px solid var(--color-border);
}
.sheet-tabs button {
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}
.sheet-tabs button.active {
  border-bottom-color: var(--color-text);
  color: var(--color-text);
  font-weight: 700;
}
.sheet-table-wrap {
  overflow: auto;
  max-height: calc(100vh - 185px);
}
.sheet-table-wrap table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.sheet-table-wrap th,
.sheet-table-wrap td {
  min-width: 120px;
  padding: 9px 10px;
  border: 1px solid var(--color-border);
  text-align: left;
  white-space: nowrap;
}
.sheet-table-wrap th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--color-hover);
}
.sheet-table-wrap .row-number {
  min-width: 50px;
  width: 50px;
  color: var(--color-text-muted);
  text-align: center;
}
.mindmap-document {
  height: calc(100vh - 150px);
  min-height: 560px;
  overflow: hidden;
}
.audio-document {
  min-height: 380px;
  padding: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.audio-document h1 {
  margin: 18px 0 26px;
  font-size: 20px;
}
.audio-document audio {
  width: min(560px, 100%);
}
.audio-document article {
  width: min(720px, 100%);
  margin-top: 30px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}
.audio-document article h2 {
  font-size: 15px;
}
.audio-document article p {
  color: var(--color-text-muted);
  line-height: 1.75;
}
.empty-preview {
  min-height: 300px;
  display: grid;
  place-items: center;
  color: var(--color-text-muted);
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
  .paper-document {
    min-height: 80vh;
    padding: 28px 20px;
  }
  .pdf-document,
  .mindmap-document {
    height: calc(100vh - 110px);
    min-height: 480px;
  }
}
</style>
