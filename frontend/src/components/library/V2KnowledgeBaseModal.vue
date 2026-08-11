<script setup lang="ts">
import { ref, watch } from 'vue'
import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'
import type { KnowledgeBase } from '@/types/contracts/assetLibraryV2'

const props = defineProps<{
  open: boolean
  knowledgeBase?: KnowledgeBase | null
}>()
const emit = defineEmits<{ close: []; saved: [knowledgeBase: KnowledgeBase] }>()
const store = useAssetLibraryV2Store()
const name = ref('')
const description = ref('')
const error = ref('')

watch(
  () => [props.open, props.knowledgeBase] as const,
  ([open, knowledgeBase]) => {
    if (!open) return
    name.value = knowledgeBase?.name ?? ''
    description.value = knowledgeBase?.description ?? ''
    error.value = ''
  },
  { immediate: true },
)

async function submit() {
  const normalizedName = name.value.trim()
  if (!normalizedName || store.mutating) return
  error.value = ''
  try {
    const item = props.knowledgeBase
      ? await store.updateKnowledgeBase(props.knowledgeBase.knowledgeBaseId, {
          name: normalizedName,
          description: description.value.trim(),
        })
      : await store.createKnowledgeBase(normalizedName, description.value.trim())
    emit('saved', item)
    emit('close')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存知识库失败。'
  }
}
</script>

<template>
  <AppModal
    :open="open"
    :close-on-backdrop="!store.mutating"
    :title="knowledgeBase ? '编辑知识库' : '新建知识库'"
    @close="!store.mutating && emit('close')"
  >
    <form id="v2-knowledge-base-form" class="knowledge-form" @submit.prevent="submit">
      <label>
        <span>知识库名称</span>
        <input v-model="name" maxlength="160" autofocus placeholder="例如：计算机网络期末复习" />
      </label>
      <label>
        <span>说明 <small>可选</small></span>
        <textarea
          v-model="description"
          maxlength="1000"
          rows="4"
          placeholder="说明这组资料的课程、用途或范围"
        />
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <div class="form-actions">
        <AppButton variant="ghost" :disabled="store.mutating" @click="emit('close')">取消</AppButton>
        <AppButton
          :loading="store.mutating"
          :disabled="!name.trim()"
          @click="submit"
        >
          {{ knowledgeBase ? '保存' : '创建' }}
        </AppButton>
      </div>
    </template>
  </AppModal>
</template>

<style scoped>
.knowledge-form { display: grid; gap: 18px; }
.knowledge-form label { display: grid; gap: 8px; color: var(--color-text); font-size: 14px; font-weight: 650; }
.knowledge-form small { color: var(--color-text-muted); font-size: 12px; font-weight: 500; }
.knowledge-form input, .knowledge-form textarea { width: 100%; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-bg); color: var(--color-text); padding: 11px 12px; font: inherit; outline: none; }
.knowledge-form input:focus, .knowledge-form textarea:focus { border-color: var(--color-text-muted); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-text) 7%, transparent); }
.knowledge-form textarea { resize: vertical; min-height: 104px; line-height: 1.55; }
.form-error { margin: 0; padding: 10px 12px; border-radius: 9px; background: color-mix(in srgb, var(--color-danger) 10%, transparent); color: var(--color-danger); font-size: 13px; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; }
</style>
