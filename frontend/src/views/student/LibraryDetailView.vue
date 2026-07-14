<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import { courseLibraries } from '@/mock'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'

const route = useRoute()
const router = useRouter()
const libraryResourceStore = useLibraryResourceStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const uploadOpen = ref(false)
const library = computed(() => {
  const id = Number(route.params.id)
  const stored = knowledgeBaseStore.list.find((item) => item.id === id)
  const preset = courseLibraries.find((item) => item.id === id)
  return {
    id,
    name: stored?.name || preset?.name || '未命名知识库',
    description: stored?.description || preset?.description || '暂无说明',
    tags: preset?.tags || [],
    fileCount: stored?.documentCount || preset?.fileCount || 0,
    chunkCount: preset?.chunkCount || 0,
    status: preset?.status || 'ready',
    updatedAt: stored?.updateTime || preset?.updatedAt || '刚刚',
  }
})
const files = computed(() => libraryResourceStore.resources.filter((item) => item.id.startsWith('mock-') || item.libraryId === library.value.id))
const fileCount = computed(() => library.value.fileCount + libraryResourceStore.resources.filter((item) => item.libraryId === library.value.id).length)

onMounted(() => {
  void knowledgeBaseStore.fetchList()
})
</script>

<template>
  <StudentShell>
    <div class="detail-page">
      <header class="hero">
        <button class="back-btn" type="button" @click="router.push('/library')">
          <AppIcon name="chevron-left" :size="18" />
          返回资料库
        </button>
        <div class="hero-card">
          <div>
            <h1>{{ library.name }}</h1>
            <p>{{ library.description }}</p>
            <div class="tags">
              <span v-for="tag in library.tags" :key="tag">{{ tag }}</span>
            </div>
          </div>
          <div class="hero-actions">
            <button class="outline-btn" type="button" @click="uploadOpen = true">
              <AppIcon name="upload-cloud" :size="18" />
              上传资料
            </button>
            <button
              class="primary-btn"
              type="button"
              @click="router.push({ path: '/learning', query: { libraryId: library.id } })"
            >
              <AppIcon name="graduation" :size="18" />
              用于智能学习
            </button>
          </div>
        </div>
      </header>

      <section class="stats">
        <article>
          <strong>{{ fileCount }}</strong>
          <span>文件</span>
        </article>
        <article>
          <strong>{{ library.chunkCount }}</strong>
          <span>知识片段</span>
        </article>
        <article>
          <strong>{{ library.status === 'ready' ? '已完成' : '处理中' }}</strong>
          <span>向量化状态</span>
        </article>
      </section>

      <div class="content-grid">
        <section class="panel files-panel">
          <div class="section-head">
            <h2>文件列表</h2>
            <label>
              <AppIcon name="search" :size="18" />
              <input placeholder="搜索文件" />
            </label>
          </div>
          <table>
            <thead>
              <tr>
                <th>文件名</th>
                <th>类型</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="file in files" :key="file.id">
                <td>
                  <AppIcon name="file" :size="18" />
                  {{ file.name }}
                </td>
                <td>{{ file.type }}</td>
                <td>
                  <span
                    class="status"
                    :class="{
                      success: file.status === '解析完成',
                      active: file.status === '向量化中',
                    }"
                  >
                    {{ file.status }}
                  </span>
                </td>
                <td>{{ file.updatedAt }}</td>
                <td>
                  <button class="icon-btn" type="button"><AppIcon name="eye" :size="17" /></button>
                  <button class="icon-btn" type="button"><AppIcon name="download" :size="17" /></button>
                  <button class="icon-btn" type="button"><AppIcon name="more-horizontal" :size="18" /></button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <aside class="panel summary-panel">
          <div class="panel-title">
            <AppIcon name="book" :size="22" />
            <h2>资料库摘要</h2>
          </div>
          <p>该资料库适合用于画像分析、知识库问答、个性化学习手册生成、思维导图和代码案例生成。</p>
          <div class="summary-list">
            <article>
              <span>主要知识点</span>
              <strong>{{ library.tags.join('、') || '待上传资料后识别' }}</strong>
            </article>
            <article>
              <span>推荐用途</span>
              <strong>期末复习、项目实操、错题强化</strong>
            </article>
            <article>
              <span>最近更新</span>
              <strong>{{ library.updatedAt }}</strong>
            </article>
          </div>
        </aside>
      </div>
    </div>

    <UploadMaterialModal :open="uploadOpen" :library-id="library.id" @close="uploadOpen = false" />
  </StudentShell>
</template>

<style scoped>
.detail-page {
  min-height: 100%;
  padding: 34px 28px 56px;
  background: var(--color-bg);
  color: var(--color-text);
}

.detail-page,
.detail-page * {
  box-sizing: border-box;
}

.hero,
.stats,
.content-grid {
  max-width: 1180px;
  margin-left: auto;
  margin-right: auto;
}

h1,
h2,
p {
  margin: 0;
}

.back-btn {
  height: 28px;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--ui-hover-radius);
  padding: 0 8px;
}

.back-btn:hover,
.outline-btn:hover {
  background: var(--ui-hover-bg);
}

.back-btn .icon {
  width: 14px;
  height: 14px;
}

.hero-card {
  margin-top: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 24px;
  background: var(--color-surface);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
}

h1 {
  font-size: 30px;
  color: var(--color-text);
}

.hero-card p {
  margin-top: 10px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.tags span {
  padding: 5px 10px;
  border-radius: 6px;
  background: var(--color-hover);
  color: var(--color-text-muted);
  font-size: 13px;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.outline-btn,
.primary-btn {
  height: 42px;
  border-radius: 8px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-weight: 700;
}

.outline-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

.primary-btn {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.stats {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stats article,
.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.stats article {
  padding: 18px;
  display: grid;
  gap: 5px;
}

.stats strong {
  font-size: 26px;
  color: var(--color-text);
}

.stats span {
  color: var(--color-text-muted);
}

.content-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
}

.panel {
  padding: 20px;
}

.section-head,
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.panel-title {
  justify-content: flex-start;
}

h2 {
  font-size: 21px;
  color: var(--color-text);
}

.section-head label {
  width: 220px;
  height: 38px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: var(--color-text-muted);
}

.section-head input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  height: 44px;
  border-top: 1px solid var(--color-border);
  text-align: left;
  color: var(--color-text);
  font-size: 14px;
}

th {
  color: var(--color-text-muted);
  font-size: 13px;
}

td:first-child {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status {
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--color-hover);
  color: var(--color-text-muted);
  font-size: 13px;
}

.status.success {
  background: color-mix(in srgb, var(--color-success) 15%, var(--color-surface));
  color: var(--color-success);
}

.status.active {
  background: color-mix(in srgb, var(--color-info) 12%, var(--color-surface));
  color: var(--color-info);
}

.icon-btn {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: var(--ui-hover-radius);
  padding: 6px;
}

.icon-btn:hover {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.summary-panel {
  align-self: start;
}

.summary-panel p {
  color: var(--color-text-muted);
  line-height: 1.7;
}

.summary-list {
  margin-top: 16px;
  display: grid;
  gap: 12px;
}

.summary-list article {
  border-top: 1px solid var(--color-border);
  padding-top: 12px;
}

.summary-list span {
  display: block;
  color: var(--color-text-muted);
  font-size: 13px;
  margin-bottom: 5px;
}

.summary-list strong {
  color: var(--color-text);
  line-height: 1.5;
}

@media (max-width: 980px) {
  .hero-card,
  .stats,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    flex-wrap: wrap;
  }
}
</style>
