<script setup lang="ts">
import { ref } from 'vue'
import StudentSidebar from './StudentSidebar.vue'

const sidebarWidth = ref(276)
</script>

<template>
  <div class="student-shell" :style="{ '--sidebar-width': `${sidebarWidth}px` }">
    <StudentSidebar @width-change="sidebarWidth = $event" />
    <main class="student-main">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.student-shell {
  height: 100vh;
  display: flex;
  background: var(--color-bg);
  color: var(--color-text);
  overflow: hidden;
}

.student-main {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: auto;
}
</style>
