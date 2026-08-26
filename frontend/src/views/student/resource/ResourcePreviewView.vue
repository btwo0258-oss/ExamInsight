<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import StudentShell from "@/components/layout/StudentShell.vue";
import ResourcePreviewContent from "@/components/resource-preview/ResourcePreviewContent.vue";
import { fetchAssetContent } from "@/api/assetLibraryV2";
import {
  disposePreparedV2AssetPreview,
  prepareV2AssetPreview,
  type PreparedV2AssetPreview,
} from "@/features/resource-preview/v2AssetPreview";
import type { ResourcePreviewDto } from "@/types/contracts/library";
import type { SpreadsheetSheetDraft } from "@/types/contracts/spreadsheet";
import { renderMarkdownToHtml } from "@/utils/markdown";
import { downloadBlob } from "@/utils/download";

const route = useRoute();
const router = useRouter();

const preview = ref<ResourcePreviewDto | null>(null);
const documentBlob = ref<Blob | null>(null);
const presentationData = ref<ArrayBuffer | null>(null);
const sheets = ref<SpreadsheetSheetDraft[]>([]);
const activeSheetIndex = ref(0);
const loading = ref(true);
const downloading = ref(false);
const localError = ref("");

let requestSequence = 0;
let preparedPreview: PreparedV2AssetPreview | null = null;

const resourceId = computed(() =>
  typeof route.params.resourceId === "string" ? route.params.resourceId : "",
);
const resource = computed(() => preview.value?.resource ?? null);
const targetPage = computed(() => {
  const raw = Array.isArray(route.query.page) ? route.query.page[0] : route.query.page;
  const page = Number.parseInt(typeof raw === "string" ? raw : "", 10);
  return Number.isInteger(page) && page > 0 && page <= 1000 ? page : undefined;
});
const canRender = computed(() => preview.value?.status === "ready" && !localError.value);
const canDownload = computed(() => preview.value?.canDownload === true);
const textHtml = computed(() => {
  const content = preview.value?.textContent ?? "";
  const name = resource.value?.name.toLowerCase() ?? "";
  return name.endsWith(".md") || resource.value?.format === "Markdown"
    ? renderMarkdownToHtml(content)
    : "";
});
const stateTitle = computed(() => {
  if (localError.value) return "预览加载失败";
  if (preview.value?.status === "too_large") return "文件过大，暂不在线预览";
  if (preview.value?.status === "processing") return "文件正在处理中";
  return "无法在线预览";
});
const statusMessage = computed(() => {
  if (localError.value) return localError.value;
  if (preview.value?.errorMessage) return preview.value.errorMessage;
  switch (preview.value?.status) {
    case "processing":
      return "文件仍在安全检查或解析中，请稍后再试。";
    case "too_large":
      return "文件超过在线预览限制，可下载后查看。";
    case "unsupported":
      return "当前格式暂不支持在线预览，可下载后查看。";
    case "failed":
      return "预览处理失败，可下载原文件。";
    default:
      return "当前文件暂时无法预览。";
  }
});

function returnPath() {
  const value = route.query.returnTo;
  return typeof value === "string" && value.startsWith("/") && !value.startsWith("//")
    ? value
    : "/library";
}

function closePreview() {
  void router.push(returnPath());
}

function clearPreparedPreview() {
  if (preparedPreview) disposePreparedV2AssetPreview(preparedPreview);
  preparedPreview = null;
}

function resetContent() {
  clearPreparedPreview();
  preview.value = null;
  documentBlob.value = null;
  presentationData.value = null;
  sheets.value = [];
  activeSheetIndex.value = 0;
  localError.value = "";
}

async function loadPreview() {
  const sequence = ++requestSequence;
  resetContent();
  loading.value = true;
  try {
    const prepared = await prepareV2AssetPreview(resourceId.value);
    if (sequence !== requestSequence) {
      disposePreparedV2AssetPreview(prepared);
      return;
    }
    preparedPreview = prepared;
    preview.value = prepared.preview;
    documentBlob.value = prepared.documentBlob;
    presentationData.value = prepared.presentationData;
    sheets.value = prepared.sheets;
  } catch (error) {
    if (sequence === requestSequence) {
      localError.value = error instanceof Error ? error.message : "文件预览加载失败。";
    }
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function download() {
  if (!resource.value || !canDownload.value || downloading.value) return;
  downloading.value = true;
  localError.value = "";
  try {
    const blob = await fetchAssetContent(resource.value.resourceId, "attachment");
    downloadBlob(blob, resource.value.name);
  } catch (error) {
    localError.value = error instanceof Error ? error.message : "文件下载失败。";
  } finally {
    downloading.value = false;
  }
}

watch(resourceId, () => void loadPreview(), { immediate: true });

onBeforeUnmount(() => {
  requestSequence += 1;
  clearPreparedPreview();
});
</script>

<template>
  <StudentShell>
    <section class="resource-preview-workspace">
      <header class="preview-header">
        <button class="header-icon" type="button" aria-label="关闭预览" title="关闭预览" @click="closePreview">
          <AppIcon name="close" :size="19" />
        </button>
        <div class="preview-breadcrumb">
          <span>资料库</span>
          <AppIcon name="chevron-right" :size="14" />
          <strong>{{ resource?.name || "文件预览" }}</strong>
        </div>
        <button
          v-if="canDownload"
          class="download-button"
          type="button"
          :disabled="downloading"
          @click="download"
        >
          <AppIcon name="download" :size="17" />
          {{ downloading ? "下载中" : "下载" }}
        </button>
      </header>

      <main class="preview-stage">
        <section v-if="loading" class="preview-state">
          <span class="state-icon"><AppIcon name="file" :size="32" /></span>
          <h1>正在准备预览</h1>
          <p>请稍候。</p>
        </section>

        <section v-else-if="!canRender" class="preview-state">
          <span class="state-icon"><AppIcon name="file" :size="32" /></span>
          <h1>{{ stateTitle }}</h1>
          <p>{{ statusMessage }}</p>
          <button v-if="canDownload" type="button" @click="download">
            <AppIcon name="download" :size="17" />下载文件
          </button>
        </section>

        <ResourcePreviewContent
          v-else-if="preview"
          v-model:active-sheet-index="activeSheetIndex"
          :preview="preview"
          :presentation="null"
          :document-blob="documentBlob"
          :presentation-data="presentationData"
          :sheets="sheets"
          word-html=""
          :text-html="textHtml"
          :initial-page="targetPage"
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

.download-button,
.preview-state button {
  min-height: 36px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  cursor: pointer;
}

.download-button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.preview-stage {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 24px clamp(18px, 4vw, 64px) 48px;
}

.preview-state {
  width: min(760px, 100%);
  min-height: 68vh;
  margin: 0 auto;
  padding: 48px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-surface);
  text-align: center;
}

.preview-state h1,
.preview-state p {
  margin: 0;
}

.preview-state h1 {
  font-size: 20px;
}

.preview-state p {
  max-width: 560px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.state-icon {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: var(--color-hover);
}

@media (max-width: 720px) {
  .preview-header {
    padding: 0 10px;
  }

  .preview-stage {
    padding: 14px 10px 28px;
  }

  .download-button {
    width: 36px;
    padding: 0;
    overflow: hidden;
    color: transparent;
    gap: 0;
  }

  .download-button :deep(svg) {
    color: var(--color-text);
  }
}
</style>
