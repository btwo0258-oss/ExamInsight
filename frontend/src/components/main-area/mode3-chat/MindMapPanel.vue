<template>
  <Teleport to="body">
    <Transition name="slide">
      <div v-if="visible" class="mindmap-panel-overlay" @click.self="$emit('close')">
        <div class="mindmap-panel">
          <div class="panel-header">
            <h3 class="panel-title">🧠 {{ generatedData?.displayTitle || "知识图谱" }}</h3>
            <div class="panel-actions">
              <button
                class="panel-btn"
                @click="saveToKb"
                :disabled="!generatedData"
                title="保存到知识库"
              >
                <AppIcon name="book" :size="16" />
                <span>保存到知识库</span>
              </button>
              <button
                class="panel-btn"
                @click="openInEditor"
                :disabled="!generatedData"
                title="在编辑器中打开"
              >
                <AppIcon name="external-link" :size="16" />
                <span>编辑</span>
              </button>
              <button class="panel-close" @click="$emit('close')">
                <AppIcon name="x" :size="18" />
              </button>
            </div>
          </div>

          <div class="panel-content">
            <div v-if="isGenerating" class="generating-state">
              <div class="generating-spinner"></div>
              <p>AI 正在生成知识图谱...</p>
              <p class="generating-hint">正在分析回答内容，提炼知识结构</p>
            </div>

            <div v-else-if="generatedData" class="mindmap-container" ref="mindMapContainer"></div>

            <div v-else class="empty-state">
              <AppIcon name="layers" :size="48" color="var(--color-text-muted)" />
              <p>点击AI回答下方的 🧠 按钮生成知识图谱</p>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <AppModal :open="showKbModal" @close="showKbModal = false">
    <div class="kb-modal">
      <h3>保存到知识库</h3>
      <p class="kb-modal-desc">选择要保存到的知识库：</p>
      <div class="kb-list">
        <div
          v-for="kb in knowledgeBases"
          :key="kb.id"
          class="kb-item"
          :class="{ 'kb-item--selected': selectedKbId === kb.id }"
          @click="selectedKbId = kb.id"
        >
          <AppIcon name="book" :size="20" :color="kb.color || 'var(--color-primary)'" />
          <span>{{ kb.name }}</span>
        </div>
      </div>
      <div class="kb-modal-actions">
        <AppButton variant="secondary" @click="showKbModal = false">取消</AppButton>
        <AppButton variant="primary" @click="confirmSaveToKb" :disabled="!selectedKbId"
          >确认保存</AppButton
        >
      </div>
    </div>
  </AppModal>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import AppButton from "@/components/common/AppButton.vue";
import AppModal from "@/components/common/AppModal.vue";
import { generateMindMapFromAi, updateMindMap } from "@/api/mindmap";
import { getKnowledgeBases, type KnowledgeBase } from "@/api/knowledgeBase";
import MindMap from "simple-mind-map";
import KeyboardNavigation from "simple-mind-map/src/plugins/KeyboardNavigation.js";
import Drag from "simple-mind-map/src/plugins/Drag.js";

MindMap.usePlugin(KeyboardNavigation);
MindMap.usePlugin(Drag);

const props = defineProps<{
  visible: boolean;
  aiContent: string;
}>();

const emit = defineEmits<{
  close: [];
  saved: [mindMapId: number];
}>();

const router = useRouter();
const isGenerating = ref(false);
const generatedData = ref<any>(null);
const mindMapContainer = ref<HTMLElement | null>(null);
let mindMapInstance: any = null;

const showKbModal = ref(false);
const knowledgeBases = ref<KnowledgeBase[]>([]);
const selectedKbId = ref<number | null>(null);

function closePanel() {
  emit("close");
}

watch(
  () => [props.visible, props.aiContent] as const,
  async ([newVisible, newContent], [oldVisible]) => {
    if (newVisible && newContent) {
      if (!generatedData.value || generatedData.value.contentHash !== hashContent(newContent)) {
        generatedData.value = null;
        await generateMindMap();
      }
    }
  },
);

function hashContent(content: string): string {
  let hash = 0;
  for (let i = 0; i < content.length; i++) {
    const char = content.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash;
  }
  return String(hash);
}

async function generateMindMap() {
  if (!props.aiContent) return;
  isGenerating.value = true;
  try {
    const result = await generateMindMapFromAi(props.aiContent);
    result.contentHash = hashContent(props.aiContent);
    result.displayTitle = result.title || "知识图谱";
    generatedData.value = result;
    isGenerating.value = false;
    await nextTick();
    renderMindMap(result.treeData);
  } catch (error) {
    console.error("Failed to generate mind map:", error);
    isGenerating.value = false;
    alert("生成知识图谱失败，请稍后重试");
  }
}

async function renderMindMap(treeData: any) {
  if (!mindMapContainer.value) return;

  try {
    if (mindMapInstance) {
      mindMapInstance.destroy();
    }

    mindMapContainer.value.innerHTML = "";

    mindMapInstance = new MindMap({
      el: mindMapContainer.value,
      data: treeData || { data: { text: "知识图谱" }, children: [] },
      theme: "classic",
      layout: "logicalStructure",
      readonly: true,
      mousewheelAction: "zoom",
      initRootNodePosition: ["center", "center"],
      themeConfig: {
        root: { fillColor: "transparent", color: "#303133", fontSize: 18, fontWeight: "bold" },
        second: {
          fillColor: "#4f46e5",
          color: "#fff",
          fontSize: 14,
          margin: { top: 10, bottom: 10, left: 20, right: 20 },
        },
        node: { fontSize: 13, margin: { top: 5, bottom: 5, left: 10, right: 10 } },
      },
    });
  } catch (error) {
    console.error("Failed to render mind map:", error);
    if (mindMapContainer.value) {
      mindMapContainer.value.innerHTML =
        '<div style="padding:40px;text-align:center;color:var(--color-text-muted)">思维导图渲染失败</div>';
    }
  }
}

function saveToKb() {
  if (!generatedData.value) return;
  selectedKbId.value = null;
  fetchKnowledgeBases();
  showKbModal.value = true;
}

async function fetchKnowledgeBases() {
  try {
    const data = await getKnowledgeBases();
    knowledgeBases.value = data;
  } catch (error) {
    console.error("Failed to fetch knowledge bases:", error);
  }
}

async function confirmSaveToKb() {
  if (!generatedData.value || !selectedKbId.value) return;
  try {
    await updateMindMap({
      id: generatedData.value.id,
      kbId: selectedKbId.value,
    });
    showKbModal.value = false;
    alert("已保存到知识库！");
  } catch (error: any) {
    console.error("Failed to save to knowledge base:", error);
    const msg = error?.response?.data?.message || error?.message || "保存到知识库失败";
    alert(msg);
  }
}

function openInEditor() {
  if (!generatedData.value) return;
  emit("close");
  router.push(`/mindmap/${generatedData.value.id}`);
}

onUnmounted(() => {
  if (mindMapInstance) {
    mindMapInstance.destroy();
    mindMapInstance = null;
  }
});
</script>

<style scoped>
.mindmap-panel-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  display: flex;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.3);
}

:root[data-theme="dark"] .mindmap-panel-overlay {
  background: rgba(0, 0, 0, 0.5);
}

.mindmap-panel {
  width: 520px;
  max-width: 50vw;
  height: 100vh;
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.1);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.panel-btn:hover:not(:disabled) {
  background: var(--color-bg-alt);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.panel-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.panel-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.panel-close:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.panel-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.mindmap-container {
  width: 100%;
  height: 100%;
}

.generating-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: var(--color-text-muted);
}

.generating-state p {
  margin: 0;
  font-size: 15px;
}

.generating-hint {
  font-size: 13px !important;
  opacity: 0.7;
}

.generating-spinner {
  width: 40px;
  height: 40px;
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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
  color: var(--color-text-muted);
  font-size: 14px;
}

.slide-enter-active,
.slide-leave-active {
  transition:
    transform 0.3s ease,
    opacity 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

.kb-modal {
  padding: 8px;
}

.kb-modal h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--color-text);
}

.kb-modal-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0 0 20px 0;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 24px;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  color: var(--color-text);
}

.kb-item:hover {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.05);
}

.kb-item--selected {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.1);
}

.kb-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.slide-enter-to,
.slide-leave-from {
  transform: translateX(0);
  opacity: 1;
}
</style>
