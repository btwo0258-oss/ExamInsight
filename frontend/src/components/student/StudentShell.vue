<script setup lang="ts">
import StudentSidebar from './StudentSidebar.vue'
</script>

<template>
  <div class="student-shell">
    <StudentSidebar />
    <main class="student-main">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.student-shell {
  min-height: 100vh;
  display: flex;
  background: var(--color-bg);
}

.student-main {
  flex: 1;
  min-width: 0;
  height: 100vh;
  overflow: auto;
}
</style>
