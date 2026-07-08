<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries, recentUploads } from '@/mock'

const route = useRoute()
const router = useRouter()
const library = computed(() => courseLibraries.find((item) => item.id === Number(route.params.id)) ?? courseLibraries[0])
</script>

<template>
  <StudentShell>
    <div class="page">
      <header class="head">
        <div>
          <button class="back" type="button" @click="router.push('/library')">返回资料库</button>
          <h1>{{ library.name }}</h1>
          <p>{{ library.description }}</p>
        </div>
        <div class="head-actions">
          <AppButton variant="secondary">上传资料</AppButton>
          <AppButton variant="primary" @click="router.push({ path: '/learning', query: { libraryId: library.id } })">
            用于智能学习
          </AppButton>
        </div>
      </header>

      <section class="stats">
        <article>
          <strong>{{ library.fileCount }}</strong>
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

      <div class="grid">
        <section class="panel">
          <div class="section-head">
            <h2>文件列表</h2>
            <span>这里后续接文档列表 API</span>
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
              <tr v-for="file in recentUploads" :key="file.id">
                <td>{{ file.name }}</td>
                <td>{{ file.type }}</td>
                <td>{{ file.status }}</td>
                <td>{{ file.updatedAt }}</td>
                <td><button class="link-btn">查看</button></td>
              </tr>
            </tbody>
          </table>
        </section>

        <aside class="panel summary-panel">
          <div class="section-head">
            <h2>资料库摘要</h2>
          </div>
          <div class="summary-icon">
            <AppIcon name="book" :size="28" />
          </div>
          <p>该资料库适合用于智能学习分析、知识库答疑、资源生成和阶段练习。</p>
          <div class="tags">
            <span v-for="tag in library.tags" :key="tag">{{ tag }}</span>
          </div>
        </aside>
      </div>
    </div>
  </StudentShell>
</template>

<style scoped>
.page {
  width: min(1120px, calc(100% - 48px));
  margin: 0 auto;
  padding: 34px 0 56px;
}

.head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.back {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: var(--color-text-muted);
  margin-bottom: 10px;
}

.head h1,
.section-head h2 {
  margin: 0;
}

.head h1 {
  font-size: 26px;
}

.head p,
.section-head span,
.stats span,
.summary-panel p {
  color: var(--color-text-muted);
}

.head p {
  margin: 8px 0 0;
}

.head-actions {
  display: flex;
  gap: 10px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stats article,
.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
}

.stats article {
  padding: 16px;
  display: grid;
  gap: 4px;
}

.stats strong {
  font-size: 24px;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
}

.panel {
  padding: 18px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-head h2 {
  font-size: 18px;
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

.link-btn {
  border: 0;
  background: transparent;
  cursor: pointer;
  color: var(--color-text);
  font-weight: 700;
}

.summary-panel {
  align-self: start;
}

.summary-icon {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
}

.summary-panel p {
  line-height: 1.6;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tags span {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px 9px;
  font-size: 12px;
  background: var(--color-bg);
}

@media (max-width: 920px) {
  .stats,
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
