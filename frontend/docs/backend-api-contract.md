# 学生端前后端零猜测交接契约

> 版本：1.3
>
> 更新日期：2026-07-18
>
> 适用范围：新对话、资料库、智能学习、PPT/电子表格生成、语音输入、拍照/图片上传及其关联的思维导图、登录与用户设置。
>
> 本文以当前 Spring Boot 后端和当前前端 API Repository 为基线。后端不得参考 Mock Generator 设计正式算法。

## 0. 使用方式

### 0.1 唯一权威来源

接口实现发生冲突时，按以下顺序判断：

1. 本文。
2. frontend/src/types/contracts 中的共享类型。
3. frontend/src/repositories 中 API Repository 的实际调用。
4. backend/src/main/java 中已经存在的 Controller、DTO、VO 和 Service。
5. student-frontend-spec.md 只说明页面行为，不作为字段定义来源。
6. frontend/src/mock 只用于页面演示，不是后端算法、数据库或评分依据。

本文已经明确标注现有实现与目标实现的差异。后端开发不能根据 Mock 数据补字段，也不能在接口未实现时返回伪造成功。

### 0.2 实施状态

| 标记 | 含义 | 后端动作 |
| --- | --- | --- |
| KEEP | 现有后端已实现且前端可兼容 | 保留路径和语义，只补统一错误处理 |
| EXTEND | 现有接口可复用，但字段或行为不足 | 在现有 Controller、Service、表结构上扩展 |
| NEW | 当前后端没有该能力 | 按本文新增 |
| DEFER | 当前页面保持禁用或不调用 | 首期不实现 |

### 0.3 交付范围

首期必须完成：

- 用户认证和当前用户信息。
- 会话、历史消息和聊天流。
- 知识库、文档上传、解析、状态、删除和下载。
- 资料库聚合资源接口。
- 学习项目、画像、确认稿、方案、任务、题目、答题、错题和资源生成。
- 学习助教复用会话与聊天接口。
- 麦克风/上传音频转文字、拍照/图片上传、图片 OCR/题目识别和聊天多模态引用。
- PPT 配置、大纲、异步生成、任务恢复、预览、下载和自动归档资料库。
- 电子表格基于对话、附件、知识库和项目上下文直接异步生成、预览、下载并自动归档资料库。
- 资料库、知识库、学习资源包和对话生成文件的统一只读在线预览。

首期不包含：

- 选中多个资料后直接创建聊天上下文。
- 视频上传、识别或抽帧。

### 0.4 前端清理同步说明（2026-07-18）

- 本轮只删除前端零引用空壳、旧版页面、重复实现、示例测试和构建缓存，不修改后端源码、数据库或本契约中的接口交付范围。
- 旧知识库页面已经由 `/library`、`/library/:id` 和 `/resources/:resourceId/preview` 替代；后端仍须实现本文约定的知识库、文档和资料库聚合接口。
- `src/api/document.ts` 与 `src/repositories/document.ts` 仍被当前试卷分析和附件链路使用，相关 `/api/doc/**` 接口不因旧 `documentStore` 删除而废弃。
- 公共资源中心、试卷分析、思维导图和后台管理路由仍保留，对应现有接口继续有效。
- 后端生成实现只应参考当前路由、`src/repositories` 和 `src/types/contracts`，不得参考已删除的 `legacy` 或旧知识库页面推导字段和状态。

## 1. 现有后端复用矩阵

| 模块 | 当前后端 | 目标状态 | 说明 |
| --- | --- | --- | --- |
| 用户登录、注册、信息、设置 | 已存在 | KEEP | 保留 /api/user 路径和 Bearer JWT |
| 模型列表与模型选择 | 部分存在 | EXTEND | 复用 system_config，新增前端需要的模型列表接口，聊天服务必须使用已校验模型 |
| 会话创建、列表、消息、更新、删除 | 已存在 | EXTEND | 增加学习项目关联、类型和置顶字段；更新接口必须返回更新后的对象 |
| AI 聊天 SSE | 已存在 | EXTEND | 保留当前文本事件协议，补中止、超时、错误 HTTP 状态和模型校验 |
| 知识库 CRUD | 已存在 | EXTEND | 增加 availableForAi，响应改用 VO，不返回 userId、逻辑删除字段 |
| 文档上传、解析、状态、下载 | 已存在 | EXTEND | 保留数字状态 0/1/2；扩展 DOC、Excel、PPT、ZIP，资料库聚合层映射为英文状态 |
| 思维导图 CRUD 与 AI 生成 | 已存在 | KEEP | 继续复用 mind_map 表和 /api/mindmap |
| 公共资源中心 | 已存在 | KEEP | 不等于学生资料库聚合接口，可复用文件存储逻辑 |
| 资料库聚合资源 | 不存在 | NEW | 聚合 document、mind_map、learning_resource |
| 智能学习 | 不存在 | NEW | 新增项目、任务、题目、答题、错题、资源与生成任务 |
| 语音、图片与识别任务 | 不存在 | NEW | 新增 media_asset、麦克风/上传音频转写和图片识别任务；聊天只引用媒体资产 ID |
| PPT 生成 | 不存在 | NEW | 新增 presentation 聚合、异步任务和文件资产；讯飞只作为后端内部 Provider，不向前端暴露 |
| 电子表格生成 | 不存在 | NEW | 新增对话直达生成任务、只读工作簿预览、XLSX 下载和资料库资源 |

## 2. 通用协议

### 2.1 基础地址与内容类型

- 前端通过 VITE_API_BASE_URL 配置基础地址。
- JSON 请求使用 Content-Type: application/json;charset=UTF-8。
- 上传使用 multipart/form-data，不能手工拼接 boundary。
- SSE 使用 Accept: text/event-stream，响应 Content-Type: text/event-stream;charset=UTF-8。
- 下载返回文件流，不使用 Result 包裹。
- 开发环境 CORS 允许 http://localhost:5173；生产环境从配置读取明确前端域名，禁止 allowedOriginPatterns("*")。
- CORS 方法固定为 GET、POST、PUT、PATCH、DELETE、OPTIONS，请求头至少允许 Authorization、Content-Type、Accept。
- 当前使用 Bearer Token，不依赖跨域 Cookie；allowCredentials 默认 false。

### 2.2 认证

当前项目采用 Bearer JWT，首期不切换 Cookie：

~~~http
Authorization: Bearer <token>
~~~

- token 有效期沿用现有实现：7 天。
- 后端只接受 JWT 中的 userId，不接受请求体中的 userId 作为权限依据。
- 兼容期可以继续读取旧 token 请求头，但新前端只发送 Authorization。
- 所有 /api/** 默认需要认证，以下接口除外：
  - POST /api/user/register
  - POST /api/user/login
  - POST /api/user/forgot-password
  - POST /api/user/reset-password
- 退出登录当前为前端清除 token，不新增退出接口。
- JWT 密钥和 AI API Key 必须从环境变量或非提交配置读取，不得硬编码或提交真实值。

### 2.3 统一响应

现有后端 Result 的 code 是数字。正式契约继续使用数字，避免无必要重写：

~~~ts
type ApiResult<T> = {
  code: number
  message: string
  data: T | null
  requestId?: string
  errorCode?: string
}
~~~

成功示例：

~~~json
{
  "code": 200,
  "message": "success",
  "data": {}
}
~~~

错误示例：

~~~json
{
  "code": 403,
  "message": "无权访问该学习项目",
  "data": null,
  "errorCode": "FORBIDDEN",
  "requestId": "9dd225de-50a8-4be0-b7d0-71ddd89a3b72"
}
~~~

强制规则：

- HTTP 状态码必须与业务失败一致。不能发生 HTTP 200、body.code=401/403/404/500。
- 2xx 才表示请求被服务端接受；4xx/5xx 必须让 Axios 或 fetch 进入失败分支。
- Void 接口返回 data: null。
- SSE 在建立连接前发生校验错误时直接返回 4xx JSON；建立连接后发生错误才发送 error 事件。
- 当前 GlobalExceptionHandler 和 AuthInterceptor 需要改为设置真实 HTTP 状态，这是现有后端 P0 兼容修正。

### 2.4 错误码

| HTTP | errorCode | 触发条件 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 字段缺失、格式或长度错误 |
| 401 | UNAUTHENTICATED | token 缺失、无效或过期 |
| 403 | FORBIDDEN | 当前用户没有实体权限 |
| 404 | NOT_FOUND | 实体不存在 |
| 409 | STATE_CONFLICT | 状态不允许操作、重复提交冲突 |
| 413 | FILE_TOO_LARGE | 文件超过服务端限制 |
| 415 | UNSUPPORTED_FILE_TYPE | 文件类型不支持 |
| 422 | GENERATION_REJECTED | 资料不可用、生成条件不满足 |
| 429 | TOO_MANY_REQUESTS | 频率限制 |
| 500 | INTERNAL_ERROR | 未分类服务错误 |
| 503 | AI_SERVICE_UNAVAILABLE | AI、Embedding、ES 或判题服务不可用 |

禁止把“无权限”和“不存在”统一抛为 IllegalArgumentException 后返回 400。后端需要使用可区分的业务异常。

### 2.5 ID、时间、字段和列表

- 普通实体 ID：Java Long，JSON number，必须小于等于 9007199254740991。
- 生成任务 ID：UUID 字符串。
- 资料库聚合资源 ID：字符串，格式见 7.2。
- 时间：ISO 8601，必须带时区，例如 2026-07-16T10:30:00+08:00。
- JSON 字段统一 camelCase。
- 未分页列表按当前前端要求直接返回数组，不包 items。
- GET /api/learning/projects 当前必须返回数组。未来分页需要前后端同时升级版本。
- 枚举值区分大小写，不能返回中文状态再依赖前端猜测。本文明确标记的中文展示枚举除外。

### 2.6 幂等与并发

以下请求必须携带 clientRequestId，并以 userId + operation + clientRequestId 建立唯一约束：

- 提交答案。
- 上报学习行为。
- 后续新增的支付或不可逆操作。

重复 clientRequestId：

- 请求内容相同：返回第一次成功结果。
- 请求内容不同：返回 409 IDEMPOTENCY_CONFLICT。

生成任务按钮由前端禁止重复点击，后端仍需按用户、项目、任务类型做运行中任务去重。

### 2.7 权限

每个实体操作都必须验证当前用户：

- conversation.userId。
- knowledge_base.userId。
- document.userId 及其 knowledge_base.userId。
- mind_map.userId。
- learning_project.userId。
- 所有阶段、任务、题目、答案、错题、资源必须通过所属 projectId 反查用户。

前端路由参数、knowledgeBaseId、projectId、conversationId 均不构成授权。

## 3. 用户与认证

### 3.1 数据类型

~~~ts
type LoginRequest = {
  username: string
  password: string
  isAdmin?: boolean
}

type RegisterRequest = {
  username: string
  password: string
  nickname?: string
}

type AuthUserDto = {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  role: string | null
  lastLoginTime: string | null
  token: string
}

type UserInfoDto = {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  status: number
  lastLoginTime: string | null
  createTime: string
}

type UserSettingsDto = {
  theme: "light" | "dark"
  defaultModel: string
}
~~~

登录和注册保持现有平铺结构：token 与用户字段位于同一个 data 对象，不额外包 user。

### 3.2 接口

| 状态 | 方法与路径 | 请求 | 成功 data |
| --- | --- | --- | --- |
| KEEP | POST /api/user/register | RegisterRequest | AuthUserDto |
| KEEP | POST /api/user/login | LoginRequest | AuthUserDto |
| KEEP | GET /api/user/info | 无 | UserInfoDto |
| KEEP | PUT /api/user/update | { nickname?: string, avatar?: string } | null |
| KEEP | GET /api/user/settings | 无 | UserSettingsDto |
| KEEP | PUT /api/user/settings | Partial<UserSettingsDto> | null |
| KEEP | POST /api/user/forgot-password | { username: string } | null |
| KEEP | POST /api/user/reset-password | { username: string, verifyCode: string, newPassword: string } | null |

校验规则：

- username 去除首尾空格后不能为空，最长 50。
- password 登录时不能为空；注册和重置时长度 6 至 72。
- nickname 最长 100。
- isAdmin=true 时必须验证 user.role=admin。
- 忘记密码接口不能泄露账号是否存在；正式环境统一返回已受理。
- 当前 reset-password 只读取 verifyCode 但没有验证，正式上线前必须接入一次性验证码或管理员审批记录，否则不得开放。

### 3.3 模型列表

#### GET /api/config/model

状态：EXTEND。现有后端只有 /api/config/all，本接口从 system_config 读取允许学生使用的模型，不返回 API Key 或内部供应商配置。

~~~ts
type ModelInfoDto = {
  name: string
  label: string
  displayName: string
  description: string
  enabled: boolean
  capabilities: Array<"chat" | "reasoning" | "vision">
}
~~~

成功：ApiResult<ModelInfoDto[]>。只返回 enabled=true，按后台配置顺序排列。

- UserSettingsDto.defaultModel 必须存在于该列表。
- POST /api/chat/stream 的 model 必须来自该列表；缺失时使用用户默认模型。
- 前端生产环境不得在接口失败时伪造模型列表。

## 4. 会话与消息

### 4.1 会话类型

~~~ts
type ConversationType =
  | "general"
  | "learning-setup"
  | "learning-tutor"
~~~

- general：普通对话。
- learning-setup：收集画像和制定学习方案。
- learning-tutor：学习执行过程中的 AI 助教。

### 4.2 会话 DTO

~~~ts
type ConversationDto = {
  id: number
  title: string | null
  kbId: number | null
  kbName?: string | null
  isPinned: boolean
  messageCount: number
  totalTokens?: number
  updateTime: string
  createTime: string
  projectId: number | null
  learningProjectName?: string | null
  conversationType: ConversationType
}

type CreateConversationRequest = {
  title?: string
  kbId?: number | null
  projectId?: number | null
  learningProjectName?: string
  conversationType?: ConversationType
}

type UpdateConversationRequest = {
  title?: string
  isPinned?: boolean
  knowledgeBaseId?: number | null
  projectId?: number | null
  learningProjectName?: string
  conversationType?: ConversationType
}
~~~

兼容规则：

- 创建请求沿用现有字段 kbId。
- 更新请求当前前端使用 knowledgeBaseId；后端将其写入 conversation.kb_id。
- 响应沿用 kbId，前端 Repository 会映射为 knowledgeBaseId。
- learningProjectName 只用于兼容展示，后端有 projectId 时应从项目表读取权威名称。
- title 缺失或空白时后端使用“新对话”，最长 200。
- conversationType 缺失时默认为 general。
- learning-setup 和 learning-tutor 必须提供有效 projectId。
- general 的 projectId 必须为 null。
- `/learning/new` 是纯前端初始路由。用户首次提交目标后，前端先创建 draft 项目，再创建 `conversationType=learning-setup` 的会话，并进入 `/learning/setup/:conversationId?projectId=:projectId`。
- 后端不需要实现 `/learning/setup/*` 页面路由，但必须持久化该会话中的学习画像 Card 和确认文档消息，保证 `GET /api/message/conversation/{conversationId}` 可以恢复方案制定历史。

### 4.3 接口

#### POST /api/conversation/create

状态：EXTEND。

请求：CreateConversationRequest。

成功：ApiResult<ConversationDto>。

后端步骤：

1. 校验 kbId 和 projectId 归属。
2. 校验 conversationType 与 projectId 组合。
3. 创建会话，messageCount=0、totalTokens=0、isPinned=false。
4. 返回 ConversationDto，不返回 userId 和内部 status。

#### GET /api/conversation/list

状态：EXTEND。

成功：ApiResult<ConversationDto[]>，按 isPinned DESC、updateTime DESC 排序。

#### GET /api/conversation/{id}/messages

状态：KEEP。

~~~ts
type MessageRole = "user" | "assistant" | "system"

type MessageSourceDto = {
  docId: number
  docName: string
  chunkIndex: number
  content: string
  _score?: number
}

type MessageAttachmentDto = {
  name: string
  type: string
  size: number
}

type MessageDto = {
  id: number
  conversationId: number
  parentId: number | null
  role: MessageRole
  content: string
  sourceChunks: string | null
  model: string | null
  durationMs: number | null
  createTime: string
  turnId: string | null
  qVersion: number | null
  aVersion: number | null
  files: string | null
  kind: "learning-profile" | "learning-document" | "presentation" | "spreadsheet" | null
  learningData: string | null
  presentationData: string | null
  spreadsheetData: string | null
  artifacts: string | null
}

type LearningMessageData = {
  loading: boolean
  confirmed?: boolean
  profile?: LearningProfileData
  content?: string
  resourceId?: string | null
}
~~~

sourceChunks 必须是 JSON.stringify(MessageSourceDto[])；files 必须是 JSON.stringify(MessageAttachmentDto[])；learningData 和 presentationData 分别保存学习工作流卡与 PPT 生成前确认卡。所有生成文件统一使用 artifacts 保存 `ChatArtifactDto[]` JSON。spreadsheetData 仅用于兼容迁移前消息，新消息不得继续写入。无值时返回 null，不返回非法 JSON。

- `kind=learning-profile` 时，learningData 必须包含 `loading=false`、`confirmed` 和完整 profile。
- `kind=learning-document` 时，learningData 必须包含 `loading=false`、content 和确认稿 resourceId。
- `loading=true` 只用于前端即时生成占位；后端不得把中断的占位消息伪装成已完成结果。
- 同一个 setupId 重新生成画像或确认稿时更新该会话内最新的同类 Card，避免刷新后出现重复确认文档。

消息按 createTime ASC、id ASC 返回，只返回 status=1。

#### PUT /api/conversation/{id}

状态：EXTEND。

请求：UpdateConversationRequest，至少有一个字段。

成功：ApiResult<ConversationDto>。

现有后端返回 data:null，与当前前端 Repository 不兼容，必须改为返回更新后的 ConversationDto。

#### DELETE /api/conversation/{id}

状态：KEEP。

成功：ApiResult<null>。继续使用现有逻辑删除，并同时逻辑删除消息。

## 5. AI 对话流

### 5.1 请求

#### POST /api/chat/stream

状态：EXTEND。

~~~ts
type ChatStreamRequest = {
  conversationId: number
  question: string
  model?: string
  kbId?: number | null
  fileContext?: string
  history?: Array<{
    role: "user" | "assistant" | "system"
    content: string
  }>
  parentId?: number | null
  turnId?: string
  qVersion?: number
  aVersion?: number
  isRegenerate?: boolean
  editMsgId?: number | null
  files?: string
  mediaAssetIds?: string[]
  projectId?: number | null
  stageId?: number | string | null
  taskId?: number | string | null
  exerciseId?: number | string | null
  clientAction?: "presentation.create" | "spreadsheet.create"
}
~~~

规则：

- conversationId 必填并校验权限。
- 普通发送 question 必须非空；isRegenerate=true 时允许为空并从目标用户消息恢复。
- kbId 只作兼容，实际 RAG 知识库以 conversation.kbId 为准；两者冲突时返回 409。
- model 缺失时使用用户设置 defaultModel；必须校验模型白名单。
- history 只用于补充上下文，不能覆盖后端保存的权限和项目关联。
- fileContext 只承载第 6.4 节文档/Office/ZIP 的解析正文或音频转写文本，最大 200000 字符。
- files 必须是合法 MessageAttachmentDto[] JSON 字符串。
- mediaAssetIds 只承载第 18 节已上传图片或已转写音频资产 ID；最多 5 个，后端逐个校验所有权、状态和 conversationId 关联。
- 同一媒体同时出现在 files 元数据和 mediaAssetIds 时，以 mediaAssetIds 作为模型输入，files 只用于消息展示，禁止重复上传、重复识别或重复转写。
- 学习助教必须通过 conversation.projectId 加载权威项目上下文，不能只信任前端拼接的 tutorContext。
- stageId、taskId、exerciseId 只用于声明当前学习页面位置；提供时必须属于 conversation.projectId，任一不匹配返回 409 CONTEXT_MISMATCH。
- `presentation.create` 和 `spreadsheet.create` 是前端显式触发生成时使用的明确动作，不需要后端再次猜测功能类型；后端仍须校验 conversationId、conversation.projectId 和知识库权限。
- projectId 只用于前端声明当前页面上下文；后端必须与 conversation.projectId 交叉校验，冲突返回 409 CONTEXT_MISMATCH。
- 未传 clientAction 时，用户自然语言是否要生成 PPT 或电子表格由后端 AI 意图层结合当前问题和会话上下文判断。前端不做正式关键词判断；后端不得照抄 Mock 的正则表达式作为正式意图算法。
- 新对话欢迎页当前只展示“撰写或编辑、生成图片、生成 PPT、生成思维导图”四个入口；“查找资料”和“生成表格”不展示仅属于前端入口收敛，`spreadsheet.create`、电子表格任务及下载接口仍在交付范围内。

### 5.2 SSE 协议

本版本保留当前后端与当前前端都能处理的协议，不采用旧文档中的 start/delta/done：

#### 文本增量

未指定 event 等同于 event: message，data 是原始 UTF-8 文本：

~~~text
data: 这是

data: 一段回答

~~~

#### 完成事件

~~~text
event: finish
data: [{"docId":12,"docName":"继承与多态.pdf","chunkIndex":3,"content":"...","_score":0.82}]

~~~

- data 必须是 MessageSourceDto[] JSON。
- 无引用时发送 []。
- finish 后立即完成 SSE 响应。
- 前端当前通过流结束判断完成，并在结束后重新拉取消息，获取 messageId、durationMs 和 sourceChunks。

#### PPT 确认卡事件

快捷动作或后端 AI 判断用户需要创建 PPT 时，在文本增量之后、finish 之前发送一次：

~~~text
event: presentation-card
data: {"cardType":"presentation","view":"proposal","status":"draft","presentationId":"ppt_01J...","conversationId":102,"sourceMessageId":9081,"knowledgeBaseId":1,"projectId":null,"config":{"topic":"Java 多态","title":"Java 多态","pageCount":8,"templateId":"ink-focus","aspectRatio":"16:9","style":"academic","audience":"student","language":"zh-CN"}}

~~~

- data 必须是第 19.3 节 PresentationChatCardDto 的完整 JSON，不得只返回按钮文案或讯飞字段。正式聊天编排应先创建 draft 再发送事件，因此 proposal 必须带 `presentationId`；前端仅为迁移期无 id 响应保留“收到后立即调用创建草稿”的兼容分支。
- proposal 卡的 `knowledgeBaseId` 可由用户在“页数”旁直接选择或清空；后续创建草稿、生成大纲和最终资源必须沿用卡片当前值，不得回退为会话旧值。
- 同一回答最多发送一个 presentation-card；前端收到后把当前助手消息渲染为确认卡。
- 后端必须在完成 SSE 前保存该助手消息，kind=presentation，presentationData 为同一份卡片 JSON；sourceMessageId 必须等于该助手消息 id。
- 仅返回普通文本“请去生成 PPT”不算成功；刷新后 GET messages 必须仍能恢复同一张卡。

#### 统一附件事件

图片、思维导图、DOCX、PDF、XLSX、PPTX 等文件统一发送同一种附件事件。电子表格不再是特殊对话卡；PPT 只有生成前的确认卡是特例，生成结果仍发送附件事件：

~~~text
event: artifact
data: {"artifactId":"spreadsheet:sheet_01J...","jobId":"job_01J...","conversationId":102,"sourceMessageId":9082,"knowledgeBaseId":11,"projectId":7,"learningResourceId":null,"title":"考试成绩统计","fileName":"考试成绩统计.xlsx","fileType":"spreadsheet","format":"XLSX","mimeType":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","status":"generating","progress":48,"preview":{"kind":"spreadsheet","table":{"sheetName":"工作表 1","columns":["姓名","成绩"],"rows":[["张三",92]]}},"editable":false}

~~~

- 同一个 `artifactId` 可重复发送，前端按 id upsert；状态固定为 `queued | generating | ready | failed | cancelled`。
- ready 必须包含唯一 `resourceId`，该资源已经自动进入资料库；聊天卡与资料库不得保存两份文件。
- preview 仅包含对话内轻量预览数据，不得包含可执行 HTML、永久公开文件 URL 或 Base64 原文件。
- XLSX 可返回受限行列摘要，图片返回短期 URL，思维导图返回树结构，文档返回受限摘要；重型完整内容通过统一资源预览读取。
- XLSX 固定 `editable=false` 且不得返回 `editorRoute`；电子表格只允许统一只读预览和下载。支持编辑的类型才可返回 `editable=true/editorRoute`。
- 后端在完成 SSE 前把当前 artifacts 数组写入助手消息；任务后续状态变化必须更新同一 artifact，刷新 GET messages 可恢复。
- `projectId` 表示该对话所属学习项目；`learningResourceId` 仅表示需要更新的既有项目资源位，可空。项目 AI 对话生成文件即使没有 `learningResourceId`，ready 时也必须向项目 `resources[]` 新增条目。
- 生成信息不足时只发送普通追问，不创建空附件或前端配置表单。
- 迁移期前端仍兼容 `event: spreadsheet-card`，但后端新实现应发送 `event: artifact`；旧事件将在双方完成迁移后删除。

#### 错误事件

~~~text
event: error
data: AI 服务暂不可用，请稍后重试

~~~

- 建立流之前的参数、认证、权限错误必须返回标准 4xx JSON。
- 建立流之后的 AI 错误发送 error 并关闭连接。
- 错误事件不得包含堆栈、SQL、API Key 或供应商原始敏感响应。

### 5.3 持久化与停止

- 正常发送：保存用户消息，AI 完成后保存助手消息。
- PPT 意图回答：保存普通文本内容和结构化卡片；结构化字段不得拼进 content。
- 文件生成回答：普通 Markdown 保存在 content，附件生命周期保存在 artifacts；同一个 artifactId 只保留最新状态。
- 重新生成：保留旧版本，新助手消息递增 aVersion。
- 编辑问题：创建同 turnId 的新 qVersion，不覆盖原消息。
- 前端停止使用 AbortController 中断 HTTP。
- 后端监听 SseEmitter completion、timeout、error；客户端断开后必须取消上游 AI 订阅。
- 被中止的助手回答不保存为正常完成消息；已经保存的用户问题保留。
- SSE 超时配置为 120 秒，可配置，禁止 SseEmitter(0L) 无限占用。

### 5.3.1 单文件稳定重试

- `POST /api/chat/artifacts/{artifactId}/retry`：请求包含 `conversationId`、`sourceMessageId`、`projectId?`、`knowledgeBaseId?`、`learningResourceId?`、`resourceId?`、`clientRequestId`，返回同一 `ChatArtifactDto` 的最新状态。
- `GET /api/chat/artifacts/{artifactId}`：返回该附件任务的权威最新状态，用于前端轮询恢复。
- 重试必须保持 `artifactId` 不变；原附件已有 `resourceId` 时也必须保持不变，只更新原资源内容/版本。不得重新生成整轮聊天回答，也不得创建重复资料库或项目资源条目。
- `clientRequestId` 幂等；任务仍在运行时重复重试返回同一任务，状态冲突返回 409。

### 5.4 标题生成

#### POST /api/chat/generate-title

状态：KEEP。

请求：{ text: string }。

当前响应为 text/plain 原始标题字符串，不使用 ApiResult。前端当前主流程不依赖该接口；自动标题仍可在首次回答完成时由 ChatService 更新。

## 6. 知识库与文档

### 6.1 知识库 DTO

~~~ts
type KnowledgeBaseDto = {
  id: number
  name: string
  description?: string
  avatar?: string
  color?: string
  docCount: number
  chunkCount: number
  mindMapCount: number
  examAnalysisId?: number | null
  availableForAi: boolean
  createTime: string
  updateTime: string
}
~~~

- availableForAi=true 的条件：至少有一个 status=1 的文档，且至少有一个成功向量化分块。
- Controller 必须返回专用 VO，不能直接返回 KnowledgeBase 实体中的 userId、status。

### 6.2 知识库接口

| 状态 | 方法与路径 | 请求 | 成功 data |
| --- | --- | --- | --- |
| EXTEND | POST /api/kb/create | { name, description?, avatar?, color?, examAnalysisId? } | KnowledgeBaseDto |
| EXTEND | GET /api/kb/list | 无 | KnowledgeBaseDto[] |
| EXTEND | GET /api/kb/{id} | 无 | KnowledgeBaseDto |
| EXTEND | PUT /api/kb/{id} | { name?, description?, avatar?, color? } | KnowledgeBaseDto |
| KEEP | DELETE /api/kb/{id} | 无 | null |
| KEEP | GET /api/kb/by-exam-analysis/{examAnalysisId} | 无 | KnowledgeBaseDto 或 null |

前端知识库选择器中的“无”、知识库列表和“新建知识库”是展示与交互组合，不新增选择器接口。“新建知识库”继续调用 `POST /api/kb/create`，成功后前端使用返回的 `KnowledgeBaseDto.id` 作为当前 `knowledgeBaseId`；取消选择时前端值为 `null`，只有对应业务 DTO 允许 `knowledgeBaseId=null` 时才可提交。

校验：

- name 长度 1 至 100。
- description 最长 500。
- 删除知识库继续使用现有事务：文档分块、文档、ES 向量和知识库逻辑删除必须保持一致。
- 被会话或学习项目引用时，删除策略必须返回 409，不能产生悬空关联。

### 6.3 文档状态

现有 document 表继续使用数字状态：

| document.status | 含义 | 聚合资源状态 |
| --- | --- | --- |
| 0 | 解析、分块或向量化处理中 | processing |
| 1 | 可用于 AI | ready |
| 2 | 处理失败 | failed |

禁止把 0 解释为“仅上传中”。上传请求成功后数据库记录立即进入处理中。

~~~ts
type DocumentDto = {
  id: number
  kbId: number
  fileName: string
  fileType: "pdf" | "docx" | "md" | "txt"
  fileSize: number
  charCount: number
  chunkCount: number
  status: 0 | 1 | 2
  errorMsg: string | null
  createTime: string
  updateTime: string
}

type DocStatusDto = {
  id: number
  status: 0 | 1 | 2
  chunkCount: number
  errorMsg: string | null
}
~~~

### 6.4 文档接口

#### POST /api/doc/upload

状态：KEEP。

multipart 字段：

- kbId：number，必填。
- file：binary，必填。

支持 PDF、DOC、DOCX、XLS、XLSX、PPT、PPTX、MD、TXT、ZIP，单文件最大 21 MB。文件名必须安全重命名，原文件名仅保存为元数据。

解析约束：

- DOC/DOCX 提取正文、标题和表格可见文本；不执行宏、嵌入对象或外链。
- XLS/XLSX 按工作表顺序提取非空单元格，结果包含工作表名和行列位置；公式返回服务端计算值或缓存值，不执行宏。
- PPT/PPTX 按幻灯片顺序提取标题、正文、表格和演讲者备注；不把该接口误当作 PPT 生成功能。
- ZIP 只解压本节支持的文件类型；最多 50 个条目、目录深度最多 3 层、解压后总大小最多 100 MB。拒绝绝对路径、`..` 路径穿越、软链接、加密包、可执行文件和嵌套压缩包。
- 单个压缩包中部分文件解析失败时整体返回 422，不返回伪完整正文；错误中列出首个失败条目。

成功：ApiResult<DocumentDto>，初始 status=0。

#### POST /api/doc/extract

状态：KEEP，聊天附件兼容接口。

multipart 字段 file。成功：ApiResult<string>。

该接口只解析当前聊天附件，不写入 knowledge_base、document、ES。格式、安全和 21 MB 限制与 upload 相同，解析正文最大 200000 字符。图片和音频不得调用本接口，分别走第 18 节图片上传和音频转写接口。

#### 其他接口

| 状态 | 方法与路径 | 成功 data 或响应 |
| --- | --- | --- |
| KEEP | GET /api/doc/list?kbId={id} | ApiResult<DocumentDto[]> |
| KEEP | GET /api/doc/{id} | ApiResult<DocumentDto> |
| KEEP | GET /api/doc/status/{id} | ApiResult<DocStatusDto> |
| KEEP | DELETE /api/doc/{id} | ApiResult<null> |
| KEEP | GET /api/doc/download/{id} | 文件流 |

下载：

- 必须校验文档和知识库权限。
- Content-Disposition 同时提供 filename 和 filename*。
- /api/doc/download 可以继续 inline；资料库聚合下载必须 attachment。

## 7. 资料库统一资源

### 7.1 权威模型

状态：NEW。本节是资料库、知识库和智能学习文件关联的唯一规则。

- 资料库是当前用户全部文件的全局入口；上传文件和 AI 生成文件成功创建后都必须出现于资料库。
- 知识库和学习项目不是第二份文件，只是同一资源的可选关联。
- `resourceId` 是资料库资源唯一 ID；`knowledgeBaseId` 是可选知识库关联；`projectId` 是可选学习项目关联。
- 三个字段不得互相代替，不再提供 `libraryId`、`libraryResourceId`、`learningProjectId` 等同义字段。
- `knowledgeBaseId=null` 且 `projectId=null` 合法，表示文件只进入资料库。
- 用户在生成或上传时选择知识库、项目后，后端必须在创建资源的同一事务中写入对应关联。
- 智能学习项目内生成的资源必须写入当前 `projectId` 及该项目的 `knowledgeBaseId`。
- 重命名、关联或取消关联只更新元数据，不复制物理文件。

~~~ts
type ResourceSourceType = "uploaded" | "generated"
type ResourceOrigin =
  | "resource-library" | "chat" | "learning"
  | "presentation" | "spreadsheet" | "mindmap"
type ResourceFileType =
  | "image" | "document" | "spreadsheet" | "presentation"
  | "pdf" | "audio" | "archive" | "mindmap" | "other"
type ResourceStatus = "waiting" | "processing" | "ready" | "failed"

type LibraryResourceDto = {
  resourceId: string
  name: string
  format: string
  fileType: ResourceFileType
  mimeType?: string
  sizeBytes: number
  status: ResourceStatus
  errorMessage?: string
  updatedAt: string
  sourceType: ResourceSourceType
  origin: ResourceOrigin
  projectId: number | null
  knowledgeBaseId: number | null
  externalKey?: string
}

type ResourceAssociations = {
  projectId: number | null
  knowledgeBaseId: number | null
}
~~~

字段约束：

- `resourceId` 必须稳定且不可猜测。允许使用带业务前缀的 ID，例如 `presentation:{id}`、`spreadsheet:{id}`、`media:{id}`。
- `externalKey` 仅用于后端聚合去重和排障，前端操作一律使用 `resourceId`。
- `updatedAt` 返回 ISO 8601；`sizeBytes` 返回原始字节数，展示单位由前端计算。
- PPT/PPTX 的 `fileType=presentation`，XLS/XLSX/CSV 的 `fileType=spreadsheet`。
- 上传入口必须提供真实 `origin`。AI 生成服务按自身模块写入 `presentation`、`spreadsheet`、`learning` 或 `mindmap`。
- 同一生成实体重试成功时更新原资源，不新建重复资源；通过 `externalKey` 建唯一约束。

### 7.2 统一创建规则

| 场景 | sourceType | 必须进入资料库 | 关联规则 |
| --- | --- | --- | --- |
| 资料库页面上传 | uploaded | 是 | 按用户选择写入知识库；项目为空 |
| 对话附件 | uploaded | 是 | 关联当前对话知识库；学习助教同时关联项目 |
| 对话 AI 生成文件 | generated | 是 | 普通对话按可选知识库关联；项目 AI 对话同时关联项目并 upsert 项目资源包 |
| 智能学习上传 | uploaded | 是 | 关联当前项目和项目知识库 |
| PPT/电子表格生成 | generated | 是 | 按创建 DTO 中选择的项目、知识库关联；有项目时同时 upsert 项目资源包，均未选择则都为空 |
| 智能学习资源生成 | generated | 是 | 必须关联当前项目和项目知识库 |
| 思维导图生成 | generated | 是 | 按创建上下文关联 |

图片和音频仍通过第 18 节媒体接口上传，但媒体服务成功创建资产时必须同时创建本节资源记录。普通文档通过本节上传接口。聊天现有文档提取接口在保存附件时也必须创建同一资源记录，不能只返回提取文本。

### 7.3 接口

#### GET /api/resources

Query：`knowledgeBaseId?: number`。不传返回当前用户全部资源；传入时只返回关联该知识库的资源。成功返回 `ApiResult<LibraryResourceDto[]>`，按 `updatedAt DESC`。

来源和文件类型筛选当前由前端对已加载列表执行：来源单选、文件类型单选，两组条件可同时生效。后端不得返回“最近删除”伪分类。

#### POST /api/resources/upload

multipart：

- `file`：必填。
- `origin`：`resource-library | chat | learning`，必填。
- `knowledgeBaseId`：可选。
- `projectId`：可选。

两个关联字段都允许为空。若同时提供，后端必须校验项目和知识库属于当前用户且关联关系一致。成功先返回资源记录；需要解析时状态为 `waiting` 或 `processing`。

#### PATCH /api/resources/{resourceId}

请求：`{ name: string }`。只修改展示名，不直接拼接物理存储路径。

#### PUT /api/resources/{resourceId}/associations

请求为 `ResourceAssociations`。两个字段都是全量值，`null` 表示取消该关联。后端校验权限和项目/知识库一致性，成功返回更新后的 `LibraryResourceDto`。

#### POST /api/resources/{resourceId}/retry

只允许 `failed` 资源。重复请求使用新的 `clientRequestId` 或后端任务幂等键，成功返回 `waiting` 或 `processing` 的资源。

#### GET /api/resources/{resourceId}/download

返回文件流，使用真实 MIME，并设置 `Content-Disposition: attachment`。无权访问返回 403，不存在返回 404，未就绪返回 409 `RESOURCE_NOT_READY`。

#### DELETE /api/resources/{resourceId}

删除资源及其全部关联。后端负责清理解析数据、生成文件和对象存储；不得级联删除学习项目、知识库、会话或生成实体。

### 7.4 权限、幂等与错误

- 每次查询、下载、修改和删除都必须按 `resourceId` 反查当前用户。
- `projectId`、`knowledgeBaseId` 来自前端上下文，不构成授权。
- 上传可使用 `clientRequestId` 或文件上传会话防止重复创建；生成资源以生成实体 ID 建唯一索引。
- 错误码至少包含：`RESOURCE_NOT_FOUND`、`RESOURCE_FORBIDDEN`、`RESOURCE_NOT_READY`、`RESOURCE_CONTEXT_MISMATCH`、`RESOURCE_TYPE_UNSUPPORTED`、`RESOURCE_TOO_LARGE`、`RESOURCE_PROCESSING_FAILED`。
## 8. 思维导图

现有 /api/mindmap 接口继续保留：

~~~ts
type MindMapDto = {
  id: number
  resourceId: string
  version: number
  kbId: number | null
  title: string
  content: string
  renderConfig?: MindMapRenderConfig
  createTime: string
  updateTime: string
}

type MindMapRenderConfig = {
  theme?: string
  layout?: string
  themeConfig?: Record<string, unknown>
}

type MindMapTreeNode = {
  data: { text: string; [key: string]: unknown }
  children: MindMapTreeNode[]
}

type MindMapUpdateResult = {
  id: number
  resourceId: string
  version: number
  updatedAt: string
  previewData: { kind: "mindmap"; mindMap: MindMapTreeNode; mindMapConfig?: MindMapRenderConfig }
}
~~~

| 状态 | 方法与路径 | 请求 | 成功 data |
| --- | --- | --- | --- |
| EXTEND | POST /api/mindmap/create | { title, kbId?, content, renderConfig? } | number |
| EXTEND | POST /api/mindmap/update | { id, title?, kbId?, content?, renderConfig? } | MindMapUpdateResult |
| KEEP | POST /api/mindmap/delete/{id} | 无 | null |
| KEEP | GET /api/mindmap/list?kbId={id} | 无 | MindMapDto[] |
| KEEP | GET /api/mindmap/detail/{id} | 无 | MindMapDto |
| EXTEND | POST /api/mindmap/generate-from-ai | { content, title? } | { id, title, treeData, renderConfig? } |

所有接口必须补齐用户权限校验。generate-from-ai 当前为同步接口，首期允许保留，但必须在 30 秒内完成；超过后应迁入第 10 节生成任务，不得让连接无限等待。

思维导图的聊天附件、统一预览和编辑页必须引用同一个 `resourceId`，读取同一份完整树和同一份 `renderConfig`。树节点 `data` 中的颜色、形状、字体等生成样式不得被接口清洗；正式生成不强制绿色主题，绿色 logicalStructure 仅是前端 Mock 缺省示例。`update` 成功时，后端应在同一事务中更新 `mind_map.content/render_config/version/update_time` 及资源预览版本，再返回持久化后的 `previewData`。`GET /api/resources/{resourceId}/preview` 必须读取最新版本，不能长期返回生成时写入消息的旧快照；消息中的轻量 preview 仅作首屏降级。前端收到保存结果后会立即刷新当前页并广播同一资源更新，跨设备或刷新后的正确性仍以后端持久化版本为准。

## 9. 智能学习数据契约

以下接口均为 NEW。后端返回英文状态，前端 Repository 映射为现有中文界面。

### 9.1 状态

~~~ts
type LearningProjectStatus =
  | "draft"
  | "configuring"
  | "ready"
  | "in_progress"
  | "completed"

type LearningTaskStatus =
  | "not_started"
  | "in_progress"
  | "completed"
  | "needs_review"
  | "locked"

type LearningResourceStatus =
  | "not_selected"
  | "generating"
  | "ready"
  | "failed"

type WrongQuestionStatus = "needs_review" | "mastered"
type TrainingSetStatus = "pending" | "answering" | "submitted"
type WrongReviewSetStatus = "pending" | "answering" | "completed"
~~~

- `status=locked` 时 done 必须为 false，后端拒绝该任务的学习行为、答题和资源完成请求；前端只展示锁定状态。
- `status=needs_review` 表示任务可进入但需要复习，done 必须为 false；用户产生新学习行为后由后端决定继续保持或转为 in_progress/completed。
- `status=completed` 时 done 必须为 true，其他任务状态 done 必须为 false。返回未声明的状态属于契约错误，前端 Repository 只回落到安全默认状态，不把未知字符串直接展示。

### 9.2 画像和创建请求

~~~ts
type LearningProfileData = {
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

type LearningProfileRequest = {
  conversationId?: number | null
  knowledgeBaseId: number | null
  text: string
  currentProfile?: LearningProfileData
  source?: string
  subject?: string
  knowledgeTags?: string[]
  supplementalRequirement?: string
  mediaAssetIds?: string[]
}

type LearningProfileResult = {
  profile: LearningProfileData
}

type LearningConfirmationRequest = {
  setupId: string
  conversationId?: number | null
  knowledgeBaseId: number | null
  goal: string
  profile: LearningProfileData
  uploadedFileNames?: string[]
  mediaAssetIds?: string[]
  relatedProjectName?: string
  questionCount?: number
  difficultyStrategy?: string
  projectId?: number | null
  confirmationResourceId?: string | null
  clientRequestId: string
}

type LearningConfirmationResult = {
  content: string
  resourceId: string
}

type CreateLearningDraftRequest = {
  title: string
  knowledgeBaseId: number | null
  knowledgeBaseName?: string
  icon?: string
  iconColor?: string
}

type CreateLearningPlanRequest = {
  prompt: string
  knowledgeBaseId: number | null
  targetType: string
  preferences: string[]
  resourceGroups: Array<
    "学习方案" | "个性化学习手册" | "PPT" |
    "思维导图" | "代码案例" | "图片" | "文档" |
    "电子表格" | "音频" | "其他文件"
  >
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
  draftPlanId?: number | null
  knowledgeBaseName?: string
}
~~~

字段语义：

- draftPlanId：正在配置并将被生成结果替换的草稿项目 ID。
- draftPlanId 有值且项目状态为 draft/configuring 时，生成结果原地替换该草稿并保留同一个项目 id。
- draftPlanId 为空时创建全新项目；本请求不再提供同义 projectId。
- knowledgeBaseName 只用于兼容展示，权威名称由 knowledgeBaseId 查询。
- questionCount 范围 10 至 200。
- knowledgeBaseId 可空；非空时必须存在、属于当前用户且 availableForAi=true。为空时只使用用户输入、sourceResourceIds 和 mediaAssetIds。
- sourceResourceIds 最多 20 个，必须属于当前用户且状态为 ready。生成成功后这些资源继续保留在全局资料库，并关联 result.projectId；knowledgeBaseId 非空时同时关联该知识库，不复制文件。
- mediaAssetIds 最多 5 个，必须属于当前用户且状态为 ready；它们与画像请求使用同一批媒体资产，并继续参与最终学习方案、阶段和题目生成。
- confirmationResourceId 非空时必须包含在 sourceResourceIds；后端用 prompt 覆盖该 Markdown 资源的最终确认版本，再开始生成项目。
- mediaAssetIds 最多 5 个，只接受 purpose=learning-input 且属于当前用户的 ready 图片或音频；后端读取图片内容或音频转写，uploadedFileNames 只用于展示，不能替代媒体引用。

### 9.3 项目聚合 DTO

GET 项目列表和详情当前都返回同一个 LearningProjectDto。列表暂不分页。

~~~ts
type LearningProjectDto = {
  id: number
  title: string
  icon?: string
  iconColor?: string
  goal: string
  updatedAt: string
  knowledgeBaseId: number | null
  status: LearningProjectStatus
  period: string
  targetType: string
  progress: number
  taskDone: number
  totalTasks: number
  exerciseDone: number
  totalExercises: number
  correctRate: number
  weeklyHours: string
  profile: Array<{ label: string, value: string }>
  stages: LearningStageDto[]
  resources: LearningResourceDto[]
  exercises: ExerciseDto[]
  questionBank?: QuestionBankConfigDto
  trainingSets?: TrainingSetDto[]
  wrongQuestions: WrongQuestionDto[]
  wrongReviewSets?: WrongReviewSetDto[]
  dashboard: Array<{ label: string, value: number }>
  agents: Array<{
    name: string
    desc: string
    status: "done" | "running" | "pending"
  }>
}

type LearningStageDto = {
  id: number
  title: string
  desc: string
  scheduleLabel?: string
  tasks: LearningTaskDto[]
}

type LearningTaskDto = {
  id: number
  title: string
  duration: string
  done: boolean
  type: "讲解" | "资料" | "练习" | "测验" | "案例"
  learningResourceId?: number
  exerciseIds?: number[]
  status: LearningTaskStatus
  completionMode?:
    | "content" | "resource" | "exercise"
    | "assessment" | "case"
  completionSource?: string
  readProgress?: number
  validStudySeconds?: number
  completedActions?: string[]
}

type LearningResourceDto = {
  id: number
  resourceId?: string
  group:
    | "学习方案" | "个性化学习手册" | "PPT"
    | "思维导图" | "代码案例" | "图片" | "文档"
    | "电子表格" | "音频" | "其他文件"
  title: string
  desc: string
  status: LearningResourceStatus
  action: string
  fileName?: string
  content?: string
  previewUrl?: string
  mindMapId?: number
  mindMapTreeData?: unknown
  mindMapRenderConfig?: MindMapRenderConfig
  presentationId?: string
  artifactId?: string
  source?: "default" | "ai-conversation" | "learning-profile"
  errorMessage?: string
}
~~~

### 9.4 题目 DTO

~~~ts
type ExerciseType =
  | "单选题" | "多选题" | "判断题"
  | "填空题" | "简答题" | "代码题"

type CodeLanguageKey =
  | "java" | "python" | "javascript"
  | "c" | "cpp" | "csharp" | "go"

type CodeLanguageDto = {
  key: CodeLanguageKey
  label: string
  runtime: string
  starterCode: string
  referenceAnswer: string
  requiredCodePatterns: string[]
}

type ExerciseDto = {
  id: number
  title: string
  knowledge: string
  difficulty: "基础" | "中等" | "提高" | "进阶" | "挑战"
  type: ExerciseType
  code?: string
  options: string[]
  answer: string
  acceptedAnswers?: string[]
  gradingKeywords?: string[]
  gradingRubric?: string[]
  passingScore?: number
  language?: string
  runtime?: string
  starterCode?: string
  requiredCodePatterns?: string[]
  sampleTests?: Array<{ input: string, expected: string }>
  codeLanguages?: CodeLanguageDto[]
  selectedLanguage?: CodeLanguageKey
  userAnswer?: string
  submitted?: boolean
  gradingCorrect?: boolean
  gradingScore?: number
  gradingFeedback?: string
  explanation: string
  scene?: "checkpoint" | "practice" | "assessment"
  sourceExerciseId?: number
  cognitiveLevel?: "概念理解" | "直接应用" | "综合迁移"
  purpose?:
    | "随堂检查" | "阶段练习" | "阶段测验"
    | "备用题" | "错题巩固" | "追加练习"
  generationBatch?: string
  sourceTaskId?: number
}
~~~

`draftAnswer` 和各语言 `codeDrafts` 是前端 sessionStorage 中的未提交草稿，不属于 ExerciseDto，后端不得在项目详情中返回或保存为正式答案。`selectedLanguage` 只有已提交答案存在时才作为权威结果返回；未提交时前端可用本地草稿覆盖显示。

答案安全规则：

- submitted 不为 true 时，answer 和 explanation 返回空字符串。
- 未提交代码题的 referenceAnswer、requiredCodePatterns、gradingKeywords、gradingRubric 返回空值或空数组。
- sampleTests 只能包含公开样例，隐藏测试永不返回前端。
- 提交成功后 AnswerResult 返回本次正确答案和解析；后续项目详情只对已提交题目返回答案。
- 代码执行必须使用隔离判题服务，不能复制 Mock 字符串匹配逻辑。

### 9.5 题库、题组和错题

~~~ts
type QuestionBankConfigDto = {
  targetCount: number
  initialCount: number
  generatedCount: number
  difficultyStrategy: "基础为主" | "均衡" | "强化提高"
  difficultyCounts: {
    basic: number
    advanced: number
    challenge: number
  }
  typeCounts?: Partial<Record<ExerciseType, number>>
  generatedAt: string
}

type TrainingSetDto = {
  id: number
  title: string
  exerciseIds: number[]
  status: TrainingSetStatus
  source: "专项训练" | "错题巩固"
  knowledge: string
  difficulty: string
  questionType: string
  createdAt: string
}

type WrongQuestionDto = {
  id: number
  title: string
  knowledge: string[]
  userAnswer: string
  correctAnswer: string
  answerLanguage?: CodeLanguageKey
  reason: string
  synced: boolean
  status: WrongQuestionStatus
  errorCount?: number
  reviewCount?: number
  correctStreak?: number
  lastWrongAt?: string
  reviewHistory?: Array<{
    date: string
    correct: boolean
    answer: string
  }>
}

type WrongReviewSetDto = {
  id: number
  title: string
  exerciseIds: number[]
  sourceWrongIds: number[]
  status: WrongReviewSetStatus
  createdAt: string
  difficultyMode: "保持难度" | "逐步提升"
  correctRate?: number
}
~~~

## 10. 智能学习接口

### 10.1 项目

#### GET /api/learning/projects

成功：ApiResult<LearningProjectDto[]>，按 updatedAt DESC。

#### GET /api/learning/projects/{id}

成功：ApiResult<LearningProjectDto>。

项目不存在返回 404，无权限返回 403。前端不会回退到第一个项目。

#### POST /api/learning/projects/drafts

请求：CreateLearningDraftRequest。

成功：ApiResult<LearningProjectDto>。

草稿初始要求：

- status=draft。
- progress=0。
- stages、resources、exercises、wrongQuestions、dashboard、agents 为空数组。
- knowledgeBaseId 可以为 null；没有知识库也允许继续画像和方案生成。

#### PATCH /api/learning/projects/{id}

状态：NEW，当前 API Repository 已接入。

请求：

~~~ts
type UpdateLearningProjectRequest = {
  title?: string
  icon?: string
  iconColor?: string
  knowledgeBaseId?: number | null
  targetType?: string
  period?: string
  dailyTime?: string
  weakPoints?: string
  preferences?: string[]
  keepExercises?: boolean
  keepProgress?: boolean
}
~~~

成功返回 ApiResult<LearningProjectDto>。调整计划时后端根据 keepExercises/keepProgress 决定是否保留题目和进度，并重新计算权威统计；前端不会本地伪造 API 成功结果。

#### DELETE /api/learning/projects/{id}

状态：NEW，当前 API Repository 已接入。

成功：`ApiResult<null>`。

使用逻辑删除或归档。运行中的生成任务存在时返回 409。删除项目不删除全局资料库中的文件，只把这些资源的 projectId 解除；原 knowledgeBaseId 关联保持不变。项目置顶仍是 localStorage UI 偏好，不需要后端字段。

### 10.2 通用生成任务

~~~ts
type GenerationJob<T> = {
  jobId: string
  status: "pending" | "running" | "succeeded" | "failed" | "cancelled"
  progress?: number
  result?: T
  errorCode?: string
  errorMessage?: string
}
~~~

状态转换：

~~~text
pending -> running -> succeeded
                   -> failed
pending/running -> cancelled
~~~

- succeeded 必须有 result。
- failed 必须有 errorCode 和 errorMessage。
- progress 范围 0 至 100，只允许单调增加。
- 任务和结果保存在后端，刷新后仍可查询。
- 前端每秒轮询一次，最多 120 次。
- 创建任务接口首次返回 status=pending、progress=0；不得在响应线程中等待 AI 完成后才返回。
- 查询任务时必须校验 job.userId，不能凭 UUID 直接访问其他用户任务。

#### GET /api/learning/generation-jobs/{jobId}

成功：ApiResult<GenerationJob<T>>。

### 10.3 学习画像

#### POST /api/learning/profile-jobs

请求：LearningProfileRequest。

成功：ApiResult<GenerationJob<LearningProfileResult>>。

后端步骤：

1. 校验 conversationId 所属用户、conversationType=learning-setup、projectId 一致性，以及知识库权限和 availableForAi。
2. 创建 generation_job。
3. 读取文档解析结果和用户输入。
4. 调用 AI 并校验结构。
5. 保存结果并更新任务；conversationId 非空时，在该会话保存或更新 `kind=learning-profile` 的结构化 Card 消息。

#### POST /api/learning/profile-confirmations

请求：LearningConfirmationRequest。

成功：ApiResult<LearningConfirmationResult>。

~~~json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": "确认稿 Markdown 内容",
    "resourceId": "resource-confirmation-123"
  }
}
~~~

该接口当前为同步调用，必须在 30 秒内完成。确认稿由后端生成，正式前端不拼装权威确认稿。

- 生成成功必须立即创建全局资料库 Markdown 资源并返回 resourceId。
- projectId/knowledgeBaseId 非空时建立对应关联；均为空时仍进入资料库。
- conversationId 非空时必须校验其为当前用户的 learning-setup 会话，并保存或更新 `kind=learning-document` 的结构化消息；learningData.content 和 resourceId 必须与接口结果一致。
- setupId 由前端为本次配置流程生成并在 sessionStorage 草稿中保持稳定，只用于首次创建项目之前定位同一份确认稿，不是业务实体 ID，也不能用于权限判断。
- 首次生成 confirmationResourceId 为空；重新生成时前端传回上次 resourceId，后端更新同一资源和版本，不创建重复文件。
- clientRequestId 用于防止重复点击；同一幂等键必须返回同一个 resourceId 和内容版本。

### 10.4 学习方案

#### POST /api/learning/plan-jobs

请求：CreateLearningPlanRequest。

成功：ApiResult<GenerationJob<{ projectId: number }>>。

后端事务边界：

1. 验证 draftPlanId、knowledgeBaseId、sourceResourceIds 和 mediaAssetIds 归属。
2. 保存用户确认版本，并用 prompt 更新 confirmationResourceId 对应的确认稿文件。
3. 创建生成任务并立即返回。
4. 后台生成阶段、任务、题目与资源需求。
5. 默认创建 ready 的“学习方案”和“思维导图”；手册、PPT、代码案例和图片按资源描述状态 not_selected 等待用户生成。
6. 校验任务、题目、资源引用完整性后事务保存，并关联 sourceResourceIds。
7. 成功任务 result.projectId 必须能立即通过 GET 项目详情读取。

### 10.5 学习行为

#### POST /api/learning/projects/{projectId}/activities

~~~ts
type LearningActivityRequest = {
  projectId: number
  taskId: number
  eventType: "start" | "reading" | "action" | "complete"
  progress?: number
  secondsDelta?: number
  action?: string
  clientRequestId: string
}
~~~

成功：ApiResult<LearningProjectDto>。

- Path projectId 必须等于 body.projectId，否则 400。
- progress 范围 0 至 100。
- secondsDelta 范围 0 至 300，后端校验频率和异常累计。
- 前端上报行为，后端计算权威 task.status、project.progress。
- reading 事件前端按 projectId+taskId 聚合，最多约每 10 秒发送一次；任务完成、页面隐藏或离开时立即冲刷。后端仍需按 clientRequestId 幂等并校验异常时长。
- start/action/complete 接口失败时前端撤回本次乐观状态并展示错误；后端不得返回成功但不保存。
- Mock 的“阅读 80% + 5 秒”不是正式规则。

### 10.6 提交答案

#### POST /api/learning/projects/{projectId}/answers

~~~ts
type SubmitAnswerRequest = {
  projectId: number
  exerciseId: number
  answer: string
  language?: CodeLanguageKey
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
~~~

成功：ApiResult<AnswerResult>。

后端必须在一个事务或可恢复流程中：

1. 幂等保存答案版本。
2. 执行题型对应评分。
3. 更新题目提交状态。
4. 答错时创建或更新错题。
5. 更新掌握度、任务和项目进度。
6. 返回结果。

#### POST /api/learning/projects/{projectId}/answers/batch

~~~ts
type SubmitAnswerBatchRequest = {
  projectId: number
  answers: Array<{
    exerciseId: number
    answer: string
    language?: CodeLanguageKey
  }>
  clientRequestId: string
}
~~~

成功：ApiResult<AnswerResult[]>，结果顺序必须与 answers 请求顺序一致。

- answers 数量 1 至 200，同一个 exerciseId 不得重复。
- Path projectId 必须等于 body.projectId，全部题目必须属于该项目。
- 整组评分、答案保存、错题更新、任务完成和项目进度必须在一个事务或可恢复工作流中提交；任一题校验失败时整组失败，禁止部分成功。
- 同一 clientRequestId 重试返回原结果；相同幂等键对应不同答案内容时返回 409 IDEMPOTENCY_CONFLICT。
- 学习页统一交卷和错题巩固题组均调用该接口；单题重新作答仍调用上一接口。

### 10.7 自适应练习

#### POST /api/learning/projects/{projectId}/tasks/{taskId}/adaptive-practice-jobs

~~~ts
type AdaptivePracticeRequest = {
  mode: "repeat" | "reinforce"
  count: number
  difficultyMode: "保持难度" | "逐步提升"
}
~~~

count 范围 1 至 20。成功：ApiResult<GenerationJob<{ projectId: number }>>。

任务成功后将新题和训练题组写入项目，前端重新获取项目详情。

### 10.8 错题巩固

#### POST /api/learning/projects/{projectId}/mistake-review-jobs

~~~ts
type MistakeReviewRequest = {
  wrongIds: number[]
  count: number
  difficultyMode: "保持难度" | "逐步提升"
}
~~~

- wrongIds 数量 1 至 50，全部属于项目。
- count 范围 1 至 20。
- 成功：ApiResult<GenerationJob<{ projectId: number }>>。
- 生成后创建 WrongReviewSetDto，并在答题结果中更新 correctStreak；连续正确次数达到后端规则时状态变为 mastered。

当前前端通过项目详情读取错题，不要求单独实现 GET mistakes 和 mastery 接口。

### 10.9 学习资源

#### POST /api/learning/projects/{projectId}/resource-jobs

请求：{ learningResourceId: number }。

成功：ApiResult<GenerationJob<{ projectId: number }>>。

- 只有 not_selected 或 failed 可发起。
- 已 generating 返回 409。
- 成功后状态 ready，并保存稳定文件、content 或 mindMapId。
- 失败后状态 failed，保存 errorMessage。
- 项目初次生成时“学习方案”和“思维导图”直接为 ready；其余可选资源初始为 not_selected。
- 每个 ready 学习资源必须有 resourceId，并自动出现在全局资料库；knowledgeBaseId/projectId 关联沿用项目上下文，不存在额外“保存到资料库”接口。
- 页面刷新后如果项目详情仍有 generating 资源，前端每 3 秒重新获取项目详情，最多约 120 秒；后端任务继续运行且状态必须可恢复。

#### PUT /api/learning/projects/{projectId}/resources/generated

项目 AI 对话、学习画像确认稿、学习方案/手册/导图以及后续生成文件统一调用该幂等回写接口：

~~~ts
type GeneratedProjectResourceRequest = {
  learningResourceId?: number | null
  resourceId: string
  artifactId: string
  title: string
  fileName: string
  fileType: ResourceFileType
  preview?: ArtifactInlinePreview
  content?: string
  source: "ai-conversation" | "learning-profile"
  clientRequestId: string
}
~~~

成功返回更新后的 `LearningProjectDto`。`learningResourceId` 有值时更新该既有资源位；为空时按 `artifactId` 或 `resourceId` upsert 项目资源条目。后端必须校验 `resourceId` 已属于当前用户且其 `projectId` 与路径项目一致。该接口不复制文件：全局资料库、可选知识库、项目资源包和聊天附件始终引用同一 `resourceId`。相同 `artifactId/resourceId` 重复调用不得增加第二条资源。学习项目创建任务使用的 `sourceResourceIds` 和 `confirmationResourceId` 也必须执行同样回写，因此通过学习画像生成的文件在项目完成后仍同时存在于资料库与项目资源包。

#### GET /api/learning/projects/{projectId}/resources/{learningResourceId}/download

返回文件流，不使用 ApiResult 包装。必须校验项目和资源关联；`Content-Type` 使用资源真实 MIME，`Content-Length` 为实际字节数，`Content-Disposition` 使用 `attachment; filename*=UTF-8''...` 返回 UTF-8 文件名。

项目详情必须直接返回 resources；当前前端不调用单独资源列表接口。

### 10.10 智能学习错误码与性能约束

| HTTP | errorCode | 触发条件 |
| --- | --- | --- |
| 404 | LEARNING_PROJECT_NOT_FOUND | 项目不存在或已删除 |
| 403 | LEARNING_PROJECT_FORBIDDEN | 项目不属于当前用户 |
| 409 | LEARNING_PROJECT_STATE_CONFLICT | 当前状态不允许修改、删除或生成 |
| 404 | LEARNING_TASK_NOT_FOUND | 阶段/任务/题目不属于该项目 |
| 409 | LEARNING_CONTEXT_MISMATCH | knowledgeBaseId、sourceResourceIds 或上下文层级不一致 |
| 409 | LEARNING_SOURCE_NOT_READY | 输入资源未解析完成或媒体未 ready |
| 404 | LEARNING_JOB_NOT_FOUND | 生成任务不存在 |
| 409 | LEARNING_JOB_CONFLICT | 同类生成任务正在运行 |
| 422 | LEARNING_GENERATION_FAILED | AI 输出无法校验或生成失败 |
| 409 | ANSWER_ALREADY_SUBMITTED | 当前题目不允许再次提交 |
| 409 | ANSWER_BATCH_INVALID | 批量题目重复、跨项目或不满足统一交卷条件 |
| 409 | IDEMPOTENCY_CONFLICT | 相同 clientRequestId 对应不同请求内容 |
| 409 | LEARNING_RESOURCE_NOT_READY | 下载资源未 ready |

- 项目列表首期不分页，但单用户最多返回 200 个未归档项目；超过后后端要求归档或启用分页版本。
- 项目详情最多 20 个阶段、每阶段 100 个任务、初始题量 10 至 200、单次追加题量 1 至 20。
- sourceResourceIds 最多 20 个、mediaAssetIds 最多 5 个；正文进入模型前由后端按 token 预算裁剪和检索，不把全部文件正文直接拼接。
- 生成任务接口应在 1 秒内返回 jobId；任务查询 P95 低于 500ms；项目详情 P95 低于 1 秒，不包含 AI 生成时间。
- 前端轮询计划任务每秒一次、最多 120 次；资源恢复每 3 秒一次、最多约 120 秒。达到上限只停止前端自动查询，不取消后端任务。

## 11. 学习助教

学习助教不新增独立聊天协议，复用：

1. POST /api/conversation/create，conversationType=learning-tutor，projectId 必填。
2. GET /api/conversation/list 恢复项目关联会话。
3. GET /api/conversation/{id}/messages 恢复历史。
4. POST /api/chat/stream 发送问题。

后端根据 conversation.projectId 加载：

- 项目目标和画像。
- 当前阶段、任务和题目。
- 用户已提交答案和错题。
- 项目资源。

请求可额外携带 stageId、taskId、exerciseId 说明用户当前所在位置。后端必须先按 conversation.projectId 校验层级归属，再从数据库加载权威状态；前端不发送 tutorContext 作为正式系统提示词。

正式上下文组装顺序：

1. 读取项目目标、画像、阶段、当前任务、进度、已提交答案、错题和资源元数据。
2. 以 project.knowledgeBaseId 作为知识库检索范围；为空时跳过知识库 RAG。
3. 对当前问题做查询改写和向量/关键词混合检索，返回有权限且 ready 的片段。
4. 组合系统约束、结构化项目摘要、当前题目安全边界、RAG 片段和会话历史后调用模型。
5. 保存用户消息、回答、引用片段和上下文实体 ID；不保存或回传隐藏答案、评分规则和内部提示词。

项目结构化数据来自业务数据库，知识库正文来自 RAG，两者缺一不可但职责不同。RAG 不负责保存学习进度，项目数据库也不替代文档检索。

未提交的测验只能给提示，不得返回正确答案、标准代码、隐藏测试或完整解法。

## 12. 数据库与现有表复用

### 12.1 现有表

继续复用：

- user、user_settings。
- conversation、message。
- knowledge_base、document、document_chunk。
- mind_map。
- resource、user_resource。

conversation 至少新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| is_pinned | TINYINT NOT NULL DEFAULT 0 | 会话置顶 |
| learning_project_id | BIGINT NULL | 学习项目关联 |
| conversation_type | VARCHAR(32) NOT NULL DEFAULT 'general' | 会话类型 |

learning_project_name 不作为权威冗余字段；响应通过 learning_project.title 取得。

### 12.2 新增学习表

后端可以调整表名，但以下数据必须持久化：

| 建议表 | 关键字段 |
| --- | --- |
| learning_project | id、user_id、knowledge_base_id、title、goal、status、period、target_type、progress、统计字段、deleted、create_time、update_time |
| learning_profile_version | id、project_id、version、profile_json、confirmation_content、confirmed、create_time |
| learning_stage | id、project_id、sort_no、title、description、schedule_label |
| learning_task | id、stage_id、type、status、completion_mode、进度和时长字段 |
| learning_resource | id、project_id、resource_id、task_id、group_type、title、status、content、mind_map_id、error_message |
| exercise | id、project_id、source_task_id、题型、题干、难度、知识点、选项、答案、解析、评分规则、生成批次 |
| task_exercise | task_id、exercise_id、sort_no |
| answer_submission | id、user_id、project_id、exercise_id、answer、language、score、correct、feedback、client_request_id、create_time |
| wrong_question | id、user_id、project_id、exercise_id、状态、连续正确、错误次数、复习次数、最近错误时间 |
| wrong_review_set | id、project_id、状态、来源错题、难度策略、正确率 |
| generation_job | job_id、user_id、project_id、job_type、status、progress、request_json、result_json、error_code、error_message、create_time、update_time |
| learning_activity | id、user_id、project_id、task_id、event_type、progress、seconds_delta、action、client_request_id、create_time |

关键约束：

- answer_submission 唯一键：(user_id, client_request_id, exercise_id)；批量请求还需单独保存 batch client_request_id 与请求摘要，保证整组幂等。
- learning_activity 唯一键：(user_id, client_request_id)。
- conversation 索引：(user_id, is_pinned, update_time)。
- generation_job 索引：(user_id, status, update_time)。
- 所有子表必须有可反查 project_id 的索引。

## 13. 状态机

### 13.1 文档

~~~text
0 processing -> 1 ready
             -> 2 failed
2 failed -> 0 processing  仅重试触发
~~~

### 13.2 学习项目

~~~text
draft -> configuring -> ready -> in_progress -> completed
~~~

- 创建草稿：draft。
- 开始画像和方案配置：configuring。
- 方案保存完成：ready。
- 首次有效学习行为：in_progress。
- 后端确认全部必需任务完成：completed。

### 13.3 学习任务

~~~text
not_started -> in_progress -> completed
~~~

完成状态只能由后端根据任务规则计算。

### 13.4 学习资源

~~~text
not_selected -> generating -> ready
                           -> failed
failed -> generating
~~~

### 13.5 错题

~~~text
needs_review -> mastered
mastered -> needs_review  再次答错时
~~~

## 14. 前后端已知兼容修正

这些不是新功能，是现有代码与正式契约之间必须处理的差异：

| 优先级 | 问题 | 责任 |
| --- | --- | --- |
| P0 | AuthInterceptor 和异常处理没有设置真实 HTTP 状态 | 后端 |
| P0 | PUT conversation 当前返回 data:null，前端需要 ConversationDto | 后端 |
| P0 | conversation 缺少学习项目、会话类型、置顶字段 | 后端 |
| P0 | CORS 未允许 PATCH，资料重命名会被预检阻止 | 后端 |
| P0 | CORS 当前使用通配来源并允许凭证 | 后端改为环境化来源白名单 |
| P0 | JWT 密钥和 AI Key 不能硬编码或提交 | 后端 |
| P0 | SSE 无限超时且客户端断开未取消上游任务 | 后端 |
| P0 | ChatService 当前固定模型，没有校验请求 model | 后端按模型列表配置选择 |
| P1 | 知识库直接返回实体，缺 availableForAi | 后端 |
| P1 | ModelSwitch 当前没有调用 fetchList，model API 失败会返回本地固定列表 | 前端加载 /api/config/model，并取消生产 fallback |
| P1 | 文档 status=0 在前端当前显示映射需要统一为 processing | 前端 |
| P1 | 前端现已允许 DOC、Excel、PPT、ZIP，现有文档解析器尚未全部支持 | 后端按第 6.4 节扩展解析；图片和音频固定走第 18 节媒体接口 |
| P1 | 学习项目重命名、删除需要后端实现正式持久化 | 前端 API Repository 已接入本文 PATCH/DELETE，后端按契约实现 |
| P1 | LearningProject 前端类型暂位于 Mock 文件 | 前端迁移到正式 contracts |
| P1 | 前端 common.ts 中 ApiResponse.code 仍声明为 string | 前端改为 number，与现有 Java Result 和本文一致 |
| P1 | 未提交题目的 answer 字段需要按第 9.4 节脱敏 | 前后端 |

## 15. 实现顺序

后端可以立即按以下顺序开发：

1. 修复统一 HTTP 状态、CORS、密钥配置、模型列表和会话更新响应。
2. 扩展 conversation 表、DTO、VO 和 Service。
3. 给知识库增加 VO 和 availableForAi。
4. 新增资料库聚合资源 Controller，复用 Document、MindMap 和文件服务。
5. 新增 generation_job 和学习项目基础表。
6. 实现项目草稿、项目列表和详情。
7. 实现画像、确认稿和方案生成任务。
8. 实现学习行为、答题、错题和进度。
9. 实现自适应练习、错题巩固和资源生成。
10. 新增媒体资产、麦克风/上传音频转写、图片识别任务，并让聊天和资料库按媒体 ID 关联。
11. 按第 19 节新增 PPT 实体、任务、Provider Adapter、预览、下载和自动资源归档。
12. 按第 20 节新增对话直达电子表格任务、XLSX 生成、预览、下载和自动资源归档。
13. 前后端联调 API 模式。

## 16. 后端验收清单

- 所有接口使用真实 HTTP 状态。
- 所有实体接口执行用户权限校验。
- 生产日志不输出 token、密码、AI Key、完整文件正文。
- API 错误时前端显示失败，不会出现 Mock 成功数据。
- 刷新页面后，会话、项目、任务、答题、错题和生成任务可从后端恢复。
- 重复 clientRequestId 不产生重复答题或学习行为。
- SSE 停止后上游 AI 调用被取消。
- 文档失败可以重试，ready 文档才能作为 AI 上下文。
- 未提交题目响应不包含正确答案和隐藏评分规则。
- 生成任务成功后，result 中的实体可以立即查询。
- 聚合资源删除、移动、重试不会留下孤立文件、分块或向量。
- 拒绝的麦克风权限不会发起后端请求；空录音、上传音频超时和不支持格式返回固定错误码。
- 图片和音频资产只能被所属用户和已关联的会话、资料库或学习项目引用。
- 图片识别任务可在刷新后通过 jobId 查询，重复 clientRequestId 不创建重复资产或任务。
- PPT 固定经过大纲人工确认，刷新后可恢复任务，ready 后自动进入资料库并可预览、下载。
- 电子表格根据对话和已关联上下文直接生成，ready 后自动进入资料库并可预览、下载 XLSX。
- 讯飞密钥、任务 ID、模板 ID 和原始文件 URL 不进入前端响应或普通业务日志。

## 17. 首期明确不实现

以下按钮继续由前端保持禁用，后端不需要猜接口：

- 资料库文件在线预览。
- 选择多个资料直接开始聊天。

需要启用时另行新增契约版本，不在现有接口中偷偷扩展字段。

## 18. 语音、拍照与图片识别

### 18.1 前端与后端边界

状态：NEW。

- 前端只负责请求浏览器权限、录音、选择音频、选图/拍照、格式与大小预校验、状态展示、取消和重试。
- 正式环境的语音识别、OCR、题目结构化、图片意图判断和多模态理解全部由后端完成。
- Mock Repository 只保存媒体元数据和模拟结果到按用户隔离的 sessionStorage，不保存真实图片或录音二进制。
- 正式环境的原始文件存对象存储，元数据、权限关联和任务状态存后端数据库；前端不把业务媒体写入 localStorage 或 sessionStorage。
- API 模式失败时禁止回退 Mock，也禁止仅凭文件名或扩展名伪造识别成功。
- 当前前端只有移动端 AppInput 暴露 source=camera 的拍照入口；Web 通用附件、移动端附件和资料库通用文件上传均可选择图片或音频，并使用 source=upload。该 UI 差异不改变媒体接口和后端权限规则。
- 视频不属于本契约。后端检测到 `video/*`、视频轨道或伪装成音频的容器时必须返回 415，不能只依据扩展名放行。

### 18.2 共享类型

~~~ts
type MediaKind = "image" | "audio"
type MediaSource = "upload" | "camera" | "microphone"
type MediaPurpose = "chat-attachment" | "library-resource" | "learning-input"
type MediaAssetStatus = "uploading" | "uploaded" | "processing" | "ready" | "failed"
type ImageRecognitionMode = "auto" | "ocr" | "question"

type MediaContext = {
  conversationId?: number | null
  knowledgeBaseId?: number | null
  projectId?: number | null
}

type MediaAssetDto = MediaContext & {
  id: string
  kind: MediaKind
  source: MediaSource
  purpose: MediaPurpose
  fileName: string
  mimeType: string
  size: number
  status: MediaAssetStatus
  createdAt: string
  updatedAt: string
  errorCode?: string
  errorMessage?: string
}

type UploadImageRequest = MediaContext & {
  source: "upload" | "camera"
  purpose: MediaPurpose
  clientRequestId: string
}

type TranscribeAudioRequest = MediaContext & {
  source: "upload" | "microphone"
  purpose: MediaPurpose
  clientRequestId: string
  language?: string
  durationMs?: number
}

type AudioTranscriptionDto = {
  asset: MediaAssetDto
  text: string
  language: string
  durationMs: number
  confidence?: number
}

type CreateImageRecognitionRequest = MediaContext & {
  mode: ImageRecognitionMode
  prompt?: string
  clientRequestId: string
}

type ImageRecognitionResult = {
  assetId: string
  mode: ImageRecognitionMode
  text: string
  intent: "general-image" | "document-ocr" | "question-capture"
  confidence?: number
  questionText?: string
  options?: string[]
}

type ImageRecognitionJob = {
  jobId: string
  status: "pending" | "running" | "succeeded" | "failed" | "cancelled"
  progress?: number
  result?: ImageRecognitionResult
  errorCode?: string
  errorMessage?: string
}
~~~

字段规则：

- media asset id 和 jobId 均为 UUID 字符串，不使用可枚举自增 ID。
- size 是原始字节数，mimeType 由后端根据文件签名重新确认，不能只信任浏览器上传值。
- confidence 取值 0 至 1；模型不提供可信置信度时不返回，不得伪造固定值。
- errorCode/errorMessage 只在 failed 状态返回；ready/succeeded 不返回错误字段。
- `uploading` 是前端本地状态；后端已接收请求后从 `uploaded` 开始。

### 18.3 上传图片或拍照

#### POST /api/media/images

状态：NEW。Content-Type 为 multipart/form-data，字段固定为：

- file：必填，图片二进制。
- metadata：必填，Content-Type 为 application/json，内容是 UploadImageRequest。

限制：

- 支持 JPEG、PNG、WEBP、HEIC、HEIF；后端必须同时校验扩展名、MIME 和文件签名。
- 单图最大 10 MB；单次请求只传 1 张；输入框最多引用 5 个媒体资产。
- 去除 EXIF GPS 等敏感元数据，并根据 EXIF orientation 规范化方向。
- `purpose=chat-attachment` 时 conversationId 必填。
- `purpose=library-resource` 和 `learning-input` 的 knowledgeBaseId、projectId 都允许为空；传入时必须校验归属和关系。
- 除 `source=microphone` 的临时录音外，成功上传的媒体都必须创建第 7 节资料库资源；聊天、资料库、智能学习分别写入对应 origin。
- source 仅说明入口来自上传或摄像头，不影响权限和识别算法。
- 以 userId + operation + clientRequestId 保证幂等；相同请求返回原资产，不重复保存文件。

成功：HTTP 201，ApiResult<MediaAssetDto>。

示例 metadata：

~~~json
{
  "source": "camera",
  "purpose": "chat-attachment",
  "conversationId": 42,
  "knowledgeBaseId": null,
  "projectId": null,
  "clientRequestId": "d682cf5f-5037-4cc5-841f-9280dc16ac51"
}
~~~

成功响应：

~~~json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "kind": "image",
    "source": "camera",
    "purpose": "chat-attachment",
    "fileName": "photo-20260716.jpg",
    "mimeType": "image/jpeg",
    "size": 1842201,
    "status": "uploaded",
    "conversationId": 42,
    "knowledgeBaseId": null,
    "projectId": null,
    "createdAt": "2026-07-16T16:30:00+08:00",
    "updatedAt": "2026-07-16T16:30:00+08:00"
  }
}
~~~

### 18.4 语音转文字

#### POST /api/media/audio/transcriptions

状态：NEW。Content-Type 为 multipart/form-data，字段固定为：

- file：必填，录音二进制。
- metadata：必填，Content-Type 为 application/json，内容是 TranscribeAudioRequest。

限制与行为：

- 支持 WebM/Opus、Ogg/Opus、M4A/MP4、WAV、MP3、AAC、FLAC；容器中存在视频轨道时拒绝。
- 单文件最大 25 MB，后端探测的真实时长最大 120 秒；durationMs 仅用于前端展示，不能作为服务端校验依据。
- 默认语言 zh-CN；后端可以自动检测并返回实际 language。
- 本接口首期同步返回，后端处理超时 90 秒，前端请求超时 120 秒。
- 无有效人声返回 HTTP 422 + NO_SPEECH_DETECTED，不返回空字符串成功。
- `source=microphone` 的转写文字只回填输入框，前端不得在识别完成后自动发送。
- `source=upload` 在用户发送消息或确认资料上传后执行；成功资产固定为 `kind=audio`、`source=upload`、`status=ready`，聊天后端复用已返回的 text，不得再次转写同一 asset。
- `purpose=chat-attachment` 时 conversationId 必填；其他 purpose 的 knowledgeBaseId、projectId 允许为空，传入时必须校验。
- 用户选择上传的音频成功后建立第 7 节资源记录，并在 `GET /api/resources` 中返回 `resourceId=media:{assetId}`、`fileType=audio`；麦克风临时录音不自动归档。
- 麦克风临时录音默认 24 小时后清理；用户主动上传并关联资料库/学习项目的音频按业务资源保留策略保存。删除聚合资源时同时解除关联，只有无其他引用时才删除原件。

成功：HTTP 200，ApiResult<AudioTranscriptionDto>。

### 18.5 图片识别任务

#### POST /api/media/images/{assetId}/recognition-jobs

状态：NEW。请求体为 CreateImageRecognitionRequest，成功返回 HTTP 202 + ApiResult<ImageRecognitionJob>。

- mode=auto：结合用户 prompt 与图片内容判断 general-image、document-ocr 或 question-capture。
- mode=ocr：强制做文字版面识别，保留自然阅读顺序。
- mode=question：提取题干、选项和可见公式；不根据图片猜测用户答案。
- prompt 最大 2000 字符，只作为识别意图补充，不能覆盖权限、purpose 或实体关联。
- asset 必须属于当前用户且状态不是 failed；重复 clientRequestId 返回原任务。

#### GET /api/media/jobs/{jobId}

状态：NEW。成功返回 ApiResult<ImageRecognitionJob>。

- 前端初次 1 秒后查询；运行中采用 1、2、3、5、5 秒间隔，最长等待 120 秒。
- 页面刷新后可使用 jobId 继续查询；jobId 只能查询当前用户任务。
- 任务失败保留原图片资产，允许用户重试；取消任务不删除图片。
- succeeded 必须包含 result，failed 必须包含稳定 errorCode 和可展示 errorMessage。

### 18.6 状态机与数据流

语音前端状态：

~~~text
idle -> requesting-permission -> recording -> transcribing -> idle
  ^              |                |              |
  |              +-- denied -----+-- failed ----+
  +---------------- cancel / unsupported --------+
~~~

- 拒绝权限、取消授权、没有设备或浏览器不支持时回到 idle，保留原输入文字。
- 录音达到 120 秒自动停止并转写；转写失败保留原输入文字并提供重试。
- 组件卸载、路由离开或用户中止时停止所有 MediaStream track，并取消尚未完成的 HTTP 请求。

上传音频状态：

~~~text
selected -> transcribing -> ready
               |
               +-> failed -> selected
~~~

- selected 阶段只保存在当前页面内存，用户可以删除或取消；用户发送/确认后才调用转写接口。
- failed 保留文字和附件选择，允许重试；正式 API 失败不得使用 Mock 文本伪造 ready。

图片资产状态：

~~~text
frontend selected -> uploading -> uploaded -> processing -> ready
                         |             |            |
                         +---------- failed <-------+
~~~

识别任务状态：

~~~text
pending -> running -> succeeded
   |         |
   +---------+-> failed
   +---------+-> cancelled
~~~

图片聊天数据流：

1. 前端选择图片或调用后置摄像头，完成格式、大小和数量预校验。
2. 前端调用 POST /api/media/images，取得 MediaAssetDto.id。
3. 用户点击发送后，前端调用 POST /api/chat/stream，并传 mediaAssetIds。
4. 后端校验媒体资产、会话和用户关系，再根据用户问题与图片内容决定直接多模态理解、OCR 或题目识别。
5. 后端把媒体资产引用写入用户消息，SSE 只传回答文本和完成/错误事件。
6. 上传或发送失败时不得创建伪成功消息；前端保留待发送文字和附件以便重试。

音频附件聊天数据流：

1. 前端选择音频并校验格式、25 MB 和附件总数，视频立即拒绝。
2. 用户点击发送后，前端调用 `POST /api/media/audio/transcriptions`，metadata 使用 `source=upload`、`purpose=chat-attachment` 和 conversationId。
3. 成功后前端把 `AudioTranscriptionDto.asset.id` 放入 mediaAssetIds，把转写文本加入本轮 fileContext，并调用聊天 SSE。
4. 后端验证音频资产与会话归属，复用已保存转写文本作为当前轮上下文，不重复调用语音模型。
5. 转写失败时前端不创建伪成功消息、不发起聊天 SSE，并保留待发送文字和附件供重试。

资料库/智能学习数据流：

1. purpose=library-resource 或 learning-input 上传图片，或提交音频转写。
2. 后端保存私有原件和媒体元数据；图片启动识别/索引，音频保存转写并创建资料库关联。
3. GET /api/resources 返回 media:{assetId} 资源及 processing/ready/failed 状态。
4. 只有 ready 图片或音频可以进入 RAG 或正式学习生成上下文；failed 可通过聚合资源 retry 接口重试。

### 18.7 意图、上下文与权限

- 前端只发送显式 mode、purpose、用户 prompt 和实体 ID，不在浏览器用关键词规则决定 OCR、题目类型或模型。
- 后端 auto 意图至少使用用户当前问题、媒体 purpose、图片内容和会话/学习项目类型；低置信度时按 general-image 处理或向用户追问，不能静默执行高风险动作。
- 图片识别结果加入当前消息上下文，不默认污染后续所有会话；需要跨轮引用时由后端已保存的消息媒体关联恢复。
- projectId、knowledgeBaseId 和 conversationId 必须逐个反查当前用户；三者同时存在时必须属于同一用户且关联关系一致，否则返回 409 CONTEXT_MISMATCH。
- 任何前端传入的 OCR 文本、durationMs、mimeType、source 和 confidence 都不是权限或业务真值。

### 18.8 错误码

| HTTP | errorCode | 触发条件 |
| --- | --- | --- |
| 400 | MEDIA_CONTEXT_REQUIRED | chat-attachment 缺少 conversationId |
| 400 | CONTEXT_MISMATCH | 会话、资料库和学习项目关联冲突 |
| 403 | MEDIA_FORBIDDEN | 当前用户无权访问媒体或上下文实体 |
| 404 | MEDIA_NOT_FOUND | 媒体资产不存在 |
| 409 | MEDIA_NOT_READY | 资产状态不允许当前操作 |
| 409 | IDEMPOTENCY_CONFLICT | 相同 clientRequestId 对应不同文件或请求 |
| 413 | FILE_TOO_LARGE | 图片超过 10 MB 或音频超过 25 MB |
| 415 | UNSUPPORTED_MEDIA_TYPE | 扩展名、MIME 或文件签名不支持/不一致 |
| 422 | NO_SPEECH_DETECTED | 录音中没有可识别人声 |
| 422 | AUDIO_DURATION_EXCEEDED | 音频真实时长超过 120 秒 |
| 422 | IMAGE_RECOGNITION_REJECTED | 图片损坏、不可解码或无法识别 |
| 429 | MEDIA_RATE_LIMITED | 上传、转写或识别频率超限 |
| 503 | MEDIA_AI_UNAVAILABLE | 语音或视觉模型服务不可用 |

浏览器的 MIC_PERMISSION_DENIED、CAMERA_PERMISSION_DENIED、DEVICE_NOT_FOUND 和 MEDIA_RECORDER_UNSUPPORTED 属于前端本地错误，不请求后端，也不伪装成 HTTP 错误。

## 19. PPT 生成与讯飞适配

### 19.1 唯一流程

状态：NEW。PPT 不再区分“先确认大纲”和“自动生成”两种模式，正式流程固定为：

~~~text
对话确认卡或功能入口
→ 创建/更新 PPT 草稿
→ AI 生成结构化大纲
→ 用户进入大纲页检查和修改
→ 保存确认后的大纲
→ 创建 PPT 生成任务
→ 生成完成并自动创建资料库资源
→ 预览或下载
~~~

“生成大纲”只表示 AI 自动填充大纲，绝不跳过用户确认。配置页不能直接进入最终生成；只有 `status=outline_ready` 且大纲非空时，前端才展示“确认大纲并生成”。后端在大纲未就绪时收到生成请求必须返回 409 `PRESENTATION_OUTLINE_NOT_READY`。

讯飞 AppId、密钥、模板映射、Provider taskId、回调签名和原始下载地址仅存在于后端。前端只识别本节 DTO、后端 jobId、资源 ID 和本站下载接口。

### 19.2 路由与对话卡

| 入口 | 路由/动作 | 必须携带的上下文 |
| --- | --- | --- |
| 新对话快捷入口或自然语言意图 | SSE `presentation-card` | conversationId、sourceMessageId，可选 knowledgeBaseId/projectId |
| 卡片“更多设置” | 先创建草稿，再进入 `/presentations/{id}` | 同一 presentationId，卡片与工作区同步 |
| 卡片“生成大纲” | 创建草稿并启动大纲任务，进入 `/presentations/{id}` | 不生成最终 PPT |
| 直接新建 | `/presentations/new` | 可选 knowledgeBaseId/projectId/returnTo |
| 智能学习资源 | `/presentations/new?projectId=:id&learningResourceId=:id&knowledgeBaseId=:id` | 三个实体必须属于同一用户 |
| 资料库资源 | `/presentations/{id}` | 由 `externalKey=presentations:{id}` 或资源 ID 打开 |

配置页主题、页数、模板、知识库等改变后调用草稿更新接口；返回对话时，消息卡必须显示同一草稿的最新配置。消息卡不保存另一份 PPT 配置。

SSE 事件示例：

~~~text
event: presentation-card
data: {"cardType":"presentation","view":"proposal","status":"draft","conversationId":102,"sourceMessageId":9081,"knowledgeBaseId":1,"projectId":null,"config":{"topic":"Java 多态","title":"Java 多态","pageCount":8,"templateId":"ink-focus","aspectRatio":"16:9","style":"academic","audience":"student","language":"zh-CN"}}
~~~

### 19.3 共享类型

以 `frontend/src/types/contracts/presentation.ts` 为准，关键字段如下：

~~~ts
type PresentationStatus =
  | "draft" | "outlining" | "outline_ready"
  | "generating" | "ready" | "failed" | "cancelled"

type PresentationContext = {
  conversationId?: number | null
  sourceMessageId?: number | string | null
  knowledgeBaseId?: number | null
  projectId?: number | null
  learningResourceId?: number | null
}

type PresentationConfig = {
  topic: string
  title: string
  pageCount: number
  templateId: string
  aspectRatio: "16:9" | "4:3"
  style: "academic" | "minimal" | "vibrant" | "professional"
  audience: "student" | "teacher" | "general" | "business"
  language: string
  sourceText?: string
  sourceFileNames?: string[]
  mediaAssetIds?: string[]
}

type PresentationDto = PresentationContext & {
  id: string
  status: PresentationStatus
  config: PresentationConfig
  outline: PresentationSlideOutline[]
  previewPages: PresentationPreviewPage[]
  activeJobId?: string
  fileName?: string
  fileSize?: number
  resourceId?: string
  errorCode?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}
~~~

`resourceId` 在 `ready` 状态必填。`PresentationChatCardDto` 的 proposal/result 状态必须由同一 Presentation 实体组装，不允许前端猜测最终状态。
proposal 中 `knowledgeBaseId` 是用户可编辑字段，前端在页数旁显示统一知识库选择器；选择“无”发送 `null`，新建知识库后发送新 id。创建或更新 Presentation 时以后一次卡片值为准。

### 19.4 接口与调用顺序

1. `GET /api/presentations/templates`：返回可用模板。
2. `GET /api/presentations`：返回当前用户 PPT 列表。
3. `GET /api/presentations/{id}`：返回实体及恢复状态。
4. `POST /api/presentations`：创建 draft，请求为 `CreatePresentationRequest`，包含 `clientRequestId`。
5. `PUT /api/presentations/{id}/draft`：全量更新 config 和可选上下文，请求为 `UpdatePresentationDraftRequest`。
6. `PUT /api/presentations/{id}/associations`：请求 `{ projectId?, knowledgeBaseId?, learningResourceId?, clientRequestId }`，在 draft 和 ready 阶段都可更新关联；同时更新 Presentation、同一 `LibraryResourceDto` 和项目资源条目。
7. `POST /api/presentations/{id}/cancel`：请求 `{ clientRequestId }`，取消 proposal/draft；存在活动任务时一并取消，保留实体用于刷新恢复。
8. `POST /api/presentations/{id}/outline-jobs`：请求 `{ clientRequestId }`，创建大纲任务。
9. `PUT /api/presentations/{id}/outline`：请求 `{ slides, clientRequestId }`，保存用户确认后的结构化大纲。
10. `POST /api/presentations/{id}/generation-jobs`：请求 `{ clientRequestId }`，创建最终 PPT 任务。
11. `GET /api/presentations/jobs/{jobId}`：返回统一 `AsyncJob`。
12. `POST /api/presentations/jobs/{jobId}/cancel`：停止任务但保留配置和大纲。
13. `GET /api/presentations/{id}/preview-pages/{pageId}`：返回本站预览图。
14. `GET /api/presentations/{id}/download`：仅 ready 可下载 PPTX。

没有“保存到资料库”按钮或第二份文件。最终生成事务必须：保存本站 PPTX、更新 Presentation 为 ready、创建或更新全局 `LibraryResourceDto`、写入 `resourceId`、同步对话卡和项目资源包。若用户选择知识库或项目，同时写入对应关联；`learningResourceId` 为空但 `projectId` 非空时必须新增项目资源条目，不能跳过资源包。两者都未选择时资源仍进入资料库。前端随后调用第 10.9 节幂等回写接口作一致性确认，后端重复处理不得产生副本。

### 19.5 状态机、刷新与异常

- `draft -> outlining -> outline_ready -> generating -> ready` 是唯一成功路径。
- `outlining/generating -> failed | cancelled`；重试复用原实体并创建新 jobId。
- 前端刷新后通过 `GET /api/presentations/{id}` 和 `activeJobId` 恢复，不依赖本地生成进度。
- `clientRequestId` 在同一用户、接口和实体范围内幂等；重复点击不得创建多个 Provider 任务或多个资料库资源。
- 配置更新只允许 draft、outline_ready、cancelled；大纲编辑不允许覆盖正在生成的版本。
- sourceMessageId 存在时，后端每次状态提交都同步消息 `presentationData`；ready/failed 使用 result 视图。
- 错误码至少包含：`PRESENTATION_NOT_FOUND`、`PRESENTATION_FORBIDDEN`、`PRESENTATION_CONTEXT_MISMATCH`、`PRESENTATION_OUTLINE_NOT_READY`、`PRESENTATION_JOB_CONFLICT`、`PRESENTATION_PROVIDER_FAILED`、`PRESENTATION_DOWNLOAD_NOT_READY`。

### 19.6 Mock 与正式实现边界

Mock Repository 使用 sessionStorage 保存实体和任务元数据，只在下载时用 PptxGenJS 生成演示 PPTX。正式 API Repository 不导入 Mock 生成器；后端可接讯飞或其他 Provider，但必须转换为本节 DTO，且最终文件由本站存储和下载。

## 20. 电子表格生成

### 20.1 能力边界与唯一流程

状态：NEW。电子表格上传/读取属于资料解析；生成新电子表格由聊天编排服务和 Spreadsheet Repository 协作完成。前端不提供配置页或工作簿确认步骤，唯一流程为：

~~~text
用户在对话中说明要求并可上传文件
→ 聊天服务结合 conversationId、消息附件、knowledgeBaseId、projectId 判断信息是否充分
→ 信息不足：普通对话追问，不创建任务
→ 信息充分：创建 Spreadsheet 并立即启动 generation job
→ AI 根据上下文生成受约束的工作簿 JSON
→ 后端校验 JSON 并生成 XLSX
→ Spreadsheet=ready，并在同一业务提交中创建资料库资源
→ 对话卡和预览页展示结果，用户可预览或下载
~~~

“直接生成”表示不要求用户经过前端配置和人工确认，不表示信任模型直接返回二进制。正式后端仍应让 AI 生成结构化工作簿 JSON，校验工作表名、列数、行数、值类型和公式策略后，再用 Apache POI、EasyExcel 或等价库生成 XLSX。Mock 根据同一请求生成演示工作簿，并在下载时使用 ExcelJS 生成文件。

### 20.2 路由、卡片与类型

- `/spreadsheets/new` 只重定向 `/chat?intent=spreadsheet`，用于预填生成要求，不承载配置页面。
- 前端不提供 `/spreadsheets/{id}` 独立页面；任务状态在聊天统一附件中恢复，ready 文件只使用 `/resources/{resourceId}/preview` 统一只读预览。
- 对话显式动作：`clientAction=spreadsheet.create`。
- SSE 事件：统一使用第 5.2、22 节的 `artifact`，只更新同一 artifactId 的 `queued/generating/ready/failed/cancelled` 状态，结构保存到消息 `artifacts`。`spreadsheet-card/spreadsheetData` 仅用于读取迁移期历史消息。
- 资料库资源使用 `externalKey=spreadsheet:{id}`，`fileType=spreadsheet`。

~~~ts
type SpreadsheetStatus =
  | "generating" | "ready" | "failed" | "cancelled"

type SpreadsheetContext = {
  conversationId?: number | null
  sourceMessageId?: number | string | null
  knowledgeBaseId?: number | null
  projectId?: number | null
}

type SpreadsheetConfig = {
  topic: string
  title: string
  sheetCount: number
  language: string
  requirements?: string
}

type CreateSpreadsheetRequest = SpreadsheetContext & {
  prompt: string
  resourceIds?: string[]
  mediaAssetIds?: string[]
  clientRequestId: string
}

type SpreadsheetSheetDraft = {
  sheetId: string
  name: string
  columns: string[]
  rows: Array<Array<string | number | boolean | null>>
}

type SpreadsheetDto = SpreadsheetContext & {
  id: string
  status: SpreadsheetStatus
  config: SpreadsheetConfig
  workbook: { sheets: SpreadsheetSheetDraft[] }
  activeJobId?: string
  fileName?: string
  fileSize?: number
  resourceId?: string
  errorCode?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

type SpreadsheetChatCardDto = SpreadsheetContext & {
  cardType: "spreadsheet"
  status: SpreadsheetStatus
  spreadsheetId: string
  config: SpreadsheetConfig
  fileName?: string
  resourceId?: string
  errorMessage?: string
}
~~~

`SpreadsheetChatCardDto` 是迁移期兼容类型，不再对应独立前端组件；API 新响应必须把 SpreadsheetDto 映射为 `ChatArtifactDto`。电子表格没有编辑按钮和 `/spreadsheets/{id}` 前端页面，只能进入统一只读预览或下载。

`prompt` 是当前用户完整要求；聊天服务还必须读取该会话已保存的历史消息和 `sourceMessageId` 对应附件。`resourceIds/mediaAssetIds` 用于显式补充来源，不能代替权限校验。`config` 和 `sheetCount` 是后端生成后的摘要，不是前端配置请求。

首期工作表数量为 1 至 5；工作表名不能为空、去重后最长 31 个字符。后端必须限制最大行列数、单元格字符串长度和总 JSON 大小；不得执行 AI 返回的宏、外部链接或不可信公式。超限时任务进入 `failed`，不能截断后伪造完整结果。

### 20.3 接口

1. `GET /api/spreadsheets`：返回当前用户表格列表。
2. `GET /api/spreadsheets/{id}`：返回实体及恢复状态。
3. `POST /api/spreadsheets/generation-jobs`：请求 `CreateSpreadsheetRequest`，在同一事务中创建 Spreadsheet 和任务，返回 `SpreadsheetDto`，初始 `status=generating` 且包含 `activeJobId`。
4. `POST /api/spreadsheets/{id}/generation-jobs`：失败或取消后重试，请求 `{ clientRequestId }`，返回 `AsyncJob<{ spreadsheetId }>`。
5. `GET /api/spreadsheets/jobs/{jobId}`：返回统一 `AsyncJob<{ spreadsheetId }>`。
6. `POST /api/spreadsheets/jobs/{jobId}/cancel`：取消任务但保留原始 prompt 和上下文，状态变为 `cancelled`。
7. `GET /api/spreadsheets/{id}/download`：仅 `ready` 返回 XLSX 文件流。

生成成功必须在同一业务提交中创建或更新资料库资源并返回 `resourceId`。选择了 `knowledgeBaseId`、`projectId` 时写入对应关联；均为空时只进入资料库。不存在额外“保存到资料库”步骤。

聊天入口不要求浏览器再调用一次创建接口：后端聊天编排层识别到明确需求后应调用同一 Spreadsheet Application Service，创建任务并通过统一 `artifact` 事件返回状态。`spreadsheet-card` 只用于读取历史消息兼容，不得用于新响应。`POST /api/spreadsheets/generation-jobs` 供非 SSE 编排、重试恢复测试或其他明确入口复用，两条路径必须使用同一业务服务和幂等规则。

### 20.4 状态、幂等与错误

- 唯一成功路径：`generating -> ready`；可以从 `generating` 进入 `failed/cancelled`。
- 重试复用原 Spreadsheet 实体和 `resourceId/externalKey`，但创建新的 jobId；不得重新创建第二个资料库资源。
- 聊天附件、统一预览和资料库都以同一 Spreadsheet/Resource 权威状态恢复；不存在电子表格工作区。
- `clientRequestId` 防重复任务；`externalKey=spreadsheet:{id}` 建唯一资源约束。
- 相同 `clientRequestId`、用户和操作必须返回原 Spreadsheet/Job；相同键对应不同 prompt 或上下文时返回 409 `IDEMPOTENCY_CONFLICT`。
- `conversationId/sourceMessageId/knowledgeBaseId/projectId/resourceIds/mediaAssetIds`都必须反查当前用户；项目和知识库不一致返回 409。
- 错误码至少包含：`SPREADSHEET_NOT_FOUND`、`SPREADSHEET_FORBIDDEN`、`SPREADSHEET_CONTEXT_MISMATCH`、`SPREADSHEET_INPUT_INSUFFICIENT`、`SPREADSHEET_STRUCTURE_INVALID`、`SPREADSHEET_LIMIT_EXCEEDED`、`SPREADSHEET_JOB_CONFLICT`、`SPREADSHEET_GENERATION_FAILED`、`SPREADSHEET_DOWNLOAD_NOT_READY`。

## 21. 统一资源在线预览

### 21.1 前端入口与职责边界

状态：NEW。资料库、知识库详情、智能学习资源包和对话生成结果都使用同一个只读路由：

~~~text
/resources/{resourceId}/preview?returnTo={内部路径}&source={library|knowledge|learning|chat}
~~~

该页面只负责预览和下载，不包含文件问答输入框，不创建会话，也不改变 `projectId/knowledgeBaseId` 关联。PPT 工作区中的“配置 -> 大纲 -> 生成 -> 预览”第 4 步是生成流程的一部分，继续使用 Presentation 自身的 `previewPages`；PPT 在资料库、资源包和聊天结果卡中的“预览”才进入统一资源预览路由。电子表格生成任务的 generating/failed 恢复页可保留，ready 文件的外部预览统一进入资源预览路由。

所有入口只传 `resourceId`。后端必须通过资源记录反查文件、用户权限、类型及 Presentation/Spreadsheet 等业务实体，不能信任前端传递的文件 URL、MIME 或关联 ID。

### 21.2 共享响应

~~~ts
type ResourcePreviewStatus =
  | "processing" | "ready" | "failed" | "unsupported" | "too_large"

type ResourcePreviewKind =
  | "text" | "image" | "pdf" | "word"
  | "presentation" | "spreadsheet" | "mindmap" | "audio"
  | "unsupported"

type ResourcePreviewDto = {
  resource: LibraryResourceDto
  status: ResourcePreviewStatus
  previewKind: ResourcePreviewKind
  textContent?: string
  previewUrl?: string
  transcript?: string
  presentationId?: string
  spreadsheetId?: string
  mindMapId?: number
  previewData?: ArtifactInlinePreview
  errorMessage?: string
}
~~~

接口：`GET /api/resources/{resourceId}/preview`。

- `resource.status=waiting/processing` 时返回 `status=processing`，不得伪造可预览结果。
- 解析失败返回 `status=failed` 和用户可读 `errorMessage`。
- 格式不支持返回 `status=unsupported`；文件超限返回 `status=too_large`。这两种业务状态允许 HTTP 200，便于前端展示下载兜底。
- 资源不存在返回 404 `RESOURCE_NOT_FOUND`；无权访问返回 403 `RESOURCE_FORBIDDEN`。
- `previewUrl` 必须是本站或受控对象存储的短期签名 URL，禁止返回本机路径，建议 5 至 15 分钟过期。
- 不允许返回 Base64 文件正文。`textContent` 只用于受大小限制的文本、Markdown 和代码。
- `presentationId/spreadsheetId/mindMapId` 只在资源确实关联本站结构化实体时返回；前端可继续读取对应只读结构。普通上传的 Office 文件由预览服务转换后返回 PDF 或分页图片 URL。

### 21.3 格式和大小限制

| 类型 | 在线预览上限 | 正式处理方式 |
| --- | ---: | --- |
| Markdown、TXT、代码、JSON | 10MB | 安全转义或受控 Markdown 渲染 |
| 思维导图 | 10MB | 返回只读树结构或受控预览数据 |
| 图片 | 20MB | 返回缩放图/原图短期 URL |
| PDF | 30MB | 支持 Range 的文件流或短期 URL |
| Word | 30MB | 服务端转 PDF/分页图片，不能依赖前端 Mammoth 保真 |
| PPT/PPTX | 30MB | 生成实体返回 previewPages；上传文件服务端转 PDF/分页图片 |
| Excel/CSV | 30MB | 生成实体返回受限工作簿 JSON；上传文件服务端解析只读网格 |
| 音频 | 30MB | 返回可流式播放 URL，可选返回已有转写文本 |
| ZIP/RAR/7Z、视频 | 不支持 | 只允许下载 |

前端在读取预览正文前按 `sizeBytes` 拦截，后端仍必须执行同样限制。Word、PPT、PDF 和 Excel 的转换任务应复用文件解析状态；超时、转换失败和恶意文件不能回退为“成功”。

### 21.4 性能、安全与缓存

- PDF、图片和音频必须支持流式读取或 Range；分页图片按可视区域懒加载。
- 同一资源版本可缓存预览产物；资源内容或版本变化时必须使旧预览失效。
- 文本和 Office 转换结果必须移除脚本、外链执行、宏和嵌入对象；预览域不得携带主站认证 Cookie。
- 前端离开路由时取消未完成请求并释放 Blob URL；后端转换任务要有超时、并发和单用户配额。
- `returnTo` 仅由前端作为内部返回路径使用，后端不处理；前端只接受以单个 `/` 开头的站内路径。
- 下载继续使用 `GET /api/resources/{resourceId}/download`，预览失败、超限或不支持时仍可下载有权限的原文件。

### 21.5 Mock 边界

Mock Repository 使用与正式环境相同的 `ResourcePreviewDto`。生成的 PPT、表格、思维导图和文本读取现有结构化 Mock 实体；当前标签页刚上传的图片、PDF、DOCX、XLSX、文本和音频可使用内存 Blob URL。真实文件正文不写入 sessionStorage，因此整页刷新后只剩元数据时必须返回明确失败状态，不能生成假的文件内容。API 模式不得在预览接口失败后回退 Mock。

## 22. 对话统一附件模型

### 22.1 共享类型

~~~ts
type ChatArtifactStatus = "queued" | "generating" | "ready" | "failed" | "cancelled"

type ArtifactInlinePreview = {
  kind: "image" | "mindmap" | "document" | "spreadsheet" | "presentation" | "none"
  imageUrl?: string
  text?: string
  table?: { sheetName?: string; columns: string[]; rows: Array<Array<string | number | boolean | null>> }
  mindMap?: { data: { text: string }; children?: unknown[] }
  mindMapConfig?: MindMapRenderConfig
  slides?: Array<{ title: string; points?: string[] }>
}

type ChatArtifactDto = {
  artifactId: string
  resourceId?: string
  jobId?: string
  sourceMessageId?: number | string | null
  conversationId?: number | null
  projectId?: number | null
  learningResourceId?: number | null
  knowledgeBaseId?: number | null
  title: string
  fileName: string
  fileType: "image" | "document" | "spreadsheet" | "presentation" | "pdf" | "audio" | "archive" | "mindmap" | "other"
  format: string
  mimeType?: string
  sizeBytes?: number
  status: ChatArtifactStatus
  progress?: number
  preview: ArtifactInlinePreview
  editable?: boolean
  editorRoute?: string
  errorCode?: string
  errorMessage?: string
}
~~~

### 22.2 状态、资源与删除规则

- 状态只能按 `queued -> generating -> ready` 或 `queued/generating -> failed/cancelled` 演进；重试复用业务实体但使用新 jobId。
- ready 前不得返回伪 resourceId；ready 后 `resourceId` 必须对应第 7 节的真实资源记录和私有文件。
- 同一请求生成 DOCX 与 PDF 时返回两个 artifact 和两个 resourceId，不把多个物理文件隐藏在一个下载按钮后。
- 删除会话只解除消息引用，不删除资料库资源；删除资料库资源后历史附件保留元数据并显示资源不可用。
- 正式意图识别、文件生成、格式转换和对象存储全部由后端执行；前端 Mock 正则和演示内容不得进入正式业务实现。
- SSE 中止时保留已输出 Markdown；后端是否取消文件任务按 job 能力处理。已经 ready 的资源不得因客户端断流而删除。
- 卡片操作顺序由前端固定为“编辑（仅支持类型，最左）/ 下载 / 预览（最右）”。当前支持编辑的是 PPT 与思维导图；电子表格、DOCX、PDF、图片和其他普通附件不返回编辑入口。
- Mock 和 API 使用同一 DTO、状态机与页面组件。区别仅在数据来源：Mock 在 sessionStorage 中复现 upsert/关联状态，API 通过本契约接口持久化；生产构建禁止在 API 失败时回退 Mock。
