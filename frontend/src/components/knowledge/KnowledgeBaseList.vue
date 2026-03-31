<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useConversationStore } from '@/stores/conversation'
import { useMindMapStore } from '@/stores/mindmap'
import KnowledgeBaseCard from './KnowledgeBaseCard.vue'
import KnowledgeBaseCreate from './KnowledgeBaseCreate.vue'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()
const kbStore = useKnowledgeBaseStore()
const conversationStore = useConversationStore()
const mindMapStore = useMindMapStore()

const searchQuery = ref('')
const sortBy = ref('updated_at') // 'updated_at', 'name'
const currentPage = ref(1)
const pageSize = ref(10)
const showCreateDialog = ref(false)
const errorState = ref(false)

const filteredKnowledgeBases = computed(() => {
  let result = kbStore.list

  // Search
  if (searchQuery.value) {
    const lowerQuery = searchQuery.value.toLowerCase()
    result = result.filter(kb => 
      kb.name.toLowerCase().includes(lowerQuery)
    )
  }

  // Sort
  result = [...result].sort((a, b) => {
    if (sortBy.value === 'name') {
      return a.name.localeCompare(b.name)
    } else {
      // updated_at (or updateTime)
      const timeA = new Date(a.updateTime || 0).getTime()
      const timeB = new Date(b.updateTime || 0).getTime()
      return timeB - timeA // descending
    }
  })

  return result
})

const totalPages = computed(() => Math.ceil(filteredKnowledgeBases.value.length / pageSize.value))

const paginatedKnowledgeBases = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredKnowledgeBases.value.slice(start, start + pageSize.value)
})

function prevPage() {
  if (currentPage.value > 1) currentPage.value--
}

function nextPage() {
  if (currentPage.value < totalPages.value) currentPage.value++
}

async function loadData() {
  errorState.value = false
  try {
    await kbStore.fetchAll()
    await Promise.all([
      conversationStore.fetchList(),
      mindMapStore.fetchList()
    ])
  } catch (err) {
    errorState.value = true
    console.error('Failed to load knowledge bases', err)
  }
}

onMounted(() => {
  loadData()
})

function handleCreate() {
  showCreateDialog.value = true
}

function handleViewDetail(id: number) {
  router.push(`/knowledge/${id}`)
}
</script>

<template>
  <div class="knowledge-base-list">
    <div class="header">
      <h1 class="title">知识库</h1>
      <div class="actions">
        <div class="search">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索知识库..."
          />
        </div>
        <select v-model="sortBy" class="sort-select">
          <option value="updated_at">按更新时间排序</option>
          <option value="name">按名称排序</option>
        </select>
        <AppButton variant="primary" @click="handleCreate">
          + 新建知识库
        </AppButton>
      </div>
    </div>

    <div v-if="errorState" class="empty">
      <div class="empty__icon">
        <AppIcon name="alert-triangle" :size="48" />
      </div>
      <h3 class="empty__title">加载失败</h3>
      <p class="empty__description">无法获取知识库列表，请稍后重试</p>
      <AppButton @click="loadData" style="margin-top: 16px;">重试</AppButton>
    </div>

    <div v-else-if="filteredKnowledgeBases.length === 0" class="empty">
      <div class="empty__icon">
        <AppIcon name="book" :size="48" />
      </div>
      <h3 class="empty__title">暂无知识库</h3>
      <p class="empty__description">创建您的第一个知识库来开始使用</p>
    </div>

    <div v-else>
      <div class="grid">
        <KnowledgeBaseCard
          v-for="kb in paginatedKnowledgeBases"
          :key="kb.id"
          :knowledge-base="kb"
          @click="handleViewDetail(kb.id)"
        />
      </div>
      <div class="pagination" v-if="totalPages > 1">
        <button :disabled="currentPage === 1" @click="prevPage">上一页</button>
        <span>{{ currentPage }} / {{ totalPages }}</span>
        <button :disabled="currentPage === totalPages" @click="nextPage">下一页</button>
      </div>
    </div>

    <KnowledgeBaseCreate
      v-if="showCreateDialog"
      :open="showCreateDialog"
      @close="showCreateDialog = false"
    />
  </div>
</template>

<style scoped>
.knowledge-base-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  flex-wrap: wrap;
  gap: 16px;
}

.title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
}

.actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search {
  position: relative;
}

.search input {
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  width: 280px;
  background: var(--color-surface);
  color: var(--color-text);
}

.search input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.empty {
  text-align: center;
  padding: 80px 20px;
}

.empty__icon {
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--color-text-muted);
  margin-bottom: 24px;
}

.empty__title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}

.empty__description {
  font-size: 14px;
  color: var(--color-text-muted);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.sort-select {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: none;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 32px;
}

.pagination button {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
