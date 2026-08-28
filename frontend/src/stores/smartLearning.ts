import { defineStore } from 'pinia'
import {
  archiveSmartLearningProject,
  confirmSmartLearningPlan,
  confirmSmartLearningResourceConfig,
  confirmSmartLearningScope,
  confirmSmartLearningSources,
  confirmSmartLearningTarget,
  createSmartLearningProject,
  getSmartLearningJob,
  getSmartLearningProject,
  listSmartLearningProjects,
  renameSmartLearningProject,
  saveSmartLearningPlan,
  saveSmartLearningDiagnosisAnswers,
  saveSmartLearningResourceConfig,
  saveSmartLearningScope,
  saveSmartLearningSources,
  saveSmartLearningTarget,
  skipSmartLearningDiagnosis,
  startSmartLearningDiagnosis,
  startSmartLearningPlan,
  startSmartLearningScope,
  submitSmartLearningDiagnosis,
} from '@/api/smartLearning'
import type { SmartLearningJob, SmartLearningProject, SmartLearningProjectDetail } from '@/types/contracts/smartLearning'

type Json = Record<string, unknown>

function messageOf(error: unknown, fallback: string) {
  const response = (error as { response?: { data?: { error?: { message?: string }; message?: string } } })?.response?.data
  return response?.error?.message || response?.message || (error instanceof Error ? error.message : fallback)
}

export const useSmartLearningStore = defineStore('smartLearning', {
  state: () => ({
    projects: [] as SmartLearningProject[],
    current: null as SmartLearningProjectDetail | null,
    loading: false,
    saving: false,
    errorMessage: '',
    runningJobId: '',
    runningJobKind: '' as 'SCOPE_ANALYSIS' | 'DIAGNOSIS_GENERATION' | 'PLAN_GENERATION' | '',
  }),
  actions: {
    async fetchProjects() {
      this.loading = true
      this.errorMessage = ''
      try {
        this.projects = await listSmartLearningProjects()
      } catch (error) {
        this.errorMessage = messageOf(error, '获取智能学习项目失败。')
        throw error
      } finally {
        this.loading = false
      }
    },
    async fetchProject(projectId: string) {
      this.loading = true
      this.errorMessage = ''
      try {
        this.current = await getSmartLearningProject(projectId)
        return this.current
      } catch (error) {
        this.errorMessage = messageOf(error, '获取学习项目详情失败。')
        throw error
      } finally {
        this.loading = false
      }
    },
    async create(payload: { name: string; knowledgeBaseId?: string | null }) {
      this.saving = true
      this.errorMessage = ''
      try {
        this.current = await createSmartLearningProject(payload)
        return this.current
      } catch (error) {
        this.errorMessage = messageOf(error, '创建学习项目失败。')
        throw error
      } finally {
        this.saving = false
      }
    },
    async rename(projectId: string, name: string) {
      this.current = await renameSmartLearningProject(projectId, name)
      await this.fetchProjects()
    },
    async archive(projectId: string) {
      await archiveSmartLearningProject(projectId)
      await this.fetchProjects()
    },
    async saveTarget(projectId: string, target: Json) { this.current = await saveSmartLearningTarget(projectId, target); return this.current },
    async confirmTarget(projectId: string) { this.current = await confirmSmartLearningTarget(projectId); return this.current },
    async saveSources(projectId: string, sources: Json) { this.current = await saveSmartLearningSources(projectId, sources); return this.current },
    async confirmSources(projectId: string) { this.current = await confirmSmartLearningSources(projectId); return this.current },
    async saveScope(projectId: string, scope: Json) { this.current = await saveSmartLearningScope(projectId, scope); return this.current },
    async confirmScope(projectId: string) { this.current = await confirmSmartLearningScope(projectId); return this.current },
    async submitDiagnosis(projectId: string, answers: Json) { this.current = await submitSmartLearningDiagnosis(projectId, answers); return this.current },
    async saveDiagnosisAnswers(projectId: string, answers: Json) { this.current = await saveSmartLearningDiagnosisAnswers(projectId, answers); return this.current },
    async skipDiagnosis(projectId: string, reason: string) { this.current = await skipSmartLearningDiagnosis(projectId, reason); return this.current },
    async savePlan(projectId: string, plan: Json) { this.current = await saveSmartLearningPlan(projectId, plan); return this.current },
    async confirmPlan(projectId: string) { this.current = await confirmSmartLearningPlan(projectId); return this.current },
    async saveResourceConfig(projectId: string, config: Json) { this.current = await saveSmartLearningResourceConfig(projectId, config); return this.current },
    async confirmResourceConfig(projectId: string) { this.current = await confirmSmartLearningResourceConfig(projectId); return this.current },
    async startJob(projectId: string, kind: 'scope' | 'diagnosis' | 'plan') {
      const accepted = kind === 'scope'
        ? await startSmartLearningScope(projectId)
        : kind === 'diagnosis' ? await startSmartLearningDiagnosis(projectId) : await startSmartLearningPlan(projectId)
      this.runningJobId = accepted.jobId
      this.runningJobKind = accepted.kind as typeof this.runningJobKind
      return this.waitForJob(accepted.jobId, projectId)
    },
    async waitForJob(jobId: string, projectId: string) {
      for (let attempt = 0; attempt < 180; attempt += 1) {
        const job: SmartLearningJob = await getSmartLearningJob(jobId)
        if (job.status === 'SUCCEEDED') {
          this.runningJobId = ''
          this.runningJobKind = ''
          this.current = await getSmartLearningProject(projectId)
          return job
        }
        if (['FAILED', 'CANCELLED', 'UNKNOWN'].includes(job.status)) {
          this.runningJobId = ''
          this.runningJobKind = ''
          throw new Error(job.errorMessage || '学习分析任务未完成。')
        }
        await new Promise(resolve => window.setTimeout(resolve, 1000))
      }
      this.runningJobId = ''
      this.runningJobKind = ''
      throw new Error('学习分析仍在进行中，请稍后回到项目查看。')
    },
  },
})
