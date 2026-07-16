<script setup lang="ts">
import { computed } from 'vue'
import type { PresentationAspectRatio, PresentationPreviewPage } from '@/types/contracts/presentation'

const props = withDefaults(defineProps<{
  page: PresentationPreviewPage
  aspectRatio: PresentationAspectRatio
  compact?: boolean
}>(), {
  compact: false,
})

const slideStyle = computed(() => ({
  aspectRatio: props.aspectRatio === '4:3' ? '4 / 3' : '16 / 9',
  backgroundColor: props.page.backgroundColor,
  color: props.page.textColor,
  '--slide-accent': props.page.accentColor,
  '--slide-surface': props.page.surfaceColor,
}))
</script>

<template>
  <article class="slide-preview" :class="[`slide-preview--${page.layout}`, { 'slide-preview--compact': compact }]" :style="slideStyle">
    <img v-if="page.previewImageUrl" class="slide-preview__image" :src="page.previewImageUrl" :alt="`第 ${page.order} 页：${page.title}`" />
    <template v-else>
    <span class="slide-preview__rail" aria-hidden="true" />
    <div v-if="page.layout === 'cover'" class="slide-preview__cover">
      <span>EXAMINSIGHT</span>
      <h2>{{ page.title }}</h2>
      <p>{{ page.points[0] }}</p>
    </div>
    <div v-else class="slide-preview__content">
      <header>
        <span>{{ String(page.order).padStart(2, '0') }}</span>
        <h2>{{ page.title }}</h2>
      </header>
      <div class="slide-preview__rule" />
      <ul>
        <li v-for="point in page.points.slice(0, 6)" :key="point">{{ point }}</li>
      </ul>
    </div>
    </template>
  </article>
</template>

<style scoped>
.slide-preview {
  --slide-accent: #2563eb;
  --slide-surface: #fff;
  position: relative;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, currentColor 16%, transparent);
  border-radius: 6px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.13);
}

.slide-preview__rail {
  position: absolute;
  inset: 0 auto 0 0;
  width: 1.5%;
  background: var(--slide-accent);
}

.slide-preview__image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.slide-preview__cover {
  position: absolute;
  inset: 10% 8%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.slide-preview__cover > span {
  margin-bottom: 7%;
  color: var(--slide-accent);
  font-size: 11px;
  font-weight: 700;
}

.slide-preview h2,
.slide-preview p {
  margin: 0;
}

.slide-preview__cover h2 {
  max-width: 88%;
  font-size: 28px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.slide-preview__cover p {
  max-width: 78%;
  margin-top: 5%;
  color: var(--slide-accent);
  font-size: 14px;
  line-height: 1.6;
}

.slide-preview__content {
  position: absolute;
  inset: 8% 7% 8% 8%;
}

.slide-preview__content header {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}

.slide-preview__content header span {
  color: var(--slide-accent);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.8;
}

.slide-preview__content h2 {
  font-size: 21px;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.slide-preview__rule {
  width: 54px;
  height: 3px;
  margin: 5% 0 6% 40px;
  border-radius: 2px;
  background: var(--slide-accent);
}

.slide-preview__content ul {
  display: grid;
  gap: 12px;
  margin: 0 0 0 40px;
  padding: 0;
  list-style: none;
}

.slide-preview__content li {
  position: relative;
  padding-left: 18px;
  font-size: 14px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.slide-preview__content li::before {
  content: '';
  position: absolute;
  top: 0.56em;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--slide-accent);
}

.slide-preview--compact {
  box-shadow: none;
}

.slide-preview--compact .slide-preview__cover > span,
.slide-preview--compact .slide-preview__content header span {
  font-size: 7px;
}

.slide-preview--compact .slide-preview__cover h2 {
  font-size: 13px;
}

.slide-preview--compact .slide-preview__cover p,
.slide-preview--compact .slide-preview__content li {
  font-size: 8px;
}

.slide-preview--compact .slide-preview__content h2 {
  font-size: 11px;
}

.slide-preview--compact .slide-preview__content ul {
  gap: 5px;
}
</style>
