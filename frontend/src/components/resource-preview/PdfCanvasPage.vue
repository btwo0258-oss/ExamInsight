<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";

const props = defineProps<{
  document: PDFDocumentProxy;
  pageNumber: number;
}>();

const frame = ref<HTMLElement | null>(null);
const canvas = ref<HTMLCanvasElement | null>(null);
const visible = ref(false);
const rendering = ref(false);
const rendered = ref(false);
const errorMessage = ref("");

let intersectionObserver: IntersectionObserver | null = null;
let resizeObserver: ResizeObserver | null = null;
let renderTask: RenderTask | null = null;
let animationFrame = 0;
let lastWidth = 0;

function cancelRender() {
  if (animationFrame) cancelAnimationFrame(animationFrame);
  animationFrame = 0;
  renderTask?.cancel();
  renderTask = null;
}

function scheduleRender(force = false) {
  if (!visible.value || !frame.value || !canvas.value) return;
  const width = Math.floor(frame.value.clientWidth);
  if (width <= 0 || (!force && rendered.value && Math.abs(width - lastWidth) < 2)) return;
  if (animationFrame) cancelAnimationFrame(animationFrame);
  animationFrame = requestAnimationFrame(() => void renderPage(width));
}

async function renderPage(width: number) {
  cancelRender();
  rendering.value = true;
  errorMessage.value = "";
  try {
    const page = await props.document.getPage(props.pageNumber);
    const baseViewport = page.getViewport({ scale: 1 });
    const cssScale = width / baseViewport.width;
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    const cssViewport = page.getViewport({ scale: cssScale });
    const renderViewport = page.getViewport({ scale: cssScale * pixelRatio });
    const target = canvas.value;
    if (!target) return;

    target.width = Math.max(1, Math.floor(renderViewport.width));
    target.height = Math.max(1, Math.floor(renderViewport.height));
    target.style.width = `${Math.floor(cssViewport.width)}px`;
    target.style.height = `${Math.floor(cssViewport.height)}px`;

    renderTask = page.render({ canvas: target, viewport: renderViewport });
    await renderTask.promise;
    lastWidth = width;
    rendered.value = true;
  } catch (error) {
    if (!(error instanceof Error && error.name === "RenderingCancelledException")) {
      errorMessage.value = "这一页暂时无法显示";
    }
  } finally {
    renderTask = null;
    rendering.value = false;
  }
}

onMounted(() => {
  if (typeof IntersectionObserver === "undefined") {
    visible.value = true;
  } else if (frame.value) {
    intersectionObserver = new IntersectionObserver(
      ([entry]) => {
        if (!entry?.isIntersecting) return;
        visible.value = true;
        intersectionObserver?.disconnect();
        intersectionObserver = null;
      },
      { rootMargin: "800px 0px" },
    );
    intersectionObserver.observe(frame.value);
  }

  if (typeof ResizeObserver !== "undefined" && frame.value) {
    resizeObserver = new ResizeObserver(() => scheduleRender());
    resizeObserver.observe(frame.value);
  }
});

watch(
  [visible, () => props.document, () => props.pageNumber],
  async () => {
    rendered.value = false;
    lastWidth = 0;
    await nextTick();
    scheduleRender(true);
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  intersectionObserver?.disconnect();
  resizeObserver?.disconnect();
  cancelRender();
});
</script>

<template>
  <article ref="frame" class="pdf-page-card" :aria-label="`第 ${pageNumber} 页`">
    <div class="page-label">第 {{ pageNumber }} 页</div>
    <div class="page-surface" :class="{ loading: rendering && !rendered }">
      <canvas ref="canvas" />
      <p v-if="errorMessage" class="page-error">{{ errorMessage }}</p>
      <div v-else-if="!rendered" class="page-placeholder" aria-hidden="true" />
    </div>
  </article>
</template>

<style scoped>
.pdf-page-card {
  width: min(1080px, 100%);
  margin: 0 auto 24px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.page-label {
  height: 38px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 600;
}
.page-surface {
  position: relative;
  width: 100%;
  min-height: 320px;
  display: grid;
  place-items: center;
  background: #fff;
}
.page-surface canvas {
  display: block;
  max-width: 100%;
  height: auto;
}
.page-placeholder {
  width: 100%;
  aspect-ratio: 1 / 1.4142;
  background: linear-gradient(110deg, #fff 8%, #f6f6f6 18%, #fff 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
}
.page-error {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}
@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}
</style>
