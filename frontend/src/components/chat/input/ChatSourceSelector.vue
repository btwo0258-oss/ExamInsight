<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Check, ChevronDown, FileText, Folder, Search } from 'lucide-vue-next'

import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'

const props = defineProps<{
  knowledgeBaseId: string | null
  assetIds: string[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:knowledgeBaseId': [value: string | null]
  'update:assetIds': [value: string[]]
}>()

const store = useAssetLibraryV2Store()
const open = ref(false)
const query = ref('')

const readyAssets = computed(() => store.assets.filter(asset => (
  asset.status?.toUpperCase() === 'ACTIVE'
  && asset.version?.status?.toUpperCase() === 'READY'
)))
const filteredKnowledgeBases = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return store.knowledgeBases.filter(item => !keyword || item.name.toLocaleLowerCase().includes(keyword))
})
const filteredAssets = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return readyAssets.value.filter(item => !keyword || item.name.toLocaleLowerCase().includes(keyword))
})
const selectedCount = computed(() => props.assetIds.length + (props.knowledgeBaseId ? 1 : 0))

async function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value && !store.assets.length && !store.knowledgeBases.length) {
    await store.refresh('library').catch(() => undefined)
  }
}

function toggleAsset(assetId: string) {
  if (props.assetIds.includes(assetId)) {
    emit('update:assetIds', props.assetIds.filter(id => id !== assetId))
  } else if (props.assetIds.length < 20) {
    emit('update:assetIds', [...props.assetIds, assetId])
  }
}

onMounted(() => {
  if (!store.assets.length && !store.knowledgeBases.length) {
    void store.refresh('library').catch(() => undefined)
  }
})
</script>

<template>
  <div class="chat-source-selector">
    <button
      class="source-trigger"
      type="button"
      :disabled="disabled"
      :aria-expanded="open"
      @click="toggle"
    >
      <Folder :size="16" />
      <span>{{ selectedCount ? `已关联 ${selectedCount} 项` : '添加资料' }}</span>
      <ChevronDown :size="14" />
    </button>

    <div v-if="open" class="source-panel">
      <label class="source-search">
        <Search :size="16" />
        <input v-model="query" placeholder="搜索知识库或资料" />
      </label>

      <section class="source-section">
        <header><strong>知识库</strong><small>最多 1 个</small></header>
        <button
          class="source-option"
          type="button"
          :aria-selected="knowledgeBaseId === null"
          @click="emit('update:knowledgeBaseId', null)"
        >
          <span class="source-radio" :class="{ selected: knowledgeBaseId === null }" />
          <span>不关联知识库</span>
        </button>
        <button
          v-for="item in filteredKnowledgeBases"
          :key="item.knowledgeBaseId"
          class="source-option"
          type="button"
          :aria-selected="knowledgeBaseId === item.knowledgeBaseId"
          @click="emit('update:knowledgeBaseId', item.knowledgeBaseId)"
        >
          <span class="source-radio" :class="{ selected: knowledgeBaseId === item.knowledgeBaseId }" />
          <Folder :size="16" />
          <span class="source-name">{{ item.name }}</span>
          <small>{{ item.assetCount }} 项</small>
        </button>
      </section>

      <div class="source-divider" />
      <section class="source-section">
        <header><strong>单独资料</strong><small>{{ assetIds.length }}/20</small></header>
        <button
          v-for="item in filteredAssets"
          :key="item.assetId"
          class="source-option"
          type="button"
          :aria-selected="assetIds.includes(item.assetId)"
          @click="toggleAsset(item.assetId)"
        >
          <span class="source-check" :class="{ selected: assetIds.includes(item.assetId) }">
            <Check v-if="assetIds.includes(item.assetId)" :size="12" :stroke-width="3" />
          </span>
          <FileText :size="16" />
          <span class="source-name">{{ item.name }}</span>
        </button>
        <p v-if="!filteredAssets.length" class="source-empty">暂无解析完成的资料</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.chat-source-selector { position: relative; }
.source-trigger {
  display: inline-flex; align-items: center; gap: 7px; height: 34px; padding: 0 12px;
  border: 1px solid var(--color-border); border-radius: 999px; color: var(--color-text);
  background: var(--color-bg); cursor: pointer;
}
.source-trigger:disabled { cursor: not-allowed; opacity: .48; }
.source-trigger:not(:disabled):hover { background: var(--color-surface); }
.source-panel {
  position: absolute; bottom: 42px; left: 0; z-index: 40; width: min(420px, calc(100vw - 48px));
  max-height: 440px; overflow: auto; padding: 10px; border: 1px solid var(--color-border);
  border-radius: 18px; color: var(--color-text); background: var(--color-bg);
  box-shadow: 0 18px 48px rgb(0 0 0 / 15%);
}
.source-search {
  display: flex; align-items: center; gap: 8px; height: 40px; padding: 0 11px; margin-bottom: 8px;
  border: 1px solid var(--color-border); border-radius: 12px;
}
.source-search input { width: 100%; border: 0; outline: 0; color: inherit; background: transparent; font: inherit; }
.source-section { display: grid; gap: 2px; }
.source-section header { display: flex; justify-content: space-between; padding: 8px 9px 5px; font-size: 13px; }
.source-section small { color: var(--color-text-muted); font-weight: 400; }
.source-option {
  display: flex; align-items: center; gap: 9px; min-height: 40px; padding: 7px 9px; border: 0;
  border-radius: 10px; color: inherit; background: transparent; text-align: left; cursor: pointer;
}
.source-option:hover, .source-option[aria-selected='true'] { background: var(--color-surface); }
.source-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-radio, .source-check { flex: 0 0 auto; width: 17px; height: 17px; border: 1.5px solid var(--color-border); }
.source-radio { border-radius: 50%; }
.source-radio.selected { border: 5px solid var(--color-text); }
.source-check { display: grid; place-items: center; border-radius: 5px; }
.source-check.selected { border-color: var(--color-text); color: var(--color-bg); background: var(--color-text); }
.source-divider { height: 1px; margin: 8px 4px; background: var(--color-border); }
.source-empty { margin: 8px; color: var(--color-text-muted); font-size: 13px; }
</style>
