import { request } from '@/api/request'
import type {
  SmartLearningJob,
  SmartLearningJobAccepted,
  SmartLearningProject,
  SmartLearningProjectDetail,
} from '@/types/contracts/smartLearning'

type Json = Record<string, unknown>

function unwrap<T>(response: { data: T }) {
  return response.data
}

export function listSmartLearningProjects() {
  return request.get<SmartLearningProject[]>('/api/v2/learning/projects').then(unwrap)
}

export function createSmartLearningProject(payload: {
  name: string
  icon?: string
  iconColor?: string
  knowledgeBaseId?: string | null
}) {
  return request.post<SmartLearningProjectDetail>('/api/v2/learning/projects', payload).then(unwrap)
}

export function getSmartLearningProject(projectId: string) {
  return request.get<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}`).then(unwrap)
}

export function renameSmartLearningProject(projectId: string, name: string) {
  return request.patch<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}`, { name }).then(unwrap)
}

export function archiveSmartLearningProject(projectId: string) {
  return request.delete(`/api/v2/learning/projects/${projectId}`)
}

export function restoreSmartLearningProject(projectId: string) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/restore`).then(unwrap)
}

export function saveSmartLearningTarget(projectId: string, target: Json) {
  return request.patch<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/target`, target).then(unwrap)
}

export function confirmSmartLearningTarget(projectId: string) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/target/confirm`).then(unwrap)
}

export function saveSmartLearningSources(projectId: string, sources: Json) {
  return request.put<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/sources`, sources).then(unwrap)
}

export function confirmSmartLearningSources(projectId: string) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/sources/confirm`).then(unwrap)
}

export function startSmartLearningScope(projectId: string) {
  return request.post<SmartLearningJobAccepted>(`/api/v2/learning/projects/${projectId}/scope/generate`).then(unwrap)
}

export function saveSmartLearningScope(projectId: string, scope: Json) {
  return request.patch<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/scope/candidate`, scope).then(unwrap)
}

export function confirmSmartLearningScope(projectId: string) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/scope/confirm`).then(unwrap)
}

export function startSmartLearningDiagnosis(projectId: string) {
  return request.post<SmartLearningJobAccepted>(`/api/v2/learning/projects/${projectId}/diagnosis/generate`).then(unwrap)
}

export function submitSmartLearningDiagnosis(projectId: string, answers: Json) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/diagnosis/submit`, answers).then(unwrap)
}

export function saveSmartLearningDiagnosisAnswers(projectId: string, answers: Json) {
  return request.patch<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/diagnosis/answers`, answers).then(unwrap)
}

export function skipSmartLearningDiagnosis(projectId: string, reason: string) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/diagnosis/skip`, { reason }).then(unwrap)
}

export function startSmartLearningPlan(projectId: string) {
  return request.post<SmartLearningJobAccepted>(`/api/v2/learning/projects/${projectId}/plan/generate`).then(unwrap)
}

export function saveSmartLearningPlan(projectId: string, plan: Json) {
  return request.patch<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/plan/candidate`, plan).then(unwrap)
}

export function confirmSmartLearningPlan(projectId: string) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/plan/confirm`).then(unwrap)
}

export function saveSmartLearningResourceConfig(projectId: string, config: Json) {
  return request.put<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/resources/config`, config).then(unwrap)
}

export function confirmSmartLearningResourceConfig(projectId: string) {
  return request.post<SmartLearningProjectDetail>(`/api/v2/learning/projects/${projectId}/resources/confirm`).then(unwrap)
}

export function getSmartLearningJob(jobId: string) {
  return request.get<SmartLearningJob>(`/api/v2/learning/jobs/${jobId}`).then(unwrap)
}
