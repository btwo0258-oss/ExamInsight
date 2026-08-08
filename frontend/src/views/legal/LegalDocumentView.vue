<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { legalDocument, type LegalDocument } from '@/content/legal'

const props = defineProps<{ documentType: LegalDocument['type'] }>()
const document = computed(() => legalDocument(props.documentType))
</script>

<template>
  <main class="legal-page">
    <article class="legal-document">
      <nav class="legal-document__nav" aria-label="法律文档导航">
        <RouterLink to="/chat">返回 ExamInsight</RouterLink>
        <div>
          <RouterLink v-if="documentType !== 'terms'" to="/terms">用户协议</RouterLink>
          <RouterLink v-if="documentType !== 'privacy'" to="/privacy">隐私政策</RouterLink>
        </div>
      </nav>

      <header class="legal-document__header">
        <p class="legal-document__eyebrow">ExamInsight · 公开 Beta</p>
        <h1>{{ document.title }}</h1>
        <p class="legal-document__summary">{{ document.summary }}</p>
        <dl class="legal-document__meta">
          <div><dt>版本</dt><dd>{{ document.version }}</dd></div>
          <div><dt>生效日期</dt><dd>{{ document.effectiveDate }}</dd></div>
        </dl>
      </header>

      <aside class="legal-document__draft" role="note">
        {{ document.draftNotice }}
      </aside>

      <section
        v-for="section in document.sections"
        :key="section.title"
        class="legal-document__section"
      >
        <h2>{{ section.title }}</h2>
        <p v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</p>
      </section>
    </article>
  </main>
</template>

<style scoped>
.legal-page { min-height: 100vh; padding: 40px 20px 72px; background: var(--color-bg); color: var(--color-text); }
.legal-document { width: min(860px, 100%); margin: 0 auto; border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: clamp(24px, 5vw, 56px); background: var(--color-surface); box-shadow: var(--shadow-sm); }
.legal-document__nav { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 24px; border-bottom: 1px solid var(--color-border); font-size: 13px; font-weight: 700; }
.legal-document__nav div { display: flex; gap: 16px; }
.legal-document__nav a { color: var(--color-text); text-decoration: none; }
.legal-document__nav a:hover { text-decoration: underline; }
.legal-document__header { padding: 42px 0 26px; }
.legal-document__eyebrow { margin: 0 0 10px; color: var(--color-text-muted); font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.legal-document__header h1 { margin: 0; font-size: clamp(28px, 5vw, 42px); line-height: 1.2; }
.legal-document__summary { max-width: 680px; margin: 18px 0 0; color: var(--color-text-muted); font-size: 15px; line-height: 1.8; }
.legal-document__meta { display: flex; flex-wrap: wrap; gap: 24px; margin: 24px 0 0; }
.legal-document__meta div { display: flex; gap: 8px; }
.legal-document__meta dt { color: var(--color-text-muted); }
.legal-document__meta dd { margin: 0; font-weight: 700; }
.legal-document__draft { margin-bottom: 34px; border-left: 3px solid var(--color-text); padding: 12px 16px; background: var(--color-hover); color: var(--color-text-muted); font-size: 13px; line-height: 1.7; }
.legal-document__section { padding: 24px 0; border-top: 1px solid var(--color-border); }
.legal-document__section h2 { margin: 0 0 14px; font-size: 19px; line-height: 1.4; }
.legal-document__section p { margin: 0; color: var(--color-text-muted); font-size: 14px; line-height: 1.9; }
.legal-document__section p + p { margin-top: 10px; }
@media (max-width: 560px) {
  .legal-page { padding: 12px 10px 36px; }
  .legal-document { border-radius: var(--radius-md); padding: 22px 18px 34px; }
  .legal-document__nav { align-items: flex-start; }
  .legal-document__nav div { flex-direction: column; align-items: flex-end; gap: 8px; }
}
</style>
