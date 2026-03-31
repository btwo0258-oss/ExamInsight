<script setup lang="ts">
import { computed } from 'vue'

import { renderMarkdownToHtml } from '@/utils/markdown'

type Props = { content: string }
const props = defineProps<Props>()

const html = computed(() => renderMarkdownToHtml(props.content))
</script>

<template>
  <div class="markdown" v-html="html" />
</template>
