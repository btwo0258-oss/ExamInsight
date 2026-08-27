<script setup lang="ts">
import { computed, nextTick, onMounted, onUpdated, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ArrowLeft, ArrowUp, Check, LoaderCircle, Move, Plus, Save, Trash2, ZoomIn, ZoomOut } from 'lucide-vue-next'

import * as chatApi from '@/api/chatV2'
import StudentShell from '@/components/layout/StudentShell.vue'
import { useChatV2Store } from '@/stores/chatV2'
import { previewReturnQuery, resolvePreviewReturn } from '@/utils/previewReturn'
import type { Artifact, MindMapNode, PresentationSlide } from '@/types/contracts/chatV2'

const route = useRoute()
const router = useRouter()
const chatStore = useChatV2Store()
const artifact = ref<Artifact | null>(null)
const title = ref('')
const markdown = ref('')
const outline = ref('')
const slides = ref<PresentationSlide[]>([])
const slideBodyRefs = ref<HTMLTextAreaElement[]>([])
const mapScale = ref(1)
const mapOffset = ref({ x: 0, y: 0 })
const mapDragging = ref(false)
const dragOrigin = ref({ x: 0, y: 0 })
const dragStartOffset = ref({ x: 0, y: 0 })
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const artifactId = computed(() => String(route.params.artifactId || ''))
const editable = computed(() => artifact.value && ['DRAFT', 'READY'].includes(artifact.value.status))
const isDocument = computed(() => artifact.value?.type === 'DOCUMENT')
const isMindMap = computed(() => artifact.value?.type === 'MINDMAP')
const mindMapRows = computed(() => {
  const rows: Array<{ key: string; text: string; depth: number }> = []
  const visit = (node: MindMapNode, depth: number, key: string) => {
    rows.push({ key, text: node.text, depth })
    node.children.forEach((child, index) => visit(child, depth + 1, `${key}-${index}`))
  }
  visit(outlineToNode(outline.value), 0, 'root')
  return rows
})
const mapTransform = computed(() => ({
  transform: `translate(${mapOffset.value.x}px, ${mapOffset.value.y}px) scale(${mapScale.value})`,
}))

function nodeToOutline(node: MindMapNode, depth = 0): string[] {
  return [`${'  '.repeat(depth)}${node.text}`, ...node.children.flatMap(child => nodeToOutline(child, depth + 1))]
}

function outlineToNode(value: string): MindMapNode {
  const lines = value.split('\n').map(line => ({
    depth: Math.min(9, Math.floor((line.match(/^\s*/)?.[0].replace(/\t/g, '  ').length ?? 0) / 2)),
    text: line.trim(),
  })).filter(line => line.text)
  if (!lines.length) return { text: '未命名主题', children: [] }
  const root: MindMapNode = { text: lines[0]!.text, children: [] }
  const stack: Array<{ depth: number; node: MindMapNode }> = [{ depth: 0, node: root }]
  for (const line of lines.slice(1)) {
    const node: MindMapNode = { text: line.text, children: [] }
    const depth = Math.max(1, line.depth)
    while (stack.length && stack[stack.length - 1]!.depth >= depth) stack.pop()
    const parent = stack[stack.length - 1]?.node ?? root
    parent.children.push(node)
    stack.push({ depth, node })
  }
  return root
}

function hydrate(value: Artifact) {
  artifact.value = value
  title.value = value.title
  if (value.type === 'DOCUMENT') markdown.value = String(value.content.markdown ?? '')
  if (value.type === 'MINDMAP' && value.content.root) {
    outline.value = nodeToOutline(value.content.root).join('\n')
    resetMapView()
  }
  if (value.type === 'PRESENTATION') {
    slides.value = structuredClone(value.content.slides ?? [])
    resizeAllSlideBodies()
  }
}

function setMapScale(value: number) {
  mapScale.value = Math.min(2.4, Math.max(0.5, Number(value.toFixed(2))))
}

function zoomMap(delta: number) {
  setMapScale(mapScale.value + delta)
}

function zoomMapWithWheel(event: WheelEvent) {
  zoomMap(event.deltaY < 0 ? 0.1 : -0.1)
}

function resetMapView() {
  mapScale.value = 1
  mapOffset.value = { x: 0, y: 0 }
}

function startMapDrag(event: PointerEvent) {
  if (event.button !== 0) return
  mapDragging.value = true
  dragOrigin.value = { x: event.clientX, y: event.clientY }
  dragStartOffset.value = { ...mapOffset.value }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function moveMapDrag(event: PointerEvent) {
  if (!mapDragging.value) return
  mapOffset.value = {
    x: dragStartOffset.value.x + event.clientX - dragOrigin.value.x,
    y: dragStartOffset.value.y + event.clientY - dragOrigin.value.y,
  }
}

function stopMapDrag(event?: PointerEvent) {
  if (!mapDragging.value) return
  mapDragging.value = false
  if (event && (event.currentTarget as HTMLElement).hasPointerCapture(event.pointerId)) {
    ;(event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId)
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try { hydrate(await chatApi.getArtifact(artifactId.value)) }
  catch (cause) { error.value = chatApi.chatError(cause, '加载草稿失败。').message }
  finally { loading.value = false }
}

function addSlide() {
  slides.value.push({ title: `第 ${slides.value.length + 1} 页`, bullets: [''], speakerNotes: '' })
}

function moveSlide(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= slides.value.length) return
  const current = slides.value[index]
  const replacement = slides.value[target]
  if (!current || !replacement) return
  slides.value[index] = replacement
  slides.value[target] = current
}

function updateBullets(slide: PresentationSlide, value: string) {
  slide.bullets = value.split('\n')
}

function setSlideBodyRef(element: unknown, index: number) {
  if (element instanceof HTMLTextAreaElement) slideBodyRefs.value[index] = element
}

function resizeSlideBody(element: HTMLTextAreaElement) {
  element.style.height = 'auto'
  element.style.height = `${Math.max(48, element.scrollHeight)}px`
}

function resizeAllSlideBodies() {
  void nextTick(() => slideBodyRefs.value.forEach(resizeSlideBody))
}

async function save(): Promise<boolean> {
  if (!artifact.value || !editable.value) return false
  saving.value = true
  error.value = ''
  try {
    const content = isDocument.value
      ? { ...artifact.value.content, markdown: markdown.value }
      : isMindMap.value
      ? { ...artifact.value.content, root: outlineToNode(outline.value) }
      : { ...artifact.value.content, slides: slides.value.map(slide => ({
        ...slide,
        title: slide.title.trim(),
        bullets: slide.bullets.map(item => item.trim()).filter(Boolean),
      })) }
    hydrate(await chatApi.updateArtifact(artifact.value.id, {
      title: title.value.trim(),
      content,
      version: artifact.value.version,
    }))
    return true
  } catch (cause) {
    error.value = chatApi.chatError(cause, '保存草稿失败。').message
    return false
  } finally { saving.value = false }
}

async function confirm() {
  if (!artifact.value || !editable.value) return
  const saved = await save()
  if (!saved) return
  if (!artifact.value) return
  saving.value = true
  try {
    const confirmed = await chatApi.confirmArtifact(artifact.value.id)
    hydrate(confirmed)
    chatStore.upsertArtifact(confirmed)
    if (confirmed.confirmedAssetId) {
      await router.replace({
        name: 'resource-preview',
        params: { resourceId: confirmed.confirmedAssetId },
        query: previewReturnQuery(route.query),
      })
    }
  } catch (cause) { error.value = chatApi.chatError(cause, '确认生成内容失败。').message }
  finally { saving.value = false }
}

onMounted(load)
onUpdated(resizeAllSlideBodies)

function closeEditor() {
  void router.push(resolvePreviewReturn(router, route.query))
}
</script>

<template>
  <StudentShell>
    <div class="editor-page">
      <header class="editor-header">
        <button type="button" aria-label="返回" @click="closeEditor"><ArrowLeft :size="20" /></button>
        <input v-model="title" maxlength="120" :disabled="!editable" aria-label="标题" />
        <div class="header-actions">
          <button v-if="editable" type="button" :disabled="saving" @click="save"><Save :size="16" />保存</button>
          <button v-if="editable" class="primary" type="button" :disabled="saving" @click="confirm"><Check :size="16" />确认并存入资料库</button>
        </div>
      </header>

      <main v-if="loading" class="editor-state"><LoaderCircle class="spin" :size="24" />正在加载</main>
      <main v-else-if="error && !artifact" class="editor-state error">{{ error }}</main>
      <main v-else-if="artifact" class="editor-workspace">
        <div v-if="error" class="inline-error">{{ error }}</div>

        <template v-if="isDocument">
          <section class="document-editor-workspace">
            <div class="document-editor-paper">
              <textarea v-model="markdown" :readonly="!editable" aria-label="文档内容" />
            </div>
          </section>
        </template>

        <template v-else-if="isMindMap">
          <section class="mindmap-editor">
            <aside>
              <h2>大纲</h2>
              <p>每行一个节点，用两个空格表示一层缩进。</p>
              <textarea v-model="outline" :readonly="!editable" />
            </aside>
            <div
              class="mindmap-canvas"
              :class="{ dragging: mapDragging }"
              @pointerdown="startMapDrag"
              @pointermove="moveMapDrag"
              @pointerup="stopMapDrag"
              @pointercancel="stopMapDrag"
              @wheel.prevent="zoomMapWithWheel"
            >
              <div class="mindmap-toolbar" aria-label="思维导图视图控制">
                <button type="button" title="缩小" aria-label="缩小" @pointerdown.stop @click="zoomMap(-0.1)"><ZoomOut :size="16" /></button>
                <span>{{ Math.round(mapScale * 100) }}%</span>
                <button type="button" title="放大" aria-label="放大" @pointerdown.stop @click="zoomMap(0.1)"><ZoomIn :size="16" /></button>
                <button type="button" title="重置视图" aria-label="重置视图" @pointerdown.stop @click="resetMapView"><Move :size="16" /></button>
              </div>
              <div class="mindmap-viewport">
                <div class="mindmap-tree" :style="mapTransform">
                  <div
                    v-for="row in mindMapRows"
                    :key="row.key"
                    class="tree-row"
                    :class="{ 'tree-row--root': row.depth === 0 }"
                    :style="{ marginLeft: `${row.depth * 42}px` }"
                  >
                    <span class="tree-branch" aria-hidden="true" />
                    <strong>{{ row.text }}</strong>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="presentation-editor">
            <article v-for="(slide, index) in slides" :key="index" class="slide-card">
              <header>
                <span>Slide {{ index + 1 }}</span>
                <div class="slide-actions">
                  <button v-if="editable" type="button" :disabled="index === 0" aria-label="上移幻灯片" title="上移" @click="moveSlide(index, -1)"><ArrowUp :size="15" /></button>
                  <button v-if="editable" type="button" :disabled="index === slides.length - 1" aria-label="下移幻灯片" title="下移" @click="moveSlide(index, 1)"><ArrowDown :size="15" /></button>
                  <button v-if="editable && slides.length > 1" type="button" aria-label="删除幻灯片" title="删除" @click="slides.splice(index, 1)"><Trash2 :size="15" /></button>
                </div>
              </header>
              <div class="slide-surface">
                <input v-model="slide.title" :readonly="!editable" aria-label="幻灯片标题" />
                <textarea
                  :ref="element => setSlideBodyRef(element, index)"
                  :value="slide.bullets.join('\n')"
                  :readonly="!editable"
                  rows="1"
                  aria-label="幻灯片要点"
                  @input="updateBullets(slide, ($event.target as HTMLTextAreaElement).value); resizeSlideBody($event.target as HTMLTextAreaElement)"
                />
              </div>
            </article>
            <button v-if="editable" class="add-slide" type="button" @click="addSlide"><Plus :size="18" />添加一页</button>
          </section>
        </template>
      </main>
    </div>
  </StudentShell>
</template>

<style scoped>
.editor-page { height: 100%; overflow: auto; background: var(--color-surface); }
.editor-header { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 12px; height: 68px; padding: 0 24px; border-bottom: 1px solid var(--color-border); background: var(--color-bg); }
.editor-header > button { display: grid; width: 38px; height: 38px; padding: 0; place-items: center; border: 0; border-radius: 10px; color: inherit; background: transparent; cursor: pointer; }
.editor-header > button:hover { background: var(--color-surface); }
.editor-header > input { flex: 1; min-width: 0; border: 0; outline: 0; color: inherit; background: transparent; font: 600 17px inherit; }
.header-actions { display: flex; gap: 8px; }
.header-actions button, .add-slide { display: inline-flex; align-items: center; gap: 6px; min-height: 38px; padding: 0 14px; border: 1px solid var(--color-border); border-radius: 999px; color: inherit; background: var(--color-bg); cursor: pointer; }
.header-actions button.primary { border-color: var(--color-text); color: var(--color-bg); background: var(--color-text); }
.editor-state { display: flex; align-items: center; justify-content: center; gap: 10px; min-height: 60vh; color: var(--color-text-muted); }
.editor-state.error, .inline-error { color: #b42318; }
.editor-workspace { width: min(1280px, calc(100% - 48px)); margin: 24px auto 80px; }
.inline-error { margin-bottom: 16px; padding: 12px; border-radius: 10px; background: #fef3f2; }
.document-editor-workspace { display: flex; justify-content: center; }
.document-editor-paper { width: min(920px, 100%); min-height: calc(100vh - 170px); padding: clamp(34px, 6vw, 74px) clamp(28px, 8vw, 96px); border: 1px solid var(--color-border); border-radius: 16px; background: var(--color-bg); box-shadow: var(--shadow-sm); }
.document-editor-paper textarea { display: block; width: 100%; min-height: calc(100vh - 250px); resize: vertical; border: 0; outline: 0; color: var(--color-text); background: transparent; font: 15px/1.85 inherit; }
.mindmap-editor { display: grid; grid-template-columns: 320px 1fr; gap: 18px; min-height: 680px; }
.mindmap-editor aside, .mindmap-canvas { border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-bg); }
.mindmap-editor aside { padding: 20px; }
.mindmap-editor h2 { margin: 0 0 6px; }
.mindmap-editor p { margin: 0 0 14px; color: var(--color-text-muted); font-size: 13px; }
.mindmap-editor textarea { width: 100%; height: 570px; resize: none; padding: 12px; border: 1px solid var(--color-border); border-radius: 12px; color: inherit; background: var(--color-bg); font: 14px/1.7 ui-monospace, monospace; }
.mindmap-canvas { position: relative; min-height: 680px; overflow: hidden; background: radial-gradient(circle at center, var(--color-surface), var(--color-bg)); cursor: grab; touch-action: none; }
.mindmap-canvas.dragging { cursor: grabbing; }
.mindmap-toolbar { position: absolute; top: 14px; right: 14px; z-index: 2; display: flex; align-items: center; gap: 2px; padding: 4px; border: 1px solid var(--color-border); border-radius: 11px; background: color-mix(in srgb, var(--color-bg) 92%, transparent); box-shadow: var(--shadow-sm); cursor: default; }
.mindmap-toolbar button { display: grid; width: 28px; height: 28px; padding: 0; place-items: center; border: 0; border-radius: 7px; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.mindmap-toolbar button:hover { color: var(--color-text); background: var(--color-surface); }
.mindmap-toolbar span { min-width: 42px; color: var(--color-text-muted); font-size: 11px; text-align: center; }
.mindmap-viewport { position: absolute; inset: 0; display: grid; place-items: center; overflow: hidden; }
.mindmap-tree { display: grid; min-width: min(580px, 80%); gap: 12px; padding: 50px; transform-origin: center center; }
.tree-row { position: relative; display: flex; min-height: 40px; align-items: center; gap: 10px; padding: 8px 15px; border: 1px solid var(--color-border); border-radius: 12px; color: var(--color-text); background: var(--color-bg); box-shadow: 0 4px 16px rgb(15 23 42 / 6%); }
.tree-row::before { position: absolute; top: 50%; right: 100%; width: 28px; height: 1px; background: var(--color-border); content: ''; }
.tree-row--root { justify-content: center; border: 2px solid var(--color-text); font-size: 17px; }
.tree-row--root::before { display: none; }
.tree-branch { width: 7px; height: 7px; flex: 0 0 7px; border-radius: 50%; background: var(--color-text-muted); }
.tree-row--root .tree-branch { display: none; }
.tree-row strong { overflow-wrap: anywhere; }
.presentation-editor { display: grid; gap: 20px; }
.slide-card { overflow: hidden; border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-bg); }
.slide-card > header { display: flex; justify-content: space-between; padding: 9px 14px; border-bottom: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 12px; }
.slide-actions { display: inline-flex; align-items: center; gap: 2px; }
.slide-card > header button { display: grid; width: 26px; height: 26px; padding: 0; place-items: center; border: 0; border-radius: 7px; color: inherit; background: transparent; cursor: pointer; }
.slide-card > header button:hover:not(:disabled) { background: var(--color-surface); color: var(--color-text); }
.slide-card > header button:disabled { cursor: not-allowed; opacity: .35; }
.slide-surface { display: grid; align-content: start; width: min(960px, calc(100% - 40px)); min-height: 360px; aspect-ratio: 16/9; margin: 20px auto; padding: 9%; border-radius: 8px; color: #111; background: #fff; box-shadow: 0 5px 26px rgb(0 0 0 / 8%); }
.slide-surface input, .slide-surface textarea { width: 100%; border: 0; outline: 0; color: inherit; background: transparent; }
.slide-surface input { font: 700 clamp(24px, 4vw, 48px)/1.2 inherit; }
.slide-surface textarea { min-height: 48px; height: auto; margin-top: 7%; overflow: hidden; resize: none; font: clamp(15px, 2vw, 24px)/1.7 inherit; field-sizing: content; }
.add-slide { margin: 0 auto; }
.spin { animation: spin .9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 860px) { .mindmap-editor { grid-template-columns: 1fr; } .header-actions button { padding: 0 10px; } }
</style>
