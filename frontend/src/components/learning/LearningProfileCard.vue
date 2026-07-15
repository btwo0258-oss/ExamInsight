<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningProfileMenu from '@/components/learning/LearningProfileMenu.vue'
import type { LearningProfileData } from '@/types/contracts/learning'

export type { LearningProfileData }

const props = defineProps<{ profile: LearningProfileData; loading?: boolean; confirmed?: boolean }>()
const emit = defineEmits<{ confirm: []; change: [profile: LearningProfileData] }>()

const tagInput = ref('')
const customPeriodMode = ref(false)
const customDailyTimeMode = ref(false)
const goalOptions = ['系统学习', '考试备考', '作业 / 科研', '职业技能', '项目实践', '兴趣拓展']
const foundationOptions = ['尚未接触', '刚入门', '基础薄弱', '基础一般', '有一定基础', '较熟练']
const periodOptions = ['1 天', '3 天', '1 周', '1 个月', '自定义']
const dailyTimeOptions = ['每天 30 分钟', '每天 1 小时', '每天 1～2 小时', '仅周末', '自定义']
const preferenceOptions = ['概念讲解', '案例演示', '练习驱动', '项目实操', '图表梳理', '阅读总结']

function updateField<K extends keyof LearningProfileData>(key: K, value: LearningProfileData[K]) {
  emit('change', { ...props.profile, [key]: value })
}

function addTags() {
  const additions = tagInput.value.split(/[,，、\n]/).map((item) => item.trim()).filter(Boolean)
  if (!additions.length) return
  updateField('weakPoints', Array.from(new Set([...props.profile.weakPoints, ...additions])))
  tagInput.value = ''
}

function removeTag(tag: string) {
  updateField('weakPoints', props.profile.weakPoints.filter((item) => item !== tag))
}

function choosePeriod(value: string) {
  customPeriodMode.value = value === '自定义'
  updateField('period', value)
}

function chooseDailyTime(value: string) {
  customDailyTimeMode.value = value === '自定义'
  updateField('dailyTime', value)
}
</script>

<template>
  <section class="profile-card">
    <header class="profile-card__head">
      <div class="profile-card__icon"><AppIcon name="user" :size="19" /></div>
      <div>
        <h3>学习画像</h3>
        <p>{{ loading ? '正在从对话中提取学习目标与约束…' : confirmed ? '画像已确认并用于生成学习方案。' : 'AI 已预填画像；点击任意字段即可修正。' }}</p>
      </div>
      <span v-if="confirmed" class="profile-card__status"><AppIcon name="check" :size="13" />已确认</span>
    </header>

    <div v-if="loading" class="profile-skeleton" aria-label="正在生成学习画像">
      <span v-for="item in 9" :key="item" :class="{ wide: item === 1 || item > 7 }" />
    </div>

    <div v-else class="profile-grid">
      <div class="profile-item">
        <span>学习目标</span>
        <LearningProfileMenu :model-value="profile.goal" :options="goalOptions" :disabled="confirmed" @update:model-value="updateField('goal', $event as string)" />
      </div>
      <label class="profile-item">
        <span>学习主题</span>
        <input :value="profile.subject" :disabled="confirmed" placeholder="例如：经济学、摄影、Python" @input="updateField('subject', ($event.target as HTMLInputElement).value)" />
      </label>
      <div class="profile-item">
        <span>当前水平</span>
        <LearningProfileMenu :model-value="profile.foundation" :options="foundationOptions" :disabled="confirmed" @update:model-value="updateField('foundation', $event as string)" />
      </div>
      <div class="profile-item">
        <span>学习周期</span>
        <LearningProfileMenu :model-value="profile.period" :options="periodOptions" :disabled="confirmed" @update:model-value="choosePeriod($event as string)" />
        <input v-if="customPeriodMode && !confirmed" :value="profile.period === '自定义' ? '' : profile.period" autofocus placeholder="例如：两周、三个月" @input="updateField('period', ($event.target as HTMLInputElement).value)" />
      </div>
      <div class="profile-item">
        <span>每日可用时间</span>
        <LearningProfileMenu :model-value="profile.dailyTime" :options="dailyTimeOptions" :disabled="confirmed" @update:model-value="chooseDailyTime($event as string)" />
        <input v-if="customDailyTimeMode && !confirmed" :value="profile.dailyTime === '自定义' ? '' : profile.dailyTime" autofocus placeholder="例如：工作日晚上 2 小时" @input="updateField('dailyTime', ($event.target as HTMLInputElement).value)" />
      </div>
      <div class="profile-item">
        <span>学习方式（可多选）</span>
        <LearningProfileMenu :model-value="profile.preferences" :options="preferenceOptions" multiple :disabled="confirmed" @update:model-value="updateField('preferences', $event as string[])" />
      </div>
      <div class="profile-item profile-item--wide">
        <span>重点 / 薄弱内容</span>
        <div class="profile-tags">
          <button v-for="tag in profile.weakPoints" :key="tag" type="button" :disabled="confirmed" @click="removeTag(tag)">{{ tag }}<b v-if="!confirmed">×</b></button>
          <input v-if="!confirmed" v-model="tagInput" placeholder="输入内容后按 Enter，可添加多个标签" @keydown.enter.prevent="addTags" @blur="addTags" />
          <em v-if="confirmed && !profile.weakPoints.length">待进一步识别</em>
        </div>
      </div>
      <label class="profile-item profile-item--wide">
        <span>补充信息</span>
        <textarea :value="profile.extra" :disabled="confirmed" rows="2" placeholder="例如时间限制、输出形式、特殊要求等" @input="updateField('extra', ($event.target as HTMLTextAreaElement).value)" />
      </label>
      <div class="profile-item profile-item--wide profile-item--source">
        <span>资料来源</span>
        <strong><AppIcon name="folder" :size="14" />{{ profile.source || '无' }}</strong>
      </div>
    </div>

    <footer v-if="!loading && !confirmed">
      <span>画像内容会直接用于生成确认稿，请在确认前完成修改。</span>
      <button type="button" @click="emit('confirm')">确认画像并生成方案 <AppIcon name="chevron-right" :size="15" /></button>
    </footer>
  </section>
</template>

<style scoped>
.profile-card { --profile-field-bg: #fffffc; --profile-input-bg: #fffffc; --profile-selected-bg: #e9e9e6; overflow: visible; border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-surface); box-shadow: var(--shadow-sm); }
:global([data-theme='dark']) .profile-card { --profile-field-bg: var(--color-surface-subtle); --profile-input-bg: var(--color-surface); --profile-selected-bg: var(--color-hover-strong); }
.profile-card__head { display: flex; align-items: center; gap: 12px; padding: 18px 20px; border-bottom: 1px solid var(--color-border); }
.profile-card__icon { width: 38px; height: 38px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 11px; color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 12%, transparent); }
.profile-card__head h3 { margin: 0; font-size: 16px; line-height: 24px; }
.profile-card__head p { margin: 2px 0 0; color: var(--color-text-muted); font-size: 12px; }
.profile-card__status { margin-left: auto; display: flex; align-items: center; gap: 4px; color: var(--color-success); font-size: 12px; }
.profile-grid, .profile-skeleton { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 14px; background: transparent; }
.profile-item { min-height: 72px; padding: 12px 14px; display: flex; flex-direction: column; justify-content: center; gap: 4px; min-width: 0; border: 1px solid color-mix(in srgb, var(--color-border) 72%, transparent); border-radius: 12px; background: var(--profile-field-bg); transition: border-color .16s ease, background .16s ease, box-shadow .16s ease; }
.profile-item--wide { grid-column: 1 / -1; }
.profile-item > span { color: var(--color-text-muted); font-size: 12px; }
.profile-item input, .profile-item textarea { width: 100%; box-sizing: border-box; padding: 7px 8px; border: 1px solid color-mix(in srgb, var(--color-border) 88%, var(--color-text-muted)); border-radius: 8px; outline: none; resize: vertical; background: var(--profile-input-bg); color: var(--color-text); font: inherit; font-weight: 600; box-shadow: 0 1px 1px color-mix(in srgb, var(--color-text) 4%, transparent); }
.profile-item input:hover:not(:disabled), .profile-item textarea:hover:not(:disabled) { border-color: color-mix(in srgb, var(--color-text) 34%, var(--color-border)); }
.profile-item input:focus, .profile-item textarea:focus { border-color: color-mix(in srgb, var(--color-text) 72%, var(--color-border)); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-text) 7%, transparent); }
.profile-item input:disabled, .profile-item textarea:disabled { opacity: 1; }
.profile-item strong { padding: 7px 8px; font-size: 14px; }
.profile-item--source { min-height: 0; padding: 5px 8px; flex-direction: row; align-items: center; justify-content: flex-start; gap: 10px; border-color: transparent; border-radius: 8px; background: var(--profile-field-bg); box-shadow: none; }
.profile-item--source > span { flex: 0 0 auto; }
.profile-item--source strong { display: inline-flex; align-items: center; gap: 7px; padding: 0; font-size: 13px; }
.profile-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; min-height: 38px; padding: 5px 7px; border: 1px solid color-mix(in srgb, var(--color-border) 88%, var(--color-text-muted)); border-radius: 8px; background: var(--profile-input-bg); box-shadow: 0 1px 1px color-mix(in srgb, var(--color-text) 4%, transparent); }
.profile-tags:focus-within { border-color: color-mix(in srgb, var(--color-text) 72%, var(--color-border)); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-text) 7%, transparent); }
.profile-tags button, .profile-tags em { padding: 5px 9px; border: 0; border-radius: 999px; color: var(--color-info); background: color-mix(in srgb, var(--color-info) 11%, var(--profile-field-bg)); font-size: 12px; font-style: normal; }
.profile-tags button:not(:disabled) { cursor: pointer; }
.profile-tags button b { margin-left: 5px; font-weight: 500; color: var(--color-info); }
.profile-tags input { width: min(300px, 100%); padding: 4px 2px; border: 0; background: transparent; box-shadow: none; font-weight: 400; }
.profile-tags input:focus, .profile-tags input:hover:not(:disabled) { border: 0; box-shadow: none; }
.profile-skeleton { gap: 12px; padding: 18px 20px; background: transparent; }
.profile-skeleton span { height: 58px; border-radius: 10px; background: linear-gradient(90deg, var(--color-surface-subtle), var(--color-hover-strong), var(--color-surface-subtle)); background-size: 220% 100%; animation: shimmer 1.25s infinite linear; }
.profile-skeleton .wide { grid-column: 1 / -1; }
.profile-card footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 18px; border-top: 1px solid var(--color-border); }
.profile-card footer span { color: var(--color-text-muted); font-size: 12px; }
.profile-card footer button { min-height: 38px; padding: 0 15px; display: flex; align-items: center; gap: 7px; border: 0; border-radius: 9px; background: var(--color-primary); color: var(--color-on-primary); font-weight: 700; cursor: pointer; white-space: nowrap; }
@keyframes shimmer { to { background-position: -220% 0; } }
@media (max-width: 680px) { .profile-grid, .profile-skeleton { grid-template-columns: 1fr; } .profile-item--wide, .profile-skeleton .wide { grid-column: auto; } .profile-card footer { align-items: stretch; flex-direction: column; } .profile-card footer button { justify-content: center; } }
</style>
