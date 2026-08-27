<script setup lang="ts">
import { computed } from "vue";
import AppIcon from "@/components/common/AppIcon.vue";
import DocxPageReader from "@/components/resource-preview/DocxPageReader.vue";
import PdfPageReader from "@/components/resource-preview/PdfPageReader.vue";
import PptxPageReader from "@/components/resource-preview/PptxPageReader.vue";
import LearningMindMapPreview from "@/components/learning/LearningMindMapPreview.vue";
import MindMapStaticPreview from "@/components/artifact/MindMapStaticPreview.vue";
import PresentationSlidePreview from "@/components/presentation/PresentationSlidePreview.vue";
import type { ResourcePreviewDto } from "@/types/contracts/library";
import type { PresentationDto } from "@/types/contracts/presentation";
import type { SpreadsheetSheetDraft } from "@/types/contracts/spreadsheet";

const props = defineProps<{
  preview: ResourcePreviewDto;
  presentation: PresentationDto | null;
  sheets: SpreadsheetSheetDraft[];
  activeSheetIndex: number;
  documentBlob: Blob | null;
  presentationData: ArrayBuffer | null;
  wordHtml: string;
  textHtml: string;
  initialPage?: number;
}>();

const emit = defineEmits<{
  download: [];
  "update:activeSheetIndex": [value: number];
}>();

const resourceName = computed(() => props.preview.resource.name || "文件预览");
const generatedDocumentText = computed(() => props.preview.previewData?.text ?? "");
const activeSheet = computed(() => props.sheets[props.activeSheetIndex] ?? null);
const learningMindMapTree = computed(
  () => (props.preview as ResourcePreviewDto & { mindMapTreeData?: unknown }).mindMapTreeData,
);
</script>

<template>
  <PptxPageReader
    v-if="preview.previewKind === 'presentation' && presentationData"
    :data="presentationData"
    :initial-page="initialPage"
  />

  <div
    v-else-if="preview.previewKind === 'presentation' && presentation"
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
    v-else-if="preview.previewKind === 'presentation' && preview.previewData?.slides"
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

  <section v-else-if="preview.previewKind === 'spreadsheet'" class="spreadsheet-document">
    <div class="sheet-tabs" role="tablist" aria-label="工作表">
      <button
        v-for="(sheet, index) in sheets"
        :key="sheet.sheetId"
        type="button"
        :class="{ active: activeSheetIndex === index }"
        @click="emit('update:activeSheetIndex', index)"
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

  <section v-else-if="preview.previewKind === 'mindmap'" class="mindmap-document">
    <MindMapStaticPreview
      v-if="preview.previewData?.mindMap"
      :tree="preview.previewData.mindMap"
      :render-config="preview.previewData.mindMapConfig"
    />
    <LearningMindMapPreview
      v-else
      :title="resourceName"
      :tree-data="learningMindMapTree"
    />
  </section>

  <DocxPageReader
    v-else-if="preview.previewKind === 'word' && documentBlob"
    :blob="documentBlob"
  />
  <article
    v-else-if="preview.previewKind === 'word' && generatedDocumentText"
    class="paper-document generated-document"
  >
    <h1>{{ resourceName }}</h1>
    <p>{{ generatedDocumentText }}</p>
  </article>
  <article
    v-else-if="preview.previewKind === 'word'"
    class="paper-document word-document"
    v-html="wordHtml"
  />
  <article
    v-else-if="preview.previewKind === 'text' && textHtml"
    class="paper-document markdown-document"
    v-html="textHtml"
  />
  <pre
    v-else-if="preview.previewKind === 'text'"
    class="paper-document text-document"
  ><code>{{ preview.textContent }}</code></pre>
  <img
    v-else-if="preview.previewKind === 'image' && preview.previewUrl"
    class="image-document"
    :src="preview.previewUrl"
    :alt="resourceName"
  />
  <article
    v-else-if="preview.previewKind === 'pdf' && generatedDocumentText"
    class="paper-document generated-document"
  >
    <h1>{{ resourceName }}</h1>
    <p>{{ generatedDocumentText }}</p>
  </article>
  <PdfPageReader
    v-else-if="preview.previewKind === 'pdf' && preview.previewUrl"
    :src="preview.previewUrl"
    :initial-page="initialPage"
  />
  <section
    v-else-if="preview.previewKind === 'audio' && preview.previewUrl"
    class="audio-document"
  >
    <span class="state-icon"><AppIcon name="microphone" :size="30" /></span>
    <h1>{{ resourceName }}</h1>
    <audio :src="preview.previewUrl" controls preload="metadata" />
    <article v-if="preview.transcript">
      <h2>识别文本</h2>
      <p>{{ preview.transcript }}</p>
    </article>
  </section>

  <section v-else class="content-fallback">
    <span class="state-icon"><AppIcon name="file" :size="32" /></span>
    <h1>无法在线预览</h1>
    <p>当前预览内容不可用，可下载文件后查看</p>
    <button v-if="preview.canDownload" type="button" @click="emit('download')">
      <AppIcon name="download" :size="17" />下载文件
    </button>
  </section>
</template>

<style scoped>
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
  width: min(794px, 100%);
  min-height: 76vh;
  padding: clamp(40px, 7vw, 88px) clamp(32px, 8vw, 96px);
  overflow-wrap: anywhere;
}
.markdown-document {
  color: var(--color-text);
  font-size: 15px;
  line-height: 1.8;
}
.markdown-document :deep(h1),
.markdown-document :deep(h2),
.markdown-document :deep(h3),
.markdown-document :deep(h4) {
  color: var(--color-text);
  line-height: 1.35;
  text-wrap: balance;
}
.markdown-document :deep(h1) {
  margin: 0 0 30px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
  font-size: 30px;
}
.markdown-document :deep(h2) {
  margin: 38px 0 16px;
  font-size: 23px;
}
.markdown-document :deep(h3) {
  margin: 30px 0 12px;
  font-size: 18px;
}
.markdown-document :deep(h4) {
  margin: 24px 0 10px;
  font-size: 16px;
}
.markdown-document :deep(p),
.markdown-document :deep(ul),
.markdown-document :deep(ol),
.markdown-document :deep(blockquote),
.markdown-document :deep(pre),
.markdown-document :deep(table) {
  margin: 0 0 18px;
}
.markdown-document :deep(ul),
.markdown-document :deep(ol) {
  padding-left: 1.6em;
}
.markdown-document :deep(li + li) {
  margin-top: 7px;
}
.markdown-document :deep(a) {
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.markdown-document :deep(blockquote) {
  padding: 10px 16px;
  border-left: 3px solid var(--color-border-strong, var(--color-text-muted));
  color: var(--color-text-muted);
}
.markdown-document :deep(code) {
  padding: 2px 5px;
  border-radius: 5px;
  background: var(--color-hover);
  font: 0.9em/1.6 ui-monospace, SFMono-Regular, Consolas, monospace;
}
.markdown-document :deep(pre) {
  overflow-x: auto;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-hover);
}
.markdown-document :deep(pre code) {
  padding: 0;
  background: transparent;
}
.markdown-document :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.markdown-document :deep(th),
.markdown-document :deep(td) {
  padding: 9px 11px;
  border: 1px solid var(--color-border);
  text-align: left;
  vertical-align: top;
}
.markdown-document :deep(th) {
  background: var(--color-hover);
  font-weight: 700;
}
.markdown-document :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 24px auto;
}
.text-document {
  white-space: pre-wrap;
  color: var(--color-text);
  font: 14px/1.75 ui-monospace, SFMono-Regular, Consolas, monospace;
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
.presentation-pages {
  width: min(1100px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 34px;
}
.presentation-page > span,
.generated-slide > span {
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
.content-fallback {
  width: min(520px, 100%);
  min-height: 320px;
  margin: 8vh auto 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.content-fallback h1 {
  margin: 18px 0 0;
  font-size: 20px;
}
.content-fallback p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}
.content-fallback button {
  min-height: 36px;
  margin-top: 20px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
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

@media (max-width: 720px) {
  .paper-document {
    min-height: 80vh;
    padding: 28px 20px;
  }
  .mindmap-document {
    height: calc(100vh - 110px);
    min-height: 480px;
  }
}
</style>
