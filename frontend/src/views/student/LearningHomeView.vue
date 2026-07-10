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
const modelMenuOpen = ref(false)
const selectedModelKey = ref('qwen-plus')

const selectedLibrary = computed(() => courseLibraries.find((item) => item.id === selectedLibraryId.value))
const modelOptions = [
  { key: 'qwen-plus', name: 'Qwen Plus', desc: '最强大的推理能力' },
  { key: 'gpt-4o', name: 'GPT-4 Omni', desc: '适合日常对话' },
]
const selectedModel = computed(() => modelOptions.find((item) => item.key === selectedModelKey.value) ?? modelOptions[0]!)

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

function selectModel(key: string) {
  selectedModelKey.value = key
  modelMenuOpen.value = false
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
          <span v-if="!prompt" class="prompt-placeholder">处理任何事务</span>
          <div class="prompt-toolbar">
            <div class="prompt-tools prompt-tools--left">
              <button type="button" title="上传文件" @click="uploadModalOpen = true">
                <AppIcon name="paperclip" :size="21" />
              </button>
              <button type="button" title="语音输入">
                <AppIcon name="microphone" :size="20" />
              </button>
              <button type="button" title="图片识别">
                <AppIcon name="eye" :size="20" />
              </button>
              <button class="library-chip" type="button" @click="libraryModalOpen = true">
                <AppIcon name="folder" :size="17" />
                {{ selectedLibrary?.name }}
                <AppIcon name="close" :size="14" />
              </button>
            </div>
            <div class="prompt-tools prompt-tools--right">
              <div class="model-switch" @click.stop>
                <button class="model-trigger" type="button" @click="modelMenuOpen = !modelMenuOpen">
                  <span>{{ selectedModel.name }}</span>
                  <AppIcon :name="modelMenuOpen ? 'chevron-up' : 'chevron-down'" :size="13" />
                </button>
                <transition name="model-menu">
                  <div v-if="modelMenuOpen" class="model-menu">
                    <button
                      v-for="item in modelOptions"
                      :key="item.key"
                      class="model-option"
                      :class="{ 'is-active': item.key === selectedModelKey }"
                      type="button"
                      @click="selectModel(item.key)"
                    >
                      <span>
                        <strong>{{ item.name }}</strong>
                        <small>{{ item.desc }}</small>
                      </span>
                      <AppIcon v-if="item.key === selectedModelKey" name="check" :size="16" />
                    </button>
                  </div>
                </transition>
              </div>
              <span class="model-label">5.6 Sol 轻度</span>
              <button type="button" title="璇煶杈撳叆">
                <AppIcon name="microphone" :size="20" />
              </button>
              <button class="send-btn" type="button" @click="submitPrompt">
                <AppIcon name="upload" :size="21" />
              </button>
            </div>
          </div>
        </div>

        <div class="prompt-subbar">
          <div class="subbar-left">
            <button class="subbar-action" type="button" @click="libraryModalOpen = true">
              <AppIcon name="folder" :size="17" />
              <span>选择项目</span>
            </button>
            <button class="subbar-action" type="button">
              <AppIcon name="activity" :size="16" />
              <span>连接插件</span>
            </button>
          </div>
          <button class="subbar-action subbar-action--right" type="button">
            <AppIcon name="monitor" :size="16" />
            <span>下载桌面应用</span>
          </button>
        </div>

        <div class="action-chips">
          <button v-for="item in quickActions" :key="item.label" type="button" @click="prompt = item.label">
            <span class="action-icon"><AppIcon :name="item.icon" :size="18" /></span>
            <span>{{ item.label }}</span>
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
  max-width: 770px;
  margin: 112px auto 0;
  text-align: center;
}

.home-center h1,
.profile-flow h1 {
  color: var(--color-text);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: 0;
}

.home-center > h1 {
  font-size: 0;
}

.home-center > h1::before {
  content: "我们该做什么？";
  font-size: 28px;
  font-weight: 600;
}

.home-center > p {
  display: none;
}

.home-center p,
.flow-head p {
  margin-top: 14px;
  color: var(--color-text-muted);
  font-size: 16px;
}

.prompt-box {
  position: relative;
  margin-top: 32px;
  min-height: 150px;
  border: 1px solid var(--color-border);
  border-radius: 24px;
  background: var(--color-surface);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
  padding: 0;
  text-align: left;
  box-sizing: border-box;
}

.prompt-box textarea {
  width: 100%;
  min-height: 118px;
  max-height: 200px;
  resize: none;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
  font-size: 16px;
  line-height: 1.6;
  padding: 14px 12px 60px 16px;
  box-sizing: border-box;
  border-radius: 24px;
  display: block;
}

.prompt-box textarea::placeholder {
  color: transparent;
}

.prompt-placeholder {
  position: absolute;
  left: 16px;
  top: 15px;
  color: var(--color-text-muted);
  font-size: 16px;
  line-height: 1.6;
  pointer-events: none;
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
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
  padding: 8px 10px;
  background: linear-gradient(to top, var(--color-surface) 70%, transparent);
  border-bottom-left-radius: 24px;
  border-bottom-right-radius: 24px;
  pointer-events: none;
}

.prompt-tools {
  gap: 8px;
  pointer-events: auto;
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

.prompt-tools--left > button:not(:first-child) {
  display: none;
}

.prompt-tools > button {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  border: 0;
  background: transparent;
  color: var(--color-text);
}

.prompt-toolbar .library-chip {
  display: none !important;
}

.model-label {
  color: var(--color-text-muted);
  font-size: 0;
  white-space: nowrap;
}

.model-label::before {
  content: "5.6 Sol 轻度⌄";
  font-size: 16px;
}

.send-btn {
  width: 32px !important;
  height: 32px !important;
  border: 0 !important;
  border-radius: 50% !important;
  background: var(--color-hover-strong) !important;
  color: var(--color-text-muted) !important;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.prompt-subbar {
  width: calc(100% - 40px);
  min-height: 42px;
  margin: 0 auto;
  padding: 0 12px;
  border-radius: 0 0 16px 16px;
  background: var(--color-hover);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.subbar-left {
  display: flex;
  align-items: center;
  gap: 0;
}

.subbar-left .subbar-action:not(:first-child),
.subbar-action--right {
  display: none;
}

.subbar-action {
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 4px;
  font-size: 14px;
}

.subbar-action:hover {
  background: var(--color-hover-strong);
}

/* kept for the profile step and hidden in the home input toolbar */
.library-chip {
  width: auto !important;
  height: 36px !important;
  padding: 0 12px;
  border: 1px solid var(--color-border) !important;
  border-radius: 10px !important;
  background: var(--color-surface) !important;
  display: inline-flex !important;
  align-items: center;
  gap: 8px;
  color: var(--color-text) !important;
  font-size: 13px;
  font-weight: 700;
}

.action-chips {
  width: 260px;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  gap: 10px;
  margin: 22px 0 0 28px;
}

.action-chips button {
  height: 42px;
  border-radius: 8px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  border-color: transparent;
  background: transparent;
  color: #8a8a8a;
  font-weight: 500;
  text-align: left;
}

.action-chips button:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.action-icon {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.action-chips button:nth-child(1) .action-icon { color: #2563eb; }
.action-chips button:nth-child(2) .action-icon { color: #ef4444; }
.action-chips button:nth-child(3) .action-icon { color: #16a34a; }
.action-chips button:nth-child(4) .action-icon { color: #f97316; }
.action-chips button:nth-child(5) .action-icon { color: #7c3aed; }
.action-chips button:nth-child(6) .action-icon { color: #0ea5e9; }

.action-chips button span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-row {
  display: none;
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

.prompt-tools--right .model-label {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 8px;
  border-radius: 8px;
  color: var(--color-text-muted);
  cursor: default;
}

.prompt-tools--right .model-label:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.prompt-tools--right .model-label::before {
  content: "Qwen Plus⌄";
  font-size: 14px;
  font-weight: 500;
}

.prompt-tools--right > .model-label {
  display: none !important;
}

.model-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.model-trigger {
  height: 32px !important;
  width: auto !important;
  padding: 0 8px !important;
  border: 0 !important;
  border-radius: 8px !important;
  background: transparent !important;
  color: var(--color-text) !important;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
}

.model-trigger:hover {
  background: var(--color-hover) !important;
}

.model-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  width: 276px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  z-index: 30;
}

.model-option {
  width: 100% !important;
  height: auto !important;
  min-height: 52px;
  padding: 8px 10px !important;
  border: 0 !important;
  border-radius: 7px !important;
  background: transparent !important;
  color: var(--color-text) !important;
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  cursor: pointer;
}

.model-option:hover,
.model-option.is-active {
  background: var(--color-hover) !important;
}

.model-option span {
  display: grid;
  gap: 3px;
}

.model-option strong {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.model-option small {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.2;
}

.model-option svg {
  color: #10a37f;
}

.model-menu-enter-active,
.model-menu-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.model-menu-enter-from,
.model-menu-leave-to {
  opacity: 0;
  transform: translateY(6px);
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
