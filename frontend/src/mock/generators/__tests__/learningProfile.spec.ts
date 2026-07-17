import { describe, expect, it } from 'vitest'
import {
  buildMockLearningConfirmation,
  inferMockLearningProfile,
} from '@/mock/generators/learningProfile'

describe('Mock learning profile generator', () => {
  it('extracts an editable profile without involving page state', () => {
    const profile = inferMockLearningProfile({
      knowledgeBaseId: 1,
      text: '我要准备 2 周后的面试，每天学习 90 分钟，算法基础薄弱，希望刷题和案例讲解',
      subject: '算法基础',
      source: '算法资料库',
      knowledgeTags: ['算法基础'],
    })

    expect(profile.goal).toBe('职业技能')
    expect(profile.period).toBe('2 周')
    expect(profile.dailyTime).toBe('每天 90 分钟')
    expect(profile.preferences).toEqual(expect.arrayContaining(['练习驱动', '概念讲解', '案例演示']))
    expect(profile.source).toBe('算法资料库')
  })

  it('builds the confirmation text from the shared request contract', () => {
    const profile = inferMockLearningProfile({
      knowledgeBaseId: 1,
      text: '下周复习数据库，每天 60 分钟',
      subject: '数据库',
      source: '数据库资料库',
    })
    const content = buildMockLearningConfirmation({
      setupId: 'setup-1',
      knowledgeBaseId: 1,
      goal: '期末复习',
      profile,
      questionCount: 30,
      clientRequestId: 'test-confirmation-1',
    })

    expect(content).toContain('# 个性化学习方案确认稿')
    expect(content).toContain('期末复习')
    expect(content).toContain('计划练习总量：30 题')
  })
})
