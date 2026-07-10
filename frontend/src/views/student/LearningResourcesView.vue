<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()
const plan = computed(() => learningPlans.find((item) => item.id === Number(route.params.id)) ?? learningPlans[0]!)
const tabs = ['讲义', 'PPT', '练习题', '思维导图', '代码案例', '拓展阅读', '导出文件']
const activeTab = ref('讲义')
const activeResource = computed(() => plan.value.resources.find((item) => item.group === activeTab.value) ?? plan.value.resources[0])

function iconName(group: string) {
  if (group === 'PPT') return 'presentation'
  if (group === '练习题') return 'edit'
  if (group === '思维导图') return 'mind-topic'
  if (group === '代码案例') return 'code'
  if (group === '拓展阅读') return 'book'
  if (group === '导出文件') return 'download'
  return 'file'
}
</script>

<template>
  <StudentShell>
    <div class="resources-page">
      <header class="page-head">
        <button type="button" @click="router.push(`/learning/${plan.id}`)">
          <AppIcon name="chevron-left" :size="18" />
        </button>
        <div>
          <h1>资源包详情</h1>
          <p>{{ plan.title }} 的个性化学习资源，可查看、编辑、重新生成或导出。</p>
        </div>
      </header>

      <nav class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab"
          :class="{ active: activeTab === tab }"
          type="button"
          @click="activeTab = tab"
        >
          {{ tab }}
        </button>
      </nav>

      <main class="resource-layout">
        <aside class="outline-panel">
          <h2>{{ activeTab }}大纲</h2>
          <button class="outline-active" type="button">1. 基础概念</button>
          <button type="button">1.1 继承的定义</button>
          <button type="button">1.2 多态的定义</button>
          <button type="button">2. 继承</button>
          <button type="button">2.1 继承的语法</button>
          <button type="button">2.2 访问权限与继承</button>
          <button type="button">3. 多态</button>
          <button type="button">3.1 方法重写</button>
          <button type="button">3.2 向上转型</button>
          <button type="button">4. 结合案例</button>
        </aside>

        <section class="preview-panel">
          <header>
            <div>
              <AppIcon :name="iconName(activeTab)" :size="28" />
              <div>
                <h2>{{ activeResource?.title ?? activeTab }}</h2>
                <p>{{ activeResource?.desc }}</p>
              </div>
            </div>
            <span :class="{ muted: activeResource?.status === '未选择' }">{{ activeResource?.status ?? '未选择' }}</span>
          </header>

          <div class="tool-row">
            <button type="button"><AppIcon name="edit" :size="16" /> 编辑</button>
            <button type="button"><AppIcon name="refresh" :size="16" /> 重新生成</button>
            <button type="button"><AppIcon name="file" :size="16" /> 导出 PDF</button>
            <button type="button"><AppIcon name="notebook" :size="16" /> 导出 DOCX</button>
            <button type="button"><AppIcon name="code" :size="16" /> 导出 Markdown</button>
          </div>

          <article class="document">
            <div class="page-toolbar">
              <button type="button">‹</button>
              <span>1 / 28</span>
              <button type="button">›</button>
              <select>
                <option>100%</option>
                <option>125%</option>
              </select>
              <button type="button"><AppIcon name="search" :size="16" /></button>
            </div>
            <h3>1. 基础概念</h3>
            <h4>1.1 继承的定义</h4>
            <p>
              继承是面向对象编程中的一种机制，允许一个类获取另一个类的属性和方法，并在此基础上进行扩展。
              对你来说，重点不是死记语法，而是理解“父类负责抽象共性，子类负责扩展差异”。
            </p>
            <pre><code>class Animal {
  void eat() {
    System.out.println("Animal is eating");
  }
}

class Dog extends Animal {
  void bark() {
    System.out.println("Dog is barking");
  }
}</code></pre>
            <h4>1.2 多态的定义</h4>
            <p>多态是指同一方法调用，由于对象的不同而产生不同的行为。理解它的关键是区分编译类型和运行类型。</p>
          </article>
        </section>

        <aside class="file-panel">
          <h2>资源包内容（{{ plan.resources.length }} 项）</h2>
          <button
            v-for="resource in plan.resources"
            :key="resource.id"
            class="file-row"
            type="button"
            @click="activeTab = resource.group"
          >
            <AppIcon :name="iconName(resource.group)" :size="20" />
            <span>{{ resource.fileName ?? resource.title }}</span>
            <small>{{ resource.status }}</small>
          </button>
        </aside>
      </main>
    </div>
  </StudentShell>
</template>

<style scoped>
.resources-page {
  min-height: 100%;
  padding: 28px 34px 42px;
  background: var(--color-bg);
}

.resources-page,
.resources-page * {
  box-sizing: border-box;
}

h1,
h2,
h3,
h4,
p {
  margin: 0;
}

button,
input,
select {
  font: inherit;
}

.page-head,
.tabs,
.resource-layout {
  max-width: 1500px;
  margin-left: auto;
  margin-right: auto;
}

.page-head {
  display: grid;
  grid-template-columns: 42px 1fr;
  align-items: center;
  gap: 16px;
}

.page-head > button {
  width: 42px;
  height: 42px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

h1 {
  color: var(--color-text);
  font-size: 30px;
  font-weight: 800;
}

.page-head p {
  margin-top: 7px;
  color: var(--color-text-muted);
}

.tabs {
  margin-top: 24px;
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--color-border);
}

.tabs button {
  height: 44px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  font-weight: 800;
}

.tabs button.active {
  color: var(--color-text);
  border-bottom-color: var(--color-primary);
}

.resource-layout {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr) 360px;
  gap: 16px;
}

.outline-panel,
.preview-panel,
.file-panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.outline-panel,
.file-panel {
  padding: 18px;
  align-self: start;
}

h2 {
  color: var(--color-text);
  font-size: 18px;
  font-weight: 800;
}

.outline-panel {
  display: grid;
  gap: 8px;
}

.outline-panel h2,
.file-panel h2 {
  margin-bottom: 10px;
}

.outline-panel button {
  min-height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  text-align: left;
  padding: 0 8px;
  cursor: pointer;
}

.outline-panel button.outline-active,
.outline-panel button:hover {
  background: #f4f6f8;
  color: var(--color-text);
}

.preview-panel {
  padding: 20px;
}

.preview-panel > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.preview-panel > header > div {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #2563eb;
}

.preview-panel header p {
  margin-top: 5px;
  color: var(--color-text-muted);
}

.preview-panel header span {
  border-radius: 6px;
  padding: 5px 10px;
  background: #ecfdf3;
  color: #16a34a;
  font-size: 13px;
  font-weight: 800;
}

.preview-panel header span.muted {
  background: #f3f4f6;
  color: var(--color-text-muted);
}

.tool-row {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tool-row button {
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  cursor: pointer;
}

.document {
  margin-top: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 22px;
  min-height: 560px;
}

.page-toolbar {
  height: 42px;
  border-bottom: 1px solid var(--color-border);
  margin: -22px -22px 22px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-toolbar button,
.page-toolbar select {
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text);
}

.document h3 {
  color: #2563eb;
  font-size: 24px;
}

.document h4 {
  margin-top: 18px;
  color: var(--color-text);
  font-size: 18px;
}

.document p {
  margin-top: 10px;
  color: var(--color-text);
  line-height: 1.8;
}

pre {
  margin: 16px 0 0;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fafc;
  color: var(--color-text);
  overflow: auto;
}

.file-panel {
  display: grid;
  gap: 8px;
}

.file-row {
  min-height: 42px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  cursor: pointer;
  text-align: left;
}

.file-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-row small {
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .resource-layout {
    grid-template-columns: 1fr;
  }
}
</style>
