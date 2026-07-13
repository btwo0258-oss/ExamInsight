import { learningPlans } from '@/mock'
import type { LearningPlan } from '@/mock'

const STORAGE_KEY = 'examinsight.learning.plans'

function clonePlans(plans: LearningPlan[]) {
  return structuredClone(plans)
}

export function getLearningPlans(): LearningPlan[] {
  const stored = sessionStorage.getItem(STORAGE_KEY)
  if (!stored) return clonePlans(learningPlans)

  try {
    return JSON.parse(stored) as LearningPlan[]
  } catch {
    return clonePlans(learningPlans)
  }
}

export function saveLearningPlans(plans: LearningPlan[]) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(plans))
}

