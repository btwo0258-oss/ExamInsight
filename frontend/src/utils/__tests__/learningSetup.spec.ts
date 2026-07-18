import { isProxy, reactive } from 'vue'
import { describe, expect, it } from 'vitest'

import { snapshotLearningProfile } from '@/utils/learningSetup'

describe('snapshotLearningProfile', () => {
  it('converts a Vue reactive profile into a cloneable plain DTO', () => {
    const profile = reactive({
      goal: '考试复习',
      subject: 'Java',
      foundation: '一般',
      weakPoints: ['多态'],
      period: '3 天',
      dailyTime: '60 分钟',
      preferences: ['练习驱动'],
      source: 'Java 知识库',
      extra: '',
    })

    const snapshot = snapshotLearningProfile(profile)

    expect(isProxy(snapshot)).toBe(false)
    expect(isProxy(snapshot.weakPoints)).toBe(false)
    expect(isProxy(snapshot.preferences)).toBe(false)
    expect(() => structuredClone(snapshot)).not.toThrow()
    profile.weakPoints.push('接口')
    expect(snapshot.weakPoints).toEqual(['多态'])
  })
})
