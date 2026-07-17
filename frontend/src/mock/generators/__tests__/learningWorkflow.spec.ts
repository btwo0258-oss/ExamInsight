import { describe, expect, it } from 'vitest'

import { learningPlans } from '@/mock/student'
import {
  createMockLearningPlan,
  createMockLearningResourceContent,
} from '@/mock/generators/learning'
import type { CreateLearningPlanInput } from '@/types/contracts/learning'

const baseInput: CreateLearningPlanInput = {
  prompt: '三天复习 Java 面向对象',
  knowledgeBaseId: null,
  targetType: '考试复习',
  preferences: ['练习驱动'],
  resourceGroups: ['思维导图'],
  period: '3 天',
  foundation: '基础薄弱',
  weakPoints: '继承、多态、接口',
  dailyTime: '每天 60 分钟',
  studyDepth: '刷题强化',
  questionCount: 30,
  supplementalRequirement: '',
}

describe('Mock intelligent learning workflow', () => {
  it('creates a generated project without inventing a knowledge-base association', () => {
    const plan = createMockLearningPlan(baseInput, structuredClone(learningPlans))

    expect(plan.knowledgeBaseId).toBeNull()
    expect(plan.status).toBe('已生成')
    expect(plan.exercises).toHaveLength(30)
    expect(plan.stages.flatMap((stage) => stage.tasks)).not.toHaveLength(0)
    expect(plan.stages.flatMap((stage) => stage.tasks).some((task) => task.exerciseIds?.length)).toBe(true)
  })

  it('generates the default plan and mind map while keeping optional files on demand', () => {
    const plan = createMockLearningPlan(baseInput, structuredClone(learningPlans))
    const planFile = plan.resources.find((resource) => resource.group === '学习方案')
    const mindMap = plan.resources.find((resource) => resource.group === '思维导图')
    const handbook = plan.resources.find((resource) => resource.group === '个性化学习手册')

    expect(planFile?.status).toBe('已生成')
    expect(planFile?.content).toContain('# ')
    expect(mindMap?.status).toBe('已生成')
    expect(mindMap?.mindMapTreeData).toBeTruthy()
    expect(handbook?.status).toBe('未选择')
    expect(createMockLearningResourceContent(plan, handbook!)).toContain('学习建议')
    expect(plan).not.toHaveProperty('relatedProjectId')
  })
})
