import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import type { LearningPlan } from '@/mock'
import { useLearningStore } from '@/stores/learning'

function createEmptyPlan(id: number): LearningPlan {
  return {
    id,
    title: '',
    goal: '',
    updatedAt: '',
    knowledgeBaseId: null,
    status: '待开启',
    period: '',
    targetType: '',
    progress: 0,
    taskDone: 0,
    totalTasks: 0,
    exerciseDone: 0,
    totalExercises: 0,
    correctRate: 0,
    weeklyHours: '0h',
    profile: [],
    stages: [],
    resources: [],
    exercises: [],
    wrongQuestions: [],
    dashboard: [],
    agents: [],
  }
}

export function useLearningPlanRoute() {
  const route = useRoute()
  const learningStore = useLearningStore()
  const planId = computed(() => Number(route.params.id))
  const placeholder = ref(createEmptyPlan(planId.value))
  const plan = computed(() => learningStore.getPlan(planId.value) ?? placeholder.value)
  const hasPlan = computed(() => Boolean(learningStore.getPlan(planId.value)))
  const loadError = ref('')
  const isLoading = ref(false)

  async function loadPlan() {
    loadError.value = ''
    if (!Number.isFinite(planId.value) || planId.value <= 0) {
      placeholder.value = createEmptyPlan(0)
      return
    }
    isLoading.value = true
    placeholder.value = createEmptyPlan(planId.value)
    try {
      await learningStore.fetchPlan(planId.value)
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '获取学习项目失败'
    } finally {
      isLoading.value = false
    }
  }

  watch(planId, () => void loadPlan(), { immediate: true })

  return { plan, planId, hasPlan, isLoading, loadError, loadPlan }
}
