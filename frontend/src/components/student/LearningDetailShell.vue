<script setup lang="ts">
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'

withDefaults(defineProps<{
  eyebrow: string
  title: string
  subtitle: string
  progress?: number
  backLabel?: string
  showFooter?: boolean
}>(), {
  progress: undefined,
  backLabel: '返回学习项目',
  showFooter: true,
})

defineEmits<{ back: [] }>()
</script>

<template>
  <StudentShell>
    <div class="detail-page">
      <header class="detail-header">
        <button class="back-button" type="button" :aria-label="backLabel" @click="$emit('back')">
          <AppIcon name="chevron-left" :size="19" />
        </button>
        <div class="heading-copy">
          <span class="eyebrow">{{ eyebrow }}</span>
          <h1>{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
        <div class="header-actions">
          <div v-if="progress !== undefined" class="progress-summary">
            <span>项目进度</span>
            <i><b :style="{ width: `${progress}%` }" /></i>
            <strong>{{ progress }}%</strong>
          </div>
          <slot name="actions" />
        </div>
      </header>

      <main class="detail-layout" :class="{ 'without-aside': !$slots.aside }">
        <aside class="detail-navigation">
          <slot name="navigation" />
        </aside>
        <section class="detail-content">
          <slot />
        </section>
        <aside v-if="$slots.aside" class="detail-aside">
          <slot name="aside" />
        </aside>
      </main>

      <footer v-if="showFooter && $slots.footer" class="detail-footer">
        <slot name="footer" />
      </footer>
    </div>
  </StudentShell>
</template>

<style scoped>
.detail-page,
.detail-page * {
  box-sizing: border-box;
}

.detail-page {
  min-height: 100%;
  padding: 24px 30px 88px;
  background: var(--color-bg);
}

.detail-header,
.detail-layout,
.detail-footer {
  width: min(1540px, 100%);
  margin-inline: auto;
}

.detail-header {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 76px;
}

.back-button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.eyebrow {
  display: block;
  margin-bottom: 3px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .08em;
}

.heading-copy h1,
.heading-copy p {
  margin: 0;
}

.heading-copy h1 {
  color: var(--color-text);
  font-size: 24px;
  line-height: 1.25;
}

.heading-copy p {
  margin-top: 5px;
  color: var(--color-text-muted);
  font-size: 14px;
}

.header-actions,
.progress-summary {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-summary {
  min-width: 230px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.progress-summary i {
  width: 96px;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-border);
}

.progress-summary b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
}

.progress-summary strong {
  color: var(--color-text);
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(220px, 260px) minmax(520px, 1fr) minmax(250px, 300px);
  gap: 16px;
  align-items: start;
  margin-top: 18px;
}

.detail-layout.without-aside {
  grid-template-columns: minmax(220px, 280px) minmax(560px, 1fr);
}

.detail-navigation,
.detail-aside {
  position: sticky;
  top: 18px;
}

.detail-footer {
  position: fixed;
  z-index: 12;
  right: 0;
  bottom: 0;
  left: var(--sidebar-width, 272px);
  width: auto;
  min-height: 64px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  padding: 10px 30px;
  border-top: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-surface) 94%, transparent);
  backdrop-filter: blur(12px);
}

@media (max-width: 1180px) {
  .detail-layout,
  .detail-layout.without-aside {
    grid-template-columns: 230px minmax(0, 1fr);
  }

  .detail-aside {
    position: static;
    grid-column: 2;
  }
}

@media (max-width: 820px) {
  .detail-page {
    padding-inline: 16px;
  }

  .detail-header {
    grid-template-columns: 40px 1fr;
  }

  .header-actions {
    grid-column: 1 / -1;
  }

  .detail-layout,
  .detail-layout.without-aside {
    grid-template-columns: 1fr;
  }

  .detail-navigation,
  .detail-aside {
    position: static;
    grid-column: 1;
  }

  .detail-footer {
    left: 0;
    padding-inline: 16px;
  }
}
</style>
