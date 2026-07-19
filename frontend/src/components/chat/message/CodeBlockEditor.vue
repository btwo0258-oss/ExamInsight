<template>
  <div class="code-block-editor">
    <div class="code-header">
      <span class="language-label">{{ language }}</span>
      <button class="copy-btn" @click="copyCode" :title="copied ? '已复制' : '复制代码'">
        <svg
          v-if="!copied"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
        </svg>
        <svg
          v-else
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <polyline points="20 6 9 17 4 12"></polyline>
        </svg>
      </button>
    </div>
    <div ref="editorContainer" class="editor-container"></div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

const props = defineProps<{
  code: string;
  language: string;
}>();

const copied = ref(false);

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(props.code);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 2000);
  } catch (err) {
    console.error("复制失败:", err);
  }
};
</script>

<style scoped>
.code-block-editor {
  border-radius: 8px;
  overflow: hidden;
  background: #1e1e1e;
  margin: 12px 0;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #3e3e3e;
}

.language-label {
  font-size: 12px;
  color: #9cdcfe;
  font-weight: 500;
  text-transform: uppercase;
}

.copy-btn {
  background: transparent;
  border: none;
  color: #9cdcfe;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.copy-btn:hover {
  background: rgba(156, 207, 254, 0.1);
  color: #ffffff;
}

.editor-container {
  height: auto;
  min-height: 100px;
  max-height: 600px;
  overflow: auto;
}
</style>
