<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  BookOpen,
  Download,
  File,
  FileImage,
  FileSpreadsheet,
  FileText,
  FolderOpen,
  LoaderCircle,
  MoreHorizontal,
  Pencil,
  Plus,
  Presentation,
  RefreshCw,
  Search,
  Trash2,
  Unlink,
  Upload,
} from 'lucide-vue-next'
import AppButton from '@/components/common/AppButton.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import UploadMaterialModal from '@/components/library/UploadMaterialModal.vue'
import V2AssetPickerModal from '@/components/library/V2AssetPickerModal.vue'
import V2KnowledgeBaseModal from '@/components/library/V2KnowledgeBaseModal.vue'
import {
  fetchAssetContent,
  getKnowledgeBase,
  listKnowledgeBaseAssets,
  removeAssetFromKnowledgeBase,
} from '@/api/assetLibraryV2'
import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'
import { downloadBlob } from '@/utils/download'
import type { KnowledgeBase, LibraryAsset } from '@/types/contracts/assetLibraryV2'

const props = defineProps<{ id: string }>()
const router = useRouter()
const store = useAssetLibraryV2Store()
const knowledgeBase = ref<KnowledgeBase | null>(null)
const assets = ref<LibraryAsset[]>([])
const nextCursor = ref<string | null>(null)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const notice = ref('')
const searchQuery = ref('')
const uploadOpen = ref(false)
const pickerOpen = ref(false)
const editOpen = ref(false)
const removeTarget = ref<LibraryAsset | null>(null)
const trashKnowledgeBaseConfirmOpen = ref(false)
const detailMenuOpen = ref(false)
let refreshTimer: number | undefined

const visibleAssets = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  return query ? assets.value.filter((asset) => asset.name.toLocaleLowerCase().includes(query)) : assets.value
})
const readyCount = computed(() => assets.value.filter((asset) => asset.version?.indexStatus === 'READY').length)
const processingCount = computed(() => assets.value.filter((asset) => !['READY', 'FAILED', 'REJECTED', 'WITHDRAWN'].includes(asset.version?.status ?? '') || (asset.version?.status === 'READY' && !['READY', 'EMPTY', 'DEGRADED'].includes(asset.version.indexStatus))).length)

function formatDate(value: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}

function formatSize(bytes = 0) {
  if (!bytes) return '—'
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function fileIcon(asset: LibraryAsset) {
  const extension = asset.name.split('.').pop()?.toLowerCase() ?? ''
  if (['jpg', 'jpeg', 'png', 'webp'].includes(extension)) return FileImage
  if (['xlsx', 'csv'].includes(extension)) return FileSpreadsheet
  if (extension === 'pptx') return Presentation
  if (['txt', 'md', 'docx'].includes(extension)) return FileText
  return File
}

function status(asset: LibraryAsset) {
  const version = asset.version
  if (!version || version.status === 'QUARANTINED') return { label: '安全检查中', tone: 'pending' }
  if (version.status === 'PROCESSING') return { label: '解析中', tone: 'pending' }
  if (['FAILED', 'REJECTED', 'WITHDRAWN'].includes(version.status)) return { label: '处理失败', tone: 'error' }
  if (version.indexStatus === 'READY') return { label: '可用于 AI', tone: 'success' }
  if (version.indexStatus === 'EMPTY') return { label: '无可索引文本', tone: 'neutral' }
  if (version.indexStatus === 'DEGRADED') return { label: '部分索引失败', tone: 'error' }
  return { label: '向量化中', tone: 'pending' }
}

function isReadable(asset: LibraryAsset) {
  return ['PROCESSING', 'READY', 'FAILED'].includes(asset.version?.status ?? '')
}

async function loadPage(append = false, silent = false) {
  if (!silent) {
    if (append) loadingMore.value = true
    else loading.value = true
  }
  error.value = ''
  try {
    const [detail, page] = await Promise.all([
      append && knowledgeBase.value ? Promise.resolve({ knowledgeBase: knowledgeBase.value }) : getKnowledgeBase(props.id),
      listKnowledgeBaseAssets(props.id, append ? nextCursor.value : null),
    ])
    knowledgeBase.value = detail.knowledgeBase
    assets.value = append ? [...assets.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '知识库加载失败。'
  } finally {
    if (!silent) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

async function downloadAsset(asset: LibraryAsset) {
  error.value = ''
  try {
    const blob = await fetchAssetContent(asset.assetId, 'attachment')
    downloadBlob(blob, asset.name)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '下载失败。'
  }
}

async function confirmRemove() {
  const asset = removeTarget.value
  removeTarget.value = null
  if (!asset) return
  try {
    await removeAssetFromKnowledgeBase(props.id, asset.assetId)
    assets.value = assets.value.filter((item) => item.assetId !== asset.assetId)
    if (knowledgeBase.value) knowledgeBase.value.assetCount = Math.max(0, knowledgeBase.value.assetCount - 1)
    notice.value = `已从知识库移除“${asset.name}”，个人资料库中的原文件仍然保留。`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '移除资料失败。'
  }
}

async function trashKnowledgeBase() {
  const item = knowledgeBase.value
  trashKnowledgeBaseConfirmOpen.value = false
  if (!item) return
  try {
    await store.moveKnowledgeBaseToTrash(item.knowledgeBaseId)
    await router.replace('/library')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '移入回收站失败。'
  }
}

function handleKnowledgeBaseSaved(item: KnowledgeBase) {
  knowledgeBase.value = item
  notice.value = '知识库信息已保存。'
}

function handleAssetsAdded() {
  notice.value = '资料已加入知识库。'
  void loadPage()
}

function handleUploaded() {
  notice.value = '资料已上传并关联该知识库，安全检查和解析会在后台继续。'
  void Promise.all([loadPage(), store.loadAssets('library')])
}

function startRefreshTimer() {
  window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (document.hidden) return
    if (assets.value.some((asset) => !['READY', 'FAILED', 'REJECTED', 'WITHDRAWN'].includes(asset.version?.status ?? '') || (asset.version?.status === 'READY' && asset.version.indexStatus === 'PROCESSING'))) {
      void loadPage(false, true)
    }
  }, 5000)
}

watch(() => props.id, () => void loadPage())
onMounted(() => {
  void Promise.all([loadPage(), store.assets.length ? Promise.resolve() : store.refresh('library')])
  startRefreshTimer()
})
onBeforeUnmount(() => window.clearInterval(refreshTimer))
</script>

<template>
  <StudentShell>
    <main class="detail-page">
      <section v-if="loading" class="state-panel" aria-live="polite">
        <LoaderCircle class="spin" :size="28" />
        <strong>正在加载知识库…</strong>
      </section>
      <section v-else-if="!knowledgeBase" class="state-panel">
        <BookOpen :size="34" />
        <strong>知识库不可用</strong>
        <span>它可能已进入回收站或被删除。</span>
        <AppButton variant="secondary" @click="router.push('/library')">返回资料库</AppButton>
      </section>
      <template v-else>
        <header class="hero">
          <button class="back-button" type="button" @click="router.push('/library')">
            <ArrowLeft :size="17" />返回资料库
          </button>
          <div class="hero-card">
            <button class="detail-more" type="button" aria-label="知识库菜单" @click.stop="detailMenuOpen = !detailMenuOpen">
              <MoreHorizontal :size="19" />
            </button>
            <div v-if="detailMenuOpen" class="detail-menu">
              <button type="button" @click="editOpen = true; detailMenuOpen = false"><Pencil :size="15" />编辑知识库</button>
              <button class="danger" type="button" @click="trashKnowledgeBaseConfirmOpen = true; detailMenuOpen = false"><Trash2 :size="15" />移入回收站</button>
            </div>
            <div class="hero-copy">
              <span class="title-icon"><BookOpen :size="25" /></span>
              <div>
                <h1>{{ knowledgeBase.name }}</h1>
                <p>{{ knowledgeBase.description || '尚未填写说明' }}</p>
              </div>
            </div>
            <div class="hero-actions">
              <AppButton variant="secondary" @click="pickerOpen = true">
                <template #icon><Plus :size="15" /></template>从资料库添加
              </AppButton>
              <AppButton @click="uploadOpen = true">
                <template #icon><Upload :size="15" /></template>上传资料
              </AppButton>
            </div>
          </div>
        </header>

        <div v-if="notice" class="notice">
          <span>{{ notice }}</span><button type="button" aria-label="关闭提示" @click="notice = ''">×</button>
        </div>
        <div v-if="error" class="error-banner" role="alert">
          <span>{{ error }}</span><button type="button" @click="loadPage()"><RefreshCw :size="15" />重试</button>
        </div>

        <section class="facts">
          <article><strong>{{ knowledgeBase.assetCount }}</strong><span>资料总数</span></article>
          <article><strong>{{ readyCount }}</strong><span>本页可用于 AI</span></article>
          <article><strong>{{ processingCount }}</strong><span>本页处理中</span></article>
        </section>

        <div class="content-grid">
          <section class="panel files-panel">
            <div class="panel-header">
              <div>
                <h2>文件列表</h2>
                <p>移除只会解除知识库关联，不会删除个人资料库原文件。</p>
              </div>
              <label class="search-field">
                <Search :size="16" /><input v-model="searchQuery" placeholder="搜索文件" />
              </label>
            </div>

            <div v-if="visibleAssets.length" class="table-wrap">
              <table>
                <thead><tr><th>文件名</th><th>状态</th><th>内容</th><th>大小</th><th>更新时间</th><th aria-label="操作" /></tr></thead>
                <tbody>
                  <tr v-for="asset in visibleAssets" :key="asset.assetId">
                    <td><div class="file-name"><span><component :is="fileIcon(asset)" :size="18" /></span><strong :title="asset.name">{{ asset.name }}</strong></div></td>
                    <td><span class="status-chip" :class="`is-${status(asset).tone}`">{{ status(asset).label }}</span></td>
                    <td>{{ asset.version?.chunkCount ?? 0 }} 个片段</td>
                    <td>{{ formatSize(asset.version?.sizeBytes) }}</td>
                    <td>{{ formatDate(asset.updatedAt) }}</td>
                    <td>
                      <div class="row-actions">
                        <button
                          :disabled="!isReadable(asset)"
                          @click="router.push({ path: `/resources/${asset.assetId}/preview`, query: { source: 'library-v2', returnTo: `/library/${props.id}` } })"
                        >预览</button>
                        <button :disabled="!isReadable(asset)" aria-label="下载" @click="downloadAsset(asset)"><Download :size="16" /></button>
                        <button class="danger" aria-label="从知识库移除" title="从知识库移除" @click="removeTarget = asset"><Unlink :size="16" /></button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-else class="empty-panel">
              <FolderOpen :size="34" />
              <strong>{{ searchQuery.trim() ? '没有匹配的资料' : '知识库里还没有资料' }}</strong>
              <span>{{ searchQuery.trim() ? '换个关键词试试。' : '可上传新资料，或从个人资料库选择已有资料。' }}</span>
              <div v-if="!searchQuery.trim()">
                <AppButton variant="secondary" @click="pickerOpen = true">从资料库添加</AppButton>
                <AppButton @click="uploadOpen = true">上传新资料</AppButton>
              </div>
            </div>
            <div v-if="nextCursor" class="load-more"><AppButton variant="secondary" :loading="loadingMore" @click="loadPage(true)">加载更多</AppButton></div>
          </section>

          <aside class="panel summary-panel">
            <div class="summary-title"><BookOpen :size="21" /><h2>知识库信息</h2></div>
            <p>{{ knowledgeBase.description || '尚未填写说明' }}</p>
            <div class="summary-list">
              <article><span>资料数量</span><strong>{{ knowledgeBase.assetCount }} 个</strong></article>
              <article><span>当前页索引状态</span><strong>{{ processingCount ? `${processingCount} 个处理中` : '全部处理完成' }}</strong></article>
              <article><span>最近更新</span><strong>{{ formatDate(knowledgeBase.updatedAt) }}</strong></article>
            </div>
          </aside>
        </div>
      </template>
    </main>

    <UploadMaterialModal :open="uploadOpen" :knowledge-base-id="knowledgeBase?.knowledgeBaseId" @close="uploadOpen = false" @uploaded="handleUploaded" />
    <V2AssetPickerModal :open="pickerOpen" :knowledge-base-id="props.id" :existing-asset-ids="assets.map((asset) => asset.assetId)" @close="pickerOpen = false" @added="handleAssetsAdded" />
    <V2KnowledgeBaseModal :open="editOpen" :knowledge-base="knowledgeBase" @close="editOpen = false" @saved="handleKnowledgeBaseSaved" />
    <ConfirmDialog :open="Boolean(removeTarget)" title="从知识库移除" :message="removeTarget ? `移除“${removeTarget.name}”后，原文件仍保留在个人资料库中。` : ''" confirm-text="移除" @close="removeTarget = null" @confirm="confirmRemove" />
    <ConfirmDialog :open="trashKnowledgeBaseConfirmOpen" title="移入回收站" :message="knowledgeBase ? `“${knowledgeBase.name}”将进入回收站，资料本身不会被删除。` : ''" confirm-text="移入回收站" confirm-variant="danger" @close="trashKnowledgeBaseConfirmOpen = false" @confirm="trashKnowledgeBase" />
  </StudentShell>
</template>

<style scoped>
.detail-page { min-height: 100%; padding: 30px clamp(22px, 4vw, 58px) 64px; box-sizing: border-box; background: var(--color-bg); color: var(--color-text); }
.hero, .facts, .content-grid, .notice, .error-banner, .state-panel { max-width: 1180px; margin-left: auto; margin-right: auto; }
.back-button { display: flex; align-items: center; gap: 6px; margin-bottom: 22px; border: 0; background: transparent; color: var(--color-text-muted); padding: 0; cursor: pointer; font: inherit; font-size: 13px; }.back-button:hover { color: var(--color-text); }
.hero-card { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 24px; padding: 24px 58px 24px 24px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); box-shadow: var(--shadow-sm); }
.detail-more { position: absolute; top: 14px; right: 14px; width: 34px; height: 34px; display: grid; place-items: center; border: 0; border-radius: 9px; background: transparent; color: var(--color-text-muted); cursor: pointer; }.detail-more:hover { background: var(--color-hover); color: var(--color-text); }
.detail-menu { position: absolute; z-index: 30; top: 50px; right: 14px; width: 176px; padding: 6px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); box-shadow: var(--shadow-lg); }.detail-menu button { width: 100%; min-height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px; border: 0; border-radius: 8px; background: transparent; color: var(--color-text); cursor: pointer; font: inherit; text-align: left; }.detail-menu button:hover { background: var(--color-hover); }.detail-menu button.danger { color: var(--color-danger); }
.hero-copy { min-width: 0; display: flex; align-items: flex-start; gap: 14px; }.title-icon { width: 48px; height: 48px; display: grid; place-items: center; border-radius: 10px; background: var(--color-bg-alt); flex: 0 0 auto; }.hero-copy h1 { margin: 1px 0 7px; font-size: 28px; letter-spacing: -.03em; }.hero-copy p { margin: 0; color: var(--color-text-muted); line-height: 1.6; }.hero-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; max-width: 460px; }
.facts { margin-top: 18px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }.facts article { min-height: 82px; padding: 16px 18px; display: grid; align-content: center; gap: 5px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); box-shadow: var(--shadow-sm); }.facts strong { max-width: 95%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 24px; }.facts span { color: var(--color-text-muted); font-size: 13px; }
.content-grid { margin-top: 18px; display: grid; grid-template-columns: minmax(0, 1fr) 320px; align-items: start; gap: 18px; }.panel { min-width: 0; padding: 20px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); box-shadow: var(--shadow-sm); }.panel-header { margin-bottom: 13px; display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; }.panel-header h2 { margin: 0 0 5px; font-size: 18px; }.panel-header p { margin: 0; color: var(--color-text-muted); font-size: 12px; }.search-field { min-height: 38px; width: 210px; display: flex; align-items: center; gap: 7px; padding: 0 10px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); color: var(--color-text-muted); }.search-field input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--color-text); font: inherit; font-size: 12px; }
.table-wrap { border: 1px solid var(--color-border); border-radius: 13px; overflow: hidden; background: var(--color-surface); }.table-wrap table { width: 100%; border-collapse: collapse; table-layout: fixed; }.table-wrap th { padding: 11px 14px; border-bottom: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 11px; text-align: left; }.table-wrap td { padding: 12px 14px; border-bottom: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 12px; }.table-wrap tr:last-child td { border-bottom: 0; }.table-wrap th:first-child { width: 30%; }.table-wrap th:nth-child(2) { width: 14%; }.table-wrap th:nth-child(3) { width: 11%; }.table-wrap th:nth-child(4) { width: 9%; }.table-wrap th:nth-child(5) { width: 17%; }.table-wrap th:last-child { width: 120px; }
.file-name { min-width: 0; display: flex; align-items: center; gap: 10px; color: var(--color-text); }.file-name > span { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 9px; background: var(--color-bg-alt); }.file-name strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.status-chip { display: inline-flex; min-height: 23px; align-items: center; border-radius: 99px; padding: 0 8px; white-space: nowrap; font-size: 11px; }.status-chip.is-success { background: color-mix(in srgb, var(--color-success) 12%, transparent); color: var(--color-success); }.status-chip.is-pending, .status-chip.is-neutral { background: var(--color-bg-alt); color: var(--color-text-muted); }.status-chip.is-error { background: color-mix(in srgb, var(--color-danger) 10%, transparent); color: var(--color-danger); }
.row-actions { display: flex; justify-content: flex-end; gap: 4px; }.row-actions button { min-width: 30px; height: 30px; display: grid; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--color-text); padding: 0 7px; cursor: pointer; font: inherit; font-size: 12px; }.row-actions button:hover:not(:disabled) { background: var(--color-hover); }.row-actions button:disabled { opacity: .4; cursor: not-allowed; }.row-actions button.danger { color: var(--color-danger); }
.empty-panel, .state-panel { min-height: 360px; display: grid; place-items: center; align-content: center; gap: 9px; color: var(--color-text-muted); text-align: center; }.empty-panel strong, .state-panel strong { color: var(--color-text); }.empty-panel span, .state-panel span { font-size: 13px; }.empty-panel > div { display: flex; gap: 8px; margin-top: 8px; }.load-more { display: flex; justify-content: center; margin-top: 18px; }
.summary-title { display: flex; align-items: center; gap: 9px; }.summary-title h2 { margin: 0; font-size: 18px; }.summary-panel > p { margin: 14px 0 0; color: var(--color-text-muted); line-height: 1.7; }.summary-list { margin-top: 18px; display: grid; gap: 14px; }.summary-list article { padding-top: 13px; border-top: 1px solid var(--color-border); }.summary-list span { display: block; margin-bottom: 6px; color: var(--color-text-muted); font-size: 12px; }.summary-list strong { color: var(--color-text); font-size: 13px; line-height: 1.5; }
.notice, .error-banner { min-height: 42px; box-sizing: border-box; margin-top: 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 12px; border-radius: 10px; font-size: 13px; }.notice { background: color-mix(in srgb, var(--color-success) 9%, var(--color-surface)); }.notice button { border: 0; background: transparent; color: inherit; font-size: 19px; cursor: pointer; }.error-banner { background: color-mix(in srgb, var(--color-danger) 9%, var(--color-surface)); color: var(--color-danger); }.error-banner button { display: flex; align-items: center; gap: 5px; border: 0; background: transparent; color: inherit; cursor: pointer; }
.spin { animation: spin .85s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 980px) { .hero-card, .content-grid { grid-template-columns: 1fr; }.hero-actions { justify-content: flex-start; max-width: none; }.facts { grid-template-columns: repeat(3, 1fr); }.panel-header { align-items: stretch; flex-direction: column; }.search-field { width: 100%; box-sizing: border-box; }.table-wrap th:nth-child(3), .table-wrap td:nth-child(3), .table-wrap th:nth-child(4), .table-wrap td:nth-child(4), .table-wrap th:nth-child(5), .table-wrap td:nth-child(5) { display: none; }.table-wrap th:first-child { width: auto; }.detail-page { padding-left: 16px; padding-right: 16px; } }
@media (max-width: 640px) { .hero-copy { flex-direction: column; }.facts { grid-template-columns: 1fr; }.hero-actions { display: grid; grid-template-columns: 1fr; }.table-wrap th:nth-child(2), .table-wrap td:nth-child(2) { display: none; } }
</style>
