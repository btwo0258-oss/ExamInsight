import type { LearningProfileData } from '@/types/contracts/learning'

export function snapshotLearningProfile(profile: LearningProfileData): LearningProfileData {
  return {
    goal: profile.goal,
    subject: profile.subject,
    foundation: profile.foundation,
    weakPoints: [...profile.weakPoints],
    period: profile.period,
    dailyTime: profile.dailyTime,
    preferences: [...profile.preferences],
    source: profile.source,
    extra: profile.extra,
  }
}
