<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, ChevronDown, Folder, Plus, X } from 'lucide-vue-next'

import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'

const props = defineProps<{
  knowledgeBaseId: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:knowledgeBaseId': [value: string | null]
}>()

const store = useAssetLibraryV2Store()
const root = ref<HTMLElement | null>(null)
const open = ref(false)
const creating = ref(false)
const createName = ref('')
const createInput = ref<HTMLInputElement | null>(null)
const createError = ref('')

const selectedKnowledgeBase = computed(() => (
  store.knowledgeBases.find(item => item.knowledgeBaseId === props.knowledgeBaseId) ?? null
))
const triggerLabel = computed(() => selectedKnowledgeBase.value?.name || '不关联知识库')

async function ensureKnowledgeBases() {
  if (store.knowledgeBases.length || store.loading) return
  await store.loadKnowledgeBases('library').catch(() => undefined)
}

async function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) await ensureKnowledgeBases()
}

function selectKnowledgeBase(value: string | null) {
  emit('update:knowledgeBaseId', value)
  open.value = false
  creating.value = false
  createName.value = ''
  createError.value = ''
}

async function startCreating() {
  creating.value = true
  createError.value = ''
  await nextTick()
  createInput.value?.focus()
}

async function createKnowledgeBase() {
  const name = createName.value.trim()
  if (!name || store.mutating) return
  createError.value = ''
  try {
    const created = await store.createKnowledgeBase(name, '')
    selectKnowledgeBase(created.knowledgeBaseId)
  } catch (error) {
    createError.value = error instanceof Error ? error.message : '创建知识库失败。'
  }
}

function handleDocumentPointerDown(event: PointerEvent) {
  if (root.value?.contains(event.target as Node)) return
  open.value = false
  creating.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  void ensureKnowledgeBases()
})

onBeforeUnmount(() => document.removeEventListener('pointerdown', handleDocumentPointerDown))
</script>

<template>
  <div ref="root" class="chat-source-selector">
    <button
      class="source-trigger"
      type="button"
      :disabled="disabled"
      :aria-expanded="open"
      @click="toggle"
    >
      <Folder :size="12" />
      <span>{{ triggerLabel }}</span>
      <ChevronDown :size="11" :class="{ rotated: open }" />
    </button>

    <div v-if="open" class="source-panel">
      <div class="source-options">
        <button
          class="source-option"
          type="button"
          :aria-selected="knowledgeBaseId === null"
          @click="selectKnowledgeBase(null)"
        >
          <X :size="17" />
          <span class="source-name">无</span>
          <Check v-if="knowledgeBaseId === null" :size="17" :stroke-width="2.4" />
        </button>

        <button
          v-for="item in store.knowledgeBases"
          :key="item.knowledgeBaseId"
          class="source-option"
          type="button"
          :aria-selected="knowledgeBaseId === item.knowledgeBaseId"
          @click="selectKnowledgeBase(item.knowledgeBaseId)"
        >
          <Folder :size="17" />
          <span class="source-name">{{ item.name }}</span>
          <small>{{ item.assetCount }} 项</small>
          <Check
            v-if="knowledgeBaseId === item.knowledgeBaseId"
            :size="17"
            :stroke-width="2.4"
          />
        </button>

        <p v-if="store.loading && !store.knowledgeBases.length" class="source-empty">正在加载知识库</p>
      </div>

      <div class="source-divider" />

      <button v-if="!creating" class="create-entry" type="button" @click="startCreating">
        <Plus :size="18" />
        <span>新建空白知识库</span>
      </button>
      <form v-else class="create-form" @submit.prevent="createKnowledgeBase">
        <input
          ref="createInput"
          v-model="createName"
          maxlength="60"
          placeholder="知识库名称"
          @keydown.esc="creating = false"
        />
        <button type="submit" :disabled="!createName.trim() || store.mutating">创建</button>
      </form>
      <p v-if="createError" class="create-error">{{ createError }}</p>
    </div>
  </div>
</template>

<style scoped>
.chat-source-selector { position: relative; width: min(190px, 36vw); min-width: 150px; margin-left: 10px; }
.source-trigger {
  display: inline-flex; width: 100%; max-width: none; height: 24px; align-items: center; justify-content: flex-start; gap: 5px;
  box-sizing: border-box; padding: 0 8px; border: 0; border-radius: 7px;
  color: color-mix(in srgb, var(--color-text) 64%, transparent);
  background: transparent;
  font-family: inherit;
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
}
.source-trigger span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-trigger svg:last-child { flex: 0 0 auto; transition: transform .16s ease; }
.source-trigger svg.rotated { transform: rotate(180deg); }
.source-trigger:not(:disabled):hover { background: rgb(0 0 0 / 5%); }
.source-trigger:disabled { cursor: not-allowed; opacity: .45; }
.source-panel {
  position: absolute; bottom: 42px; left: 0; z-index: 45; width: min(390px, calc(100vw - 32px));
  max-height: 390px; overflow: auto; padding: 10px; border: 1px solid var(--color-border);
  border-radius: 18px; color: var(--color-text); background: var(--color-bg);
  box-shadow: 0 16px 46px rgb(0 0 0 / 16%);
}
.source-options { display: grid; gap: 3px; }
.source-option, .create-entry {
  display: flex; width: 100%; min-height: 42px; align-items: center; gap: 10px; padding: 8px 10px;
  border: 0; border-radius: 11px; color: inherit; background: transparent; font: inherit;
  text-align: left; cursor: pointer;
}
.source-option:hover, .source-option[aria-selected='true'], .create-entry:hover { background: var(--color-surface); }
.source-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-option small { flex: 0 0 auto; color: var(--color-text-muted); font-size: 12px; }
.source-divider { height: 1px; margin: 9px 4px; background: var(--color-border); }
.source-empty { margin: 8px 10px; color: var(--color-text-muted); font-size: 13px; }
.create-form { display: flex; gap: 8px; }
.create-form input {
  min-width: 0; flex: 1; height: 40px; padding: 0 11px; border: 1px solid var(--color-border);
  border-radius: 10px; outline: none; color: inherit; background: var(--color-bg); font: inherit;
}
.create-form input:focus { border-color: var(--color-text-muted); }
.create-form button {
  height: 40px; padding: 0 14px; border: 0; border-radius: 10px; color: var(--color-bg);
  background: var(--color-text); cursor: pointer;
}
.create-form button:disabled { cursor: default; opacity: .35; }
.create-error { margin: 7px 3px 0; color: var(--color-danger); font-size: 12px; }
:global(html.dark) .source-trigger:not(:disabled):hover { background: rgb(255 255 255 / 8%); }
@media (max-width: 560px) {
  .chat-source-selector { width: min(180px, 56vw); min-width: 132px; }
  .source-trigger { padding-inline: 6px; }
}
</style>
