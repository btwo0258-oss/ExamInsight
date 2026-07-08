<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import { courseLibraries, publicResources, recentUploads, type PublicResource } from '@/mock'

const router = useRouter()
const uploadOpen = ref(false)
const joinResource = ref<PublicResource | null>(null)

function useForLearning(id: number) {
  router.push({ path: '/learning', query: { libraryId: id } })
}
</script>

<template>
  <StudentShell>
    <div class="library-page">
      <header class="page-title">
        <h1>资料库</h1>
      </header>

      <div class="toolbar">
        <label class="search-box">
          <AppIcon name="search" :size="24" />
          <input placeholder="搜索课程资料、试卷、讲义..." />
        </label>
        <button class="outline-btn" type="button" @click="uploadOpen = true">
          <AppIcon name="upload-cloud" :size="22" />
          上传资料
        </button>
        <button class="primary-btn" type="button">
          <AppIcon name="folder" :size="20" />
          新建资料库
        </button>
      </div>

      <div class="main-grid">
        <section class="panel mine-panel">
          <div class="section-head">
            <div>
              <h2>我的课程资料</h2>
              <span><AppIcon name="folder" :size="16" /> {{ courseLibraries.length }} 个资料库</span>
            </div>
            <div class="view-switch">
              <button class="active" type="button"><AppIcon name="grid" :size="16" /></button>
              <button type="button"><AppIcon name="list" :size="16" /></button>
            </div>
          </div>

          <div class="library-grid">
            <article v-for="item in courseLibraries" :key="item.id" class="library-card">
              <div class="card-top">
                <span class="folder-box"><AppIcon name="folder" :size="42" /></span>
                <button type="button"><AppIcon name="more-horizontal" :size="22" /></button>
              </div>
              <h3>{{ item.name.replace('资料库', '') }}</h3>
              <p>{{ item.fileCount }} 个文档</p>
              <div class="meta-line">
                <AppIcon name="file" :size="16" />
                <span>文档类型：讲义、课件、代码、习题</span>
              </div>
              <div class="meta-line">
                <AppIcon name="clock" :size="16" />
                <span>更新时间：{{ item.updatedAt }}</span>
              </div>
              <div class="card-actions">
                <button class="soft-btn" type="button" @click="useForLearning(item.id)">
                  <AppIcon name="graduation" :size="16" />
                  用于智能学习
                </button>
                <button class="line-btn" type="button" @click="router.push(`/library/${item.id}`)">
                  进入资料库
                </button>
              </div>
            </article>
          </div>
        </section>

        <aside class="panel public-panel">
          <div class="section-head">
            <div>
              <h2>公共学习资源</h2>
            </div>
            <button class="more-link" type="button">
              查看更多
              <AppIcon name="chevron-right" :size="16" />
            </button>
          </div>

          <div class="public-list">
            <article v-for="item in publicResources" :key="item.id" class="public-card">
              <AppIcon name="file" :size="42" />
              <div>
                <h3>{{ item.title }}</h3>
                <p>{{ item.type }} · {{ item.category }} · 适合智能学习分析</p>
                <div class="tags">
                  <span v-for="tag in item.desc.split('、').slice(0, 3)" :key="tag">{{ tag }}</span>
                </div>
              </div>
              <button type="button" @click="joinResource = item">加入我的资料库</button>
            </article>
          </div>
        </aside>
      </div>

      <section class="panel upload-panel">
        <h2>最近上传</h2>
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
        <button class="all-files" type="button">
          查看全部文件
          <AppIcon name="chevron-right" :size="16" />
        </button>
      </section>
    </div>

    <UploadMaterialModal :open="uploadOpen" @close="uploadOpen = false" />

    <div v-if="joinResource" class="modal-backdrop" @click.self="joinResource = null">
      <section class="join-modal">
        <header>
          <h2>加入我的资料库</h2>
          <button type="button" @click="joinResource = null">×</button>
        </header>
        <p>将「{{ joinResource.title }}」加入已有资料库，后续可直接用于智能学习分析。</p>
        <label>
          <span>选择资料库</span>
          <select>
            <option v-for="item in courseLibraries" :key="item.id">{{ item.name }}</option>
          </select>
        </label>
        <footer>
          <button class="outline-btn" type="button" @click="joinResource = null">取消</button>
          <button class="primary-btn" type="button" @click="joinResource = null">确认加入</button>
        </footer>
      </section>
    </div>
  </StudentShell>
</template>

<style scoped>
.library-page {
  min-height: 100%;
  padding: 38px 28px 56px;
  background: #fffffc;
}

.page-title,
.toolbar,
.main-grid,
.upload-panel {
  max-width: 1260px;
  margin-left: auto;
  margin-right: auto;
}

h1,
h2,
h3,
p {
  margin: 0;
}

h1 {
  font-size: 34px;
  font-weight: 800;
  color: #111827;
}

.page-title {
  margin-bottom: 26px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 148px 170px;
  gap: 16px;
  margin-bottom: 24px;
}

.search-box {
  height: 56px;
  border: 1px solid #cfd7e3;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 18px;
  background: #fffffc;
  color: #667085;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 16px;
}

.outline-btn,
.primary-btn,
.soft-btn,
.line-btn {
  height: 56px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 700;
}

.outline-btn {
  border: 1px solid #cfd7e3;
  background: #fffffc;
  color: #1f2937;
}

.primary-btn {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #fff;
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.18);
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 520px;
  gap: 24px;
}

.panel {
  background: #fffffc;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.03);
}

.mine-panel,
.public-panel,
.upload-panel {
  padding: 20px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-head h2,
.upload-panel h2 {
  font-size: 22px;
  color: #1f2937;
}

.section-head span {
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #667085;
}

.view-switch {
  display: flex;
  padding: 4px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
}

.view-switch button {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #667085;
  cursor: pointer;
}

.view-switch button.active {
  background: #edf4ff;
  color: #2563eb;
}

.library-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.library-card {
  min-height: 292px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 20px 16px 16px;
  display: flex;
  flex-direction: column;
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.folder-box {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #f2f4f7;
  color: #344054;
}

.card-top button,
.icon-btn {
  border: 0;
  background: transparent;
  cursor: pointer;
  color: #667085;
}

.library-card h3 {
  font-size: 17px;
  color: #111827;
}

.library-card p {
  margin-top: 8px;
  color: #667085;
}

.meta-line {
  margin-top: 14px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}

.card-actions {
  margin-top: auto;
  display: flex;
  gap: 10px;
}

.soft-btn,
.line-btn {
  height: 40px;
  padding: 0 10px;
  font-size: 12px;
  white-space: nowrap;
}

.soft-btn {
  border: 1px solid #dbe2ec;
  background: #fffffc;
  color: #1f2937;
}

.line-btn {
  border: 1px solid #9ab9ff;
  background: #fffffc;
  color: #2563eb;
}

.more-link,
.all-files {
  border: 0;
  background: transparent;
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-weight: 700;
}

.public-list {
  display: grid;
  gap: 16px;
}

.public-card {
  min-height: 116px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 138px;
  align-items: center;
  gap: 16px;
  padding: 16px;
}

.public-card h3 {
  font-size: 17px;
  color: #111827;
}

.public-card p {
  margin-top: 7px;
  color: #667085;
}

.public-card > button {
  height: 38px;
  border: 1px solid #cfd7e3;
  border-radius: 6px;
  background: #fffffc;
  color: #344054;
  cursor: pointer;
  font-weight: 700;
}

.tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.tags span {
  padding: 4px 8px;
  border: 1px solid #dbe2ec;
  border-radius: 6px;
  background: #f8fafc;
  color: #667085;
  font-size: 12px;
}

.upload-panel {
  margin-top: 18px;
}

.upload-panel h2 {
  margin-bottom: 10px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  height: 42px;
  border-top: 1px solid #e6ebf3;
  text-align: left;
  color: #344054;
  font-size: 14px;
}

th {
  color: #667085;
  font-size: 13px;
  font-weight: 700;
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

.all-files {
  margin: 14px auto 0;
  display: flex;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 220;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.34);
}

.join-modal {
  width: min(480px, 100%);
  padding: 20px;
  border-radius: 8px;
  background: #fffffc;
  border: 1px solid #dbe2ec;
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.2);
}

.join-modal header,
.join-modal footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.join-modal header button {
  border: 0;
  background: transparent;
  color: #667085;
  cursor: pointer;
  font-size: 24px;
}

.join-modal p {
  margin: 14px 0;
  color: #667085;
  line-height: 1.6;
}

.join-modal label {
  display: grid;
  gap: 8px;
  color: #344054;
  font-weight: 700;
}

.join-modal select {
  height: 42px;
  border: 1px solid #cfd7e3;
  border-radius: 8px;
  background: #fffffc;
  padding: 0 12px;
}

.join-modal footer {
  justify-content: flex-end;
  margin-top: 18px;
}

.join-modal footer .outline-btn,
.join-modal footer .primary-btn {
  height: 40px;
  padding: 0 16px;
}

@media (max-width: 1180px) {
  .main-grid,
  .toolbar,
  .library-grid {
    grid-template-columns: 1fr;
  }

  .public-card {
    grid-template-columns: 42px 1fr;
  }

  .public-card > button {
    grid-column: 2;
    width: fit-content;
  }
}
</style>
