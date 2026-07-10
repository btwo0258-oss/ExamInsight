<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import LibrarySelectModal from '@/components/student/LibrarySelectModal.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import { courseLibraries, learningPlans } from '@/mock'

const router = useRouter()
const prompt = ref('')
const step = ref<'home' | 'profile'>('home')
const selectedLibraryId = ref(1)
const libraryModalOpen = ref(false)
const uploadModalOpen = ref(false)

const selectedLibrary = computed(() => courseLibraries.find((item) => item.id === selectedLibraryId.value))

const resourceOptions = ref([
  { key: 'path', label: '学习路径', checked: true },
  { key: 'handout', label: '讲义', checked: true },
  { key: 'exercise', label: '练习题', checked: true },
  { key: 'mindmap', label: '思维导图', checked: true },
  { key: 'ppt', label: 'PPT', checked: false },
  { key: 'code', label: '代码案例', checked: false },
  { key: 'reading', label: '拓展阅读', checked: false },
  { key: 'mistake', label: '错题本', checked: false },
])

const quickActions = [
  { icon: 'clipboard', label: '生成学习方案' },
  { icon: 'clipboard-x', label: '错题诊断' },
  { icon: 'edit', label: '生成练习题' },
  { icon: 'presentation', label: '生成 PPT/讲义' },
  { icon: 'mind-topic', label: '生成思维导图' },
  { icon: 'camera', label: '拍照问答' },
]

function selectLibrary(id: number) {
  selectedLibraryId.value = id
  libraryModalOpen.value = false
}

function submitPrompt() {
  if (!prompt.value.trim()) {
    prompt.value = '我下周要考 Java 面向对象，继承、多态和接口分不清，帮我做 3 天复习。'
  }
  step.value = 'profile'
}

function createProject() {
  router.push(`/learning/${learningPlans[0]!.id}`)
}
</script>

<template>
  <StudentShell>
    <div class="learning-home">
      <section v-if="step === 'home'" class="home-center">
        <h1>今天想解决什么学习问题？</h1>
        <p>选择资料库、上传文件或直接提问，AI 会自动生成学习路径和资源包。</p>

        <div class="prompt-box">
          <textarea
            v-model="prompt"
            placeholder="例如：基于 Java 面向对象资料库，帮我做 3 天复习计划，并生成练习题和思维导图"
            @keydown.ctrl.enter.prevent="submitPrompt"
          />
          <div class="prompt-toolbar">
            <div class="prompt-tools">
              <button type="button" title="上传文件" @click="uploadModalOpen = true">
                <AppIcon name="plus" :size="22" />
              </button>
              <button type="button" title="语音输入">
                <AppIcon name="microphone" :size="20" />
              </button>
              <button type="button" title="图片识别">
                <AppIcon name="eye" :size="20" />
              </button>
              <button class="library-chip" type="button" @click="libraryModalOpen = true">
                <AppIcon name="notebook" :size="17" />
                {{ selectedLibrary?.name }}
                <AppIcon name="close" :size="14" />
              </button>
            </div>
            <button class="send-btn" type="button" @click="submitPrompt">
              <AppIcon name="send" :size="22" />
            </button>
          </div>
        </div>

        <div class="action-chips">
          <button v-for="item in quickActions" :key="item.label" type="button" @click="prompt = item.label">
            <AppIcon :name="item.icon" :size="20" />
            {{ item.label }}
          </button>
        </div>

        <div class="upload-row">
          <span>也可以直接上传：</span>
          <button type="button" @click="uploadModalOpen = true">PDF</button>
          <button type="button" @click="uploadModalOpen = true">DOCX</button>
          <button type="button" @click="uploadModalOpen = true">Markdown</button>
          <button type="button" @click="uploadModalOpen = true">图片</button>
        </div>
      </section>

      <section v-else class="profile-flow">
        <header class="flow-head">
          <button type="button" @click="step = 'home'">
            <AppIcon name="chevron-left" :size="18" />
          </button>
          <div>
            <h1>补全学习画像</h1>
            <p>通过少量确认，让系统生成更贴合你的学习项目。</p>
          </div>
        </header>

        <div class="chat-row chat-row--user">
          <div class="message message--user">{{ prompt }}</div>
          <span class="avatar">学</span>
        </div>
        <div class="chat-row">
          <span class="avatar avatar--ai"><AppIcon name="brain" :size="18" /></span>
          <div class="message">为了生成更适合你的学习项目，我需要确认 3 件事。</div>
        </div>

        <div class="choice-grid">
          <article class="choice-card">
            <span class="number">1</span>
            <h2>目标类型</h2>
            <div class="chips">
              <button class="selected" type="button">考试复习</button>
              <button type="button">课程作业</button>
              <button type="button">面试准备</button>
              <button type="button">项目实战</button>
            </div>
          </article>
          <article class="choice-card">
            <span class="number">2</span>
            <h2>学习偏好</h2>
            <div class="chips">
              <button class="selected" type="button">图文讲解</button>
              <button class="selected" type="button">代码示例</button>
              <button type="button">先练后讲</button>
              <button type="button">先讲后练</button>
            </div>
          </article>
          <article class="choice-card">
            <span class="number">3</span>
            <h2>资源类型（可选）</h2>
            <div class="check-grid">
              <label v-for="item in resourceOptions" :key="item.key">
                <input v-model="item.checked" type="checkbox" />
                <span>{{ item.label }}</span>
              </label>
            </div>
          </article>
        </div>

        <section class="confirm-panel">
          <h2>生成配置确认</h2>
          <div class="confirm-list">
            <span>资料来源</span>
            <strong>{{ selectedLibrary?.name }}</strong>
            <span>计划周期</span>
            <strong>3 天</strong>
            <span>重点知识</span>
            <strong>继承 / 多态 / 接口</strong>
            <span>生成资源</span>
            <strong>{{ resourceOptions.filter((item) => item.checked).map((item) => item.label).join('、') }}</strong>
          </div>
          <footer>
            <button class="primary-btn" type="button" @click="createProject">开始生成学习项目</button>
            <button class="outline-btn" type="button">继续补充要求</button>
          </footer>
        </section>
      </section>
    </div>

    <LibrarySelectModal
      :open="libraryModalOpen"
      :selected-id="selectedLibraryId"
      @close="libraryModalOpen = false"
      @select="selectLibrary"
    />
    <UploadMaterialModal :open="uploadModalOpen" @close="uploadModalOpen = false" />
  </StudentShell>
</template>

<style scoped>
.learning-home {
  min-height: 100%;
  background: var(--color-bg);
  padding: 48px 32px;
}

.learning-home,
.learning-home * {
  box-sizing: border-box;
}

h1,
h2,
p {
  margin: 0;
}

button {
  font: inherit;
}

.home-center {
  max-width: 980px;
  margin: 108px auto 0;
  text-align: center;
}

.home-center h1,
.profile-flow h1 {
  color: var(--color-text);
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 0;
}

.home-center p,
.flow-head p {
  margin-top: 14px;
  color: var(--color-text-muted);
  font-size: 16px;
}

.prompt-box {
  margin-top: 42px;
  min-height: 188px;
  border: 1px solid #2563eb;
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 12px 36px rgba(37, 99, 235, 0.08);
  padding: 22px;
  text-align: left;
}

.prompt-box textarea {
  width: 100%;
  height: 86px;
  resize: none;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
  font-size: 16px;
  line-height: 1.7;
}

.prompt-toolbar,
.prompt-tools,
.action-chips,
.upload-row,
.flow-head,
.chat-row,
.chips,
.confirm-panel footer {
  display: flex;
  align-items: center;
}

.prompt-toolbar {
  justify-content: space-between;
  gap: 16px;
}

.prompt-tools {
  gap: 12px;
}

.prompt-tools button,
.action-chips button,
.upload-row button,
.chips button {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.prompt-tools > button {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  display: grid;
  place-items: center;
}

.library-chip {
  width: auto !important;
  padding: 0 12px;
  display: inline-flex !important;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.send-btn {
  width: 48px;
  height: 48px;
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.action-chips {
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 26px;
}

.action-chips button {
  height: 44px;
  border-radius: 8px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.upload-row {
  justify-content: center;
  gap: 10px;
  margin-top: 30px;
  color: var(--color-text-muted);
}

.upload-row button {
  height: 34px;
  border-radius: 8px;
  padding: 0 14px;
}

.profile-flow {
  max-width: 1260px;
  margin: 0 auto;
}

.flow-head {
  gap: 14px;
  margin-bottom: 36px;
}

.flow-head > button {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  color: var(--color-text);
}

.chat-row {
  gap: 12px;
  margin-top: 14px;
}

.chat-row--user {
  justify-content: flex-end;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: #f2f4f7;
  color: var(--color-text);
  font-weight: 800;
  flex: 0 0 auto;
}

.avatar--ai {
  background: #eff6ff;
  color: #2563eb;
}

.message {
  max-width: 620px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  padding: 14px 18px;
  color: var(--color-text);
  line-height: 1.7;
}

.message--user {
  background: #f4f6f8;
}

.choice-grid {
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.choice-card,
.confirm-panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.choice-card {
  position: relative;
  padding: 24px;
  min-height: 188px;
}

.choice-card h2,
.confirm-panel h2 {
  color: var(--color-text);
  font-size: 20px;
  font-weight: 800;
}

.number {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: var(--color-primary);
  color: #fff;
  font-weight: 800;
  margin-bottom: 14px;
}

.chips {
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.chips button {
  height: 36px;
  border-radius: 8px;
  padding: 0 14px;
}

.chips button.selected {
  border-color: var(--color-primary);
  color: var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.check-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px 18px;
}

.check-grid label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text);
  font-weight: 700;
}

.confirm-panel {
  margin-top: 24px;
  padding: 24px 28px;
}

.confirm-list {
  margin-top: 22px;
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 0;
  border-top: 1px solid var(--color-border);
}

.confirm-list span,
.confirm-list strong {
  min-height: 48px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
}

.confirm-list span {
  color: var(--color-text-muted);
}

.confirm-list strong {
  color: var(--color-text);
}

.confirm-panel footer {
  justify-content: center;
  gap: 18px;
  margin-top: 24px;
}

.primary-btn,
.outline-btn {
  height: 46px;
  border-radius: 8px;
  padding: 0 42px;
  cursor: pointer;
  font-weight: 800;
}

.primary-btn {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.outline-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

@media (max-width: 1020px) {
  .home-center {
    margin-top: 40px;
  }

  .choice-grid {
    grid-template-columns: 1fr;
  }
}
</style>
