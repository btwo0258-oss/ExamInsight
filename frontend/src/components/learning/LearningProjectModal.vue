<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AppSelectMenu from '@/components/common/AppSelectMenu.vue'
import ProjectAppearancePicker from './ProjectAppearancePicker.vue'
import type { SmartLearningProject } from '@/types/contracts/smartLearning'
import type { KnowledgeBase } from '@/types/contracts/assetLibraryV2'
type EditableProject = Pick<SmartLearningProject, 'name' | 'icon' | 'iconColor'>
const props = defineProps<{ project?: EditableProject | null; knowledgeBases: KnowledgeBase[]; busy: boolean; error: string; kbError?: string; kbLoading?: boolean; hasMoreKnowledgeBases?: boolean }>()
const emit = defineEmits<{ close: []; submit: [payload: { name: string; icon: string; iconColor: string; knowledgeBaseId?: string | null }]; retryKnowledgeBases: []; moreKnowledgeBases: [] }>()
const name = ref(props.project?.name || ''), icon = ref(props.project?.icon || 'notebook'), iconColor = ref(props.project?.iconColor || '#667085'), knowledgeBaseId = ref<string | null>(null)
const panel = ref<HTMLElement | null>(null), nameInput = ref<HTMLInputElement | null>(null)
const opener = document.activeElement as HTMLElement | null
const knowledgeBaseOptions = computed(() => [{ value: null, label: '不关联知识库', description: '默认不关联，创建后仍可选择资料' }, ...props.knowledgeBases.map(kb => ({ value: kb.knowledgeBaseId, label: kb.name, description: `${kb.assetCount} 个文件` }))])
function close() { if (!props.busy) emit('close') }
function submit() { if (props.busy || !name.value.trim()) return; emit('submit', { name: name.value.trim(), icon: icon.value, iconColor: iconColor.value, ...(!props.project ? { knowledgeBaseId: knowledgeBaseId.value } : {}) }) }
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); close() }
  if (event.key !== 'Tab') return
  const popup = document.querySelector('[data-project-appearance], .app-select-menu__panel')
  const nodes = Array.from((popup || panel.value)?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), [tabindex="0"]') || []).filter(el => el.getClientRects().length)
  if (!nodes.length) return
  if (event.shiftKey && (document.activeElement === nodes[0] || !nodes.includes(document.activeElement as HTMLElement))) { event.preventDefault(); nodes.at(-1)?.focus() }
  else if (!event.shiftKey && document.activeElement === nodes.at(-1)) { event.preventDefault(); nodes[0]?.focus() }
}
onMounted(async () => { await nextTick(); nameInput.value?.focus(); document.addEventListener('keydown', keydown) })
onBeforeUnmount(() => { document.removeEventListener('keydown', keydown); opener?.focus() })
</script>
<template>
  <Teleport to="body"><div class="project-modal-backdrop" @click.self="close"><section ref="panel" class="project-modal" role="dialog" aria-modal="true" aria-labelledby="learning-project-title">
    <header><h2 id="learning-project-title">{{ project ? '修改学习项目' : '新建学习项目' }}</h2><button type="button" class="close-button" aria-label="关闭" :disabled="busy" @click="close"><AppIcon name="close" :size="19" /></button></header>
    <form @submit.prevent="submit"><label for="learning-project-name" class="field-label">项目名称</label><div class="project-name-field"><ProjectAppearancePicker v-model:icon="icon" v-model:color="iconColor" :disabled="busy" /><input id="learning-project-name" ref="nameInput" v-model="name" :disabled="busy" maxlength="160" placeholder="例如：高数期末复习" /></div>
      <div v-if="!project" class="knowledge-field"><span class="field-label">初始知识库 <small>可选</small></span><AppSelectMenu v-model="knowledgeBaseId" :options="knowledgeBaseOptions" :disabled="busy" aria-label="初始知识库" /><small v-if="kbLoading">正在读取知识库…</small><div v-if="kbError" class="modal-error"><span>{{ kbError }}</span><button type="button" @click="emit('retryKnowledgeBases')">重试</button></div><button v-if="hasMoreKnowledgeBases" class="text-button" type="button" :disabled="kbLoading" @click="emit('moreKnowledgeBases')">加载更多知识库</button></div>
      <p class="modal-note">{{ project ? '修改名称、图标或颜色，不会改变已填写的内容和准备进度。' : '点击名称左侧图标可选图标和颜色。创建后再补充目标与资料。' }}</p>
      <div v-if="error" class="modal-error" role="alert">{{ error }}</div>
      <footer><button type="button" class="secondary-button" :disabled="busy" @click="close">取消</button><button type="submit" class="primary-button" :disabled="busy || !name.trim()">{{ busy ? '正在保存…' : project ? '保存修改' : '创建并继续' }}</button></footer>
    </form>
  </section></div></Teleport>
</template>
<style scoped>
.project-modal-backdrop,.project-modal,.project-modal *{box-sizing:border-box}.project-modal-backdrop{position:fixed;inset:0;z-index:10000;display:grid;place-items:center;padding:20px;background:rgba(0,0,0,.34)}.project-modal{width:min(500px,100%);max-height:calc(100dvh - 40px);overflow:auto;border:1px solid var(--color-border);border-radius:16px;background:var(--color-surface);color:var(--color-text);box-shadow:var(--shadow-lg);padding:22px}.project-modal header{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:24px}.project-modal h2{margin:0;font-size:18px;font-weight:600}.close-button{width:28px;height:28px;border:0;background:transparent;color:var(--color-text-muted);display:grid;place-items:center;border-radius:6px}.close-button:hover{background:var(--color-hover)}.field-label{display:block;font-size:13px;font-weight:500;margin-bottom:8px}.field-label small{color:var(--color-text-muted);font-weight:400}.project-name-field{display:flex;align-items:center;gap:4px;height:42px;padding:2px 7px;border:1px solid var(--color-border);border-radius:9px}.project-name-field:focus-within{border-color:var(--color-text-muted);box-shadow:0 0 0 3px color-mix(in srgb,var(--color-text) 5%,transparent)}.project-name-field input{width:100%;min-width:0;border:0;outline:0;background:transparent;color:var(--color-text);font:inherit;font-size:14px}.knowledge-field{margin-top:20px}.knowledge-field :deep(*){box-sizing:border-box}.modal-note{padding:12px;margin:16px 0 0;border-radius:10px;background:var(--color-hover);color:var(--color-text-muted);font-size:12px;line-height:1.6}.modal-error{display:flex;gap:8px;align-items:center;margin-top:10px;line-height:1.6;overflow-wrap:anywhere;color:var(--color-danger);font-size:13px}.modal-error span{min-width:0;flex:1}.modal-error button,.text-button{border:0;background:transparent;color:inherit;cursor:pointer}footer{display:flex;justify-content:flex-end;gap:10px;margin-top:22px}button{font:inherit;cursor:pointer}button:disabled{opacity:.5;cursor:not-allowed}.primary-button,.secondary-button{min-height:36px;padding:8px 14px;border-radius:9px;font-size:13px;border:1px solid var(--color-border);color:var(--color-text);background:var(--color-surface)}.primary-button{background:var(--color-primary);border-color:var(--color-primary);color:var(--color-on-primary)}button:focus-visible{outline:2px solid var(--color-text-muted);outline-offset:2px}
</style>
