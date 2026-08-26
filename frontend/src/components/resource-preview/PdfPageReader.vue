<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from "vue";
import {
  getDocument,
  GlobalWorkerOptions,
  type PDFDocumentLoadingTask,
  type PDFDocumentProxy,
} from "pdfjs-dist";
import pdfWorkerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import PdfCanvasPage from "./PdfCanvasPage.vue";

GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

const props = defineProps<{ src: string; initialPage?: number }>();
const reader = ref<HTMLElement | null>(null);
const document = shallowRef<PDFDocumentProxy | null>(null);
const loading = ref(true);
const errorMessage = ref("");
let loadingTask: PDFDocumentLoadingTask | null = null;
let loadSequence = 0;

const pageNumbers = computed(() =>
  document.value ? Array.from({ length: document.value.numPages }, (_, index) => index + 1) : [],
);

async function locateInitialPage() {
  if (!document.value || !props.initialPage) return;
  await nextTick();
  window.requestAnimationFrame(() => {
    reader.value
      ?.querySelector<HTMLElement>(`[data-pdf-page="${props.initialPage}"]`)
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
}

async function disposeDocument() {
  const task = loadingTask;
  loadingTask = null;
  if (task) await task.destroy().catch(() => undefined);
  document.value = null;
}

async function load() {
  const sequence = ++loadSequence;
  loading.value = true;
  errorMessage.value = "";
  await disposeDocument();
  try {
    const task = getDocument({ url: props.src });
    loadingTask = task;
    const value = await task.promise;
    if (sequence !== loadSequence) {
      await task.destroy().catch(() => undefined);
      return;
    }
    document.value = value;
    void locateInitialPage();
  } catch (error) {
    if (sequence === loadSequence) {
      errorMessage.value = error instanceof Error ? error.message : "PDF 加载失败";
    }
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

watch(() => props.src, () => void load(), { immediate: true });
watch(() => props.initialPage, () => void locateInitialPage());

onBeforeUnmount(() => {
  loadSequence += 1;
  void disposeDocument();
});
</script>

<template>
  <section ref="reader" class="pdf-reader">
    <div v-if="loading" class="reader-state">正在加载 PDF…</div>
    <div v-else-if="errorMessage" class="reader-state error">PDF 暂时无法显示</div>
    <template v-else-if="document">
      <PdfCanvasPage
        v-for="pageNumber in pageNumbers"
        :key="pageNumber"
        :document="document"
        :page-number="pageNumber"
        :data-pdf-page="pageNumber"
      />
    </template>
  </section>
</template>

<style scoped>
.pdf-reader {
  width: min(1080px, 100%);
  margin: 0 auto;
}
.reader-state {
  min-height: 360px;
  display: grid;
  place-items: center;
  color: var(--color-text-muted);
  font-size: 13px;
}
.reader-state.error {
  color: var(--color-danger, #d92d20);
}
</style>
