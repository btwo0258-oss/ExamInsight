<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StudentShell from '@/components/layout/StudentShell.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { listSmartLearningWrongItems, reviewSmartLearningWrongItem } from '@/api/smartLearning'
import type { SmartLearningWrongItem } from '@/types/contracts/smartLearning'

const route = useRoute()
const router = useRouter()
const projectId = String(route.params.id)
const items = ref<SmartLearningWrongItem[]>([])
const activeId = ref('')
const loading = ref(true)
const errorMessage = ref('')
const filter = ref<'ALL' | 'TO_REVIEW' | 'MASTERED'>('ALL')
const keyword = ref('')
const answerDraft = ref('')
const reviewMessage = ref('')
const reviewing = ref(false)
const visibleItems = computed(() => {
  const base = filter.value === 'ALL' ? items.value : items.value.filter(item => item.status === filter.value)
  const query = keyword.value.trim().toLowerCase()
  return query ? base.filter(item => `${item.stem} ${item.knowledgeKey}`.toLowerCase().includes(query)) : base
})
const active = computed(() => visibleItems.value.find(item => item.wrongItemId === activeId.value) ?? visibleItems.value[0])

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    items.value = await listSmartLearningWrongItems(projectId)
    activeId.value = items.value[0]?.wrongItemId ?? ''
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '错题本加载失败。' }
  finally { loading.value = false }
}

async function submitReview() {
  if (!active.value || !answerDraft.value.trim() || reviewing.value) return
  reviewing.value = true
  reviewMessage.value = ''
  try {
    const updated = await reviewSmartLearningWrongItem(projectId, active.value.wrongItemId, answerDraft.value)
    const index = items.value.findIndex(item => item.wrongItemId === updated.wrongItemId)
    if (index >= 0) items.value[index] = updated
    reviewMessage.value = updated.status === 'MASTERED' ? '回答正确，已标记为掌握。' : '这次还不正确，可以结合解析再试一次。'
  } catch (error) {
    reviewMessage.value = error instanceof Error ? error.message : '提交失败，请稍后重试。'
  } finally { reviewing.value = false }
}

onMounted(load)
watch(() => active.value?.wrongItemId, () => { answerDraft.value = ''; reviewMessage.value = '' })
</script>

<template>
  <StudentShell>
    <main class="mistakes-page">
      <header class="page-header"><button type="button" aria-label="返回工作台" @click="router.push(`/learning/${projectId}`)"><AppIcon name="chevron-left" :size="19" /></button><div><span>智能学习</span><h1>错题本</h1><p>统一交卷后答错的题目会自动收录，正确答案和解析保留在同一条记录中。</p></div></header>
      <p v-if="errorMessage" class="error-copy">{{ errorMessage }}</p>
      <section v-if="loading" class="mistake-loading"><i /><i /><i /></section>
      <template v-else>
        <nav class="filters"><div><button :class="{ active: filter === 'ALL' }" type="button" @click="filter = 'ALL'">全部 {{ items.length }}</button><button :class="{ active: filter === 'TO_REVIEW' }" type="button" @click="filter = 'TO_REVIEW'">待复习 {{ items.filter(item => item.status === 'TO_REVIEW').length }}</button><button :class="{ active: filter === 'MASTERED' }" type="button" @click="filter = 'MASTERED'">已掌握 {{ items.filter(item => item.status === 'MASTERED').length }}</button></div><label><AppIcon name="search" :size="15" /><input v-model="keyword" type="search" placeholder="搜索题目或知识点" /></label></nav>
        <section v-if="visibleItems.length" class="mistake-layout">
          <aside><button v-for="(item, index) in visibleItems" :key="item.wrongItemId" :class="{ active: active?.wrongItemId === item.wrongItemId }" type="button" @click="activeId = item.wrongItemId"><span>{{ index + 1 }}</span><div><strong>{{ item.stem }}</strong><small>{{ item.knowledgeKey || '未标记知识点' }}</small></div><em>{{ item.status === 'MASTERED' ? '已掌握' : '待复习' }}</em></button></aside>
          <article v-if="active" class="mistake-detail"><header><span>{{ active.knowledgeKey || '错题解析' }}</span><button type="button" @click="router.push(`/learning/${projectId}/task/${active.taskId}`)">回到原任务</button></header><h2>{{ active.stem }}</h2><section><small>你的答案</small><p class="wrong-answer">{{ active.userAnswer || '未作答' }}</p></section><section><small>正确答案</small><p>{{ active.correctAnswer }}</p></section><section><small>解析</small><p>{{ active.explanation || '暂无解析。' }}</p></section><section class="review-form"><small>重新作答</small><textarea v-model="answerDraft" rows="3" placeholder="重新写下你的答案" /><div><span :class="{ success: active.status === 'MASTERED' }">{{ reviewMessage }}</span><button type="button" :disabled="reviewing || !answerDraft.trim()" @click="submitReview">{{ reviewing ? '提交中…' : '提交答案' }}</button></div></section></article>
        </section>
        <section v-else class="empty-state"><AppIcon name="check" :size="24" /><strong>当前没有这类错题</strong><p>完成练习并交卷后，答错的题目会自动出现在这里。</p></section>
      </template>
    </main>
  </StudentShell>
</template>

<style scoped>
.mistakes-page, .mistakes-page * { box-sizing: border-box; }.mistakes-page { min-height: 100%; padding: 28px 34px 80px; color: var(--color-text); background: var(--color-bg); }.page-header, .filters, .mistake-layout, .empty-state, .mistake-loading, .error-copy { width: min(1280px, 100%); margin-inline: auto; }.page-header { display: grid; grid-template-columns: 42px 1fr; align-items: center; gap: 14px; }.page-header > button { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-border); border-radius: 10px; color: inherit; background: var(--color-surface); cursor: pointer; }.page-header span { color: var(--color-text-muted); font-size: 12px; }.page-header h1 { margin: 2px 0; font-size: 28px; }.page-header p { margin: 0; color: var(--color-text-muted); font-size: 13px; }.filters { display: flex; gap: 7px; margin-top: 24px; }.filters button { padding: 8px 12px; border: 1px solid var(--color-border); border-radius: 9px; color: inherit; background: var(--color-surface); cursor: pointer; }.filters button.active { border-color: var(--color-text); background: var(--ui-hover-strong-bg); }.mistake-layout { display: grid; grid-template-columns: minmax(280px, .75fr) minmax(0, 1.4fr); gap: 16px; margin-top: 14px; }.mistake-layout > aside, .mistake-detail, .empty-state, .mistake-loading { border: 1px solid var(--color-border); border-radius: 15px; background: var(--color-surface); }.mistake-layout > aside { max-height: calc(100vh - 220px); overflow: auto; padding: 8px; }.mistake-layout > aside button { width: 100%; display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; align-items: center; gap: 9px; padding: 12px 9px; border: 0; border-radius: 10px; color: inherit; background: transparent; text-align: left; cursor: pointer; }.mistake-layout > aside button.active, .mistake-layout > aside button:hover { background: var(--ui-hover-bg); }.mistake-layout > aside button > span { width: 26px; height: 26px; display: grid; place-items: center; border-radius: 50%; background: var(--ui-hover-strong-bg); font-size: 11px; }.mistake-layout aside div { min-width: 0; display: grid; gap: 4px; }.mistake-layout aside strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.mistake-layout aside small, .mistake-layout aside em { color: var(--color-text-muted); font-size: 10px; font-style: normal; }.mistake-detail { min-height: 520px; padding: 24px; }.mistake-detail header { display: flex; align-items: center; justify-content: space-between; }.mistake-detail header span, .mistake-detail section small { color: var(--color-text-muted); font-size: 11px; }.mistake-detail header button { border: 0; color: inherit; background: transparent; cursor: pointer; }.mistake-detail h2 { margin: 24px 0; font-size: 18px; line-height: 1.6; }.mistake-detail section { padding: 15px 0; border-top: 1px solid var(--color-border); }.mistake-detail section p { margin: 7px 0 0; line-height: 1.7; white-space: pre-wrap; }.wrong-answer { color: var(--color-danger); }.empty-state { min-height: 360px; display: grid; align-content: center; justify-items: center; gap: 8px; margin-top: 16px; }.empty-state p { margin: 0; color: var(--color-text-muted); }.mistake-loading { display: grid; gap: 12px; margin-top: 16px; padding: 24px; }.mistake-loading i { height: 64px; border-radius: 10px; background: var(--ui-hover-bg); animation: pulse 1.2s infinite; }.error-copy { margin-top: 14px; color: var(--color-danger); }@keyframes pulse { 50% { opacity: .55; } }@media (max-width: 760px) { .mistakes-page { padding-inline: 16px; }.mistake-layout { grid-template-columns: 1fr; }.mistake-layout > aside { max-height: 300px; } }
.filters{align-items:center;justify-content:space-between}.filters>div{display:flex;gap:7px}.filters label{width:min(280px,100%);display:flex;align-items:center;gap:8px;padding:0 11px;border:1px solid var(--color-border);border-radius:9px;background:var(--color-surface)}.filters input{width:100%;height:36px;border:0;outline:0;color:inherit;background:transparent}.review-form textarea{width:100%;margin-top:8px;padding:10px 12px;border:1px solid var(--color-border);border-radius:10px;outline:0;resize:vertical;color:inherit;background:var(--color-bg)}.review-form textarea:focus{border-color:var(--color-text)}.review-form>div{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:9px}.review-form>div span{color:var(--color-danger);font-size:12px}.review-form>div span.success{color:#287756}.review-form button{padding:8px 13px;border:0;border-radius:9px;color:#fff;background:var(--color-text);cursor:pointer}.review-form button:disabled{opacity:.5}@media(max-width:760px){.filters{align-items:stretch;flex-direction:column}}
.mistake-layout > aside button { position: relative; }
.mistake-layout > aside button,
.mistake-layout > aside button.active,
.mistake-layout > aside button:hover { background: transparent; }
.mistake-layout > aside button::after { position: absolute; z-index: 0; inset: 2px 2px; border-radius: 8px; background: transparent; content: ''; pointer-events: none; transition: background-color .15s ease; }
.mistake-layout > aside button.active::after,
.mistake-layout > aside button:hover::after { background: var(--ui-hover-bg); }
.mistake-layout > aside button + button::before { position: absolute; z-index: 2; top: -1px; right: 9px; left: 9px; height: 1px; background: var(--color-border); content: ''; pointer-events: none; }
.mistake-layout > aside button > span,
.mistake-layout > aside button > div,
.mistake-layout > aside button > em { position: relative; z-index: 1; }
.mistakes-page { background: var(--ui-page-canvas-bg); }
.mistake-layout { grid-template-columns: 330px minmax(0, 1fr); align-items: start; }
.mistake-layout > aside { height: max-content; align-self: start; }
@media (max-width: 760px) {
  .mistake-layout { grid-template-columns: 1fr; }
  .mistake-layout > aside { width: 100%; }
}
</style>
