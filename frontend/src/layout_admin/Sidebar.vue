<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/admin/AppIcon.vue'

const route = useRoute()

const navItems = [
  { name: 'admin-dashboard', label: '概览', icon: 'bar-chart', path: '/admin/dashboard' },
  { name: 'admin-users', label: '用户', icon: 'users', path: '/admin/users' },
  { name: 'admin-system-config', label: '配置', icon: 'settings', path: '/admin/system-config' }
]

const activeRoute = computed(() => route.name)
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <div class="brand">
        <img src="@/assets/icons/ExamInsight-Logo-White.png" alt="ExamInsight" class="brand-logo" />
        <span class="brand-text">ExamInsight Admin</span>
      </div>
    </div>
    
    <nav class="sidebar-nav">
      <router-link 
        v-for="item in navItems" 
        :key="item.name" 
        :to="item.path"
        class="nav-item"
        :class="{ active: activeRoute === item.name }"
      >
        <AppIcon :name="item.icon" :size="20" />
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
    
    <div class="sidebar-footer">
      <div class="version">v0.1.0</div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100%;
  background-color: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  height: var(--header-height);
  padding: 0 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-text);
}

.brand-logo {
  width: 48px;
  height: 48px;
  object-fit: contain;
}

.brand-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  cursor: pointer;
}

.nav-item:hover {
  background-color: rgba(0, 0, 0, 0.04);
  color: var(--color-text);
}

.nav-item.active {
  background-color: var(--color-primary);
  color: white;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--color-border);
}

.version {
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
}
</style>
