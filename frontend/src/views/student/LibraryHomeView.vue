<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import { courseLibraries, publicResources, recentUploads } from '@/mock'

const router = useRouter()
const uploadOpen = ref(false)

function useForLearning(id: number) {
  router.push({ path: '/learning', query: { libraryId: id } })
}
</script>

<template>
  <StudentShell>
    <div class="page">
      <header class="head">
        <div>
          <h1>资料库</h1>
          <p>统一管理课程资料和公共学习资源，作为智能学习分析的资料来源。</p>
        </div>
        <div class="head-actions">
          <AppButton variant="secondary" @click="uploadOpen = true">
            <template #icon><AppIcon name="upload-cloud" :size="16" /></template>
            上传资料
          </AppButton>
          <AppButton variant="primary">新建资料库</AppButton>
        </div>
      </header>

      <input class="search" placeholder="搜索课程资料、试卷、讲义..." />

      <div class="grid">
        <section class="panel library-panel">
          <div class="section-head">
            <h2>我的课程资料</h2>
            <span>{{ courseLibraries.length }} 个资料库</span>
          </div>
          <div class="library-grid">
            <article v-for="item in courseLibraries" :key="item.id" class="library-card">
              <div class="library-top">
                <AppIcon name="folder" :size="22" />
                <span :class="`status status--${item.status}`">
                  {{ item.status === 'ready' ? '解析完成' : '处理中' }}
                </span>
              </div>
              <h3>{{ item.name }}</h3>
              <p>{{ item.description }}</p>
              <div class="meta">{{ item.fileCount }} 个文件 · {{ item.chunkCount }} 个知识片段</div>
              <div class="card-actions">
                <AppButton variant="secondary" @click="useForLearning(item.id)">用于智能学习</AppButton>
                <AppButton variant="ghost" @click="router.push(`/library/${item.id}`)">进入资料库</AppButton>
              </div>
            </article>
          </div>
        </section>

        <aside class="panel">
          <div class="section-head">
            <h2>公共学习资源</h2>
            <span>可加入我的资料库</span>
          </div>
          <div class="public-list">
            <article v-for="item in publicResources" :key="item.id" class="public-card">
              <div>
                <strong>{{ item.title }}</strong>
                <p>{{ item.desc }}</p>
                <small>{{ item.category }} · {{ item.type }}</small>
              </div>
              <AppButton variant="secondary">加入</AppButton>
            </article>
          </div>
        </aside>
      </div>

      <section class="panel upload-panel">
        <div class="section-head">
          <h2>最近上传</h2>
          <span>用于观察解析和向量化状态</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>文件名</th>
              <th>类型</th>
              <th>状态</th>
              <th>更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="file in recentUploads" :key="file.id">
              <td>{{ file.name }}</td>
              <td>{{ file.type }}</td>
              <td>{{ file.status }}</td>
              <td>{{ file.updatedAt }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>

    <UploadMaterialModal :open="uploadOpen" @close="uploadOpen = false" />
  </StudentShell>
</template>

<style scoped>
.page {
  width: min(1180px, calc(100% - 48px));
  margin: 0 auto;
  padding: 34px 0 56px;
}

.head,
.section-head,
.library-top,
.card-actions,
.public-card {
  display: flex;
  align-items: center;
}

.head {
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 16px;
}

.head h1,
.section-head h2,
.library-card h3 {
  margin: 0;
}

.head h1 {
  font-size: 28px;
}

.head p,
.section-head span,
.library-card p,
.meta,
.public-card p,
.public-card small {
  color: var(--color-text-muted);
}

.head p {
  margin: 8px 0 0;
}

.head-actions {
  display: flex;
  gap: 10px;
}

.search {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-surface);
  padding: 12px 14px;
  margin-bottom: 16px;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 16px;
}

.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 18px;
}

.section-head {
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-head h2 {
  font-size: 18px;
}

.library-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.library-card,
.public-card {
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-bg);
}

.library-card {
  padding: 14px;
}

.library-top {
  justify-content: space-between;
  margin-bottom: 12px;
}

.status {
  font-size: 12px;
  border-radius: 999px;
  padding: 3px 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.status--ready {
  color: #059669;
}

.status--processing {
  color: #d97706;
}

.library-card h3 {
  font-size: 16px;
}

.library-card p,
.public-card p {
  margin: 8px 0;
  line-height: 1.5;
}

.meta {
  font-size: 13px;
}

.card-actions {
  margin-top: 14px;
  gap: 8px;
  flex-wrap: wrap;
}

.public-list {
  display: grid;
  gap: 10px;
}

.public-card {
  padding: 12px;
  gap: 12px;
  justify-content: space-between;
}

.public-card strong,
.public-card small {
  display: block;
}

.upload-panel {
  margin-top: 16px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 11px 8px;
  border-top: 1px solid var(--color-border);
  font-size: 14px;
}

th {
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .grid,
  .library-grid {
    grid-template-columns: 1fr;
  }
}
</style>
