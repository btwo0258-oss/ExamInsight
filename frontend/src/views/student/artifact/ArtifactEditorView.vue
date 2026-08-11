<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, LoaderCircle, Plus, Save, Trash2 } from 'lucide-vue-next'

import * as chatApi from '@/api/chatV2'
import StudentShell from '@/components/layout/StudentShell.vue'
import type { Artifact, MindMapNode, PresentationSlide } from '@/types/contracts/chatV2'

const route = useRoute()
const router = useRouter()
const artifact = ref<Artifact | null>(null)
const title = ref('')
const outline = ref('')
const slides = ref<PresentationSlide[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const artifactId = computed(() => String(route.params.artifactId || ''))
const editable = computed(() => artifact.value && ['DRAFT', 'READY'].includes(artifact.value.status))
const isMindMap = computed(() => artifact.value?.type === 'MINDMAP')

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
  if (value.type === 'MINDMAP' && value.content.root) {
    outline.value = nodeToOutline(value.content.root).join('\n')
  }
  if (value.type === 'PRESENTATION') {
    slides.value = structuredClone(value.content.slides ?? [])
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

function updateBullets(slide: PresentationSlide, value: string) {
  slide.bullets = value.split('\n')
}

async function save() {
  if (!artifact.value || !editable.value) return
  saving.value = true
  error.value = ''
  try {
    const content = isMindMap.value
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
  } catch (cause) { error.value = chatApi.chatError(cause, '保存草稿失败。').message }
  finally { saving.value = false }
}

async function confirm() {
  if (!artifact.value || !editable.value) return
  await save()
  if (!artifact.value) return
  saving.value = true
  try {
    const confirmed = await chatApi.confirmArtifact(artifact.value.id)
    hydrate(confirmed)
    if (confirmed.confirmedAssetId) {
      await router.replace({ name: 'resource-preview', params: { resourceId: confirmed.confirmedAssetId } })
    }
  } catch (cause) { error.value = chatApi.chatError(cause, '确认生成内容失败。').message }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <StudentShell>
    <div class="editor-page">
      <header class="editor-header">
        <button type="button" aria-label="返回" @click="router.back"><ArrowLeft :size="20" /></button>
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

        <template v-if="isMindMap">
          <section class="mindmap-editor">
            <aside>
              <h2>大纲</h2>
              <p>每行一个节点，用两个空格表示一层缩进。</p>
              <textarea v-model="outline" :readonly="!editable" />
            </aside>
            <div class="mindmap-canvas">
              <div class="root-node">{{ outlineToNode(outline).text }}</div>
              <div class="child-nodes">
                <div v-for="child in outlineToNode(outline).children" :key="child.text" class="child-node">
                  <strong>{{ child.text }}</strong>
                  <small>{{ child.children.map(item => item.text).join(' · ') }}</small>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="presentation-editor">
            <article v-for="(slide, index) in slides" :key="index" class="slide-card">
              <header><span>Slide {{ index + 1 }}</span><button v-if="editable && slides.length > 1" type="button" @click="slides.splice(index, 1)"><Trash2 :size="15" /></button></header>
              <div class="slide-surface">
                <input v-model="slide.title" :readonly="!editable" aria-label="幻灯片标题" />
                <textarea :value="slide.bullets.join('\n')" :readonly="!editable" aria-label="幻灯片要点" @input="updateBullets(slide, ($event.target as HTMLTextAreaElement).value)" />
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
.mindmap-editor { display: grid; grid-template-columns: 320px 1fr; gap: 18px; min-height: 680px; }
.mindmap-editor aside, .mindmap-canvas { border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-bg); }
.mindmap-editor aside { padding: 20px; }
.mindmap-editor h2 { margin: 0 0 6px; }
.mindmap-editor p { margin: 0 0 14px; color: var(--color-text-muted); font-size: 13px; }
.mindmap-editor textarea { width: 100%; height: 570px; resize: none; padding: 12px; border: 1px solid var(--color-border); border-radius: 12px; color: inherit; background: var(--color-bg); font: 14px/1.7 ui-monospace, monospace; }
.mindmap-canvas { display: grid; align-content: center; justify-items: center; gap: 48px; overflow: auto; padding: 48px; }
.root-node, .child-node { min-width: 160px; padding: 14px 18px; border: 2px solid var(--color-text); border-radius: 14px; background: var(--color-bg); text-align: center; }
.child-nodes { display: flex; flex-wrap: wrap; justify-content: center; gap: 22px; }
.child-node { display: grid; gap: 6px; min-width: 180px; border-width: 1px; }
.child-node small { color: var(--color-text-muted); }
.presentation-editor { display: grid; gap: 20px; }
.slide-card { overflow: hidden; border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-bg); }
.slide-card > header { display: flex; justify-content: space-between; padding: 9px 14px; border-bottom: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 12px; }
.slide-card > header button { border: 0; color: inherit; background: transparent; cursor: pointer; }
.slide-surface { display: grid; align-content: center; width: min(960px, calc(100% - 40px)); aspect-ratio: 16/9; margin: 20px auto; padding: 9%; border-radius: 8px; color: #111; background: #fff; box-shadow: 0 5px 26px rgb(0 0 0 / 8%); }
.slide-surface input, .slide-surface textarea { width: 100%; border: 0; outline: 0; color: inherit; background: transparent; }
.slide-surface input { font: 700 clamp(24px, 4vw, 48px)/1.2 inherit; }
.slide-surface textarea { min-height: 45%; margin-top: 7%; resize: none; font: clamp(15px, 2vw, 24px)/1.7 inherit; }
.add-slide { margin: 0 auto; }
.spin { animation: spin .9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 860px) { .mindmap-editor { grid-template-columns: 1fr; } .header-actions button { padding: 0 10px; } }
</style>
