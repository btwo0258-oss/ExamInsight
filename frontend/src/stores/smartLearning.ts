import { defineStore } from 'pinia'
import { learningErrorMessage } from '@/utils/learningErrors'
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
  prepareSmartLearningResources,
  getSmartLearningWorkspace,
  getSmartLearningTask,
  startSmartLearningExecution,
  pauseSmartLearningExecution,
  resumeSmartLearningExecution,
  completeSmartLearningExecution,
  skipSmartLearningExecution,
  saveSmartLearningExecutionProgress,
  saveSmartLearningExecutionPosition,
  saveSmartLearningExecutionAnswers,
  heartbeatSmartLearningExecution,
} from '@/api/smartLearning'
import type { SmartLearningExecution, SmartLearningJob, SmartLearningProject, SmartLearningProjectDetail, SmartLearningTask, SmartLearningWorkspace } from '@/types/contracts/smartLearning'

type Json = Record<string, unknown>

const messageOf = learningErrorMessage

export const useSmartLearningStore = defineStore('smartLearning', {
  state: () => ({
    projects: [] as SmartLearningProject[],
    current: null as SmartLearningProjectDetail | null,
    loading: false,
    saving: false,
    errorMessage: '',
    runningJobId: '',
    runningJobKind: '' as 'SCOPE_ANALYSIS' | 'DIAGNOSIS_GENERATION' | 'PLAN_GENERATION' | 'RESOURCE_PREPARATION' | '',
    workspace: null as SmartLearningWorkspace | null,
    currentTask: null as SmartLearningTask | null,
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
    async create(payload: { name: string; icon?: string; iconColor?: string; knowledgeBaseId?: string | null }) {
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
    async rename(projectId: string, payload: string | { name: string; icon?: string; iconColor?: string }) {
      const updated = await renameSmartLearningProject(projectId, payload)
      if (this.current?.projectId === projectId) this.current = updated
      const index = this.projects.findIndex(project => project.projectId === projectId)
      if (index >= 0) this.projects[index] = { ...this.projects[index]!, name: updated.name, icon: updated.icon, iconColor: updated.iconColor, updatedAt: updated.updatedAt }
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
    async prepareResources(projectId: string) {
      const accepted = await prepareSmartLearningResources(projectId)
      this.runningJobId = accepted.jobId
      this.runningJobKind = accepted.kind as typeof this.runningJobKind
      return this.waitForJob(accepted.jobId, projectId)
    },
    async fetchWorkspace(projectId: string) {
      this.workspace = await getSmartLearningWorkspace(projectId)
      return this.workspace
    },
    async fetchTask(projectId: string, taskId: string) {
      this.currentTask = await getSmartLearningTask(projectId, taskId)
      return this.currentTask
    },
    async startExecution(projectId: string, taskId: string) {
      const execution = await startSmartLearningExecution(projectId, taskId)
      this.syncExecution(execution)
      return execution
    },
    async pauseExecution(executionId: string) { const execution = await pauseSmartLearningExecution(executionId); this.syncExecution(execution); return execution },
    async resumeExecution(executionId: string) { const execution = await resumeSmartLearningExecution(executionId); this.syncExecution(execution); return execution },
    async completeExecution(executionId: string) { const execution = await completeSmartLearningExecution(executionId); this.syncExecution(execution); return execution },
    async skipExecution(executionId: string) { const execution = await skipSmartLearningExecution(executionId); this.syncExecution(execution); return execution },
    async saveExecutionProgress(executionId: string, progress: number, secondsDelta = 0) {
      const execution = await saveSmartLearningExecutionProgress(executionId, progress, secondsDelta)
      this.syncExecution(execution)
      return execution
    },
    async saveExecutionPosition(executionId: string, position: Record<string, unknown>) {
      const execution = await saveSmartLearningExecutionPosition(executionId, position)
      this.syncExecution(execution)
      return execution
    },
    async saveExecutionAnswers(executionId: string, answers: Record<string, unknown>) {
      const execution = await saveSmartLearningExecutionAnswers(executionId, answers)
      this.syncExecution(execution)
      return execution
    },
    async heartbeatExecution(executionId: string, sequence: number, secondsDelta = 0) {
      const execution = await heartbeatSmartLearningExecution(executionId, sequence, secondsDelta)
      this.syncExecution(execution)
      return execution
    },
    syncExecution(execution: SmartLearningExecution) {
      if (this.currentTask?.taskId === execution.taskId) this.currentTask = { ...this.currentTask, execution }
      if (this.workspace) {
        this.workspace = {
          ...this.workspace,
          activeExecution: execution.status === 'IN_PROGRESS' || execution.status === 'PAUSED' ? execution : this.workspace.activeExecution,
          tasks: this.workspace.tasks.map(task => task.taskId === execution.taskId ? { ...task, status: execution.status, execution } : task),
        }
      }
    },
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
