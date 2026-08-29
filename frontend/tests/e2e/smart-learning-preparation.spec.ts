import { expect, test, type Page, type Route } from '@playwright/test'

const base = 'http://localhost:5173'
const id = 'learning-ui-project'
const endpoint = `/api/v2/learning/projects/${id}`
type Json = Record<string, any>
async function json(route: Route, body: unknown, status = 200) { await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) }) }
async function fixture(page: Page, stage = 'RESOURCE_CONFIG_REQUIRED') {
  page.on('pageerror', error => console.error('LEARNING_PAGE_ERROR', error.message))
  page.on('console', message => { if (message.type() === 'error') console.error('LEARNING_CONSOLE_ERROR', message.text()) })
  const project: Json = {
    projectId: id, name: '前端学习', icon: 'notebook', iconColor: '#667085', knowledgeBaseId: null,
    stage, nextStep: '继续学习准备', targetVersion: 1, sourceVersion: 1, scopeVersion: 1, diagnosisVersion: 1, planVersion: 1, resourceConfigVersion: 0,
    target: { examName: '前端学习目标', examDate: '', weeklyMinutes: 300, availableDays: ['MONDAY', 'WEDNESDAY'], foundation: '基础一般', blackouts: '', targetScore: '', notes: '' }, targetDraft: {},
    sources: { knowledgeBaseId: null, assets: [], manualScope: '变量和函数' }, sourcesDraft: {},
    scope: {}, scopeCandidate: { nodes: [{ id: 'n1', title: '函数', priority: '核心' }] },
    diagnosis: {}, diagnosisCandidate: {}, diagnosisAnswersDraft: {},
    plan: {}, planCandidate: { tasks: [{ id: 't1', title: '学习函数', durationMinutes: 30, completionCriteria: '能独立编写函数', date: null, dependencies: [] }] },
    resourceConfig: {}, resourceConfigDraft: {}, versions: { target: 1, sources: 1, scope: 1, diagnosis: 0, plan: 1, resourceConfig: 0 }, activeJob: null, updatedAt: '2026-08-28T12:00:00',
  }
  const control = { assetFails: false, saveFails: false, releaseJob: false, confirmError: '', requests: [] as { path: string; method: string; body: Json }[], assetLimits: [] as number[] }
  await page.addInitScript(() => { localStorage.setItem('llm.theme', 'light') })
  await page.route('**/api/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname, method = req.method()
    if (!path.startsWith('/api/')) return route.continue()
    const body = req.postData() ? req.postDataJSON() : {}
    control.requests.push({ path, method, body })
    if (path === '/api/v2/auth/session') return json(route, { userId: 'ui-test-user', email: 'ui@example.test', displayName: '界面验证', authLevel: 'PASSWORD', idleExpiresAt: '2099-01-01T00:00:00Z', absoluteExpiresAt: '2099-01-01T00:00:00Z' })
    if (path === '/api/v2/conversations') return json(route, { items: [], nextCursor: null, hasMore: false })
    if (path === '/api/v2/assets') {
      control.assetLimits.push(Number(url.searchParams.get('limit')))
      if (control.assetFails) return json(route, { message: '服务暂时不可用' }, 500)
      const asset = (assetId: string, status: string) => ({ assetId, name: assetId + '.md', status: 'ACTIVE', version: { versionId: assetId + '-v1', status }, assetType: 'DOCUMENT', sourceType: 'UPLOAD' })
      return json(route, url.searchParams.get('cursor') ? { items: [asset('附加资料', 'READY')], nextCursor: null } : { items: [asset('学习资料', 'READY'), asset('处理中资料', 'PROCESSING')], nextCursor: 'page2' })
    }
    if (path === '/api/v2/knowledge-bases') return json(route, { items: [{ knowledgeBaseId: 'kb-js', name: 'JS学习', assetCount: 3 }], nextCursor: null })
    if (path === '/api/v2/learning/projects' && method === 'GET') return json(route, [project])
    if (path === '/api/v2/learning/projects' && method === 'POST') { Object.assign(project, body, { stage: 'TARGET_REQUIRED' }); return json(route, project) }
    if (path === endpoint && method === 'GET') return json(route, project)
    if (path === endpoint && method === 'PATCH') { Object.assign(project, body); return json(route, project) }
    const drafts: Record<string, string> = { '/target': 'targetDraft', '/sources': 'sourcesDraft', '/scope/candidate': 'scopeCandidate', '/diagnosis/answers': 'diagnosisAnswersDraft', '/plan/candidate': 'planCandidate', '/resources/config': 'resourceConfigDraft' }
    const suffix = path.slice(endpoint.length)
    if (path.startsWith(endpoint) && drafts[suffix] && ['PATCH', 'PUT'].includes(method)) {
      if (control.saveFails) return json(route, { message: '服务暂时不可用' }, 503)
      project[drafts[suffix]!] = body
      return json(route, project)
    }
    if (path === endpoint + '/target/confirm') {
      if (control.confirmError) return json(route, { message: control.confirmError }, 400)
      project.target = project.targetDraft; project.stage = 'SOURCES_REQUIRED'; project.versions.target++
      return json(route, project)
    }
    if (path === endpoint + '/diagnosis/generate') {
      project.activeJob = { jobId: 'job1', kind: 'DIAGNOSIS_GENERATION', status: 'QUEUED' }
      return json(route, project.activeJob)
    }
    if (path === '/api/v2/learning/jobs/job1') {
      if (control.releaseJob) project.diagnosisCandidate = { questions: [{ id: 'q1', stem: '函数的作用是什么？', type: 'single_choice', options: ['复用逻辑', '改变主题'] }] }
      project.activeJob.status = control.releaseJob ? 'SUCCEEDED' : 'RUNNING'
      return json(route, project.activeJob)
    }
    return json(route, {})
  })
  return { project, control }
}
async function step(page: Page, index: number) { await page.getByRole('navigation', { name: '学习准备步骤' }).getByRole('button').nth(index).click() }
async function noOverflow(page: Page) {
  const overflow = await page.locator('.setup-page').evaluate(root => {
    const bounds = root.getBoundingClientRect()
    return [...root.querySelectorAll('input:not([type=checkbox]):not([type=radio]),textarea,.app-select-menu,.setup-error,.step-card')].filter(el => {
      const r = el.getBoundingClientRect(); return r.width > 0 && (r.left < bounds.left - 1 || r.right > bounds.right + 1)
    }).map(el => el.className || el.tagName)
  })
  expect(overflow).toEqual([])
}

test('aligned compact controls, right-aligned multi-date calendar and Chinese validation', async ({ page }, info) => {
  await page.setViewportSize({ width: 1440, height: 1050 }); await fixture(page, 'TARGET_REQUIRED')
  await page.goto(`${base}/learning/${id}/setup`)
  await expect(page.getByRole('spinbutton')).toHaveValue('5')
  await expect(page.getByText('时区', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toHaveCount(0)
  const date = page.getByRole('button', { name: '截止日期', exact: true })
  const blackout = page.getByRole('button', { name: '不可安排的日期', exact: true })
  const box = await date.boundingBox(), second = await blackout.boundingBox()
  expect(Math.abs(box!.width - second!.width)).toBeLessThan(1)
  expect(Math.abs(box!.y - second!.y)).toBeLessThan(1)
  expect(box!.x + box!.width).toBeLessThan(second!.x)
  await blackout.click()
  const panel = page.getByRole('dialog', { name: '不可安排的日期' })
  const panelBox = await panel.boundingBox()
  expect(Math.abs(panelBox!.x + panelBox!.width - second!.x - second!.width)).toBeLessThan(2)
  await page.locator('.calendar-day:not(:disabled)').nth(1).click(); await page.locator('.calendar-day:not(:disabled)').nth(2).click()
  await expect(blackout).toContainText('已选 2 天')
  await page.screenshot({ path: info.outputPath('goal-calendar.png') })
  await page.getByRole('button', { name: '完成', exact: true }).click()
  await page.getByRole('spinbutton').fill('6.5')
  await page.getByRole('combobox', { name: '自评基础' }).click()
  await page.getByRole('option', { name: '基础薄弱', exact: true }).click()
  await page.getByPlaceholder('例如：高数期末考试').fill('')
  await page.getByRole('button', { name: /确认目标/ }).click()
  await expect(page.getByText('请填写一个具体的学习目标。')).toBeVisible()
  await noOverflow(page)
})

test('library failure does not prevent hydration; none association still permits file selection', async ({ page }) => {
  const { control, project } = await fixture(page, 'SOURCES_REQUIRED'); control.assetFails = true
  await page.goto(`${base}/learning/${id}/setup`)
  await expect(page.getByPlaceholder('例如：变量、函数、DOM 事件和异步编程。')).toHaveValue('变量和函数')
  await expect(page.getByText('资料库中还没有文件', { exact: false })).toHaveCount(0)
  await page.getByRole('combobox', { name: '关联知识库', exact: true }).click()
  await expect(page.getByRole('option', { name: /JS学习/ })).toBeVisible()
  await page.keyboard.press('Escape')
  control.assetFails = false
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await page.getByRole('checkbox', { name: '学习资料.md', exact: true }).check()
  await expect(page.getByRole('checkbox', { name: '处理中资料.md' })).toBeDisabled()
  await page.getByRole('button', { name: '加载更多文件' }).click()
  await expect(page.getByRole('checkbox', { name: '附加资料.md' })).toBeVisible()
  await step(page, 0); await step(page, 1)
  await expect(page.getByRole('checkbox', { name: '学习资料.md', exact: true })).toBeChecked()
  expect(project.sourcesDraft.knowledgeBaseId).toBeNull()
  expect(project.sourcesDraft.assets[0].assetId).toBe('学习资料')
  expect(control.assetLimits.every(limit => limit <= 100)).toBe(true)
})

test('all editable stages retain drafts across navigation and refresh', async ({ page }) => {
  const { project } = await fixture(page)
  await page.goto(`${base}/learning/${id}/setup`)
  await page.getByRole('spinbutton').nth(1).fill('42')
  await step(page, 4); await page.getByRole('textbox', { name: '任务名称' }).fill('修改后的任务')
  await step(page, 2); await page.getByRole('textbox', { name: '知识点名称' }).fill('修改后的知识点')
  await step(page, 1); await page.getByPlaceholder('例如：变量、函数、DOM 事件和异步编程。').fill('离开前刚输入的内容')
  await step(page, 0); await page.getByPlaceholder('例如：高数期末考试').fill('草稿目标')
  await step(page, 1)
  await page.getByRole('button', { name: '返回学习项目' }).click()
  await page.getByRole('button', { name: '继续准备', exact: true }).click()
  await expect(page.getByPlaceholder('例如：变量、函数、DOM 事件和异步编程。')).toHaveValue('离开前刚输入的内容')
  await page.reload()
  await expect(page.getByPlaceholder('例如：变量、函数、DOM 事件和异步编程。')).toHaveValue('离开前刚输入的内容')
  expect(project.targetDraft.examName).toBe('草稿目标')
  expect(project.scopeCandidate.nodes[0].title).toBe('修改后的知识点')
  expect(project.planCandidate.tasks[0].title).toBe('修改后的任务')
  expect(project.resourceConfigDraft.questionCount).toBe(42)
})

test('failed save survives refresh, retry sends latest input and error never leaks Axios text', async ({ page }) => {
  const { control, project } = await fixture(page, 'TARGET_REQUIRED'); control.saveFails = true
  await page.goto(`${base}/learning/${id}/setup`)
  await page.getByPlaceholder('例如：高数期末考试').fill('网络失败时的草稿')
  await expect(page.getByRole('button', { name: '重试保存' })).toBeVisible()
  await page.reload()
  await expect(page.getByPlaceholder('例如：高数期末考试')).toHaveValue('网络失败时的草稿')
  await expect(page.getByRole('button', { name: '重试保存' })).toBeVisible()
  control.saveFails = false
  await page.getByRole('button', { name: '重试保存' }).click()
  await expect.poll(() => project.targetDraft.examName).toBe('网络失败时的草稿')
  control.confirmError = '截止日期不能早于今天，请重新选择。'
  await page.getByRole('button', { name: /确认目标/ }).click()
  await expect(page.getByRole('alert')).toContainText(control.confirmError)
  await expect(page.getByText('Request failed', { exact: false })).toHaveCount(0)
  await noOverflow(page)
})

test('diagnosis loading is inline, does not show skipped state, and answers survive refresh', async ({ page }, info) => {
  const { control } = await fixture(page, 'DIAGNOSTIC_REQUIRED')
  await page.goto(`${base}/learning/${id}/setup`)
  await expect(page.getByText('已跳过本次诊断', { exact: false })).toHaveCount(0)
  await page.getByRole('button', { name: '生成诊断题目', exact: true }).click()
  await expect(page.locator('.step-card .generation-state')).toBeVisible()
  await expect(page.getByText('已跳过本次诊断', { exact: false })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '暂时跳过诊断' })).toHaveCount(0)
  await page.screenshot({ path: info.outputPath('diagnosis-loading.png') })
  control.releaseJob = true
  await expect(page.getByText('1. 函数的作用是什么？')).toBeVisible()
  await page.getByRole('radio', { name: '复用逻辑' }).check()
  await step(page, 2); await step(page, 3); await page.reload()
  await expect(page.getByRole('radio', { name: '复用逻辑' })).toBeChecked()
})

test('create and edit share compact appearance picker; edit has no knowledge association or archive action', async ({ page }, info) => {
  const { project, control } = await fixture(page)
  await page.goto(`${base}/learning`)
  await expect(page.getByRole('button', { name: '归档', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '修改项目', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '修改学习项目' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '初始知识库' })).toHaveCount(0)
  await page.getByLabel('项目名称', { exact: true }).fill('改名后的学习项目')
  await page.getByRole('button', { name: '选择项目图标和颜色' }).click()
  await page.getByRole('button', { name: 'flask', exact: true }).click()
  await page.getByRole('button', { name: '紫色', exact: true }).click()
  await page.screenshot({ path: info.outputPath('project-appearance.png') })
  await page.getByRole('button', { name: '完成', exact: true }).click()
  await page.getByRole('button', { name: '保存修改', exact: true }).click()
  await expect(page.getByRole('heading', { name: '改名后的学习项目' })).toBeVisible()
  expect(project.icon).toBe('flask'); expect(project.iconColor).toBe('#8b5cf6')
  expect(control.requests.find(req => req.path === endpoint && req.method === 'PATCH')?.body).not.toHaveProperty('knowledgeBaseId')
  await page.getByRole('button', { name: '新建学习项目', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '新建学习项目' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '初始知识库' })).toContainText('不关联知识库')
})

test('mobile and dark controls remain within the content area', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 }); await fixture(page, 'TARGET_REQUIRED')
  await page.goto(`${base}/learning/${id}/setup`)
  await expect(page.getByPlaceholder('例如：高数期末考试')).toBeVisible()
  await page.getByRole('button', { name: '切换主题', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await noOverflow(page)
  expect((await page.getByPlaceholder('例如：高数期末考试').boundingBox())!.width).toBeGreaterThan(220)
  await page.getByRole('button', { name: '不可安排的日期', exact: true }).click()
  const rect = await page.getByRole('dialog', { name: '不可安排的日期' }).boundingBox()
  expect(rect!.x).toBeGreaterThanOrEqual(0); expect(rect!.x + rect!.width).toBeLessThanOrEqual(390)
  await page.screenshot({ path: info.outputPath('mobile-dark.png') })
})
