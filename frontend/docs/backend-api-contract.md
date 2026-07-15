# 学生端后端接口契约

> 适用范围：新对话、资料库、智能学习及其详情页。
>
> Mock Generator 不是后端实现参考。后端以本文的请求、响应、状态和权限约束为准。

## 1. 通用约束

基础地址由 `VITE_API_BASE_URL` 配置。JSON 接口统一返回：

```ts
type ApiResponse<T> = {
  code: string
  message: string
  data: T
  requestId?: string
}
```

- 当前实体 id 使用不超过 JavaScript 安全整数范围的整数。
- 时间使用 ISO 8601，例如 `2026-07-15T10:30:00+08:00`。
- 枚举使用英文稳定值，中文只作为前端展示文案。
- 创建、答题和学习行为接口接受 `clientRequestId`，后端必须保证幂等。
- 后端必须根据当前登录用户校验资料库、会话和学习项目权限，不能信任前端提交的用户 id 或角色。
- 推荐使用 `HttpOnly + Secure + SameSite` Cookie。若暂时使用 Bearer Token，认证失败统一返回 `401`。

错误码建议：

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 参数错误 |
| 401 | `UNAUTHENTICATED` | 未登录或凭证失效 |
| 403 | `FORBIDDEN` | 无资源权限 |
| 404 | `NOT_FOUND` | 资源不存在 |
| 409 | `STATE_CONFLICT` | 当前状态不允许操作或幂等冲突 |
| 422 | `GENERATION_REJECTED` | 资料不可用或生成条件不满足 |
| 500 | `INTERNAL_ERROR` | 服务异常 |
| 503 | `AI_SERVICE_UNAVAILABLE` | AI/解析/判题服务暂不可用 |

前端收到错误后展示错误和重试，不会切换 Mock，也不会在本地伪造成功结果。

## 2. 登录与权限

### 登录

`POST /api/user/login`

```ts
type LoginRequest = {
  username: string
  password: string
}

type LoginResult = {
  token?: string
  user: {
    id: number
    username: string
    nickname: string | null
    avatar: string | null
  }
}
```

正式权限由后端执行。前端保存的用户信息只控制界面展示，不构成授权依据。

## 3. 对话与消息

### 会话列表

`GET /api/conversation/list`

### 创建会话

`POST /api/conversation/create`

```ts
type CreateConversationRequest = {
  title?: string
  kbId?: number | null
  conversationType: 'general' | 'learning-setup' | 'learning-tutor'
  learningProjectId?: number | null
  learningProjectName?: string
}
```

学习方案制定和学习助教会话必须由后端保存 `learningProjectId`。不能只依赖前端路由或 Storage 维持关联。

### 更新与删除

- `PUT /api/conversation/{id}`：更新标题、置顶、资料库和学习项目关联。
- `DELETE /api/conversation/{id}`：删除当前用户有权限的会话。
- `GET /api/conversation/{id}/messages`：按创建时间返回消息及版本字段。

### 流式回答

`POST /api/chat/stream`

```ts
type ChatStreamRequest = {
  conversationId: number
  question: string
  model?: string
  kbId?: number | null
  fileContext?: string
  history?: Array<{ role: string; content: string }>
  parentId?: number | null
  turnId?: string
  qVersion?: number
  aVersion?: number
  isRegenerate?: boolean
  editMsgId?: number | null
  files?: string
}
```

当前兼容实现会先调用 `POST /api/doc/extract`（`multipart/form-data`，字段 `file`），再把提取文本放入 `fileContext`。该接口返回 `{ data: string }`。更适合正式环境的后续版本是上传后返回 `attachmentId`，流式请求只提交 `attachmentIds`，避免解析正文经过浏览器往返；切换该方案时前后端需同步升级契约，不能静默改变字段。

SSE 事件类型固定为：

```text
start   创建回答消息并返回 messageId
delta   增量文本
sources 文档引用
done    最终消息、耗时和 token 信息
error   稳定错误码和可展示信息
```

后端负责保存用户问题、最终回答、版本关系和引用。前端只保存当前流式展示状态。

## 4. 资料库与文件

### 资料库

- `GET /api/kb/list`
- `GET /api/kb/{id}`
- `POST /api/kb/create`
- `PUT /api/kb/{id}`
- `DELETE /api/kb/{id}`

资料库响应需要包含：

```ts
type KnowledgeBase = {
  id: number
  name: string
  description?: string
  icon?: string
  color?: string
  documentCount: number
  mindMapCount: number
  availableForAi: boolean
  createTime: string
  updateTime: string
}
```

`availableForAi` 必须由后端根据可用文档和解析状态计算。

### 文件

- `GET /api/doc/list?kbId={id}`
- `POST /api/doc/upload`：`multipart/form-data`，包含 `kbId` 和 `file`。
- `GET /api/doc/status/{id}`
- `DELETE /api/doc/{id}`
- `GET /api/doc/download/{id}`
- `POST /api/doc/extract`：当前聊天附件兼容接口，返回提取文本；Mock 不调用该接口。

文件正式状态统一为：

```text
uploading → uploaded → parsing → ready
                              ↘ failed
```

上传成功不等于 AI 可用。只有 `ready` 文件可以进入检索、对话和学习生成上下文。后端负责文件原件、解析、切块、向量化和失败重试；前端不保存文件原件。

### 资料资源聚合接口

当前资料库主页面使用以下统一资源接口，覆盖上传文件和 AI 生成资源：

- `GET /api/library/resources?libraryId={id}`；不传 `libraryId` 返回当前用户全部资源。
- `POST /api/library/resources/upload`：`multipart/form-data`，字段 `file`、可选 `libraryId`、可选 `projectId`。
- `PATCH /api/library/resources/{id}`：请求 `{ name }`。
- `POST /api/library/resources/{id}/move`：请求 `{ libraryId: number | null }`。
- `POST /api/library/resources/{id}/retry`：重新触发解析，返回更新后的资源。
- `GET /api/library/resources/{id}/download`：返回 Blob/文件流，并设置正确文件名。
- `DELETE /api/library/resources/{id}`。

资源状态固定为 `waiting | processing | ready | failed`，前端 Repository 负责映射中文展示。失败时返回 `errorMessage`。`POST /api/doc/*` 面向知识库文档处理，`/api/library/resources/*` 面向学生资料库聚合视图；后端可以复用同一底层实体，但两个接口返回结构必须各自稳定。

## 5. 智能学习

### 项目与草稿

- `GET /api/learning/projects`
- `GET /api/learning/projects/{id}`
- `POST /api/learning/projects/drafts`

项目详情需要一次返回页面所需的画像、阶段、任务、题目摘要、资源、进度和状态。需要分页的数据另设列表接口。

项目、任务和资源接口使用英文状态，API Repository 会映射为当前中文界面：

| 实体 | 正式接口状态 |
| --- | --- |
| 项目 | `draft`、`configuring`、`ready`、`in_progress`、`completed` |
| 任务 | `not_started`、`in_progress`、`completed` |
| 学习资源 | `not_selected`、`generating`、`ready`、`failed` |
| 错题 | `needs_review`、`mastered` |
| 普通练习题组 | `pending`、`answering`、`submitted` |
| 错题巩固题组 | `pending`、`answering`、`completed` |

`GET /api/learning/projects` 当前返回项目数组；若后端改为分页，前端 Repository 也必须同步改为读取 `items`，不能只改后端包裹结构。

### 学习画像生成

`POST /api/learning/profile-jobs`

```ts
type LearningProfileRequest = {
  libraryId: number
  text: string
  currentProfile?: LearningProfileData
  source?: string
  subject?: string
  knowledgeTags?: string[]
  supplementalRequirement?: string
}
```

后端校验资料库权限和解析状态后创建任务，返回 `jobId`。AI 返回内容必须经过结构化校验，不能把未解析文本直接作为业务实体保存。

### 学习画像确认稿

`POST /api/learning/profile-confirmations`

请求包含用户已编辑确认的 `profile`、学习目标、题量、难度策略和关联资料。后端基于权威资料与 AI 生成确认稿，返回 `{ content: string }`。确认稿是学习方案生成输入之一，不能由正式前端规则拼装。

### 学习方案生成

`POST /api/learning/plan-jobs`

请求包含已确认的目标、画像、学习周期、偏好、题量和资料库 id。后端负责：

1. 保存用户确认版本。
2. 创建生成任务。
3. 调用 AI 生成结构化阶段、任务、题目和资源需求。
4. 校验题目和任务引用完整性。
5. 在同一事务或可恢复流程中保存项目结果。
6. 返回包含 `projectId` 的成功任务结果。

### 生成任务查询

`GET /api/learning/generation-jobs/{jobId}`

```ts
type GenerationJob<T> = {
  jobId: string
  status: 'pending' | 'running' | 'succeeded' | 'failed' | 'cancelled'
  progress?: number
  result?: T
  errorCode?: string
  errorMessage?: string
}
```

前端可以轮询或改用 SSE/WebSocket。刷新时只需保留 `jobId`，权威任务状态在后端。

## 6. 学习行为与进度

`POST /api/learning/projects/{projectId}/activities`

```ts
type LearningActivityRequest = {
  projectId: number
  taskId: number
  eventType: 'start' | 'reading' | 'action' | 'complete'
  progress?: number
  secondsDelta?: number
  action?: string
  clientRequestId: string
}
```

- 前端上报可验证的行为，不直接提交“项目进度百分比”。
- 后端去重并校验时长、任务状态和前置条件。
- 后端计算并返回最新任务状态和项目进度。
- 当前 Mock 的“阅读 80% + 5 秒”等阈值只是演示规则，后端应根据产品规则配置。

## 7. 题目、答题与错题

### 题目生成

题目由后端 AI/题库服务生成并保存，至少包含：题目 id、版本、题型、题干、选项、难度、知识点、来源、解析、评分方式和生成批次。

- `POST /api/learning/projects/{projectId}/tasks/{taskId}/adaptive-practice-jobs`
- `POST /api/learning/projects/{projectId}/mistake-review-jobs`

追加练习、错题变式和难度提升都可能调用 AI，统一返回异步 `GenerationJob<{ projectId: number }>`。前端查询任务成功后重新获取项目详情，不在正式 Store 中复制或改写原题。

正确答案、隐藏测试和完整评分规则不应在答题前返回前端。代码题必须进入隔离判题环境，不能复制前端字符串匹配规则。

### 提交答案

`POST /api/learning/projects/{projectId}/answers`

```ts
type SubmitAnswerRequest = {
  projectId: number
  exerciseId: number
  answer: string
  language?: string
  clientRequestId: string
}

type AnswerResult = {
  correct: boolean
  score?: number
  feedback?: string
  explanation: string
  correctAnswer: string
  taskProgress: number
  projectProgress: number
}
```

后端保存答题版本、评分结果和耗时，并据此更新错题、掌握状态和学习进度。

### 错题与复习

- `GET /api/learning/projects/{projectId}/mistakes`
- `POST /api/learning/projects/{projectId}/mistake-review-jobs`
- `POST /api/learning/projects/{projectId}/mistakes/{mistakeId}/mastery`

错题生成、相似题、掌握状态和复习间隔由后端负责。前端只展示并提交用户操作。

## 8. 学习资源

- `GET /api/library/resources?libraryId={id}`
- `POST /api/library/resources/upload`
- `DELETE /api/library/resources/{id}`
- `GET /api/learning/projects/{projectId}/resources`
- `POST /api/learning/projects/{projectId}/resource-jobs`
- `GET /api/learning/projects/{projectId}/resources/{resourceId}/download`

AI 生成资源必须关联项目、资料库、阶段或任务，并返回稳定资源 id。思维导图、Markdown、PPT 等生成结果保存到后端文件服务，前端不得只保存本地生成记录。

项目详情必须返回真实资源数组。前端不会在资源缺失时自行补造“学习方案”或“思维导图”。资源为 `generating` 时禁止预览和下载，为 `failed` 时展示后端 `errorMessage` 并允许重新调用资源生成任务。

## 9. Mock 与后端交接

后端开发时只参考：

- `src/types/contracts/*` 中的共享字段。
- `src/repositories/*` 中 API Repository 使用的端点。
- 本文中的状态、权限、幂等和持久化约束。

后端不参考：

- `src/mock/student.ts` 的固定数据。
- `src/mock/generators/*` 的文本规则、题目拼装和原型评分。
- Mock 的 `sessionStorage` key。
- 页面中的动画延迟、打字效果和演示计时器。

API 未实现或返回错误时，前端会显示真实失败。后端可以据此逐个完成接口，不会被 Mock 自动降级掩盖。
