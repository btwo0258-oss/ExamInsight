import { describe, expect, it } from 'vitest'

import { normalizeLearningPlan } from '@/repositories/learning'

describe('normalizeLearningPlan', () => {
  it('maps stable API statuses to the existing display model', () => {
    const plan = normalizeLearningPlan({
      id: 9,
      title: 'API plan',
      goal: 'goal',
      updatedAt: '',
      libraryId: 1,
      status: 'in_progress',
      period: '3 days',
      targetType: 'exam',
      progress: 10,
      taskDone: 0,
      totalTasks: 1,
      exerciseDone: 0,
      totalExercises: 1,
      correctRate: 0,
      weeklyHours: '0h',
      profile: [],
      stages: [{ id: 1, title: 'stage', desc: '', tasks: [{ id: 2, title: 'task', duration: '', done: false, type: '练习', status: 'not_started' }] }],
      resources: [{ id: 3, group: '思维导图', title: 'map', desc: '', status: 'failed', action: 'retry' }],
      exercises: [],
      wrongQuestions: [{ id: 4, title: 'wrong', knowledge: [], userAnswer: '', correctAnswer: '', reason: '', synced: true, status: 'needs_review' }],
      dashboard: [],
      agents: [],
    })

    expect(plan.status).toBe('进行中')
    expect(plan.stages[0]?.tasks[0]?.status).toBe('未开始')
    expect(plan.resources[0]?.status).toBe('生成失败')
    expect(plan.wrongQuestions[0]?.status).toBe('需巩固')
  })
})
