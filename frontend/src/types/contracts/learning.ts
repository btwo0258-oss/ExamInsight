import type { LearningResource } from '@/mock/student'
import type { AsyncJob, EntityId } from './common'

export type CreateLearningPlanInput = {
  prompt: string
  libraryId: EntityId
  projectId: EntityId | null
  targetType: string
  preferences: string[]
  resourceGroups: LearningResource['group'][]
  period: string
  foundation: string
  weakPoints: string
  dailyTime: string
  studyDepth: string
  questionCount: number
  supplementalRequirement: string
  draftPlanId?: EntityId | null
  libraryName?: string
}

export type CreateLearningDraftInput = {
  title: string
  libraryId: EntityId | null
  libraryName?: string
  icon?: string
  iconColor?: string
}

export type LearningProfileRequest = {
  libraryId: EntityId
  text: string
  currentProfile?: LearningProfileData
  source?: string
  subject?: string
  knowledgeTags?: string[]
  supplementalRequirement?: string
  mediaAssetIds?: string[]
}

export type LearningProfileData = {
  goal: string
  subject: string
  foundation: string
  weakPoints: string[]
  period: string
  dailyTime: string
  preferences: string[]
  source: string
  extra: string
}

export type LearningProfileResult = {
  profile: LearningProfileData
  confirmationDocument: string
}

export type LearningConfirmationRequest = {
  libraryId: EntityId
  goal: string
  profile: LearningProfileData
  uploadedFileNames?: string[]
  mediaAssetIds?: string[]
  relatedProjectName?: string
  questionCount?: number
  difficultyStrategy?: string
}

export type LearningGenerationJob = AsyncJob<{ projectId: EntityId }>

export type SubmitAnswerRequest = {
  projectId: EntityId
  exerciseId: EntityId
  answer: string
  language?: string
  clientRequestId: string
}

export type RecordLearningActivityRequest = {
  projectId: EntityId
  taskId: EntityId
  eventType: 'start' | 'reading' | 'action' | 'complete'
  progress?: number
  secondsDelta?: number
  action?: string
  clientRequestId: string
}
