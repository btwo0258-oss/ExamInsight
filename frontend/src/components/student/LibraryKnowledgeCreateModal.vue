<script setup lang="ts">
import { ref, watch } from 'vue'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; created: [id: number] }>()
const knowledgeBaseStore = useKnowledgeBaseStore()
const name = ref('')
const description = ref('')
const creating = ref(false)

watch(() => props.open, (open) => {
  if (!open) return
  name.value = ''
  description.value = ''
  creating.value = false
})

async function submit() {
  const nextName = name.value.trim()
  if (!nextName || creating.value) return
  creating.value = true
  try {
    const item = await knowledgeBaseStore.create({
      name: nextName,
      description: description.value.trim(),
      icon: 'folder',
      color: '#71717a',
    })
    emit('created', item.id)
    emit('close')
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <teleport to="body">
    <div v-if="open" class="library-modal-backdrop" @click.self="emit('close')">
      <section class="library-create-modal" role="dialog" aria-modal="true" aria-labelledby="library-create-title">
        <header>
          <h2 id="library-create-title">新建知识库</h2>
          <button type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <form @submit.prevent="submit">
          <label>
            <span>知识库名称</span>
            <input v-model="name" autofocus maxlength="60" placeholder="例如：Java 面向对象" />
          </label>
          <label>
            <span>说明</span>
            <textarea v-model="description" rows="5" maxlength="300" placeholder="简单说明知识库用途，便于后续智能学习分析" />
          </label>

          <footer>
            <button class="cancel" type="button" @click="emit('close')">取消</button>
            <button class="submit" type="submit" :disabled="!name.trim() || creating">
              {{ creating ? '创建中…' : '创建知识库' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </teleport>
</template>

<style scoped>
.library-modal-backdrop { position: fixed; inset: 0; z-index: 10030; padding: 24px; display: grid; place-items: center; background: rgba(0, 0, 0, .58); backdrop-filter: blur(3px); }
.library-create-modal { width: min(660px, 100%); overflow: hidden; border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-surface); color: var(--color-text); box-shadow: var(--shadow-lg); }
.library-create-modal header { min-height: 66px; padding: 0 20px; display: flex; align-items: center; justify-content: space-between; }
.library-create-modal h2 { margin: 0; font-size: 22px; line-height: 1.3; }
.library-create-modal header button { width: 34px; height: 34px; border: 0; border-radius: 8px; background: transparent; color: var(--color-text-muted); font-size: 25px; cursor: pointer; }
.library-create-modal header button:hover { background: var(--color-hover); color: var(--color-text); }
.library-create-modal form { padding: 0 20px 20px; }
.library-create-modal label { margin-bottom: 17px; display: grid; gap: 8px; }
.library-create-modal label span { font-size: 14px; font-weight: 650; }
.library-create-modal input, .library-create-modal textarea { width: 100%; padding: 11px 12px; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: 9px; outline: 0; background: var(--color-bg); color: var(--color-text); font: inherit; }
.library-create-modal input:focus, .library-create-modal textarea:focus { border-color: var(--color-text-muted); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-text) 7%, transparent); }
.library-create-modal textarea { min-height: 116px; resize: vertical; }
.library-create-modal footer { margin-top: 18px; display: flex; justify-content: flex-end; gap: 10px; }
.library-create-modal footer button { min-height: 40px; padding: 0 16px; border-radius: 9px; font-weight: 700; cursor: pointer; }
.library-create-modal .cancel { border: 1px solid var(--color-border); background: transparent; color: var(--color-text); }
.library-create-modal .submit { border: 0; background: var(--color-text); color: var(--color-bg); }
.library-create-modal .submit:disabled { opacity: .42; cursor: not-allowed; }
</style>
