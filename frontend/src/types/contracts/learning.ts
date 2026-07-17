import type {
  Exercise,
  LearningPlan,
  LearningResource,
  LearningStage,
  LearningTask,
  TrainingSet,
  WrongQuestion,
  WrongReviewSet,
} from '@/mock/student'
import type { AsyncJob, EntityId } from './common'

export type LearningProjectStatus = 'draft' | 'configuring' | 'ready' | 'in_progress' | 'completed'
export type LearningTaskStatus = 'not_started' | 'in_progress' | 'completed' | 'needs_review' | 'locked'
export type LearningResourceStatus = 'not_selected' | 'generating' | 'ready' | 'failed'
export type WrongQuestionStatus = 'needs_review' | 'mastered'
export type TrainingSetStatus = 'pending' | 'answering' | 'submitted'
export type WrongReviewSetStatus = 'pending' | 'answering' | 'completed'

export type LearningTaskDto = Omit<LearningTask, 'status'> & {
  status: LearningTaskStatus
}

export type LearningStageDto = Omit<LearningStage, 'tasks'> & {
  tasks: LearningTaskDto[]
}

export type LearningResourceDto = Omit<LearningResource, 'status'> & {
  status: LearningResourceStatus
}

export type ExerciseDto = Omit<Exercise, 'draftAnswer' | 'codeDrafts'>

export type TrainingSetDto = Omit<TrainingSet, 'status'> & {
  status: TrainingSetStatus
}

export type WrongQuestionDto = Omit<WrongQuestion, 'status'> & {
  status: WrongQuestionStatus
}

export type WrongReviewSetDto = Omit<WrongReviewSet, 'status'> & {
  status: WrongReviewSetStatus
}

export type LearningProjectDto = Omit<
  LearningPlan,
  'status' | 'stages' | 'resources' | 'exercises' | 'trainingSets' | 'wrongQuestions' | 'wrongReviewSets'
> & {
  status: LearningProjectStatus
  stages: LearningStageDto[]
  resources: LearningResourceDto[]
  exercises: ExerciseDto[]
  trainingSets?: TrainingSetDto[]
  wrongQuestions: WrongQuestionDto[]
  wrongReviewSets?: WrongReviewSetDto[]
}

export type CreateLearningPlanInput = {
  prompt: string
  knowledgeBaseId: EntityId | null
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
  sourceResourceIds?: string[]
  mediaAssetIds?: string[]
  confirmationResourceId?: string | null
  draftPlanId?: EntityId | null
  knowledgeBaseName?: string
}

export type CreateLearningDraftInput = {
  title: string
  knowledgeBaseId: EntityId | null
  knowledgeBaseName?: string
  icon?: string
  iconColor?: string
}

export type UpdateLearningProjectRequest = {
  title?: string
  targetType?: string
  period?: string
  dailyTime?: string
  weakPoints?: string
  preferences?: string[]
  keepExercises?: boolean
  keepProgress?: boolean
}

export type LearningProfileRequest = {
  conversationId?: EntityId | null
  knowledgeBaseId: EntityId | null
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
}

export type LearningConfirmationRequest = {
  setupId: string
  conversationId?: EntityId | null
  knowledgeBaseId: EntityId | null
  goal: string
  profile: LearningProfileData
  uploadedFileNames?: string[]
  mediaAssetIds?: string[]
  relatedProjectName?: string
  questionCount?: number
  difficultyStrategy?: string
  projectId?: EntityId | null
  confirmationResourceId?: string | null
  clientRequestId: string
}

export type LearningConfirmationResult = {
  content: string
  resourceId: string
}

export type LearningGenerationJob = AsyncJob<{ projectId: EntityId }>

export type AnswerResult = {
  correct: boolean
  score?: number
  feedback?: string
  explanation: string
  correctAnswer: string
  taskProgress: number
  projectProgress: number
}

export type SubmitAnswerRequest = {
  projectId: EntityId
  exerciseId: EntityId
  answer: string
  language?: string
  clientRequestId: string
}

export type SubmitAnswerBatchRequest = {
  projectId: EntityId
  answers: Array<{
    exerciseId: EntityId
    answer: string
    language?: string
  }>
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
