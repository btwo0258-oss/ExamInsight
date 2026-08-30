import { expect, test, type Page, type Route } from '@playwright/test'

const baseUrl = 'http://localhost:5173'
const projectId = 'smart-project'
const taskId = 'exercise-task'
const executionId = 'exercise-execution'
const conversationId = '01SMARTTUTOR00000000000000'
type Json = Record<string, any>

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function fixture(page: Page) {
  page.on('pageerror', error => console.error('SMART_LEARNING_PAGE_ERROR', error.message))
  page.on('console', message => {
    if (message.type() === 'error') console.error('SMART_LEARNING_CONSOLE_ERROR', message.text())
  })
  const project: Json = {
    projectId, name: 'CSS 学习', icon: 'notebook', iconColor: '#8b5cf6', knowledgeBaseId: null,
    stage: 'READY', nextStep: '进入学习工作台', targetVersion: 1, sourceVersion: 1,
    scopeVersion: 1, diagnosisVersion: 1, planVersion: 1, resourceConfigVersion: 1,
    learningProgress: 50, completedTaskCount: 1, totalTaskCount: 2, pinnedAt: null,
    target: { examName: '掌握 CSS', weeklyMinutes: 300 }, targetDraft: {},
    sources: { assets: [] }, sourcesDraft: {}, scope: { nodes: [{ id: 'n1', title: '选择器' }] },
    scopeCandidate: {}, diagnosis: { level: '基础一般' }, diagnosisCandidate: {}, diagnosisAnswersDraft: {},
    plan: { tasks: [] }, planCandidate: {}, resourceConfig: { questionCount: 2 }, resourceConfigDraft: {},
    versions: { target: 1, sources: 1, scope: 1, diagnosis: 1, plan: 1, resourceConfig: 1 },
    activeJob: null, updatedAt: '2026-08-30T08:00:00Z',
  }
  const resource: Json = {
    resourceId: 'exercise-resource', taskId, kind: 'EXERCISE_SET', title: '选择器练习', status: 'READY',
    content: { questionCount: 2, items: [
      { id: 'q1', stem: '下列哪个选择器优先级最高？', options: ['A', 'B'], answer: 'A', explanation: 'A 的优先级更高。', knowledgeKey: '优先级' },
      { id: 'q2', stem: '哪个写法是伪类？', options: ['A', 'B', 'C'], answer: 'B', explanation: 'B 使用单冒号。', knowledgeKey: '伪类' },
    ] }, errorMessage: null, updatedAt: '2026-08-30T08:00:00Z',
  }
  const execution: Json = {
    executionId, projectId, taskId, status: 'IN_PROGRESS', progress: 0, accumulatedSeconds: 0,
    position: {}, answers: {}, score: null, lastHeartbeatSeq: 0, startedAt: '2026-08-30T08:00:00Z',
    pausedAt: null, completedAt: null, updatedAt: '2026-08-30T08:00:00Z', grading: null,
  }
  const exerciseTask: Json = {
    taskId, title: 'CSS 选择器练习', taskType: 'EXERCISE', description: '完成选择器练习',
    completionCriteria: '完成 2 道练习题并提交判卷。', scheduledDate: '2026-08-30', durationMinutes: 20,
    status: 'IN_PROGRESS', sortOrder: 1, payload: { questionCount: 2 }, resources: [resource], execution,
    updatedAt: '2026-08-30T08:00:00Z',
  }
  const completedTask: Json = {
    taskId: 'reading-task', title: 'CSS 基础阅读', taskType: 'READING', description: '阅读基础资料',
    completionCriteria: '完成阅读', scheduledDate: '2026-08-30', durationMinutes: 30,
    status: 'COMPLETED', sortOrder: 0, payload: {}, resources: [], execution: null,
    updatedAt: '2026-08-30T07:00:00Z',
  }
  const workspace: Json = {
    projectId, projectName: project.name, stage: 'READY', progress: 50, completedTaskCount: 1,
    totalTaskCount: 2, wrongItemCount: 0, pendingWrongItemCount: 0,
    profile: { target: project.target, diagnosis: project.diagnosis, scope: project.scope },
    tasks: [completedTask, exerciseTask], resources: [resource], activeExecution: execution,
    updatedAt: project.updatedAt,
  }
  const wrongItems: Json[] = []
  const control = { pauseCount: 0, resumeCount: 0, submissionCount: 0 }
  const conversation = {
    id: conversationId, title: 'CSS 学习 · AI 助教', titleSource: 'SYSTEM', type: 'GENERAL', status: 'ACTIVE',
    knowledgeBaseId: null, activeBranchId: null, messageCount: 0, version: 1, pinnedAt: null,
    lastMessageAt: '2026-08-30T08:00:00Z', createdAt: '2026-08-30T08:00:00Z', updatedAt: '2026-08-30T08:00:00Z',
  }

  await page.addInitScript(() => localStorage.setItem('llm.theme', 'light'))
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    if (!path.startsWith('/api/')) return route.continue()
    const body = request.postData() ? request.postDataJSON() as Json : {}
    if (path === '/api/v2/auth/session') return json(route, {
      userId: 'smart-user', email: 'smart@example.test', displayName: '学习用户', authLevel: 'PASSWORD',
      idleExpiresAt: '2099-01-01T00:00:00Z', absoluteExpiresAt: '2099-01-01T00:00:00Z',
    })
    if (path === '/api/v2/conversations' && method === 'GET') return json(route, { items: [], nextCursor: null, hasMore: false })
    if (path === '/api/v2/learning/sidebar') return json(route, [])
    if (path === '/api/v2/knowledge-bases') return json(route, { items: [], nextCursor: null })
    if (path === '/api/v2/artifacts') return json(route, [])
    if (path === `/api/v2/conversations/${conversationId}/messages`) return json(route, {
      conversation, messages: [], versionGroups: [], segments: [], nextCursor: null, hasMore: false,
    })
    if (path === `/api/v2/learning/projects/${projectId}/tutor-thread`) return json(route, {
      threadId: 'tutor-thread', conversationId, projectId, taskId: null, contextType: 'PROJECT',
    })
    if (path === `/api/v2/learning/projects/${projectId}`) return json(route, project)
    if (path === `/api/v2/learning/projects/${projectId}/workspace`) return json(route, workspace)
    if (path === `/api/v2/learning/projects/${projectId}/tasks/${taskId}`) return json(route, exerciseTask)
    if (path === `/api/v2/learning/projects/${projectId}/wrong-items`) return json(route, wrongItems)
    if (path === `/api/v2/learning/projects/${projectId}/tasks/${taskId}/executions` && method === 'POST') {
      control.resumeCount += 1
      execution.status = 'IN_PROGRESS'; execution.pausedAt = null; exerciseTask.status = 'IN_PROGRESS'
      return json(route, execution)
    }
    if (path === `/api/v2/learning/executions/${executionId}/pause`) {
      control.pauseCount += 1
      execution.status = 'PAUSED'; execution.pausedAt = '2026-08-30T08:05:00Z'; exerciseTask.status = 'PAUSED'
      return json(route, execution)
    }
    if (path === `/api/v2/learning/executions/${executionId}/answers`) {
      execution.answers = body
      return json(route, execution)
    }
    if (path === `/api/v2/learning/executions/${executionId}/position`) {
      execution.position = body
      return json(route, execution)
    }
    if (path === `/api/v2/learning/executions/${executionId}/progress`) {
      execution.progress = Number(body.progress || 0)
      return json(route, execution)
    }
    if (path === `/api/v2/learning/executions/${executionId}/heartbeat`) return json(route, execution)
    if (path === `/api/v2/learning/executions/${executionId}/complete`) {
      control.submissionCount += 1
      execution.status = 'COMPLETED'; execution.progress = 100; execution.score = 50
      execution.completedAt = '2026-08-30T08:10:00Z'
      execution.grading = { total: 2, answered: 2, correct: 1, accuracy: 50, items: [
        { questionId: 'q1', index: 0, answered: true, correct: true, answer: 'A', correctAnswer: 'A', explanation: 'A 的优先级更高。' },
        { questionId: 'q2', index: 1, answered: true, correct: false, answer: 'C', correctAnswer: 'B', explanation: 'B 使用单冒号。' },
      ] }
      exerciseTask.status = 'COMPLETED'; workspace.progress = 100; workspace.completedTaskCount = 2
      wrongItems.push({
        wrongItemId: 'wrong-q2', projectId, taskId, questionId: 'q2', stem: '哪个写法是伪类？',
        userAnswer: 'C', correctAnswer: 'B', explanation: 'B 使用单冒号。', knowledgeKey: '伪类',
        status: 'TO_REVIEW', updatedAt: '2026-08-30T08:10:00Z',
      })
      return json(route, execution)
    }
    return json(route, {})
  })
  return { control }
}

test('workbench exposes grouped resources and an initialized project tutor', async ({ page }) => {
  await fixture(page)
  await page.goto(`${baseUrl}/learning/${projectId}`)

  await expect(page.getByRole('heading', { name: 'CSS 学习' })).toBeVisible()
  await expect(page.locator('.path-summary')).toContainText('50%')
  await expect(page.getByRole('button', { name: /练习资料 1\/1 项已就绪/ })).toBeVisible()

  await page.getByRole('button', { name: /问问当前项目/ }).click()
  const tutor = page.getByRole('dialog', { name: 'AI 助教' })
  await expect(tutor).toBeVisible()
  await expect(tutor.getByPlaceholder('问问当前学习项目…')).toBeVisible()
  await tutor.getByRole('button', { name: '关闭', exact: true }).click()

  await page.getByRole('button', { name: '进入资源', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/learning/${projectId}/resources\\?group=EXERCISE`))
  await expect(page.locator('.resource-question')).toHaveCount(2)
  await expect(page.getByText('正确答案', { exact: true })).toHaveCount(2)
})

test('task navigation pauses and resumes internally, then grades once and fills the wrong book', async ({ page }) => {
  const { control } = await fixture(page)
  await page.goto(`${baseUrl}/learning/${projectId}/task/${taskId}`)
  await expect(page.getByRole('heading', { name: 'CSS 选择器练习' })).toBeVisible()

  await page.getByRole('button', { name: '返回学习工作台' }).click()
  await expect(page).toHaveURL(`${baseUrl}/learning/${projectId}`)
  expect(control.pauseCount).toBe(1)

  await page.getByRole('button', { name: /CSS 选择器练习/ }).click()
  await expect(page).toHaveURL(`${baseUrl}/learning/${projectId}/task/${taskId}`)
  await expect(page.getByRole('heading', { name: 'CSS 选择器练习' })).toBeVisible()
  await expect.poll(() => control.resumeCount).toBe(1)

  const questions = page.locator('.exercise-item')
  await questions.nth(0).getByRole('radio', { name: 'A', exact: true }).check()
  await questions.nth(1).getByRole('radio', { name: 'C', exact: true }).check()
  await expect(questions.nth(0).getByRole('radio', { name: 'A', exact: true })).toBeChecked()
  await expect(questions.nth(1).getByRole('radio', { name: 'C', exact: true })).toBeChecked()
  await expect(page.locator('.question-index-grid button.answered')).toHaveCount(2)

  await page.getByRole('button', { name: '提交判卷', exact: true }).click()
  await page.getByRole('dialog', { name: '提交判卷' }).getByRole('button', { name: '提交判卷' }).click()
  await expect(page.locator('.accuracy-summary')).toContainText('50%')
  await expect(page.locator('.question-index-grid button.correct')).toHaveCount(1)
  await expect(page.locator('.question-index-grid button.incorrect')).toHaveCount(1)
  expect(control.submissionCount).toBe(1)

  await page.goto(`${baseUrl}/learning/${projectId}/mistakes`)
  await expect(page.getByRole('heading', { name: '哪个写法是伪类？' })).toBeVisible()
  await expect(page.locator('.mistake-detail > section').nth(0).locator('p')).toHaveText('C')
  await expect(page.locator('.mistake-detail > section').nth(1).locator('p')).toHaveText('B')
})
