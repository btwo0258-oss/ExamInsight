import { defineStore } from 'pinia'
import { learningErrorMessage } from '@/utils/learningErrors'
import {
  archiveSmartLearningProject,
  deleteSmartLearningProject,
  confirmSmartLearningPlan,
  confirmSmartLearningResourceConfig,
  confirmSmartLearningScope,
  confirmSmartLearningSources,
  confirmSmartLearningTarget,
  createSmartLearningProject,
  getSmartLearningJob,
  getSmartLearningProject,
  listSmartLearningProjects,
  listSmartLearningSidebarProjects,
  renameSmartLearningProject,
  setSmartLearningProjectPinned,
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
  retrySmartLearningResource,
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
  streamSmartLearningJobEvents,
} from '@/api/smartLearning'
import type { SmartLearningExecution, SmartLearningJob, SmartLearningProject, SmartLearningProjectDetail, SmartLearningResource, SmartLearningSidebarProject, SmartLearningTask, SmartLearningWorkspace } from '@/types/contracts/smartLearning'

type Json = Record<string, unknown>

const messageOf = learningErrorMessage

export const useSmartLearningStore = defineStore('smartLearning', {
  state: () => ({
    projects: [] as SmartLearningProject[],
    sidebarProjects: [] as SmartLearningSidebarProject[],
    current: null as SmartLearningProjectDetail | null,
    loading: false,
    saving: false,
    errorMessage: '',
    runningJobId: '',
    runningJobKind: '' as 'SCOPE_ANALYSIS' | 'DIAGNOSIS_GENERATION' | 'PLAN_GENERATION' | 'RESOURCE_PREPARATION' | '',
    workspace: null as SmartLearningWorkspace | null,
    currentTask: null as SmartLearningTask | null,
    activeProjectId: '',
    projectEpoch: 0,
    sessionEpoch: 0,
  }),
  actions: {
    clear() {
      this.sessionEpoch += 1
      this.projectEpoch += 1
      this.activeProjectId = ''
      this.projects = []
      this.sidebarProjects = []
      this.current = null
      this.workspace = null
      this.currentTask = null
      this.loading = false
      this.saving = false
      this.errorMessage = ''
      this.runningJobId = ''
      this.runningJobKind = ''
    },
    beginProjectContext(projectId: string) {
      if (this.activeProjectId !== projectId) {
        this.projectEpoch += 1
        this.activeProjectId = projectId
        this.current = null
        this.workspace = null
        this.currentTask = null
        this.runningJobId = ''
        this.runningJobKind = ''
      }
      return this.projectEpoch
    },
    isProjectContext(projectId: string, epoch: number) {
      return this.activeProjectId === projectId && this.projectEpoch === epoch
    },
    async fetchProjects() {
      const sessionEpoch = this.sessionEpoch
      this.loading = true
      this.errorMessage = ''
      try {
        const projects = await listSmartLearningProjects()
        if (sessionEpoch === this.sessionEpoch) this.projects = projects
      } catch (error) {
        if (sessionEpoch === this.sessionEpoch) this.errorMessage = messageOf(error, '获取智能学习项目失败。')
        throw error
      } finally {
        if (sessionEpoch === this.sessionEpoch) this.loading = false
      }
    },
    async fetchProject(projectId: string) {
      const epoch = this.beginProjectContext(projectId)
      this.loading = true
      this.errorMessage = ''
      try {
        const current = await getSmartLearningProject(projectId)
        if (this.isProjectContext(projectId, epoch)) this.current = current
        return current
      } catch (error) {
        if (this.isProjectContext(projectId, epoch)) this.errorMessage = messageOf(error, '获取学习项目详情失败。')
        throw error
      } finally {
        if (this.isProjectContext(projectId, epoch)) this.loading = false
      }
    },
    async fetchSidebarProjects() {
      const sessionEpoch = this.sessionEpoch
      const projects = await listSmartLearningSidebarProjects()
      if (sessionEpoch === this.sessionEpoch) this.sidebarProjects = projects
      return projects
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
    async deletePermanently(projectId: string) {
      this.errorMessage = ''
      try {
        await deleteSmartLearningProject(projectId)
        if (this.current?.projectId === projectId) this.current = null
        this.projects = this.projects.filter(project => project.projectId !== projectId)
        this.sidebarProjects = this.sidebarProjects.filter(project => project.projectId !== projectId)
      } catch (error) {
        this.errorMessage = messageOf(error, '删除学习项目失败。')
        throw error
      }
    },
    async setPinned(projectId: string, pinned: boolean) {
      const updated = await setSmartLearningProjectPinned(projectId, pinned)
      const project = this.projects.find(item => item.projectId === projectId)
      if (project) project.pinnedAt = updated.pinnedAt
      await this.fetchSidebarProjects()
      return updated
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
      const epoch = this.beginProjectContext(projectId)
      const accepted = await prepareSmartLearningResources(projectId)
      if (!this.isProjectContext(projectId, epoch)) throw new Error('已切换到其他学习项目。')
      this.runningJobId = accepted.jobId
      this.runningJobKind = accepted.kind as typeof this.runningJobKind
      return this.waitForJob(accepted.jobId, projectId)
    },
    async retryResource(projectId: string, resourceId: string) {
      const epoch = this.beginProjectContext(projectId)
      const accepted = await retrySmartLearningResource(projectId, resourceId)
      if (!this.isProjectContext(projectId, epoch)) throw new Error('已切换到其他学习项目。')
      this.runningJobId = accepted.jobId
      this.runningJobKind = accepted.kind as typeof this.runningJobKind
      return this.waitForJob(accepted.jobId, projectId)
    },
    async fetchWorkspace(projectId: string) {
      const epoch = this.beginProjectContext(projectId)
      const workspace = await getSmartLearningWorkspace(projectId)
      if (this.isProjectContext(projectId, epoch)) this.workspace = workspace
      return workspace
    },
    async fetchTask(projectId: string, taskId: string) {
      const epoch = this.beginProjectContext(projectId)
      const task = await getSmartLearningTask(projectId, taskId)
      if (this.isProjectContext(projectId, epoch)) this.currentTask = task
      return task
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
      const completedExecution = execution.status === 'COMPLETED' ? execution : undefined
      if (this.currentTask?.taskId === execution.taskId) {
        this.currentTask = {
          ...this.currentTask,
          execution,
          ...(completedExecution ? { completedExecution } : {}),
        }
      }
      if (this.workspace) {
        const isActive = execution.status === 'IN_PROGRESS' || execution.status === 'PAUSED'
        const activeExecution = isActive
          ? execution
          : this.workspace.activeExecution?.executionId === execution.executionId
            ? null
            : this.workspace.activeExecution
        this.workspace = {
          ...this.workspace,
          activeExecution,
          tasks: this.workspace.tasks.map(task => task.taskId === execution.taskId
            ? {
                ...task,
                status: execution.status,
                execution,
                ...(completedExecution ? { completedExecution } : {}),
              }
            : task),
        }
      }
    },
    patchResourceState(projectId: string, update: Partial<SmartLearningResource> & { resourceId: string }) {
      if (this.activeProjectId !== projectId) return
      const patch = (resource: SmartLearningResource) => resource.resourceId === update.resourceId
        && !(resource.status === 'READY' && update.status !== 'READY')
        ? { ...resource, ...update }
        : resource
      if (this.workspace?.projectId === projectId) {
        this.workspace = {
          ...this.workspace,
          resources: this.workspace.resources.map(patch),
          tasks: this.workspace.tasks.map(task => ({
            ...task,
            resources: task.resources.map(patch),
          })),
        }
      }
      if (this.currentTask) {
        this.currentTask = {
          ...this.currentTask,
          resources: this.currentTask.resources.map(patch),
        }
      }
    },
    async startJob(projectId: string, kind: 'scope' | 'diagnosis' | 'plan') {
      const epoch = this.beginProjectContext(projectId)
      const accepted = kind === 'scope'
        ? await startSmartLearningScope(projectId)
        : kind === 'diagnosis' ? await startSmartLearningDiagnosis(projectId) : await startSmartLearningPlan(projectId)
      if (!this.isProjectContext(projectId, epoch)) throw new Error('已切换到其他学习项目。')
      this.runningJobId = accepted.jobId
      this.runningJobKind = accepted.kind as typeof this.runningJobKind
      return this.waitForJob(accepted.jobId, projectId)
    },
    async waitForJob(jobId: string, projectId: string) {
      if (this.activeProjectId !== projectId) throw new Error('已切换到其他学习项目。')
      const epoch = this.projectEpoch
      const controller = new AbortController()
      let streamTerminal = false
      let lastEventId: string | null = null
      const followEvents = async () => {
        let reconnects = 0
        while (!controller.signal.aborted && !streamTerminal) {
          try {
            await streamSmartLearningJobEvents(jobId, {
              signal: controller.signal,
              lastEventId,
              onEvent: (event) => {
                if (event.id) lastEventId = event.id
                if (!this.isProjectContext(projectId, epoch)) {
                  controller.abort()
                  return
                }
                if (event.event === 'resource.updated') {
                  const resourceId = String(event.data.resourceId ?? '')
                  if (event.data.status === 'READY') {
                    // Do not unlock an empty card before its persisted content
                    // arrives. Fetch only the affected task, preserving answers.
                    void getSmartLearningTask(projectId, String(event.data.taskId ?? '')).then(task => {
                      if (!this.isProjectContext(projectId, epoch)) return
                      const resource = task.resources.find(item => item.resourceId === resourceId)
                      if (resource) this.patchResourceState(projectId, resource)
                    }).catch(() => { /* terminal job sync remains the fallback */ })
                    return
                  }
                  this.patchResourceState(projectId, {
                    resourceId,
                    taskId: String(event.data.taskId ?? ''),
                    status: String(event.data.status ?? 'GENERATING'),
                    generationStage: String(event.data.generationStage ?? 'GENERATING_CONTENT'),
                    generationProgress: Number(event.data.generationProgress ?? 0),
                    errorMessage: typeof event.data.errorMessage === 'string' ? event.data.errorMessage : null,
                  })
                }
                if (['run.completed', 'run.failed'].includes(event.event)) streamTerminal = true
              },
            })
            reconnects = 0
          } catch {
            if (controller.signal.aborted) return
            reconnects += 1
          }
          if (!streamTerminal && !controller.signal.aborted) {
            await new Promise(resolve => window.setTimeout(resolve, Math.min(4000, 500 * (2 ** reconnects))))
          }
        }
      }
      void followEvents()
      try {
        for (let attempt = 0; attempt < 240; attempt += 1) {
          const job: SmartLearningJob = await getSmartLearningJob(jobId)
          if (!this.isProjectContext(projectId, epoch)) throw new Error('已切换到其他学习项目。')
          if (job.status === 'SUCCEEDED') {
            const [current, workspace] = await Promise.all([
              getSmartLearningProject(projectId),
              job.kind === 'RESOURCE_PREPARATION' ? getSmartLearningWorkspace(projectId) : Promise.resolve(null),
            ])
            if (this.isProjectContext(projectId, epoch)) {
              this.current = current
              if (workspace) this.workspace = workspace
            }
            return job
          }
          if (['FAILED', 'CANCELLED', 'UNKNOWN'].includes(job.status)) {
            if (job.kind === 'RESOURCE_PREPARATION') {
              const workspace = await getSmartLearningWorkspace(projectId).catch(() => null)
              if (workspace && this.isProjectContext(projectId, epoch)) this.workspace = workspace
            }
            throw new Error(job.errorMessage || '学习分析任务未完成。')
          }
          await new Promise(resolve => window.setTimeout(resolve, 2500))
        }
        throw new Error('学习分析仍在进行中，请稍后回到项目查看。')
      } finally {
        controller.abort()
        if (this.isProjectContext(projectId, epoch)) {
          this.runningJobId = ''
          this.runningJobKind = ''
        }
      }
    },
  },
})
