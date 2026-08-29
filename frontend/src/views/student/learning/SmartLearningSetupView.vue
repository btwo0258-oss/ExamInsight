<script setup lang="ts">
import { useRoute } from 'vue-router'
import LearningPreparation from '@/components/learning/LearningPreparation.vue'
const route = useRoute()
</script>

<template>
  <LearningPreparation :key="String(route.params.id)" :project-id="String(route.params.id)" />
</template>
