<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { init } from "pptx-preview";
import type PPTXPreviewer from "pptx-preview/dist/previewer";

const props = defineProps<{ data: ArrayBuffer }>();
const container = ref<HTMLElement | null>(null);
const loading = ref(true);
const errorMessage = ref("");
let previewer: PPTXPreviewer | null = null;
let renderSequence = 0;

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
    const value = init(target, { width: 960, height: 540, mode: "list" });
    previewer = value;
    await value.preview(props.data.slice(0));
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
.pptx-pages {
  width: 100%;
  overflow: hidden;
}
.pptx-pages :deep(.pptx-preview-wrapper),
.pptx-pages :deep(.pptx-wrapper) {
  width: 100% !important;
  background: transparent !important;
}
.pptx-pages :deep(svg),
.pptx-pages :deep(.slide) {
  max-width: 100%;
}
</style>
