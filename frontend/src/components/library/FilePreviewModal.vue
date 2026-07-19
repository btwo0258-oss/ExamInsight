<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import AppIcon from "@/components/common/AppIcon.vue";
import { getStoredToken } from "@/api/request";
import mammoth from "mammoth";

const props = defineProps<{
  open: boolean;
  fileId: string | number;
  fileName: string;
  fileType: string;
}>();

const emit = defineEmits<{
  close: [];
}>();

const loading = ref(false);
const error = ref("");
const previewUrl = ref("");
const textContent = ref("");
const htmlContent = ref("");
const previewMode = ref<"pdf" | "image" | "text" | "docx" | "unsupported">("unsupported");

const isTextMode = computed(() => previewMode.value === "text");
const isDocxMode = computed(() => previewMode.value === "docx");
const isUnsupported = computed(() => previewMode.value === "unsupported");

function resourceId() {
  const id = String(props.fileId);
  return id.includes("-") || id.includes(":") ? id : `doc-${id}`;
}

async function fetchPreviewFile(headers: Record<string, string>) {
  return fetch(`/api/resources/${resourceId()}/preview-file`, { headers });
}

async function loadPreview() {
  if (!props.open || !props.fileId) return;

  loading.value = true;
  error.value = "";
  previewUrl.value = "";
  textContent.value = "";

  const type = props.fileType.toLowerCase();

  try {
    // Use fetch to avoid axios interceptor issues with blob responses
    const token = getStoredToken();
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

    if (type === "pdf") {
      previewMode.value = "pdf";
      const response = await fetchPreviewFile(headers);
      if (!response.ok) {
        const text = await response.text();
        try {
          const json = JSON.parse(text);
          throw new Error(json.message || `HTTP ${response.status}`);
        } catch {
          throw new Error(`HTTP ${response.status}`);
        }
      }
      const blob = await response.blob();
      previewUrl.value = URL.createObjectURL(blob);
    } else if (["png", "jpg", "jpeg", "gif", "bmp", "webp"].includes(type)) {
      previewMode.value = "image";
      const response = await fetchPreviewFile(headers);
      if (!response.ok) {
        const text = await response.text();
        try {
          const json = JSON.parse(text);
          throw new Error(json.message || `HTTP ${response.status}`);
        } catch {
          throw new Error(`HTTP ${response.status}`);
        }
      }
      const blob = await response.blob();
      previewUrl.value = URL.createObjectURL(blob);
    } else if (["txt", "md"].includes(type)) {
      previewMode.value = "text";
      const response = await fetchPreviewFile(headers);
      if (!response.ok) {
        const text = await response.text();
        try {
          const json = JSON.parse(text);
          throw new Error(json.message || `HTTP ${response.status}`);
        } catch {
          throw new Error(`HTTP ${response.status}`);
        }
      }
      textContent.value = await response.text();
    } else if (type === "docx") {
      previewMode.value = "docx";
      const response = await fetchPreviewFile(headers);
      if (!response.ok) {
        const text = await response.text();
        try {
          const json = JSON.parse(text);
          throw new Error(json.message || `HTTP ${response.status}`);
        } catch {
          throw new Error(`HTTP ${response.status}`);
        }
      }
      const blob = await response.blob();
      const arrayBuffer = await blob.arrayBuffer();
      const result = await mammoth.convertToHtml({ arrayBuffer });
      htmlContent.value = result.value;
    } else {
      previewMode.value = "unsupported";
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "加载失败";
  }

  loading.value = false;
}

function handleClose() {
  previewUrl.value = "";
  textContent.value = "";
  htmlContent.value = "";
  error.value = "";
  emit("close");
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") handleClose();
}

watch(
  () => props.open,
  (val) => {
    if (val) {
      document.addEventListener("keydown", handleKeydown);
      void loadPreview();
    } else {
      document.removeEventListener("keydown", handleKeydown);
    }
  },
);

onMounted(() => {
  if (props.open) void loadPreview();
});

onUnmounted(() => {
  document.removeEventListener("keydown", handleKeydown);
});
</script>

<template>
  <Teleport to="body">
    <Transition name="preview-fade">
      <div v-if="open" class="preview-overlay" @click.self="handleClose">
        <div class="preview-modal">
          <header class="preview-header">
            <div class="preview-title">
              <AppIcon name="eye" :size="18" />
              <span>{{ fileName }}</span>
            </div>
            <button class="preview-close" type="button" aria-label="关闭" @click="handleClose">
              <AppIcon name="close" :size="18" />
            </button>
          </header>

          <div class="preview-body">
            <div v-if="loading" class="preview-loading">
              <div class="spinner" />
              <span>正在加载预览…</span>
            </div>

            <div v-else-if="error" class="preview-error">
              <AppIcon name="alert-circle" :size="32" />
              <span>预览加载失败：{{ error }}</span>
            </div>

            <div v-else-if="isUnsupported" class="preview-unsupported">
              <AppIcon name="file" :size="48" />
              <span>该文件类型暂不支持预览</span>
              <small>{{ fileName }}</small>
            </div>

            <iframe
              v-else-if="previewMode === 'pdf'"
              :src="previewUrl"
              class="preview-iframe"
              frameborder="0"
            />

            <div v-else-if="previewMode === 'image'" class="preview-image-wrap">
              <img :src="previewUrl" :alt="fileName" class="preview-image" />
            </div>

            <pre v-else-if="isTextMode" class="preview-text">{{ textContent }}</pre>

            <div v-else-if="isDocxMode" class="preview-docx" v-html="htmlContent"></div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.6);
  display: grid;
  place-items: center;
  padding: 24px;
}

.preview-modal {
  width: 100%;
  max-width: 900px;
  max-height: 90vh;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
}

.preview-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-close {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.15s ease;
}

.preview-close:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.preview-body {
  flex: 1;
  min-height: 400px;
  max-height: calc(90vh - 70px);
  overflow: auto;
  display: grid;
  place-items: center;
  background: var(--color-bg);
}

.preview-loading,
.preview-error,
.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px;
  color: var(--color-text-muted);
  text-align: center;
}

.preview-loading .spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.preview-error {
  color: var(--color-danger);
}

.preview-unsupported small {
  font-size: 13px;
  color: var(--color-text-muted);
  word-break: break-all;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  min-height: 500px;
  border: 0;
}

.preview-image-wrap {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  padding: 20px;
  overflow: auto;
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.preview-text {
  width: 100%;
  height: 100%;
  min-height: 400px;
  margin: 0;
  padding: 20px;
  background: var(--color-surface);
  color: var(--color-text);
  font-family: "Courier New", monospace;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

.preview-docx {
  width: 100%;
  height: 100%;
  min-height: 400px;
  padding: 20px;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.6;
  overflow: auto;
}

.preview-docx :deep(h1),
.preview-docx :deep(h2),
.preview-docx :deep(h3),
.preview-docx :deep(h4),
.preview-docx :deep(h5),
.preview-docx :deep(h6) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
}

.preview-docx :deep(p) {
  margin: 0.5em 0;
}

.preview-docx :deep(ul),
.preview-docx :deep(ol) {
  margin: 0.5em 0;
  padding-left: 2em;
}

.preview-docx :deep(table) {
  border-collapse: collapse;
  margin: 1em 0;
  width: 100%;
}

.preview-docx :deep(th),
.preview-docx :deep(td) {
  border: 1px solid var(--color-border);
  padding: 8px;
  text-align: left;
}

.preview-docx :deep(th) {
  background: var(--color-hover);
  font-weight: 600;
}

.preview-fade-enter-active,
.preview-fade-leave-active {
  transition: opacity 0.2s ease;
}

.preview-fade-enter-from,
.preview-fade-leave-to {
  opacity: 0;
}
</style>
