<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { recentConversations } from '@/mock'

const route = useRoute()
const router = useRouter()

const activeSection = computed(() => {
  if (route.path.startsWith('/library')) return 'library'
  if (route.path.startsWith('/learning')) return 'learning'
  return 'chat'
})

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <aside class="student-sidebar">
    <div class="brand" @click="go('/learning')">
      <div class="brand-mark">E</div>
      <div>
        <strong>ExamInsight</strong>
        <span>智能学习平台</span>
      </div>
    </div>

    <button class="new-chat" type="button" @click="go('/chat')">
      <AppIcon name="plus" :size="16" />
      <span>新对话</span>
    </button>

    <nav class="nav">
      <button
        class="nav-item"
        :class="{ 'nav-item--active': activeSection === 'learning' }"
        type="button"
        @click="go('/learning')"
      >
        <AppIcon name="brain" :size="18" />
        <span>智能学习</span>
      </button>
      <button
        class="nav-item"
        :class="{ 'nav-item--active': activeSection === 'library' }"
        type="button"
        @click="go('/library')"
      >
        <AppIcon name="folder" :size="18" />
        <span>资料库</span>
      </button>
    </nav>

    <section class="recent">
      <div class="recent-title">最近对话</div>
      <button
        v-for="item in recentConversations"
        :key="item.id"
        class="recent-item"
        type="button"
        @click="go(`/chat/${item.id}`)"
      >
        <span>{{ item.title }}</span>
        <small>{{ item.desc }}</small>
      </button>
    </section>
  </aside>
</template>

<style scoped>
.student-sidebar {
  width: 260px;
  height: 100vh;
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  padding: 18px 14px;
  gap: 14px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 6px 10px;
  cursor: pointer;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--color-text);
  color: var(--color-bg);
  display: grid;
  place-items: center;
  font-weight: 800;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  font-size: 15px;
  line-height: 1.2;
}

.brand span {
  margin-top: 3px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.new-chat,
.nav-item,
.recent-item {
  width: 100%;
  border: 0;
  cursor: pointer;
  text-align: left;
  border-radius: 8px;
  background: transparent;
}

.new-chat {
  height: 42px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: var(--color-text);
  color: var(--color-bg);
  font-weight: 700;
}

.nav {
  display: grid;
  gap: 4px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
}

.nav-item {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  color: var(--color-text-muted);
  font-weight: 600;
}

.nav-item:hover,
.nav-item--active {
  background: var(--color-surface-hover);
  color: var(--color-text);
}

.recent {
  min-height: 0;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 6px;
}

.recent-title {
  padding: 4px 8px;
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.recent-item {
  padding: 9px 10px;
  display: grid;
  gap: 3px;
}

.recent-item:hover {
  background: var(--color-surface-hover);
}

.recent-item span {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.recent-item small {
  color: var(--color-text-muted);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
