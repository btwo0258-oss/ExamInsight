<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { renderAsync } from "docx-preview";

const props = defineProps<{ blob: Blob }>();
const bodyContainer = ref<HTMLElement | null>(null);
const styleContainer = ref<HTMLElement | null>(null);
const loading = ref(true);
const errorMessage = ref("");
let renderSequence = 0;

function clear() {
  bodyContainer.value?.replaceChildren();
  styleContainer.value?.replaceChildren();
}

async function render() {
  const sequence = ++renderSequence;
  loading.value = true;
  errorMessage.value = "";
  await nextTick();
  clear();
  const body = bodyContainer.value;
  const styles = styleContainer.value;
  if (!body || !styles) return;
  try {
    await renderAsync(props.blob, body, styles, {
      inWrapper: true,
      breakPages: true,
      ignoreWidth: false,
      ignoreHeight: false,
      renderHeaders: true,
      renderFooters: true,
      renderFootnotes: true,
      renderEndnotes: true,
      renderComments: false,
      useBase64URL: true,
    });
  } catch (error) {
    if (sequence === renderSequence) {
      errorMessage.value = error instanceof Error ? error.message : "DOCX 加载失败";
    }
  } finally {
    if (sequence === renderSequence) loading.value = false;
  }
}

watch(() => props.blob, () => void render(), { immediate: true });

onBeforeUnmount(() => {
  renderSequence += 1;
  clear();
});
</script>

<template>
  <section class="docx-reader">
    <div ref="styleContainer" />
    <div v-if="loading" class="reader-state">正在排版文档…</div>
    <div v-else-if="errorMessage" class="reader-state error">DOCX 暂时无法显示</div>
    <div ref="bodyContainer" class="docx-pages" />
  </section>
</template>

<style scoped>
.docx-reader {
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
.docx-pages :deep(.docx-wrapper) {
  padding: 0;
  background: transparent;
}
.docx-pages :deep(.docx-wrapper > section.docx) {
  margin: 0 auto 24px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}
@media (max-width: 860px) {
  .docx-pages {
    overflow-x: auto;
  }
}
</style>
