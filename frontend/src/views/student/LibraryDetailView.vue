<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import { courseLibraries } from '@/mock'
import { useLibraryResourceStore } from '@/stores/libraryResource'

const route = useRoute()
const router = useRouter()
const libraryResourceStore = useLibraryResourceStore()
const uploadOpen = ref(false)
const library = computed(() => courseLibraries.find((item) => item.id === Number(route.params.id)) ?? courseLibraries[0]!)
const files = computed(() => libraryResourceStore.resources.filter((item) => item.id.startsWith('mock-') || item.libraryId === library.value.id))
const fileCount = computed(() => library.value.fileCount + libraryResourceStore.resources.filter((item) => item.libraryId === library.value.id).length)
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
          <p>该资料库适合用于画像分析、知识库问答、个性化讲义生成、思维导图和代码案例生成。</p>
          <div class="summary-list">
            <article>
              <span>主要知识点</span>
              <strong>{{ library.tags.join('、') }}</strong>
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
  background: #fffffc;
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
  color: #667085;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
}

.back-btn .icon {
  width: 14px;
  height: 14px;
}

.hero-card {
  margin-top: 14px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 24px;
  background: #fffffc;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
}

h1 {
  font-size: 30px;
  color: #111827;
}

.hero-card p {
  margin-top: 10px;
  color: #667085;
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
  background: #f2f4f7;
  color: #667085;
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
  border: 1px solid #cfd7e3;
  background: #fffffc;
  color: #1f2937;
}

.primary-btn {
  border: 1px solid #111827;
  background: #111827;
  color: #fff;
}

.stats {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stats article,
.panel {
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  background: #fffffc;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.03);
}

.stats article {
  padding: 18px;
  display: grid;
  gap: 5px;
}

.stats strong {
  font-size: 26px;
  color: #111827;
}

.stats span {
  color: #667085;
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
  color: #1f2937;
}

.section-head label {
  width: 220px;
  height: 38px;
  border: 1px solid #cfd7e3;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: #667085;
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
  border-top: 1px solid #e6ebf3;
  text-align: left;
  color: #344054;
  font-size: 14px;
}

th {
  color: #667085;
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
  background: #f2f4f7;
  color: #667085;
  font-size: 13px;
}

.status.success {
  background: #dcfce7;
  color: #16a34a;
}

.status.active {
  background: #edf4ff;
  color: #2563eb;
}

.icon-btn {
  border: 0;
  background: transparent;
  color: #667085;
  cursor: pointer;
}

.summary-panel {
  align-self: start;
}

.summary-panel p {
  color: #667085;
  line-height: 1.7;
}

.summary-list {
  margin-top: 16px;
  display: grid;
  gap: 12px;
}

.summary-list article {
  border-top: 1px solid #e6ebf3;
  padding-top: 12px;
}

.summary-list span {
  display: block;
  color: #667085;
  font-size: 13px;
  margin-bottom: 5px;
}

.summary-list strong {
  color: #1f2937;
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
