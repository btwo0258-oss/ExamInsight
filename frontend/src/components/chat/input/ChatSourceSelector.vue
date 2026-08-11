<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
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

const readyAssets = computed(() => store.assets.filter((asset) => (
  asset.status?.toUpperCase() === 'ACTIVE'
  && asset.version?.status?.toUpperCase() === 'READY'
)))
const filteredKnowledgeBases = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return store.knowledgeBases.filter((item) => !keyword || item.name.toLocaleLowerCase().includes(keyword))
})
const filteredAssets = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return readyAssets.value.filter((item) => !keyword || item.name.toLocaleLowerCase().includes(keyword))
})
const selectedCount = computed(() => props.assetIds.length + (props.knowledgeBaseId ? 1 : 0))

async function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value && !store.assets.length && !store.knowledgeBases.length) {
    await store.refresh('library').catch(() => undefined)
  }
}

function selectKnowledgeBase(value: string | null) {
  emit('update:knowledgeBaseId', value)
}

function toggleAsset(assetId: string) {
  if (props.assetIds.includes(assetId)) {
    emit('update:assetIds', props.assetIds.filter((id) => id !== assetId))
    return
  }
  if (props.assetIds.length >= 20) return
  emit('update:assetIds', [...props.assetIds, assetId])
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
      <AppIcon name="folder" :size="15" />
      <span>{{ selectedCount ? `已关联 ${selectedCount} 项` : '添加学习资料' }}</span>
      <AppIcon name="chevron-down" :size="13" />
    </button>

    <div v-if="open" class="source-panel">
      <label class="source-search">
        <AppIcon name="search" :size="15" />
        <input v-model="query" placeholder="搜索知识库或资料" />
      </label>

      <div class="source-section">
        <div class="source-heading"><span>知识库</span><small>最多 1 个</small></div>
        <button
          class="source-option"
          type="button"
          :aria-selected="knowledgeBaseId === null"
          @click="selectKnowledgeBase(null)"
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
          @click="selectKnowledgeBase(item.knowledgeBaseId)"
        >
          <span class="source-radio" :class="{ selected: knowledgeBaseId === item.knowledgeBaseId }" />
          <AppIcon name="folder" :size="16" />
          <span class="source-name">{{ item.name }}</span>
          <small>{{ item.assetCount }} 个资料</small>
        </button>
      </div>

      <div class="source-divider" />
      <div class="source-section">
        <div class="source-heading"><span>单独资料</span><small>{{ assetIds.length }}/20</small></div>
        <button
          v-for="item in filteredAssets"
          :key="item.assetId"
          class="source-option"
          type="button"
          :aria-selected="assetIds.includes(item.assetId)"
          @click="toggleAsset(item.assetId)"
        >
          <span class="source-check" :class="{ selected: assetIds.includes(item.assetId) }">
            <AppIcon v-if="assetIds.includes(item.assetId)" name="check" :size="12" />
          </span>
          <AppIcon name="file" :size="16" />
          <span class="source-name">{{ item.name }}</span>
        </button>
        <p v-if="!filteredAssets.length" class="source-empty">暂无已解析完成的资料</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-source-selector { position: relative; }
.source-trigger {
  display: inline-flex; align-items: center; gap: 7px; height: 32px; padding: 0 11px;
  border: 1px solid var(--border-color, #dedede); border-radius: 999px;
  color: var(--text-primary, #222); background: var(--surface-primary, #fff); cursor: pointer;
}
.source-trigger:disabled { cursor: not-allowed; opacity: .5; }
.source-trigger:not(:disabled):hover { background: var(--surface-hover, #f4f4f4); }
.source-panel {
  position: absolute; bottom: 40px; left: 0; z-index: 30; width: min(420px, calc(100vw - 48px));
  max-height: 430px; overflow: auto; padding: 10px;
  border: 1px solid var(--border-color, #dedede); border-radius: 18px;
  color: var(--text-primary, #222); background: var(--surface-primary, #fff);
  box-shadow: 0 16px 42px rgb(0 0 0 / 14%);
}
.source-search {
  display: flex; align-items: center; gap: 8px; height: 38px; padding: 0 11px; margin-bottom: 8px;
  border: 1px solid var(--border-color, #dedede); border-radius: 12px;
}
.source-search input { width: 100%; border: 0; outline: 0; color: inherit; background: transparent; font: inherit; }
.source-section { display: grid; gap: 2px; }
.source-heading { display: flex; justify-content: space-between; padding: 8px 9px 5px; font-size: 13px; font-weight: 650; }
.source-heading small, .source-option small { color: var(--text-secondary, #777); font-weight: 400; }
.source-option {
  display: flex; align-items: center; gap: 9px; min-height: 38px; padding: 7px 9px;
  border: 0; border-radius: 10px; color: inherit; background: transparent; text-align: left; cursor: pointer;
}
.source-option:hover, .source-option[aria-selected='true'] { background: var(--surface-hover, #f2f2f2); }
.source-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-radio, .source-check { flex: 0 0 auto; width: 16px; height: 16px; border: 1.5px solid #999; }
.source-radio { border-radius: 50%; }
.source-radio.selected { border: 5px solid var(--text-primary, #222); }
.source-check { display: grid; place-items: center; border-radius: 5px; }
.source-check.selected { border-color: var(--text-primary, #222); color: var(--surface-primary, #fff); background: var(--text-primary, #222); }
.source-divider { height: 1px; margin: 8px 4px; background: var(--border-color, #e5e5e5); }
.source-empty { margin: 8px; color: var(--text-secondary, #777); font-size: 13px; }
</style>
