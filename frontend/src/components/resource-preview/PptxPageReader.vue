<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { init } from "pptx-preview";
import type PPTXPreviewer from "pptx-preview/dist/previewer";

const props = defineProps<{ data: ArrayBuffer; initialPage?: number }>();
const container = ref<HTMLElement | null>(null);
const loading = ref(true);
const errorMessage = ref("");
let previewer: PPTXPreviewer | null = null;
let renderSequence = 0;

function decorateSlides(target: HTMLElement) {
  const slides = Array.from(
    target.querySelectorAll<HTMLElement>(".pptx-preview-slide-wrapper"),
  );

  slides.forEach((slide, index) => {
    const frame = document.createElement("article");
    frame.className = "pptx-page-frame";
    frame.setAttribute("aria-label", `第 ${index + 1} 页`);
    frame.dataset.pptxPage = String(index + 1);

    const label = document.createElement("div");
    label.className = "pptx-page-label";
    label.textContent = `第 ${index + 1} 页`;

    slide.before(frame);
    frame.append(label, slide);
  });
}

function locateInitialPage() {
  if (!props.initialPage) return;
  window.requestAnimationFrame(() => {
    container.value
      ?.querySelector<HTMLElement>(`[data-pptx-page="${props.initialPage}"]`)
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
}

function destroy() {
  previewer?.destroy();
  previewer = null;
  container.value?.replaceChildren();
}

async function render() {
  const sequence = ++renderSequence;
  loading.value = true;
  errorMessage.value = "";
  await nextTick();
  destroy();
  const target = container.value;
  if (!target) return;
  try {
    const width = Math.max(320, Math.min(960, target.clientWidth || 960));
    // 列表模式必须交给外层阅读区滚动。传入固定 height 会让依赖创建
    // 一个内部滚动窗口，导致首尾幻灯片被截断并在页面下方留下空白。
    const value = init(target, { width, mode: "list" });
    previewer = value;
    await value.preview(props.data.slice(0));
    if (sequence === renderSequence) {
      decorateSlides(target);
      locateInitialPage();
    }
  } catch (error) {
    destroy();
    if (sequence === renderSequence) {
      errorMessage.value = error instanceof Error ? error.message : "PPTX 加载失败";
    }
  } finally {
    if (sequence === renderSequence) loading.value = false;
  }
}

watch(() => props.data, () => void render(), { immediate: true });
watch(() => props.initialPage, locateInitialPage);

onBeforeUnmount(() => {
  renderSequence += 1;
  destroy();
});
</script>

<template>
  <section class="pptx-reader">
    <div v-if="loading" class="reader-state">正在渲染演示文稿…</div>
    <div v-else-if="errorMessage" class="reader-state error">PPTX 暂时无法显示</div>
    <div ref="container" class="pptx-pages" />
  </section>
</template>

<style scoped>
.pptx-reader {
  width: 100%;
  margin: 0 auto;
  padding: 4px 0 28px;
}
.reader-state {
  width: min(960px, 100%);
  min-height: 420px;
  margin: 0 auto;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-size: 13px;
}
.reader-state.error {
  color: var(--color-danger, #d92d20);
}
.pptx-pages {
  width: 100%;
}
.pptx-pages :deep(.pptx-preview-wrapper) {
  width: 100% !important;
  height: auto !important;
  overflow: visible !important;
  background: transparent !important;
}
.pptx-pages :deep(.pptx-page-frame) {
  width: min(960px, 100%);
  margin: 0 auto 28px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.pptx-pages :deep(.pptx-page-label) {
  height: 38px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 600;
}
.pptx-pages :deep(.pptx-preview-slide-wrapper) {
  max-width: 100%;
  margin: 0 auto !important;
  box-shadow: none;
}

@media (max-width: 720px) {
  .pptx-reader {
    padding-bottom: 16px;
  }

  .pptx-pages :deep(.pptx-page-frame) {
    margin-bottom: 16px;
    border-radius: 10px;
  }

  .pptx-pages :deep(.pptx-page-label) {
    height: 34px;
    padding: 0 12px;
  }
}
</style>
