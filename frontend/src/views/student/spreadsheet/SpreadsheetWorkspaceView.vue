<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, FolderPlus, LoaderCircle, RefreshCw, Table2, X } from 'lucide-vue-next'
import AppSelectMenu from '@/components/common/AppSelectMenu.vue'
import LibraryKnowledgeCreateModal from '@/components/library/LibraryKnowledgeCreateModal.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useMessageStore } from '@/stores/message'
import { useSpreadsheetStore } from '@/stores/spreadsheet'
import type { SpreadsheetDto } from '@/types/contracts/spreadsheet'
import { toSpreadsheetChatCard } from '@/utils/spreadsheet'

const route = useRoute()
const router = useRouter()
const spreadsheetStore = useSpreadsheetStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const libraryResourceStore = useLibraryResourceStore()
const messageStore = useMessageStore()

const activeSheetIndex = ref(0)
const selectedKnowledgeBaseId = ref<number | null>(null)
const localError = ref('')
const successMessage = ref('')
const knowledgeCreateOpen = ref(false)

const currentSpreadsheet = computed(() => spreadsheetStore.current)
const activeSheet = computed(() => currentSpreadsheet.value?.workbook.sheets[activeSheetIndex.value] ?? null)
const isGenerating = computed(() => currentSpreadsheet.value?.status === 'generating')
const isReady = computed(() => currentSpreadsheet.value?.status === 'ready')
const isFailed = computed(() => ['failed', 'cancelled'].includes(currentSpreadsheet.value?.status ?? ''))
const knowledgeBaseOptions = computed(() => [
  { value: null, label: '无', icon: 'close' },
  ...knowledgeBaseStore.list.map((knowledgeBase) => ({
    value: knowledgeBase.id,
    label: knowledgeBase.name.replace(/资料库/g, '知识库'),
    icon: knowledgeBase.icon || 'folder',
  })),
])
const canUpdateAssociation = computed(() => (
  isReady.value
  && Boolean(currentSpreadsheet.value?.resourceId)
  && currentSpreadsheet.value?.knowledgeBaseId !== selectedKnowledgeBaseId.value
))

function stringQuery(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function setError(error: unknown, fallback: string) {
  localError.value = error instanceof Error ? error.message : fallback
}

function showMessage(message: string) {
  successMessage.value = message
  window.setTimeout(() => {
    if (successMessage.value === message) successMessage.value = ''
  }, 2200)
}

async function syncConversationCard(spreadsheet: SpreadsheetDto) {
  const conversationId = Number(spreadsheet.conversationId ?? stringQuery('conversationId'))
  if (!Number.isFinite(conversationId) || conversationId <= 0) return
  await messageStore.ensureLoaded(conversationId)
  const sourceMessageId = String(spreadsheet.sourceMessageId ?? stringQuery('sourceMessageId'))
  const messages = messageStore.getMessages(conversationId)
  const sourceMessage = messages.find((message) => message.id === sourceMessageId)
    ?? [...messages].reverse().find((message) => message.spreadsheetData?.spreadsheetId === spreadsheet.id)
  if (!sourceMessage) return
  const card = toSpreadsheetChatCard(spreadsheet)
  messageStore.updateLocalMessage(conversationId, sourceMessage.id, {
    kind: 'spreadsheet',
    spreadsheetData: {
      ...card,
      conversationId,
      sourceMessageId: card.sourceMessageId ?? sourceMessage.id,
    },
  })
}

async function retryGeneration() {
  localError.value = ''
  try {
    const spreadsheet = await spreadsheetStore.retry()
    await libraryResourceStore.fetchList()
    await syncConversationCard(spreadsheet)
    activeSheetIndex.value = 0
  } catch (error) {
    setError(error, '电子表格重试失败')
  }
}

async function cancelGeneration() {
  try {
    await spreadsheetStore.cancel()
  } catch (error) {
    setError(error, '停止电子表格生成失败')
  }
}

async function downloadSpreadsheet() {
  if (!currentSpreadsheet.value) return
  try {
    const blob = await spreadsheetStore.download()
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = currentSpreadsheet.value.fileName || `${currentSpreadsheet.value.config.title}.xlsx`
    anchor.click()
    URL.revokeObjectURL(url)
    showMessage('XLSX 已开始下载')
  } catch (error) {
    setError(error, '电子表格下载失败')
  }
}

async function updateKnowledgeBaseAssociation() {
  const spreadsheet = currentSpreadsheet.value
  if (!spreadsheet?.resourceId) return
  try {
    await libraryResourceStore.updateAssociations(spreadsheet.resourceId, {
      projectId: spreadsheet.projectId == null ? null : Number(spreadsheet.projectId),
      knowledgeBaseId: selectedKnowledgeBaseId.value,
    })
    spreadsheet.knowledgeBaseId = selectedKnowledgeBaseId.value
    await syncConversationCard(spreadsheet)
    showMessage(selectedKnowledgeBaseId.value === null ? '已取消知识库关联' : '已更新知识库关联')
  } catch (error) {
    setError(error, '更新知识库关联失败')
  }
}

function back() {
  const returnTo = stringQuery('returnTo')
  if (returnTo.startsWith('/')) void router.push(returnTo)
  else void router.push('/chat')
}

async function loadSpreadsheet() {
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  if (!id) {
    localError.value = '请先在对话中说明要求并生成电子表格'
    return
  }
  const spreadsheet = await spreadsheetStore.load(id)
  const archived = spreadsheet.resourceId
    ? libraryResourceStore.resources.find((resource) => resource.resourceId === spreadsheet.resourceId)
    : undefined
  selectedKnowledgeBaseId.value = archived?.knowledgeBaseId ?? (spreadsheet.knowledgeBaseId == null ? null : Number(spreadsheet.knowledgeBaseId))
  if (spreadsheet.status === 'generating' && spreadsheet.activeJobId) {
    const completed = await spreadsheetStore.resumeActiveJob()
    if (completed) await syncConversationCard(completed)
    await libraryResourceStore.fetchList()
  }
}

onMounted(async () => {
  try {
    await Promise.all([
      knowledgeBaseStore.isInitialized ? Promise.resolve() : knowledgeBaseStore.fetchList(),
      libraryResourceStore.fetchList(),
    ])
    await loadSpreadsheet()
  } catch (error) {
    setError(error, '电子表格预览加载失败')
  }
})
</script>

<template>
  <StudentShell>
    <div class="sheet-workspace">
      <header class="workspace-header">
        <button class="icon-command" type="button" title="返回" @click="back"><ArrowLeft :size="19" /></button>
        <div><span>电子表格预览</span><h1>{{ currentSpreadsheet?.config.title || '电子表格' }}</h1></div>
        <span class="archive-state">生成文件自动进入资料库</span>
      </header>

      <div v-if="localError || spreadsheetStore.errorMessage" class="workspace-error" role="alert">
        <span>{{ localError || spreadsheetStore.errorMessage }}</span>
        <button type="button" aria-label="关闭" @click="localError = ''; spreadsheetStore.clearError()"><X :size="16" /></button>
      </div>

      <main v-if="spreadsheetStore.isLoading && !currentSpreadsheet" class="status-view">
        <LoaderCircle class="spin" :size="38" />
        <h2>正在加载电子表格</h2>
      </main>

      <main v-else-if="isGenerating" class="status-view">
        <LoaderCircle class="spin" :size="38" />
        <h2>AI 正在生成电子表格</h2>
        <p>正在读取对话要求、附件、知识库和学习项目上下文。</p>
        <div class="progress-track"><i :style="{ width: `${spreadsheetStore.progress}%` }" /></div>
        <strong>{{ spreadsheetStore.progress }}%</strong>
        <button class="secondary-action status-action" type="button" :disabled="!spreadsheetStore.isSaving" @click="cancelGeneration">停止生成</button>
      </main>

      <main v-else-if="isFailed" class="status-view">
        <RefreshCw :size="36" />
        <h2>电子表格生成失败</h2>
        <p>{{ currentSpreadsheet?.errorMessage || '任务未完成，可以使用原要求重新生成。' }}</p>
        <button class="primary-action status-action" type="button" :disabled="spreadsheetStore.isSaving" @click="retryGeneration">
          <LoaderCircle v-if="spreadsheetStore.isSaving" class="spin" :size="17" /><RefreshCw v-else :size="17" />重试生成
        </button>
      </main>

      <main v-else-if="isReady" class="preview-layout">
        <section class="preview-main">
          <header class="preview-heading">
            <div class="preview-title"><Table2 :size="21" /><div><h2>{{ currentSpreadsheet?.fileName }}</h2><p>{{ currentSpreadsheet?.config.sheetCount }} 个工作表 · 已自动进入资料库</p></div></div>
            <button class="primary-action download-action" type="button" @click="downloadSpreadsheet"><Download :size="17" />下载 XLSX</button>
          </header>

          <div class="sheet-tabs" role="tablist" aria-label="工作表">
            <button v-for="(sheet, index) in currentSpreadsheet?.workbook.sheets" :key="sheet.sheetId" type="button" :class="{ active: activeSheetIndex === index }" @click="activeSheetIndex = index">{{ sheet.name }}</button>
          </div>

          <div v-if="activeSheet" class="table-scroll">
            <table>
              <thead><tr><th class="row-number">#</th><th v-for="column in activeSheet.columns" :key="column">{{ column }}</th></tr></thead>
              <tbody>
                <tr v-for="(row, rowIndex) in activeSheet.rows" :key="rowIndex">
                  <td class="row-number">{{ rowIndex + 1 }}</td>
                  <td v-for="(_, columnIndex) in activeSheet.columns" :key="columnIndex">{{ row[columnIndex] ?? '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="preview-empty">暂时没有可预览的数据</p>
        </section>

        <aside class="preview-side">
          <div><span>生成要求</span><strong>{{ currentSpreadsheet?.config.topic }}</strong></div>
          <div><span>关联项目</span><strong>{{ currentSpreadsheet?.projectId ? `项目 ${currentSpreadsheet.projectId}` : '无' }}</strong></div>
          <label class="field"><span>关联知识库</span><AppSelectMenu v-model="selectedKnowledgeBaseId" :options="knowledgeBaseOptions" aria-label="选择关联知识库" create-label="新建知识库" @create="knowledgeCreateOpen = true" /></label>
          <button class="secondary-action" type="button" :disabled="!canUpdateAssociation" @click="updateKnowledgeBaseAssociation"><FolderPlus :size="17" />{{ canUpdateAssociation ? '更新知识库关联' : '关联已同步' }}</button>
        </aside>
      </main>

      <p v-if="successMessage" class="workspace-toast">{{ successMessage }}</p>
    </div>
  </StudentShell>
  <LibraryKnowledgeCreateModal :open="knowledgeCreateOpen" @close="knowledgeCreateOpen = false" @created="selectedKnowledgeBaseId = $event; knowledgeCreateOpen = false" />
</template>

<style scoped>
.sheet-workspace,
.sheet-workspace * { box-sizing: border-box; }
.sheet-workspace { min-height: 100%; padding: 0 28px 48px; background: var(--color-bg); color: var(--color-text); }
.workspace-header { min-height: 74px; display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 12px; border-bottom: 1px solid var(--color-border); }
.workspace-header h1,
.workspace-header span,
.preview-heading h2,
.preview-heading p,
.status-view h2,
.status-view p { margin: 0; }
.workspace-header > div span { color: var(--color-text-muted); font-size: 11px; font-weight: 700; }
.workspace-header h1 { margin-top: 2px; font-size: 18px; }
.archive-state { padding: 5px 8px; border: 1px solid var(--color-border); border-radius: 6px; color: var(--color-text-muted); font-size: 11px; }
.icon-command { width: 32px; height: 32px; display: grid; place-items: center; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--color-text-muted); cursor: pointer; }
.icon-command:hover { background: var(--ui-hover-strong-bg); color: var(--color-text); }
.workspace-error { max-width: 1120px; min-height: 40px; margin: 18px auto 0; padding: 8px 10px 8px 13px; display: flex; align-items: center; justify-content: space-between; border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border)); border-radius: 7px; color: var(--color-danger); }
.workspace-error button { width: 28px; height: 28px; display: grid; place-items: center; border: 0; border-radius: 50%; background: transparent; color: inherit; cursor: pointer; }
.status-view { max-width: 640px; min-height: 470px; margin: 0 auto; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
.status-view h2 { margin-top: 18px; font-size: 19px; }
.status-view p { max-width: 500px; margin-top: 8px; color: var(--color-text-muted); font-size: 13px; line-height: 1.65; }
.status-view > strong { margin-top: 8px; font-size: 12px; }
.progress-track { width: min(420px, 90%); height: 5px; margin-top: 22px; overflow: hidden; border-radius: 999px; background: var(--color-hover); }
.progress-track i { display: block; height: 100%; background: var(--color-text); transition: width .3s ease; }
.preview-layout { max-width: 1180px; margin: 26px auto 0; display: grid; grid-template-columns: minmax(0, 1fr) 260px; align-items: start; gap: 20px; }
.preview-main { min-width: 0; }
.preview-heading { min-height: 62px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.preview-title { min-width: 0; display: flex; align-items: center; gap: 10px; }
.preview-title h2 { overflow-wrap: anywhere; font-size: 17px; }
.preview-title p { margin-top: 4px; color: var(--color-text-muted); font-size: 12px; }
.sheet-tabs { display: flex; gap: 5px; overflow-x: auto; border-bottom: 1px solid var(--color-border); }
.sheet-tabs button { min-height: 38px; padding: 0 13px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--color-text-muted); cursor: pointer; white-space: nowrap; }
.sheet-tabs button.active { border-bottom-color: var(--color-text); color: var(--color-text); }
.table-scroll { max-width: 100%; overflow: auto; border-bottom: 1px solid var(--color-border); }
.table-scroll table { width: 100%; min-width: 680px; border-collapse: collapse; background: var(--color-surface); }
.table-scroll th,
.table-scroll td { min-width: 128px; height: 42px; padding: 8px 11px; border-right: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); text-align: left; font-size: 12px; white-space: nowrap; }
.table-scroll th { position: sticky; top: 0; z-index: 1; background: var(--color-hover); font-weight: 700; }
.table-scroll .row-number { min-width: 48px; width: 48px; color: var(--color-text-muted); text-align: center; }
.preview-empty { min-height: 220px; display: grid; place-items: center; color: var(--color-text-muted); }
.preview-side { padding-left: 18px; display: grid; gap: 16px; border-left: 1px solid var(--color-border); }
.preview-side > div { display: grid; gap: 5px; }
.preview-side span,
.field > span { color: var(--color-text-muted); font-size: 12px; }
.preview-side strong { font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.field { display: grid; gap: 7px; }
.primary-action,
.secondary-action { min-height: 40px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 0 14px; border-radius: 7px; cursor: pointer; font-weight: 600; }
.primary-action { border: 1px solid var(--color-text); background: var(--color-text); color: var(--color-bg); }
.secondary-action { width: 100%; border: 1px solid var(--color-border); background: transparent; color: var(--color-text); }
.primary-action:disabled,
.secondary-action:disabled { cursor: not-allowed; opacity: .45; }
.download-action { flex: 0 0 auto; }
.status-action { width: auto; min-width: 150px; margin-top: 20px; }
.workspace-toast { position: fixed; left: 50%; bottom: 34px; z-index: 50; margin: 0; padding: 9px 13px; transform: translateX(-50%); border: 1px solid var(--color-border); border-radius: 7px; background: var(--color-surface); box-shadow: var(--shadow-md); font-size: 12px; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 900px) {
  .sheet-workspace { padding-inline: 18px; }
  .preview-layout { grid-template-columns: 1fr; }
  .preview-side { padding: 18px 0 0; border-top: 1px solid var(--color-border); border-left: 0; }
}

@media (max-width: 640px) {
  .sheet-workspace { padding-inline: 14px; }
  .workspace-header { grid-template-columns: 34px minmax(0, 1fr); }
  .archive-state { display: none; }
  .preview-heading { align-items: flex-start; flex-direction: column; }
  .download-action { width: 100%; }
}
</style>
