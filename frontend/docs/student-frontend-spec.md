# 学生端前端功能与对接说明

> 范围：当前 `npm run dev` 中的“新对话”“资料库”“智能学习”及其详情页。
>
> 本文描述前端当前实现、Mock 边界和正式后端对接约束。后端内部实现不在本文范围内。

## 1. 文档目标

1. 明确每个页面的功能、状态、触发条件、生成逻辑和路由跳转。
2. 区分当前前端 Mock 行为与正式产品行为，避免把临时规则当成最终方案。
3. 统一 Mock 阶段和正式环境的存储职责。
4. 为后续 API 对接提供稳定的字段和状态约束。
5. 为文件夹整理、组件封装、旧文件归档和无用文件删除提供依据。

## 2. 产品边界与核心原则

- 前端负责：页面展示、表单编辑、用户确认、交互状态、路由、接口调用、流式响应展示和少量 UI 偏好持久化。
- 后端负责：业务实体持久化、权限、文件存储、文档解析、AI 调用、生成任务、学习进度、题目、答题结果、错题和资源关联。
- 只有 Mock Repository 会调用 `mock/generators` 生成演示用学习方案、题目和评分；API Repository 不执行前端生成规则。
- 正式环境中，学习画像、确认稿、学习方案、题目、解析、推荐资源均应由后端调用 AI 或业务服务生成；前端不能承担这类权威业务生成逻辑。
- 前端可以保留格式校验、空值校验、展示排序和交互限制，但不能用前端规则替代后端 AI 结果。

## 3. 页面与路由清单

| 模块 | 页面 | 当前路由 | 页面职责 | 主要跳转 |
| --- | --- | --- | --- | --- |
| 新对话 | 新对话页 | `/chat` | 创建普通对话或学习助教对话，选择资料库、附件和模型 | 创建成功后进入 `/chat/:id` |
| 新对话 | 对话详情页 | `/chat/:id` | 加载历史消息、发送消息、展示流式回答、版本和引用 | 侧边栏切换其他会话或学习项目 |
| 资料库 | 资料库首页 | `/library` | 查看、创建资料库，查看资料文件，上传资料 | 进入 `/library/:id`；开始学习进入 `/learning/new?libraryId=:id` |
| 资料库 | 资料库详情 | `/library/:id` | 展示单个资料库的文件、状态和操作 | 用于智能学习进入 `/learning/new?libraryId=:id` |
| 智能学习 | 项目列表 | `/learning/projects` | 查看学习项目、状态、进度和入口 | 新建进入 `/learning/new`；详情进入 `/learning/:id` |
| 智能学习 | 创建页 | `/learning/new` | 选择资料库并收集学习目标，形成确认稿 | 确认创建后进入 `/learning/:id` |
| 智能学习 | 学习计划 | `/learning/:id` | 展示画像、阶段、任务、进度和学习入口 | 任务进入 `/learning/:id/study` |
| 智能学习 | 学习执行 | `/learning/:id/study` | 阅读材料、练习、评估、案例任务和助教交互 | 返回计划或进入错题/资源 |
| 智能学习 | 错题页 | `/learning/:id/mistakes` | 查看错题、解析和掌握状态 | 返回计划或继续学习 |
| 智能学习 | 资源页 | `/learning/:id/resources` | 查看学习资源、知识图谱等辅助内容 | 可打开 `/mindmap/:id` |

补充路由规则：

- `/` 当前重定向到 `/learning/projects`。
- `/learning` 当前重定向到 `/learning/projects`，因此任何“创建学习项目”的入口都不能跳到 `/learning`。
- `/learning/:id/practice` 当前重定向到 `/learning/:id/study`。
- `libraryId` 是创建学习项目时的预选参数。创建页读取后只在当前用户有权限的知识库列表中匹配；参数无效时选择第一项可用知识库，无可用项时展示空状态并阻止生成。

## 4. 功能、状态与触发条件

### 4.1 新对话

#### 功能说明

- 创建普通对话。
- 选择资料库作为回答上下文。
- 添加附件并展示附件状态。
- 发送问题并展示流式回答。
- 加载历史会话和消息版本。
- 在学习项目场景中承载学习助教对话。

#### 页面状态

| 状态 | 触发条件 | 前端表现 | 后续动作 |
| --- | --- | --- | --- |
| 空会话 | 进入 `/chat` | 展示输入区和引导内容 | 首次发送时创建会话 |
| 创建中 | 首次提交问题 | 禁用重复提交，展示等待状态 | 创建成功后写入路由 id |
| 回答中 | 流式接口开始 | 逐段追加回答，可展示停止按钮 | 完成后保存最终消息状态 |
| 加载历史 | 进入 `/chat/:id` | 展示加载状态 | 加载成功后渲染消息列表 |
| 加载失败 | 会话或消息接口失败 | 展示错误与重试入口 | 重试或返回新对话 |
| 附件处理中 | 添加文件 | 展示文件名、大小和状态 | 上传/解析完成后允许引用 |
| 会话不存在 | id 无效或无权限 | 展示空状态或错误页 | 返回 `/chat` |

#### 触发与生成逻辑

1. 用户提交问题前，前端校验文本或附件至少存在一个。
2. `/chat` 首次发送时先创建会话，再发送消息；`/chat/:id` 直接在当前会话发送。
3. 正式环境回答由后端 AI 流式生成，前端只负责渲染增量内容和最终状态。
4. 重新生成、编辑问题和消息版本必须携带同一轮次标识，避免覆盖错误消息。
5. 学习助教会话必须关联 `learningProjectId`，普通对话不应伪造该字段。
6. 普通对话和学习助教均支持停止流式生成；中止请求保留已生成内容，不作为失败提示。
7. Mock 附件提取只返回文件元数据，不读取真实文件；API 模式通过 Document Repository 调用附件提取接口。

### 4.2 资料库

#### 功能说明

- 创建和查看资料库。
- 查看资料库详情及文件列表。
- 上传资料并展示上传、解析状态。
- 从资料库直接发起智能学习。

#### 页面状态

| 状态 | 触发条件 | 前端表现 | 后续动作 |
| --- | --- | --- | --- |
| 空列表 | 用户没有资料库 | 展示空状态和创建入口 | 创建资料库 |
| 有数据 | 列表加载成功 | 展示资料库和文件 | 查看详情或开始学习 |
| 上传中 | 用户选择文件 | 展示进度，限制重复操作 | 上传完成后进入解析状态 |
| 解析中 | 文件上传成功 | 展示处理中 | 轮询或订阅后端任务状态 |
| 可用 | 解析完成 | 允许对话引用和智能学习 | 作为 AI 上下文使用 |
| 解析失败 | 后端解析失败 | 展示失败原因和重试 | 重新解析或删除文件 |
| 删除确认 | 用户点击删除 | 展示确认弹窗 | 确认后调用删除接口 |

#### 触发与跳转逻辑

- 首页点击资料库进入 `/library/:id`。
- 首页“开始智能学习”和详情页“用于智能学习”统一进入 `/learning/new?libraryId=:id`。
- 创建页根据 `libraryId` 预选资料库，但仍允许用户修改。
- 未解析完成的资料不能作为正式 AI 上下文；前端需要根据后端状态禁用相关入口。
- 列表搜索、网格/列表偏好、重命名、移动、下载、删除和解析失败重试均通过 Library Resource Store 调用同一 Repository。
- 当前“选择资料后开始聊天”和文件在线预览保持禁用，直到后端提供资源引用和预览接口；前端不会伪造成功。

### 4.3 智能学习

#### 功能说明

- 根据资料库、学习目标和用户补充信息创建学习项目。
- 展示学习画像、确认稿、学习方案、阶段、任务和总体进度。
- 执行阅读、练习、评估和案例任务。
- 展示题目、解析、错题和推荐资源。
- 通过学习助教继续提问。

#### 创建流程状态

| 状态 | 触发条件 | 前端表现 | 正式处理方 |
| --- | --- | --- | --- |
| 未选择资料库 | 直接进入 `/learning/new` | 要求选择资料库 | 前端交互状态 |
| 已预选资料库 | 携带有效 `libraryId` | 展示已选资料库 | 前端读取参数，后端校验权限 |
| 信息收集中 | 用户描述目标 | 展示对话或表单 | 前端收集输入 |
| 画像生成中 | 用户提交目标 | 展示生成状态 | 后端 AI |
| 待确认 | 后端返回画像/确认稿 | 允许用户修改或确认 | 前端编辑，后端保存草稿 |
| 方案生成中 | 用户确认 | 展示任务状态 | 后端 AI/任务服务 |
| 创建成功 | 后端返回项目 id | 跳转计划页 | 后端持久化，前端跳转 |
| 生成失败 | AI 或任务失败 | 展示原因和重试 | 后端记录失败，前端重试 |

#### 学习任务状态

| 状态 | 含义 | 触发条件 |
| --- | --- | --- |
| 未开始 | 任务未产生有效学习行为 | 初始状态 |
| 进行中 | 已阅读、作答或执行部分步骤 | 首次有效交互 |
| 已完成 | 满足后端定义的完成标准 | 后端确认完成 |
| 需复习 | 结果低于掌握阈值或到达复习时间 | 后端学习策略判断 |
| 已锁定 | 前置任务未完成 | 后端返回锁定条件 |

当前 Mock 完成规则仅用于页面演示：

- 阅读/资源任务：阅读进度达到 80%，且有效学习时间达到 5 秒。
- 练习/评估任务：关联题目全部提交。
- 案例任务：完成 `run-case` 操作。
- 项目进度：按已完成任务数计算；100% 标记为“已完成”，否则为“进行中”。

正式环境中，上述阈值和完成结果必须由后端返回。前端可以展示规则，但不应自行决定权威进度。

#### 当前前端状态实现

- 项目列表：加载、失败重试、无项目、筛选无结果、网格/列表偏好。
- 创建页：无可用知识库、画像生成中/失败、确认稿生成中/失败、项目生成中/失败。
- 计划、学习、错题和资源页：统一按路由 `projectId` 重新请求后端，处理加载失败和项目不存在；不再回退到第一个项目。
- 答题、追加练习、错题巩固和资源生成：阻止重复提交，失败后保留当前页面数据并允许重试。
- 学习资源：只展示项目接口真实返回的资源；正式模式不在前端补造默认方案、思维导图或下载文件。

## 5. AI 生成逻辑与接口边界

### 5.1 当前 Mock 实现

- 学习画像和确认稿：现有页面仍保留演示交互，后续统一接画像生成任务接口。
- 学习方案、题目和原型评分：集中在 `mock/generators`，不再作为正式业务实现。
- Mock 业务实体：通过 Repository 写入按用户隔离的 `sessionStorage`。
- 正式数据源：通过 API Repository 请求后端，不读取 Mock Storage。
- 数据源由 `VITE_DATA_SOURCE=mock|api` 在构建时决定，正式构建强制使用 `api`。

Mock 生成器只负责返回符合接口结构的演示数据。后端不得复制其中的文本匹配、题目拼装、评分阈值或完成条件。

### 5.2 正式实现

正式环境建议采用异步生成流程：

1. 前端提交资料库、学习目标和用户补充信息。
2. 后端创建生成任务并返回 `jobId`。
3. 后端校验资料权限、读取解析结果并调用 AI。
4. 前端通过轮询、SSE 或 WebSocket 获取任务状态。
5. 后端返回结构化画像/确认稿，用户修改后再次提交确认。
6. 后端生成并持久化学习项目、阶段、任务、题目和资源关联。
7. 前端根据返回的 `projectId` 跳转 `/learning/:id`。

题目必须由后端生成并保存，至少包含题目 id、版本、题型、题干、选项、答案、解析、来源、难度和生成状态。答案及评分规则不应依赖前端本地数据作为权威来源。

## 6. 存储方案

### 6.1 Mock 阶段推荐

| 数据 | 推荐位置 | 说明 |
| --- | --- | --- |
| Mock 登录 token、当前用户 | `sessionStorage` | 关闭标签页后结束 Mock 会话，不执行“记住我” |
| 主题、侧边栏折叠、视图模式、模型偏好 | `localStorage` | 无敏感信息，行为与正式环境一致 |
| 对话和消息 | `sessionStorage`，按用户和会话隔离 | 刷新可恢复，关闭标签页后无需保留 |
| 资料库、文件元数据 | `sessionStorage`，按用户隔离 | 不保存真实文件内容 |
| 学习项目、任务、题目、答题和错题 | `sessionStorage`，按用户隔离 | 模拟当前会话内的后端业务数据 |
| 未提交草稿、生成任务 id | `sessionStorage` | 用于刷新恢复和继续查询任务 |
| 弹窗、筛选、当前选中项、加载状态 | Pinia 或组件内存 | 不需要跨会话持久化 |
| AI 生成结果 | Mock Repository/Generator | 页面和 Store 不直接生成权威业务结果 |

Mock key 使用 `examinsight.mock.v1.{userId}.{domain}`。页面和 Store 不直接读写 Mock 业务存储，由 `mock/storage.ts` 和 Repository 统一处理。

Mock 是否跨浏览器启动保留不属于后端接口契约。正式环境通过后端数据库持久化；Mock 使用 `sessionStorage` 仍能完整模拟刷新、查询、创建和更新流程。

### 6.2 正式环境推荐

| 数据 | 权威存储 | 前端是否持久化 |
| --- | --- | --- |
| 用户、会话、消息 | 后端数据库 | 仅缓存当前页面数据 |
| 资料库、文件元数据 | 后端数据库 | 仅缓存列表与详情 |
| 文件原件 | 对象存储或后端文件服务 | 否 |
| 文档解析、向量化状态 | 后端任务/状态表 | 否，只展示状态 |
| 学习项目、阶段、任务、进度 | 后端数据库 | 否，只缓存当前项目 |
| 题目、答案、解析、答题结果、错题 | 后端数据库 | 否 |
| AI 生成任务 | 后端任务表 | 否，只保存当前 `jobId` 到内存；必要时短期保存 |
| UI 偏好 | `localStorage` | 是 |
| 未提交草稿、返回路由、生成 `jobId` | `sessionStorage` | 只用于临时恢复，不作为业务权威数据 |
| 访问令牌 | 优先 `HttpOnly + Secure + SameSite` Cookie | 当前 Bearer Token 方案属于兼容实现，需和后端认证方案统一 |

正式环境前端只做体验兜底：保存草稿、恢复路由、恢复生成任务查询、展示错误和重试。接口失败时不得在本地伪造创建成功、评分结果、学习进度或资料解析完成。

### 6.3 数据源隔离

```text
页面 / Pinia Store
        ↓
Repository 接口与共享 TypeScript 契约
        ↓
├── Mock Repository → Mock Generator + sessionStorage
└── API Repository  → HTTP / SSE → 后端数据库与 AI 任务
```

- `.env.development` 使用 `VITE_DATA_SOURCE=mock`。
- `.env.production` 使用 `VITE_DATA_SOURCE=api`，生产构建发现 Mock 模式时直接失败。
- Mock 与 API 使用相同实体和任务状态；不同之处只能是数据来源和执行实现。
- API Repository 不捕获 `404` 后回退 Mock。
- Store 只保存页面当前内存状态；正式模式刷新后重新调用后端接口。
- 学习助教会话 id 仅在 Mock 模式写 `sessionStorage`；正式模式从后端会话列表及项目关联字段恢复。
- 详细接口见 `docs/backend-api-contract.md`。

## 7. 后端接口约束建议

### 7.1 通用返回结构

```ts
interface ApiResponse<T> {
  code: string
  message: string
  data: T
  requestId?: string
}
```

- 当前前端实体 id 使用 `number`。后端需要返回不超过 JavaScript 安全整数范围的整数；若后端决定使用字符串 id，应另行统一迁移，不能混用。
- 时间统一返回 ISO 8601 字符串。
- 枚举值由接口文档固定，前端不得依赖中文展示文案作为状态值。
- 列表接口统一分页字段：`items`、`page`、`pageSize`、`total`。
- 错误至少区分：参数错误、未登录、无权限、资源不存在、状态冲突、生成失败和服务异常。

### 7.2 对话接口

创建会话请求建议包含：

```ts
interface CreateConversationRequest {
  title?: string
  kbId?: string
  conversationType: 'normal' | 'learning_tutor'
  learningProjectId?: string
}
```

当前真实创建请求只发送 `kbId` 和 `title`，而 Mock 会话还包含学习项目字段。正式对接前必须统一，否则学习助教关系只存在于前端，刷新或换设备后会丢失。

消息流接口需要稳定支持：`conversationId`、`question`、`model`、`kbId`、`history`、`parentId`、`turnId`、问题/答案版本、重新生成标识、被编辑消息 id 和附件引用。后端返回事件类型应固定，例如 `start`、`delta`、`sources`、`done`、`error`。

### 7.3 资料库接口

建议至少提供：

- 资料库列表、详情、创建、更新、删除。
- 文件上传、文件列表、删除、重新解析。
- 文件状态查询或任务事件订阅。
- 资料库是否可用于对话/学习的明确字段，例如 `availableForAi`。

文件状态建议固定为：`uploading`、`uploaded`、`parsing`、`ready`、`failed`。前端展示文案单独映射。

### 7.4 智能学习接口

建议至少提供：

- 创建画像生成任务。
- 查询生成任务状态。
- 更新并确认学习画像。
- 创建学习方案生成任务。
- 学习项目列表、详情和删除/归档。
- 阶段、任务、资源和题目详情。
- 提交答题结果、任务行为和学习时长。
- 错题列表、掌握状态和复习操作。

异步任务状态建议固定为：`pending`、`running`、`succeeded`、`failed`、`cancelled`。返回失败时应提供稳定的错误码和可展示信息。

## 8. 文件夹重构与文件清单

### 8.1 判断标准

- 页面（view）：与路由一一对应，负责读取路由参数、组合页面数据、调用 store/service 和组织页面级布局。
- 业务组件：被一个业务模块内的一个或多个页面复用，不直接决定顶层路由。
- 通用组件：不依赖具体业务实体，可跨“对话、资料库、智能学习”复用。
- 归档文件：当前主线不用，但仍有功能或实现可参考；先保留到 `legacy`，不参与新功能开发。
- 可删除文件：没有路由、没有 import、没有测试依赖、没有独有可复用逻辑，并经人工确认后才删除。

### 8.2 当前主线页面

以下文件继续作为主要页面，不应放入归档目录：

- `views/student/chat/StudentChatView.vue`
- `views/student/library/LibraryHomeView.vue`
- `views/student/library/LibraryDetailView.vue`
- `views/student/learning/LearningProjectsView.vue`
- `views/student/learning/LearningHomeView.vue`
- `views/student/learning/LearningPlanView.vue`
- `views/student/learning/LearningStudyView.vue`
- `views/student/learning/LearningResourcesView.vue`
- `views/student/learning/LearningMistakesView.vue`

### 8.3 组件分类

当前已在不改变 Vue、Pinia 和 Vue Router 架构的前提下整理为：

```text
src/
  views/
    student/
      chat/
      library/
      learning/
    admin/
    legacy/
  components/
    common/
    layout/
    chat/
    library/
    learning/
    legacy/
  composables/
    useLearningPlanRoute.ts
  stores/
  api/
  mock/
  utils/
```

不建议把 `views` 整体改名为 `pages`，因为这会产生大量无业务价值的 import 和路由改动。

当前组件归类：

| 目标分类 | 当前内容 |
| --- | --- |
| `components/layout` | `StudentShell.vue`、`StudentSidebar.vue`、`LearningDetailShell.vue` |
| `components/chat` | 消息渲染、输入附件、思维导图和分段面板 |
| `components/library` | `LibraryKnowledgeCreateModal.vue`、`UploadMaterialModal.vue` |
| `components/learning` | `LearningQuestionCard.vue`、`LearningTutorPanel.vue`、`LearningRouteState.vue`、`LearningPlanDocument.vue`、`LearningProfileCard.vue`、`LearningProfileMenu.vue`、`LearningProjectResourceChips.vue`、`LearningMindMapPreview.vue` |
| `components/common` | 经过检查后确实不依赖业务字段的按钮、弹窗、状态和图标组件 |
| `components/legacy` | 当前主线零引用但仍有参考或复用价值的旧组件 |

原 `main-area/mode3-chat/*` 已迁移到 `components/chat`。旧版组合容器和欢迎页已移入 `components/legacy/chat`。

### 8.4 归档与删除策略

以下文件目前仍有路由或其他页面引用，不能直接删除：

- `views/KnowledgeBaseView.vue`
- `views/ResourceCenterView.vue`
- `components/sidebar/*`

以下旧页面已确认无当前路由并完成归档：

- `views/legacy/ChatView.vue`
- `views/legacy/LearningWorkspaceView.vue`

完整文件归属和删除判断见 `docs/frontend-file-inventory.md`。当前没有已经确认可以删除的源文件。

建议执行顺序：

1. 建立完整的路由、动态 import、静态 import 和测试引用清单。
2. 将当前主线页面按 `chat/library/learning` 分组，逐项修正 import。
3. 将可复用组件按业务域迁移，并保持组件 API 不变。
4. 将仍有参考价值的旧文件迁入 `legacy`，同时移除主线路由引用。
5. 运行类型检查、单元测试和主流程页面检查。
6. 对确认无引用且无独有逻辑的文件单独列清单，得到人工确认后删除。

## 9. 后续实施清单

- [x] 修复资料库首页“开始智能学习”跳转到 `/learning/new?libraryId=:id`。
- [x] 修复资料库详情“用于智能学习”跳转到 `/learning/new?libraryId=:id`。
- [x] 明确当前规则生成属于 Mock，正式方案和题目由后端 AI/业务服务生成。
- [x] 明确 Mock 阶段与正式环境的存储职责。
- [x] 明确页面、业务组件、通用组件、归档文件和可删除文件的标准。
- [x] 创建统一的前端数据访问层，隔离 Mock 与真实 API。
- [x] 补齐会话、学习项目和资料库的接口类型及状态枚举。
- [x] 将创建会话的学习项目关联字段写入正式 API 请求。
- [x] Mock 业务数据统一使用按用户隔离的 `sessionStorage`。
- [x] UI 长期偏好继续使用 `localStorage`。
- [x] 移除认证、会话、资料库和文件接口的 `404 → Mock` 自动降级。
- [x] 将学习方案、题目和原型评分的活动实现隔离到 Mock Generator。
- [x] 增加正式学习生成任务、学习行为和答题 Repository 契约。
- [x] 将认证、消息和文档访问统一接入 Mock/API Repository。
- [x] 将学习画像提取和确认稿生成移入 Mock Generator，正式环境改走后端 AI 接口。
- [x] 将自适应练习、错题强化和学习资源生成统一为正式异步任务。
- [x] 增加 Mock Storage 用户隔离与学习画像生成测试。
- [x] 补充后端接口契约文档。
- [x] 按业务域移动主线页面和组件。
- [x] 将无当前路由但有复用价值的旧文件迁入 `legacy`。
- [x] 建立主线、归档、仍有路由模块和删除候选文件清单。
- [x] 补齐新对话停止生成、失败重试和附件 Mock/API 隔离。
- [x] 资料库列表、上传、详情、移动、重命名、下载、删除和解析重试统一接入 Repository。
- [x] 智能学习详情路由统一重新获取权威项目，不再回退第一个项目。
- [x] 学习资源取消正式环境前端补造和本地伪下载，改用后端资源接口。
- [x] 补齐主流程加载、空、失败、生成中和重复提交状态。
- [ ] 对完全无引用、无独有逻辑的文件二次确认后删除。

后续每一项涉及代码或目录调整时，应单独确认范围后执行，避免一次性移动造成大量不可控变更。
