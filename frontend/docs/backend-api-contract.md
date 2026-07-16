# 学生端前后端零猜测交接契约

> 版本：1.1
>
> 更新日期：2026-07-16
>
> 适用范围：新对话、资料库、智能学习、语音输入、拍照/图片上传及其关联的思维导图、登录与用户设置。
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
- 语音转文字、拍照/图片上传、图片 OCR/题目识别和聊天多模态引用。

首期不包含：

- 文件在线预览专用接口。
- 选中多个资料后直接创建聊天上下文。
- 资料库高级筛选。
- DOC 的 AI 解析。当前文档解析正式支持 PDF、DOCX、MD、TXT；图片走第 18 节媒体接口。

## 1. 现有后端复用矩阵

| 模块 | 当前后端 | 目标状态 | 说明 |
| --- | --- | --- | --- |
| 用户登录、注册、信息、设置 | 已存在 | KEEP | 保留 /api/user 路径和 Bearer JWT |
| 模型列表与模型选择 | 部分存在 | EXTEND | 复用 system_config，新增前端需要的模型列表接口，聊天服务必须使用已校验模型 |
| 会话创建、列表、消息、更新、删除 | 已存在 | EXTEND | 增加学习项目关联、类型和置顶字段；更新接口必须返回更新后的对象 |
| AI 聊天 SSE | 已存在 | EXTEND | 保留当前文本事件协议，补中止、超时、错误 HTTP 状态和模型校验 |
| 知识库 CRUD | 已存在 | EXTEND | 增加 availableForAi，响应改用 VO，不返回 userId、逻辑删除字段 |
| 文档上传、解析、状态、下载 | 已存在 | KEEP | 保留数字状态 0/1/2；资料库聚合层映射为英文状态 |
| 思维导图 CRUD 与 AI 生成 | 已存在 | KEEP | 继续复用 mind_map 表和 /api/mindmap |
| 公共资源中心 | 已存在 | KEEP | 不等于学生资料库聚合接口，可复用文件存储逻辑 |
| 资料库聚合资源 | 不存在 | NEW | 聚合 document、mind_map、learning_resource |
| 智能学习 | 不存在 | NEW | 新增项目、任务、题目、答题、错题、资源与生成任务 |
| 语音、图片与识别任务 | 不存在 | NEW | 新增 media_asset、语音转写和图片识别任务；聊天只引用媒体资产 ID |

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

前端路由参数、libraryId、projectId、conversationId 均不构成授权。

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
  learningProjectId: number | null
  learningProjectName?: string | null
  conversationType: ConversationType
}

type CreateConversationRequest = {
  title?: string
  kbId?: number | null
  learningProjectId?: number | null
  learningProjectName?: string
  conversationType?: ConversationType
}

type UpdateConversationRequest = {
  title?: string
  isPinned?: boolean
  knowledgeBaseId?: number | null
  learningProjectId?: number | null
  learningProjectName?: string
  conversationType?: ConversationType
}
~~~

兼容规则：

- 创建请求沿用现有字段 kbId。
- 更新请求当前前端使用 knowledgeBaseId；后端将其写入 conversation.kb_id。
- 响应沿用 kbId，前端 Repository 会映射为 knowledgeBaseId。
- learningProjectName 只用于兼容展示，后端有 learningProjectId 时应从项目表读取权威名称。
- title 缺失或空白时后端使用“新对话”，最长 200。
- conversationType 缺失时默认为 general。
- learning-setup 和 learning-tutor 必须提供有效 learningProjectId。
- general 的 learningProjectId 必须为 null。

### 4.3 接口

#### POST /api/conversation/create

状态：EXTEND。

请求：CreateConversationRequest。

成功：ApiResult<ConversationDto>。

后端步骤：

1. 校验 kbId 和 learningProjectId 归属。
2. 校验 conversationType 与 learningProjectId 组合。
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
}
~~~

sourceChunks 必须是 JSON.stringify(MessageSourceDto[])；files 必须是 JSON.stringify(MessageAttachmentDto[])。这是现有数据库兼容格式，前端会解析。无值时返回 null，不返回非法 JSON。

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
}
~~~

规则：

- conversationId 必填并校验权限。
- 普通发送 question 必须非空；isRegenerate=true 时允许为空并从目标用户消息恢复。
- kbId 只作兼容，实际 RAG 知识库以 conversation.kbId 为准；两者冲突时返回 409。
- model 缺失时使用用户设置 defaultModel；必须校验模型白名单。
- history 只用于补充上下文，不能覆盖后端保存的权限和项目关联。
- fileContext 只承载 PDF、DOCX、MD、TXT 的当前兼容附件正文，最大 200000 字符。
- files 必须是合法 MessageAttachmentDto[] JSON 字符串。
- mediaAssetIds 只承载第 18 节已上传图片资产 ID；最多 5 个，后端逐个校验所有权、状态和 conversationId 关联。
- 同一图片同时出现在 files 元数据和 mediaAssetIds 时，以 mediaAssetIds 作为模型输入，files 只用于消息展示，禁止重复处理。
- 学习助教必须通过 conversation.learningProjectId 加载权威项目上下文，不能只信任前端拼接的 tutorContext。

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
- 重新生成：保留旧版本，新助手消息递增 aVersion。
- 编辑问题：创建同 turnId 的新 qVersion，不覆盖原消息。
- 前端停止使用 AbortController 中断 HTTP。
- 后端监听 SseEmitter completion、timeout、error；客户端断开后必须取消上游 AI 订阅。
- 被中止的助手回答不保存为正常完成消息；已经保存的用户问题保留。
- SSE 超时配置为 120 秒，可配置，禁止 SseEmitter(0L) 无限占用。

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

支持 PDF、DOCX、MD、TXT，单文件最大 100 MB。文件名必须安全重命名，原文件名仅保存为元数据。

成功：ApiResult<DocumentDto>，初始 status=0。

#### POST /api/doc/extract

状态：KEEP，聊天附件兼容接口。

multipart 字段 file。成功：ApiResult<string>。

该接口只解析当前聊天附件，不写入 knowledge_base、document、ES。限制与 upload 相同，解析正文最大 200000 字符。

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

## 7. 资料库聚合资源

### 7.1 复用方式

NEW。该模块不是重新存一份文件，而是统一查询和操作：

- document：用户上传文档。
- mind_map：思维导图。
- learning_resource：智能学习生成资源。
- media_asset：图片上传、拍照及其识别/索引结果。

公共资源中心 resource、user_resource 不直接混入学生私人资料库列表；用户把公共资源加入知识库时，可以生成 document 或新的资源关联。

### 7.2 DTO 与聚合 ID

~~~ts
type LibraryResourceStatus = "waiting" | "processing" | "ready" | "failed"
type LibraryResourceCategory = "file" | "image" | "mindmap"
type LibraryResourceSource =
  | "资料库上传"
  | "智能学习上传"
  | "聊天上传"
  | "智能学习生成"
  | "聊天生成"

type LibraryResourceDto = {
  id: string
  name: string
  type: string
  size: string
  status: LibraryResourceStatus
  errorMessage?: string
  updatedAt: string
  category: LibraryResourceCategory
  source: LibraryResourceSource
  projectId: number | null
  libraryId: number | null
  externalKey?: string
}
~~~

id 是不可猜测的聚合路由键：

- document:123
- mindmap:45
- learning-resource:78
- media:550e8400-e29b-41d4-a716-446655440000

后端必须解析前缀并分发到对应 Service。不存在的前缀返回 400，实体不存在返回 404。

展示字段规则：

- updatedAt 始终返回 ISO 8601，前端负责转为“刚刚/今天”等文案。
- size 根据原始 byte 计算：小于 1024 显示 N B；小于 1 MB 四舍五入显示 N KB；其余保留 1 位显示 N.N MB。
- document.type 映射为 PDF、Word、Markdown、TXT。
- mind_map.type 固定为“思维导图”，没有物理文件时 size 为“--”。
- externalKey 返回底层实体数字 ID 的字符串形式，主要用于排障；前端操作必须继续使用聚合 id。

映射规则：

| 来源实体 | category | status |
| --- | --- | --- |
| document status=0 | file | processing |
| document status=1 | file | ready |
| document status=2 | file | failed |
| mind_map | mindmap | ready |
| learning_resource generating | file、image 或 mindmap | processing |
| learning_resource ready | file、image 或 mindmap | ready |
| learning_resource failed | file、image 或 mindmap | failed |
| media_asset uploaded/processing | image | processing |
| media_asset ready | image | ready |
| media_asset failed | image | failed |

### 7.3 接口

#### GET /api/library/resources

状态：NEW。

Query：

- libraryId?: number。不传时返回当前用户全部资源。

成功：ApiResult<LibraryResourceDto[]>，按 updatedAt DESC。

#### POST /api/library/resources/upload

状态：NEW。

multipart：

- file：必填。
- libraryId：可选 number。
- projectId：可选 number。

至少提供 libraryId 或 projectId；两者都提供时必须属于同一用户，且项目关联该知识库。

首期该接口支持 PDF、DOCX、MD、TXT。图片必须调用第 18 节 `POST /api/media/images`，并在 metadata 中使用 `purpose=library-resource`；成功后同一媒体资产必须出现在聚合资源列表中，不能重复上传文件。

#### PATCH /api/library/resources/{id}

状态：NEW。WebConfig 必须增加 PATCH 到 CORS allowedMethods。

请求：{ name: string }，长度 1 至 200。

后端按资源类型更新原文件展示名、思维导图标题或学习资源标题，不重命名物理安全文件名。

#### POST /api/library/resources/{id}/move

状态：NEW。

请求：{ libraryId: number | null }。

- document：更新 kbId，并重新归属向量索引；无法原子完成时返回异步任务，不允许只改 MySQL。
- mindmap：更新 kbId。
- learning-resource：更新 libraryId。
- 目标知识库必须属于当前用户。

#### POST /api/library/resources/{id}/retry

状态：NEW。

- 仅 failed 的 document 或 learning-resource 可重试。
- 已在 processing 返回 409。
- ready 返回 409。
- mindmap 返回 409。

成功返回更新后的 LibraryResourceDto，状态 waiting 或 processing。

#### GET /api/library/resources/{id}/download

状态：NEW。返回 Blob/文件流：

- Content-Type 使用真实 MIME。
- Content-Disposition: attachment。
- mindmap 没有物理文件时导出 UTF-8 JSON 或 Markdown，文件名使用标题。

#### DELETE /api/library/resources/{id}

状态：NEW。

- document：复用 DocumentService 删除文件、分块和向量。
- mindmap：复用 MindMapService。
- learning-resource：删除生成文件和关联记录，但不删除学习项目。

## 8. 思维导图

现有 /api/mindmap 接口继续保留：

~~~ts
type MindMapDto = {
  id: number
  kbId: number | null
  title: string
  content: string
  createTime: string
  updateTime: string
}

type MindMapTreeNode = {
  data: { text: string }
  children: MindMapTreeNode[]
}
~~~

| 状态 | 方法与路径 | 请求 | 成功 data |
| --- | --- | --- | --- |
| KEEP | POST /api/mindmap/create | { title, kbId?, content } | number |
| KEEP | POST /api/mindmap/update | { id, title?, kbId?, content? } | null |
| KEEP | POST /api/mindmap/delete/{id} | 无 | null |
| KEEP | GET /api/mindmap/list?kbId={id} | 无 | MindMapDto[] |
| KEEP | GET /api/mindmap/detail/{id} | 无 | MindMapDto |
| KEEP | POST /api/mindmap/generate-from-ai | { content, title? } | { id, title, treeData } |

所有接口必须补齐用户权限校验。generate-from-ai 当前为同步接口，首期允许保留，但必须在 30 秒内完成；超过后应迁入第 10 节生成任务，不得让连接无限等待。

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

type LearningResourceStatus =
  | "not_selected"
  | "generating"
  | "ready"
  | "failed"

type WrongQuestionStatus = "needs_review" | "mastered"
type TrainingSetStatus = "pending" | "answering" | "submitted"
type WrongReviewSetStatus = "pending" | "answering" | "completed"
~~~

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
  libraryId: number
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
  confirmationDocument: string
}

type LearningConfirmationRequest = {
  libraryId: number
  goal: string
  profile: LearningProfileData
  uploadedFileNames?: string[]
  mediaAssetIds?: string[]
  relatedProjectName?: string
  questionCount?: number
  difficultyStrategy?: string
}

type CreateLearningDraftRequest = {
  title: string
  libraryId: number | null
  libraryName?: string
  icon?: string
  iconColor?: string
}

type CreateLearningPlanRequest = {
  prompt: string
  libraryId: number
  projectId: number | null
  targetType: string
  preferences: string[]
  resourceGroups: Array<
    "学习方案" | "个性化学习手册" | "PPT" |
    "思维导图" | "代码案例" | "图片"
  >
  period: string
  foundation: string
  weakPoints: string
  dailyTime: string
  studyDepth: string
  questionCount: number
  supplementalRequirement: string
  draftPlanId?: number | null
  libraryName?: string
}
~~~

字段语义：

- draftPlanId：正在配置并将被生成结果替换的草稿项目 ID。
- projectId：可选的关联项目上下文 ID，生成结果写入 relatedProjectId；不表示直接覆盖该项目。
- 两者相同且项目状态为 draft/configuring 时，按 draftPlanId 原地完成该草稿。
- 两者都为空时创建全新项目。
- libraryName 只用于兼容展示，权威名称由 libraryId 查询。
- questionCount 范围 1 至 500。
- 生成前 libraryId 必须存在、属于当前用户且 availableForAi=true。
- mediaAssetIds 最多 5 个，只接受 purpose=learning-input 且已关联当前 libraryId/projectId 的图片；后端读取图片内容，uploadedFileNames 只用于展示，不能替代媒体引用。

### 9.3 项目聚合 DTO

GET 项目列表和详情当前都返回同一个 LearningProjectDto。列表暂不分页。

~~~ts
type LearningProjectDto = {
  id: number
  relatedProjectId?: number | null
  title: string
  icon?: string
  iconColor?: string
  goal: string
  updatedAt: string
  libraryId: number
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
  resourceId?: number
  exerciseIds?: number[]
  status: LearningTaskStatus
  completionMode?:
    | "content" | "resource" | "exercise"
    | "assessment" | "case" | "manual"
  completionSource?: string
  readProgress?: number
  validStudySeconds?: number
  completedActions?: string[]
}

type LearningResourceDto = {
  id: number
  group:
    | "学习方案" | "个性化学习手册" | "PPT"
    | "思维导图" | "代码案例" | "图片"
  title: string
  desc: string
  status: LearningResourceStatus
  action: string
  fileName?: string
  content?: string
  previewUrl?: string
  mindMapId?: number
  mindMapTreeData?: unknown
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
- libraryId 可以为 null；生成画像和方案前必须关联有效知识库。

#### PATCH /api/learning/projects/{id}

状态：NEW，前端后续接入。

请求：

~~~ts
type UpdateLearningProjectRequest = {
  title?: string
  icon?: string
  iconColor?: string
  libraryId?: number
}
~~~

成功返回 LearningProjectDto。

#### DELETE /api/learning/projects/{id}

状态：NEW，前端后续接入。

使用逻辑删除或归档。运行中的生成任务存在时返回 409。项目置顶仍是 localStorage UI 偏好，不需要后端字段。

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

1. 校验知识库权限及 availableForAi。
2. 创建 generation_job。
3. 读取文档解析结果和用户输入。
4. 调用 AI 并校验结构。
5. 保存结果，更新任务。

#### POST /api/learning/profile-confirmations

请求：LearningConfirmationRequest。

成功：

~~~json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": "确认稿 Markdown 内容"
  }
}
~~~

该接口当前为同步调用，必须在 30 秒内完成。确认稿由后端生成，正式前端不拼装权威确认稿。

### 10.4 学习方案

#### POST /api/learning/plan-jobs

请求：CreateLearningPlanRequest。

成功：ApiResult<GenerationJob<{ projectId: number }>>。

后端事务边界：

1. 验证 draftPlanId 或 projectId 归属。
2. 保存用户确认版本。
3. 创建生成任务并立即返回。
4. 后台生成阶段、任务、题目与资源需求。
5. 校验引用完整性后事务保存。
6. 成功任务 result.projectId 必须能立即通过 GET 项目详情读取。

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

请求：{ resourceId: number }。

成功：ApiResult<GenerationJob<{ projectId: number }>>。

- 只有 not_selected 或 failed 可发起。
- 已 generating 返回 409。
- 成功后状态 ready，并保存稳定文件、content 或 mindMapId。
- 失败后状态 failed，保存 errorMessage。

#### GET /api/learning/projects/{projectId}/resources/{resourceId}/download

返回文件流。必须校验项目和资源关联，Content-Disposition 使用 attachment。

项目详情必须直接返回 resources；当前前端不调用单独资源列表接口。

## 11. 学习助教

学习助教不新增独立聊天协议，复用：

1. POST /api/conversation/create，conversationType=learning-tutor，learningProjectId 必填。
2. GET /api/conversation/list 恢复项目关联会话。
3. GET /api/conversation/{id}/messages 恢复历史。
4. POST /api/chat/stream 发送问题。

后端根据 conversation.learningProjectId 加载：

- 项目目标和画像。
- 当前阶段、任务和题目。
- 用户已提交答案和错题。
- 项目资源。

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
| learning_project | id、user_id、library_id、title、goal、status、period、target_type、progress、统计字段、deleted、create_time、update_time |
| learning_profile_version | id、project_id、version、profile_json、confirmation_content、confirmed、create_time |
| learning_stage | id、project_id、sort_no、title、description、schedule_label |
| learning_task | id、stage_id、type、status、completion_mode、进度和时长字段 |
| learning_resource | id、project_id、library_id、task_id、group_type、title、status、file_path、content、mind_map_id、error_message |
| exercise | id、project_id、source_task_id、题型、题干、难度、知识点、选项、答案、解析、评分规则、生成批次 |
| task_exercise | task_id、exercise_id、sort_no |
| answer_submission | id、user_id、project_id、exercise_id、answer、language、score、correct、feedback、client_request_id、create_time |
| wrong_question | id、user_id、project_id、exercise_id、状态、连续正确、错误次数、复习次数、最近错误时间 |
| wrong_review_set | id、project_id、状态、来源错题、难度策略、正确率 |
| generation_job | job_id、user_id、project_id、job_type、status、progress、request_json、result_json、error_code、error_message、create_time、update_time |
| learning_activity | id、user_id、project_id、task_id、event_type、progress、seconds_delta、action、client_request_id、create_time |

关键约束：

- answer_submission 唯一键：(user_id, client_request_id)。
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
| P1 | 文档上传 UI 仍允许 DOC，但现有文档解析器不支持 | 前端限制 DOC 或后端补解析；图片固定走第 18 节媒体接口 |
| P1 | 学习项目重命名、删除目前只改前端展示 | 前端接入本文 PATCH/DELETE |
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
10. 新增媒体资产、语音转写、图片识别任务，并让聊天和资料库按媒体 ID 关联。
11. 前后端联调 API 模式。

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
- 拒绝的麦克风权限不会发起后端请求；空录音、超时和不支持格式返回固定错误码。
- 图片资产只能被所属用户和已关联的会话、资料库或学习项目引用。
- 图片识别任务可在刷新后通过 jobId 查询，重复 clientRequestId 不创建重复资产或任务。

## 17. 首期明确不实现

以下按钮继续由前端保持禁用，后端不需要猜接口：

- 资料库文件在线预览。
- 选择多个资料直接开始聊天。
- 高级筛选。

需要启用时另行新增契约版本，不在现有接口中偷偷扩展字段。

## 18. 语音、拍照与图片识别

### 18.1 前端与后端边界

状态：NEW。

- 前端只负责请求浏览器权限、录音、选图/拍照、格式与大小预校验、状态展示、取消和重试。
- 正式环境的语音识别、OCR、题目结构化、图片意图判断和多模态理解全部由后端完成。
- Mock Repository 只保存媒体元数据和模拟结果到按用户隔离的 sessionStorage，不保存真实图片或录音二进制。
- 正式环境的原始文件存对象存储，元数据、权限关联和任务状态存后端数据库；前端不把业务媒体写入 localStorage 或 sessionStorage。
- API 模式失败时禁止回退 Mock，也禁止仅凭文件名或扩展名伪造识别成功。

### 18.2 共享类型

~~~ts
type MediaKind = "image" | "audio"
type MediaSource = "upload" | "camera" | "microphone"
type MediaPurpose = "chat-attachment" | "library-resource" | "learning-input"
type MediaAssetStatus = "uploading" | "uploaded" | "processing" | "ready" | "failed"
type ImageRecognitionMode = "auto" | "ocr" | "question"

type MediaContext = {
  conversationId?: number | null
  libraryId?: number | null
  learningProjectId?: number | null
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
  source: "microphone"
  purpose: "chat-attachment" | "learning-input"
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
- `purpose=library-resource` 时 libraryId 必填，成功后创建资料库媒体关联并启动 OCR/索引任务。
- `purpose=learning-input` 时 libraryId 或 learningProjectId 至少一个有效；未创建项目时允许只关联 libraryId。
- source 仅说明入口来自上传或摄像头，不影响权限和识别算法。
- 以 userId + operation + clientRequestId 保证幂等；相同请求返回原资产，不重复保存文件。

成功：HTTP 201，ApiResult<MediaAssetDto>。

示例 metadata：

~~~json
{
  "source": "camera",
  "purpose": "chat-attachment",
  "conversationId": 42,
  "libraryId": null,
  "learningProjectId": null,
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
    "libraryId": null,
    "learningProjectId": null,
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

- 支持 WebM/Opus、Ogg/Opus、M4A/MP4、WAV、MP3。
- 单文件最大 25 MB，后端探测的真实时长最大 120 秒；durationMs 仅用于前端展示，不能作为服务端校验依据。
- 默认语言 zh-CN；后端可以自动检测并返回实际 language。
- 本接口首期同步返回，后端处理超时 90 秒，前端请求超时 120 秒。
- 无有效人声返回 HTTP 422 + NO_SPEECH_DETECTED，不返回空字符串成功。
- 转写文字只回填输入框，前端不得在识别完成后自动发送。
- 原始录音默认 24 小时后清理；需要长期保存时必须另行增加用户可见的保存语义和隐私说明。

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

聊天数据流：

1. 前端选择图片或调用后置摄像头，完成格式、大小和数量预校验。
2. 前端调用 POST /api/media/images，取得 MediaAssetDto.id。
3. 用户点击发送后，前端调用 POST /api/chat/stream，并传 mediaAssetIds。
4. 后端校验媒体资产、会话和用户关系，再根据用户问题与图片内容决定直接多模态理解、OCR 或题目识别。
5. 后端把媒体资产引用写入用户消息，SSE 只传回答文本和完成/错误事件。
6. 上传或发送失败时不得创建伪成功消息；前端保留待发送文字和附件以便重试。

资料库/智能学习数据流：

1. purpose=library-resource 或 learning-input 上传图片。
2. 后端保存私有原件和媒体元数据，创建资料库关联并启动识别/索引。
3. GET /api/library/resources 返回 media:{assetId} 聚合项及 processing/ready/failed 状态。
4. 只有 ready 图片可以进入 RAG 或正式学习生成上下文；failed 可通过聚合资源 retry 接口重试。

### 18.7 意图、上下文与权限

- 前端只发送显式 mode、purpose、用户 prompt 和实体 ID，不在浏览器用关键词规则决定 OCR、题目类型或模型。
- 后端 auto 意图至少使用用户当前问题、媒体 purpose、图片内容和会话/学习项目类型；低置信度时按 general-image 处理或向用户追问，不能静默执行高风险动作。
- 图片识别结果加入当前消息上下文，不默认污染后续所有会话；需要跨轮引用时由后端已保存的消息媒体关联恢复。
- learningProjectId、libraryId 和 conversationId 必须逐个反查当前用户；三者同时存在时必须属于同一用户且关联关系一致，否则返回 409 CONTEXT_MISMATCH。
- 任何前端传入的 OCR 文本、durationMs、mimeType、source 和 confidence 都不是权限或业务真值。

### 18.8 错误码

| HTTP | errorCode | 触发条件 |
| --- | --- | --- |
| 400 | MEDIA_CONTEXT_REQUIRED | purpose 缺少要求的 conversationId、libraryId 或 learningProjectId |
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
