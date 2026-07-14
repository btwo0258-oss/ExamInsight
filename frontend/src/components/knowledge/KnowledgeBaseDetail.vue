<script setup lang="ts">
// @ts-nocheck
import { ref, onMounted, computed, onUnmounted, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useKnowledgeBaseStore } from "@/stores/knowledgeBase";
import { useDocumentStore } from "@/stores/document";
import { useConversationStore } from "@/stores/conversation";
import { useMessageStore } from "@/stores/message";
import { useMindMapStore } from "@/stores/mindmap";
import AppButton from "@/components/common/AppButton.vue";
import KnowledgeBaseCreate from "./KnowledgeBaseCreate.vue";
import AppInput from "@/components/common/AppInput.vue";
import AppIcon from "@/components/common/AppIcon.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import DocumentPreviewModal from "./DocumentPreviewModal.vue";

const router = useRouter();
const route = useRoute();
const kbStore = useKnowledgeBaseStore();
const docStore = useDocumentStore();
const conversationStore = useConversationStore();
const messageStore = useMessageStore();
const mindMapStore = useMindMapStore();

const showEditDialog = ref(false);
const showUploadDialog = ref(false);
const activeTab = ref("chat");

const activeMenuId = ref<number | null>(null);

// 错误弹窗状态
const showError = ref(false);
const errorMessage = ref("");

// 删除文档确认状态
const showDeleteDocConfirm = ref(false);
const deletingDocId = ref<number | null>(null);

// 预览弹窗状态
const showPreview = ref(false);
const previewDocId = ref<number | null>(null);
const previewFileName = ref("");

// 对话操作确认状态
const showConvActionConfirm = ref(false);
const convActionType = ref<"remove" | "delete" | "deleteKB">("delete");
const targetConvId = ref<number | null>(null);

const knowledgeBase = computed(() => {
  return kbStore.list.find((kb) => kb.id === Number(route.params.id));
});

const convActionTitle = computed(() => {
  if (convActionType.value === "deleteKB") return "删除知识库";
  if (convActionType.value === "remove") return "从知识库移除";
  return "删除对话";
});

const convActionMessage = computed(() => {
  if (convActionType.value === "deleteKB")
    return `确定要删除知识库"${knowledgeBase.value?.name}"吗？此操作不可撤销。`;
  if (convActionType.value === "remove")
    return "确定要从知识库中移除此对话吗？对话本身不会被删除。";
  return "确定要删除此对话吗？此操作不可撤销。";
});

const documents = computed(() => {
  return docStore.documents.filter((doc: { kbId: number }) => doc.kbId === Number(route.params.id));
});

const kbConversations = computed(() => {
  return conversationStore.list.filter((conv) => conv.knowledgeBaseId === Number(route.params.id));
});

const relatedMindMaps = computed(() => {
  return mindMapStore.mindMapList.filter((map) => map.kbId === Number(route.params.id));
});

const showCreateMindMapConfirm = ref(false);
const newMindMapTitle = ref("");
const showDeleteMapConfirm = ref(false);
const deletingMapId = ref<number | null>(null);

function handleCreateMindMap() {
  newMindMapTitle.value = "";
  showCreateMindMapConfirm.value = true;
}

async function confirmCreateMindMap() {
  if (newMindMapTitle.value.trim()) {
    const mapId = await mindMapStore.createMap(
      newMindMapTitle.value.trim(),
      Number(route.params.id),
    );
    router.push(`/mindmap/${mapId}`);
  }
  showCreateMindMapConfirm.value = false;
}

function handleDeleteMindMap(id: number) {
  deletingMapId.value = id;
  showDeleteMapConfirm.value = true;
}

async function confirmDeleteMindMap() {
  if (deletingMapId.value !== null) {
    await mindMapStore.deleteMap(deletingMapId.value);
    showDeleteMapConfirm.value = false;
    deletingMapId.value = null;
  }
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (!target.closest(".menu-container")) {
    activeMenuId.value = null;
  }
}

onMounted(async () => {
  if (route.params.id) {
    await kbStore.fetchAll();
    await docStore.fetchByKbId(Number(route.params.id));
    await conversationStore.fetchList();

    // 开始轮询处理中的文档
    startPolling();
  }
  window.addEventListener("click", handleClickOutside);
});

watch(
  () => route.params.id,
  async (newId) => {
    if (newId) {
      await kbStore.fetchAll();
      await docStore.fetchByKbId(Number(newId));
      await conversationStore.fetchList();
      startPolling();
    }
  },
);

let pollingTimer: any = null;
function startPolling() {
  if (pollingTimer) return;
  pollingTimer = setInterval(async () => {
    const pendingDocs = documents.value.filter((d) => d.status === "pending");
    if (pendingDocs.length === 0) {
      stopPolling();
      return;
    }

    for (const doc of pendingDocs) {
      await docStore.pollStatus(doc.id);
    }
  }, 2000);
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer);
    pollingTimer = null;
  }
}

onUnmounted(() => {
  window.removeEventListener("click", handleClickOutside);
  stopPolling();
});

function handleBack() {
  router.push("/knowledge");
}

function handleEdit() {
  showEditDialog.value = true;
}

async function handleDelete() {
  if (!knowledgeBase.value) return;
  convActionType.value = "deleteKB";
  showConvActionConfirm.value = true;
}

function handleUpload() {
  // 触发文件上传
  const input = document.createElement("input");
  input.type = "file";
  input.accept = ".pdf,.docx,.md,.txt";
  input.onchange = (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) {
      handleFileSelect(file);
    }
  };
  input.click();
}

async function handleFileSelect(file: File) {
  const maxSize = 100 * 1024 * 1024; // 100MB
  if (file.size > maxSize) {
    errorMessage.value = "文件大小不能超过 100MB";
    showError.value = true;
    return;
  }

  const allowedTypes = [".pdf", ".docx", ".md", ".txt"];
  const extension = file.name.substring(file.name.lastIndexOf(".")).toLowerCase();
  if (!allowedTypes.includes(extension)) {
    errorMessage.value = "仅支持 PDF、Word (docx)、Markdown、TXT 格式";
    showError.value = true;
    return;
  }

  if (!knowledgeBase.value) return;

  try {
    // upload 内部已经将新文档 unshift 到 documents 列表并更新了数量
    await docStore.upload(knowledgeBase.value.id, file);
    // 上传后启动轮询
    startPolling();
  } catch (err) {
    errorMessage.value = "上传失败";
    showError.value = true;
  }
}

function handleNewChat() {
  if (!knowledgeBase.value) return;
  conversationStore.create({
    kbId: knowledgeBase.value.id,
    title: `与${knowledgeBase.value.name}的对话`,
  });
}

function handleExamAnalysis() {
  if (knowledgeBase.value?.examAnalysisId) {
    router.push(`/exam-analysis/${knowledgeBase.value.examAnalysisId}`);
  } else {
    router.push("/exam-analysis/new");
  }
}

async function handleSendMessage(text: string, files?: File[]) {
  if (!text.trim() && (!files || files.length === 0)) return;
  if (!knowledgeBase.value) return;

  const kbId = knowledgeBase.value.id;

  // 注意：files 已经在 AppInput 的 @upload 事件中通过 handleFileSelect 上传过了
  // 这里不需要再次调用 handleFileSelect(file)

  // 1. 先创建会话
  const result = await messageStore.createConversation({
    knowledgeBaseId: kbId,
  });

  // 2. 将消息存储到 sessionStorage，让聊天页面在挂载后自动发送
  sessionStorage.setItem(
    `chat_auto_msg_${result.id}`,
    JSON.stringify({
      message: text,
      files: files?.map((f) => ({ name: f.name, type: f.type, size: f.size })),
    }),
  );

  // 3. 立即跳转到聊天页面
  router.push(`/chat/${result.id}`);
}

function toggleMenu(e: Event, id: number) {
  e.stopPropagation();
  activeMenuId.value = activeMenuId.value === id ? null : id;
}

async function handleRename(id: number, oldTitle: string) {
  const newTitle = prompt("重命名对话", oldTitle);
  if (newTitle && newTitle.trim() && newTitle !== oldTitle) {
    await conversationStore.rename(id, newTitle.trim());
  }
  activeMenuId.value = null;
}

async function handleRemoveFromKB(id: number) {
  targetConvId.value = id;
  convActionType.value = "remove";
  showConvActionConfirm.value = true;
  activeMenuId.value = null;
}

async function handleDeleteConversation(id: number) {
  targetConvId.value = id;
  convActionType.value = "delete";
  showConvActionConfirm.value = true;
  activeMenuId.value = null;
}

async function confirmConvAction() {
  if (convActionType.value === "deleteKB") {
    if (knowledgeBase.value) {
      await kbStore.remove(knowledgeBase.value.id);
      router.push("/knowledge");
    }
  } else if (targetConvId.value !== null) {
    if (convActionType.value === "remove") {
      await conversationStore.moveToKnowledgeBase(targetConvId.value, null);
    } else if (convActionType.value === "delete") {
      await conversationStore.remove(targetConvId.value);
    }
  }
  showConvActionConfirm.value = false;
  targetConvId.value = null;
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + " " + sizes[i];
}

function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    pending: "处理中",
    completed: "已完成",
    failed: "失败",
  };
  return statusMap[status] || status;
}
function handleDeleteDocument(docId: number) {
  deletingDocId.value = docId;
  showDeleteDocConfirm.value = true;
}

async function handlePreviewDocument(docId: number, fileName: string) {
  previewDocId.value = docId;
  previewFileName.value = fileName;
  showPreview.value = true;
}

async function handleDownloadDocument(docId: number, fileName: string) {
  await docStore.download(docId, fileName);
}

async function confirmDeleteDocument() {
  if (deletingDocId.value === null) return;

  const docId = deletingDocId.value;
  // store.remove 内部已经处理了文档删除和知识库数量更新
  await docStore.remove(docId);

  showDeleteDocConfirm.value = false;
  deletingDocId.value = null;
}
</script>

<template>
  <div class="knowledge-base-detail">
    <div class="header">
      <div class="header__left">
        <AppButton variant="ghost" @click="handleBack" class="back-btn">
          <AppIcon name="chevron-left" :size="20" />
        </AppButton>
        <div class="info">
          <div class="title-with-icon">
            <div
              class="icon-box"
              :style="{
                backgroundColor: knowledgeBase?.color ? knowledgeBase.color + '15' : 'transparent',
                color: knowledgeBase?.color || 'inherit',
              }"
            >
              <!-- @ts-ignore -->
              <AppIcon
                :name="knowledgeBase?.examAnalysisId ? 'pie-chart' : (knowledgeBase?.icon || 'book')"
                :size="32"
                :color="knowledgeBase?.color"
              />
            </div>
            <h1 class="title">{{ knowledgeBase?.name || "知识库" }}</h1>
          </div>
          <p class="description">{{ knowledgeBase?.description || "暂无描述" }}</p>
        </div>
      </div>
    </div>

    <div class="tabs">
      <button
        class="tab"
        :class="{ 'tab--active': activeTab === 'chat' }"
        @click="activeTab = 'chat'"
      >
        聊天
      </button>
      <button
        class="tab"
        :class="{ 'tab--active': activeTab === 'source' }"
        @click="activeTab = 'source'"
      >
        相关资料
      </button>
    </div>

    <div class="content">
      <!-- 聊天标签内容 -->
      <div v-if="activeTab === 'chat'" class="chat-content">
        <div v-if="kbConversations.length === 0" class="empty">
          <div class="empty__icon">
            <AppIcon name="message-square" :size="48" color="var(--color-text-muted)" />
          </div>
          <h3 class="empty__title">暂无聊天记录</h3>
          <p class="empty__description">开始与知识库对话</p>
          <AppButton variant="primary" @click="handleNewChat"> + 开始新对话 </AppButton>
        </div>
        <div v-else class="conversations">
          <div
            v-for="conv in kbConversations"
            :key="conv.id"
            class="conversation-item"
            @click="conversationStore.open(conv.id)"
          >
            <div class="conversation-item__info">
              <div class="conversation-item__title">{{ conv.title }}</div>
              <div class="conversation-item__meta">
                <span>{{
                  new Date(conv.updateTime || conv.createTime || Date.now()).toLocaleString()
                }}</span>
                <span v-if="conv.messageCount">{{ conv.messageCount }} 条消息</span>
              </div>
            </div>
            <div class="menu-container">
              <button class="more-btn" @click="toggleMenu($event, conv.id)">
                <AppIcon name="more-horizontal" :size="16" />
              </button>
              <div v-if="activeMenuId === conv.id" class="dropdown-menu ui-menu-panel">
                <div class="menu-item ui-menu-item" @click.stop="handleRename(conv.id, conv.title)">
                  <AppIcon class="ui-menu-icon" name="edit" :size="16" />
                  <span>重命名</span>
                </div>
                <div class="menu-item ui-menu-item" @click.stop="handleRemoveFromKB(conv.id)">
                  <AppIcon class="ui-menu-icon" name="close" :size="16" />
                  <span>移除</span>
                </div>
                <div
                  class="menu-item ui-menu-item ui-menu-item--danger"
                  @click.stop="handleDeleteConversation(conv.id)"
                >
                  <AppIcon class="ui-menu-icon" name="trash" :size="16" />
                  <span>删除</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 来源标签内容 -->
      <div v-else class="source-content">
        <div class="source-sections">
          <!-- Documents Section -->
          <div class="source-section">
            <div class="section__header">
              <h2 class="section__title">文档列表</h2>
              <div style="display: flex; gap: 8px">
                <AppButton variant="secondary" size="small" @click="handleExamAnalysis">
                  <template #icon>
                    <AppIcon name="zap" :size="14" />
                  </template>
                  {{ knowledgeBase?.examAnalysisId ? "查看分析" : "分析试卷" }}
                </AppButton>
                <AppButton
                  variant="primary"
                  size="small"
                  @click="handleUpload"
                  :loading="docStore.uploading"
                  :disabled="docStore.uploading"
                >
                  <template #icon>
                    <AppIcon v-if="!docStore.uploading" name="plus" :size="16" />
                  </template>
                  上传资料
                </AppButton>
              </div>
            </div>

            <div v-if="documents.length === 0" class="empty-small">
              <AppIcon name="file" :size="32" color="var(--color-text-muted)" />
              <p>暂无文档</p>
            </div>

            <div v-else class="documents">
              <div
                v-for="doc in documents"
                :key="doc.id"
                class="document-item"
                :class="{ 'document-item--clickable': doc.status === 'completed' }"
                @click="doc.status === 'completed' && handlePreviewDocument(doc.id, doc.fileName)"
              >
                <div class="document-item__icon">
                  <AppIcon
                    :name="
                      doc.fileType === 'pdf' ? 'pdf' : doc.fileType === 'docx' ? 'word' : 'file'
                    "
                    :size="24"
                  />
                </div>
                <div class="document-item__info">
                  <div class="document-item__name">{{ doc.fileName }}</div>
                  <div class="document-item__meta">
                    <span>{{ doc.fileType }}</span>
                    <span>{{ formatFileSize(doc.fileSize) }}</span>
                    <span>{{ doc.chunkCount }} 个分块</span>
                    <span class="type-info">
                      <AppIcon 
                        :name="doc.fileName.includes('卷') || doc.fileName.includes('真题') ? 'pdf' : 'book'" 
                        :size="12" 
                      />
                      <span>类型：{{ doc.fileName.includes('卷') || doc.fileName.includes('真题') ? '试卷' : '资料' }}</span>
                    </span>
                  </div>
                </div>
                <div class="document-item__status">
                  <span :class="['status', `status--${doc.status}`]">
                    <AppIcon
                      v-if="doc.status === 'pending'"
                      name="loader"
                      :size="12"
                      class="status-spin"
                    />
                    {{ getStatusText(doc.status) }}
                  </span>
                </div>
                <div class="document-item__actions">
                  <button
                    class="action-btn"
                    @click.stop="handleDownloadDocument(doc.id, doc.fileName)"
                    title="下载"
                  >
                    <AppIcon name="download" :size="16" />
                  </button>
                  <button
                    class="action-btn action-btn--danger"
                    @click.stop="handleDeleteDocument(doc.id)"
                    title="删除"
                  >
                    <AppIcon name="trash" :size="16" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Mind Maps Section -->
          <div class="source-section">
            <div class="section__header">
              <h2 class="section__title">相关思维导图</h2>
              <AppButton variant="secondary" size="small" @click="handleCreateMindMap">
                <template #icon>
                  <AppIcon name="plus" :size="16" />
                </template>
                新建导图
              </AppButton>
            </div>

            <div v-if="relatedMindMaps.length === 0" class="empty-small">
              <AppIcon name="layers" :size="32" color="var(--color-text-muted)" />
              <p>暂无思维导图</p>
            </div>

            <div v-else class="mindmaps">
              <div
                v-for="map in relatedMindMaps"
                :key="map.id"
                class="mindmap-item-card"
                @click="router.push(`/mindmap/${map.id}`)"
              >
                <div class="mindmap-item-card__icon">
                  <AppIcon name="layers" :size="24" color="#8b5cf6" />
                </div>
                <div class="mindmap-item-card__info">
                  <div class="mindmap-item-card__name">{{ map.title }}</div>
                  <div class="mindmap-item-card__meta">
                    <span>更新于 {{ new Date(map.updateTime).toISOString().split("T")[0] }}</span>
                  </div>
                </div>
                <div class="mindmap-item-card__actions">
                  <button
                    class="action-btn action-btn--danger"
                    @click.stop="handleDeleteMindMap(map.id)"
                    title="删除"
                  >
                    <AppIcon name="trash" :size="16" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <DocumentPreviewModal
      v-if="showPreview && previewDocId"
      :open="showPreview"
      :document-id="previewDocId"
      :file-name="previewFileName"
      @close="showPreview = false"
    />

    <div class="input-container">
      <AppInput
        placeholder="开始对话..."
        class="input"
        @send="handleSendMessage"
        @upload="handleFileSelect"
      />
      <div class="input-actions"></div>
    </div>

    <KnowledgeBaseCreate
      v-if="showEditDialog && knowledgeBase"
      :open="showEditDialog"
      :knowledge-base="knowledgeBase"
      @close="showEditDialog = false"
    />

    <ConfirmDialog
      :open="showError"
      title="提示"
      :message="errorMessage"
      confirm-text="知道了"
      cancel-text=""
      @close="showError = false"
      @confirm="showError = false"
    />

    <ConfirmDialog
      :open="showDeleteDocConfirm"
      title="确认删除"
      message="确定要删除此文档吗？此操作不可撤销。"
      confirm-text="删除"
      cancel-text="取消"
      @close="showDeleteDocConfirm = false"
      @confirm="confirmDeleteDocument"
    />

    <ConfirmDialog
      :open="showConvActionConfirm"
      :title="convActionTitle"
      :message="convActionMessage"
      confirm-text="确认"
      cancel-text="取消"
      @close="showConvActionConfirm = false"
      @confirm="confirmConvAction"
    />

    <ConfirmDialog
      :open="showCreateMindMapConfirm"
      title="新建思维导图"
      message=""
      confirm-text="创建"
      cancel-text="取消"
      @close="showCreateMindMapConfirm = false"
      @confirm="confirmCreateMindMap"
    >
      <div class="confirm-input-box">
        <p class="confirm-label">请输入思维导图名称</p>
        <input
          v-model="newMindMapTitle"
          class="plain-input"
          placeholder="思维导图名称"
          @keyup.enter="confirmCreateMindMap"
          autofocus
        />
      </div>
    </ConfirmDialog>

    <ConfirmDialog
      :open="showDeleteMapConfirm"
      title="确认删除"
      message="确定要删除此思维导图吗？此操作不可撤销。"
      confirm-text="删除"
      cancel-text="取消"
      @close="showDeleteMapConfirm = false"
      @confirm="confirmDeleteMindMap"
    />
  </div>
</template>

<style scoped>
.plain-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-input-bg, #ffffff);
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.plain-input:focus {
  border-color: var(--color-primary);
}

.confirm-input-box {
  padding: 16px 0;
}

.confirm-label {
  font-size: 14px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.knowledge-base-detail {
  max-width: 1200px;
  margin: 0 auto;
  padding: 64px 20px 32px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: flex-start;
  align-items: flex-end;
  margin-bottom: 32px;
  gap: 12px;
  padding-left: 0;
}

.header__left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex: 1;
}

.back-btn {
  padding: 8px !important;
  min-width: auto !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  margin-top: 0 !important;
  border-radius: 50% !important;
  border: 1px solid var(--color-border) !important;
}

.info {
  flex: 1;
  margin-top: 0;
  margin-right: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-with-icon {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-box {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-text);
  transition: all 0.2s ease;
}

.title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.description {
  font-size: 14px;
  color: var(--color-text-muted);
  line-height: 1.6;
  margin: 0;
}

.tabs {
  display: flex;
  gap: 32px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
  padding-left: 52px; /* 与 info 对齐 */
}

.tab {
  padding: 12px 0;
  border: none;
  background: transparent;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-muted);
  transition: all 0.2s ease;
}

.tab:hover {
  color: var(--color-text);
}

.tab--active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

.content {
  flex: 1;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}

.section__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section__title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.source-sections {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
}

@media (max-width: 900px) {
  .source-sections {
    grid-template-columns: 1fr;
  }
}

.source-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-small {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  background: var(--color-bg-alt);
  border-radius: 8px;
  border: 1px dashed var(--color-border);
  color: var(--color-text-muted);
  font-size: 13px;
  gap: 8px;
}

.mindmaps {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mindmap-item-card {
  display: flex;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  transition: all 0.2s ease;
  cursor: pointer;
  position: relative;
  gap: 12px;
}

.mindmap-item-card:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.mindmap-item-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #8b5cf615;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mindmap-item-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mindmap-item-card__name {
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mindmap-item-card__meta {
  font-size: 11px;
  color: var(--color-text-muted);
}

.mindmap-item-card__actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.mindmap-item-card:hover .mindmap-item-card__actions {
  opacity: 1;
}

.empty {
  text-align: center;
  padding: 60px 20px;
}

.empty__icon {
  margin-bottom: 16px;
  display: flex;
  justify-content: center;
}

.empty__title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}

.empty__description {
  font-size: 14px;
  color: var(--color-text-muted);
  margin-bottom: 24px;
}

.conversations {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.conversation-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  transition: all 0.2s ease;
  cursor: pointer;
  position: relative;
}

.conversation-item:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.conversation-item__info {
  flex: 1;
  min-width: 0;
}

.conversation-item__title {
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item__meta {
  font-size: 12px;
  color: var(--color-text-muted);
  display: flex;
  gap: 12px;
}

.menu-container {
  position: relative;
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .menu-container {
  opacity: 1;
}

.more-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.more-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  min-width: 168px;
  z-index: 100;
  margin-top: 4px;
}

.documents {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.document-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  transition: all 0.2s ease;
}

.document-item--clickable {
  cursor: pointer;
}

.document-item:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.document-item__icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-primary);
}

.document-item__info {
  flex: 1;
  min-width: 0;
}

.document-item__name {
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.document-item__meta {
  font-size: 12px;
  color: var(--color-text-muted);
  display: flex;
  gap: 12px;
}

.type-info {
  display: flex;
  align-items: center;
  gap: 4px;
}

.document-item__status {
  min-width: 80px;
  display: flex;
  justify-content: center;
}

.status {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.status--pending {
  background: #fff3cd;
  color: #856404;
}

.status--completed {
  background: #d4edda;
  color: #155724;
}

.status--failed {
  background: #f8d7da;
  color: #721c24;
}

.document-item__actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.document-item:hover .document-item__actions {
  opacity: 1;
}

.action-btn {
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: var(--color-surface-hover);
  color: var(--color-primary);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn--danger:hover {
  color: #ef4444 !important;
}

.input-container {
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--color-surface);
  border-radius: 12px;
  padding: 2px;
  margin-top: 12px;
  margin-bottom: 2px;
}

.input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  padding: 6px 10px;
  font-size: 14px;
  min-height: 32px;
}
</style>
