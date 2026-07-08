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
    <button class="brand" type="button" @click="go('/learning')">
      <span>ExamInsight</span>
    </button>

    <button class="new-chat" type="button" @click="go('/chat')">
      <AppIcon name="plus" :size="20" />
      <span>新对话</span>
    </button>

    <nav class="nav">
      <button
        class="nav-item"
        :class="{ 'nav-item--active': activeSection === 'learning' }"
        type="button"
        @click="go('/learning')"
      >
        <AppIcon name="graduation" :size="20" />
        <span>智能学习</span>
      </button>
      <button
        class="nav-item"
        :class="{ 'nav-item--active': activeSection === 'library' }"
        type="button"
        @click="go('/library')"
      >
        <AppIcon name="file" :size="20" />
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
        <AppIcon name="message-square" :size="18" />
        <span>{{ item.title }}</span>
        <small>{{ item.desc }}</small>
      </button>
    </section>

    <button class="settings" type="button">
      <AppIcon name="settings" :size="20" />
      <span>设置</span>
    </button>
  </aside>
</template>

<style scoped>
.student-sidebar {
  width: 276px;
  height: 100vh;
  background: #fffffc;
  border-right: 1px solid #dde3ef;
  display: flex;
  flex-direction: column;
  padding: 32px 20px 24px;
  gap: 20px;
}

.brand,
.new-chat,
.nav-item,
.recent-item,
.settings {
  border: 0;
  cursor: pointer;
  background: transparent;
  color: #202838;
  font: inherit;
}

.brand {
  padding: 0;
  text-align: left;
}

.brand span {
  display: block;
  font-size: 27px;
  font-weight: 800;
  letter-spacing: 0;
}

.new-chat {
  height: 54px;
  border-radius: 8px;
  background: #111827;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(17, 24, 39, 0.12);
}

.nav {
  display: grid;
  gap: 8px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e6ebf3;
}

.nav-item {
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: #273246;
}

.nav-item:hover,
.nav-item--active {
  background: #f2f4f7;
  color: #111827;
}

.recent {
  min-height: 0;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 8px;
}

.recent-title {
  padding: 4px 2px 8px;
  color: #6b7280;
  font-size: 14px;
}

.recent-item {
  width: 100%;
  min-height: 34px;
  display: grid;
  grid-template-columns: 20px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 7px 2px;
  text-align: left;
  color: #344054;
}

.recent-item:hover {
  color: #111827;
}

.recent-item span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
}

.recent-item small {
  color: #7b8494;
  font-size: 13px;
}

.settings {
  margin-top: auto;
  height: 44px;
  border-top: 1px solid #e6ebf3;
  padding-top: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  color: #344054;
}
</style>
