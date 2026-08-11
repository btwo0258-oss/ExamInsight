<script setup lang="ts">
import { computed } from 'vue'
import ConversationItem from './ConversationItem.vue'
import type { Conversation } from '@/api/conversation'
import type { ConversationId } from '@/types/contracts/conversation'

type Props = {
  items: Conversation[]
  activeId: ConversationId | null
  showDate?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showDate: true
})
const emit = defineEmits<{
  open: [id: ConversationId]
  rename: [id: ConversationId, title: string]
  remove: [id: ConversationId]
}>()

const groupedList = computed(() => {
  if (!props.showDate) {
    return [{ label: '', items: props.items }]
  }

  const groups = {
    今天: [] as Conversation[],
    '7天内': [] as Conversation[],
    '30天内': [] as Conversation[],
  }
  
  const others: { [key: string]: Conversation[] } = {}

  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const sevenDaysAgo = todayStart - 6 * 24 * 60 * 60 * 1000
  const thirtyDaysAgo = todayStart - 29 * 24 * 60 * 60 * 1000

  for (const c of props.items) {
    const time = new Date(c.updateTime || c.createTime || Date.now()).getTime()
    if (time >= todayStart) {
      groups.今天.push(c)
    } else if (time >= sevenDaysAgo) {
      groups['7天内'].push(c)
    } else if (time >= thirtyDaysAgo) {
      groups['30天内'].push(c)
    } else {
      const d = new Date(time)
      const month = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
      if (!others[month]) others[month] = []
      others[month].push(c)
    }
  }

  const result = []
  if (groups.今天.length) result.push({ label: '今天', items: groups.今天 })
  if (groups['7天内'].length) result.push({ label: '7天内', items: groups['7天内'] })
  if (groups['30天内'].length) result.push({ label: '30天内', items: groups['30天内'] })
  
  const sortedMonths = Object.keys(others).sort((a, b) => b.localeCompare(a))
  for (const month of sortedMonths) {
    result.push({ label: month, items: others[month] })
  }

  return result
})
</script>

<template>
  <div class="list ds-virtual-list-visible-items">
    <template v-if="props.items.length > 0">
      <div v-for="group in groupedList" :key="group.label" class="group-container">
        <div v-if="group.label" class="group">{{ group.label }}</div>
        <div class="items">
          <ConversationItem
            v-for="c in group.items"
            :key="c.id"
            :item="c"
            :active="activeId === c.id"
            @open="(id) => emit('open', id)"
            @rename="(id, title) => emit('rename', id, title)"
            @remove="(id) => emit('remove', id)"
          />
        </div>
      </div>
    </template>
    <div v-else class="empty">暂无会话</div>
  </div>
</template>

<style scoped>
.list {
  margin: 0 12px 12px;
  padding: 10px 10px 8px;
  overflow: auto;
  flex: 1;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: rgba(0, 0, 0, 0.02);
}

.group {
  padding: 6px 8px 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-muted);
}

.items {
  display: grid;
  gap: 4px;
}

.empty {
  padding: 12px 8px;
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
