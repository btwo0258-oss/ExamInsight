<script setup lang="ts">
import AppButton from '@/components/common/AppButton.vue'
import { courseLibraries } from '@/mock'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal">
      <header>
        <h2>上传学习资料</h2>
        <button type="button" @click="emit('close')">×</button>
      </header>

      <div class="drop-zone">
        <strong>拖拽文件到这里，或点击选择文件</strong>
        <span>支持 PDF / Word / TXT / Markdown / 图片</span>
      </div>

      <label class="field">
        <span>归属资料库</span>
        <select>
          <option v-for="item in courseLibraries" :key="item.id">{{ item.name }}</option>
          <option>新建资料库</option>
        </select>
      </label>

      <label class="check">
        <input type="checkbox" checked />
        <span>上传后立即用于本次智能学习分析</span>
      </label>

      <footer>
        <AppButton variant="secondary" @click="emit('close')">取消</AppButton>
        <AppButton variant="primary" @click="emit('close')">开始上传</AppButton>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.32);
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 24px;
}

.modal {
  width: min(540px, 100%);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 18px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
}

header,
footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

header h2 {
  margin: 0;
  font-size: 18px;
}

header button {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 24px;
  color: var(--color-text-muted);
}

.drop-zone {
  margin: 16px 0;
  min-height: 130px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  background: var(--color-bg);
  text-align: center;
}

.drop-zone span,
.field span,
.check {
  color: var(--color-text-muted);
  font-size: 13px;
}

.field {
  display: grid;
  gap: 8px;
}

select {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--color-bg);
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 0 18px;
}

footer {
  justify-content: flex-end;
}
</style>
