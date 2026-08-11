<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import { addAssetToKnowledgeBase } from '@/api/assetLibraryV2'
import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'

const props = defineProps<{
  open: boolean
  knowledgeBaseId: string
  existingAssetIds: string[]
}>()
const emit = defineEmits<{ close: []; added: [] }>()
const store = useAssetLibraryV2Store()
const search = ref('')
const selected = ref<string[]>([])
const saving = ref(false)
const error = ref('')

const candidates = computed(() => {
  const existing = new Set(props.existingAssetIds)
  const query = search.value.trim().toLocaleLowerCase()
  return store.assets.filter(
    (asset) => !existing.has(asset.assetId) && (!query || asset.name.toLocaleLowerCase().includes(query)),
  )
})

watch(() => props.open, async (open) => {
  if (!open) return
  search.value = ''
  selected.value = []
  error.value = ''
  if (!store.assets.length) await store.refresh('library').catch(() => undefined)
})

function toggle(assetId: string) {
  selected.value = selected.value.includes(assetId)
    ? selected.value.filter((id) => id !== assetId)
    : [...selected.value, assetId]
}

async function addSelected() {
  if (!selected.value.length || saving.value) return
  saving.value = true
  error.value = ''
  try {
    for (const assetId of selected.value) {
      await addAssetToKnowledgeBase(props.knowledgeBaseId, assetId)
    }
    emit('added')
    emit('close')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加入知识库失败。'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppModal :open="open" title="从个人资料库添加" width="min(680px, 100%)" @close="emit('close')">
    <div class="picker">
      <input v-model="search" class="picker-search" placeholder="搜索尚未加入的资料" />
      <div v-if="candidates.length" class="picker-list">
        <label v-for="asset in candidates" :key="asset.assetId">
          <input
            type="checkbox"
            :checked="selected.includes(asset.assetId)"
            @change="toggle(asset.assetId)"
          />
          <span><strong>{{ asset.name }}</strong><small>{{ asset.version?.mimeType || '处理中' }}</small></span>
        </label>
      </div>
      <div v-else class="picker-empty">
        {{ search.trim() ? '没有匹配的资料' : '个人资料库中的资料都已加入该知识库' }}
      </div>
      <p v-if="error" class="picker-error" role="alert">{{ error }}</p>
    </div>
    <template #footer>
      <div class="picker-actions">
        <span>已选择 {{ selected.length }} 项</span>
        <div>
          <AppButton variant="ghost" :disabled="saving" @click="emit('close')">取消</AppButton>
          <AppButton :loading="saving" :disabled="!selected.length" @click="addSelected">加入知识库</AppButton>
        </div>
      </div>
    </template>
  </AppModal>
</template>

<style scoped>
.picker { display: grid; gap: 14px; }
.picker-search { width: 100%; box-sizing: border-box; min-height: 42px; padding: 0 12px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-bg); color: var(--color-text); font: inherit; outline: none; }
.picker-search:focus { border-color: var(--color-text-muted); }
.picker-list { max-height: 380px; overflow: auto; display: grid; gap: 6px; }
.picker-list label { display: flex; align-items: center; gap: 11px; padding: 11px 12px; border-radius: 9px; cursor: pointer; color: var(--color-text); }
.picker-list label:hover { background: var(--color-hover); }
.picker-list span { min-width: 0; display: grid; gap: 3px; }
.picker-list strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.picker-list small { color: var(--color-text-muted); font-size: 12px; }
.picker-empty { min-height: 180px; display: grid; place-items: center; color: var(--color-text-muted); text-align: center; }
.picker-error { margin: 0; color: var(--color-danger); font-size: 13px; }
.picker-actions { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--color-text-muted); font-size: 13px; }
.picker-actions > div { display: flex; gap: 8px; }
</style>
