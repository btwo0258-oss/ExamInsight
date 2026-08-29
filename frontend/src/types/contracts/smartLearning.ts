export type SmartLearningStage =
  | 'TARGET_REQUIRED'
  | 'SOURCES_REQUIRED'
  | 'SCOPE_REQUIRED'
  | 'DIAGNOSTIC_REQUIRED'
  | 'PLAN_REQUIRED'
  | 'RESOURCE_CONFIG_REQUIRED'
  | 'READY'
  | 'ARCHIVED'

export type SmartLearningJob = {
  jobId: string
  projectId: string
  kind: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'UNKNOWN'
  progressCurrent: number
  progressTotal: number
  result: Record<string, unknown>
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
}

export type SmartLearningProject = {
  projectId: string
  name: string
  icon: string
  iconColor: string
  knowledgeBaseId: string | null
  stage: SmartLearningStage
  nextStep: string
  targetVersion: number
  sourceVersion: number
  scopeVersion: number
  diagnosisVersion: number
  planVersion: number
  resourceConfigVersion: number
  updatedAt: string
}

export type SmartLearningProjectDetail = SmartLearningProject & {
  target: Record<string, unknown>
  targetDraft: Record<string, unknown>
  sources: Record<string, unknown>
  sourcesDraft: Record<string, unknown>
  scope: Record<string, unknown>
  scopeCandidate: Record<string, unknown>
  diagnosis: Record<string, unknown>
  diagnosisCandidate: Record<string, unknown>
  diagnosisAnswersDraft: Record<string, unknown>
  plan: Record<string, unknown>
  planCandidate: Record<string, unknown>
  resourceConfig: Record<string, unknown>
  resourceConfigDraft: Record<string, unknown>
  versions: Record<string, number>
  activeJob: SmartLearningJob | null
}

export type SmartLearningJobAccepted = {
  jobId: string
  projectId: string
  kind: string
  status: string
}

export type SmartLearningResource = {
  resourceId: string
  taskId: string
  kind: 'READING' | 'EXERCISE_SET' | string
  title: string
  status: 'QUEUED' | 'GENERATING' | 'READY' | 'FAILED' | string
  content: Record<string, unknown>
  errorMessage: string | null
  updatedAt: string
}

export type SmartLearningExecution = {
  executionId: string
  projectId: string
  taskId: string
  status: 'IN_PROGRESS' | 'PAUSED' | 'COMPLETION_PENDING' | 'COMPLETED' | 'SKIPPED' | string
  progress: number
  accumulatedSeconds: number
  position: Record<string, unknown>
  answers: Record<string, unknown>
  score: number | null
  lastHeartbeatSeq: number
  startedAt: string
  pausedAt: string | null
  completedAt: string | null
  updatedAt: string
}

export type SmartLearningTask = {
  taskId: string
  title: string
  taskType: 'READING' | 'EXERCISE' | 'REVIEW' | 'EXPLANATION' | string
  description: string
  completionCriteria: string
  scheduledDate: string | null
  durationMinutes: number
  status: string
  sortOrder: number
  payload: Record<string, unknown>
  resources: SmartLearningResource[]
  execution: SmartLearningExecution | null
  updatedAt: string
}

export type SmartLearningWorkspace = {
  projectId: string
  projectName: string
  stage: SmartLearningStage
  progress: number
  tasks: SmartLearningTask[]
  resources: SmartLearningResource[]
  activeExecution: SmartLearningExecution | null
  updatedAt: string
}
