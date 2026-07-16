# 学生端前端功能与对接说明

> 范围：当前 `npm run dev` 中的“新对话”“资料库”“智能学习”、PPT 生成及其详情页。
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
- 后端负责：业务实体持久化、权限、文件存储、文档解析、AI/PPT Provider 调用、生成任务、学习进度、题目、答题结果、错题和资源关联。
- 只有 Mock Repository 会调用 `mock/generators` 生成演示用学习方案、题目和评分；API Repository 不执行前端生成规则。
- 正式环境中，学习画像、确认稿、学习方案、题目、解析、推荐资源均应由后端调用 AI 或业务服务生成；前端不能承担这类权威业务生成逻辑。
- 前端可以保留格式校验、空值校验、展示排序和交互限制，但不能用前端规则替代后端 AI 结果。

## 3. 页面与路由清单

| 模块 | 页面 | 当前路由 | 页面职责 | 主要跳转 |
| --- | --- | --- | --- | --- |
| 新对话 | 新对话页 | `/chat` | 创建普通对话或学习助教对话，选择资料库、附件和模型 | 创建成功后进入 `/chat/:id` |
| 新对话 | 对话详情页 | `/chat/:id` | 加载历史消息、发送消息、展示流式回答、版本和引用 | 侧边栏切换其他会话或学习项目 |
| PPT | 创建/详情工作区 | `/presentations/new`、`/presentations/:id` | 配置、生成/编辑大纲、查看任务进度、预览、下载和保存资料库 | 按 returnTo 返回来源页 |
| 资料库 | 资料库首页 | `/library` | 查看、创建资料库，查看资料文件，上传资料 | 进入 `/library/:id`；开始学习进入 `/learning/new?libraryId=:id` |
| 资料库 | 资料库详情 | `/library/:id` | 展示单个资料库的文件、状态和操作 | 用于智能学习进入 `/learning/new?libraryId=:id` |
| 智能学习 | 项目列表 | `/learning/projects` | 查看学习项目、状态、进度和入口 | 新建进入 `/learning/new`；详情进入 `/learning/:id` |
| 智能学习 | 创建页 | `/learning/new` | 选择资料库并收集学习目标，形成确认稿 | 确认创建后进入 `/learning/:id` |
| 智能学习 | 学习计划 | `/learning/:id` | 展示画像、阶段、任务、进度和学习入口 | 任务进入 `/learning/:id/study` |
| 智能学习 | 学习执行 | `/learning/:id/study` | 阅读材料、练习、评估、案例任务和助教交互 | 返回计划或进入错题/资源 |
| 智能学习 | 错题页 | `/learning/:id/mistakes` | 查看错题、解析和掌握状态 | 返回计划或继续学习 |
| 智能学习 | 资源页 | `/learning/:id/resources` | 查看学习资源、知识图谱和 PPT 等辅助内容 | 可打开 `/mindmap/:id` 或 `/presentations/:id` |

补充路由规则：

- `/` 当前重定向到 `/learning/projects`。
- `/learning` 当前重定向到 `/learning/projects`，因此任何“创建学习项目”的入口都不能跳到 `/learning`。
- `/learning/:id/practice` 当前重定向到 `/learning/:id/study`。
- `/presentations/new` 必须位于 `/presentations/:id` 之前注册，避免把 `new` 当成实体 id。
- `returnTo` 只接受以 `/` 开头的站内路径；无有效返回路径时回 `/chat`。
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
- 在创建页输入区通过语音补充学习目标；移动端可从输入框 `+` 菜单上传照片或拍照。

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

### 4.4 语音、拍照与上传照片

#### 页面入口

| 页面 | 入口 | 用途 |
| --- | --- | --- |
| 新对话 `/chat`、`/chat/:id` | Web：附件、模型、语音；移动端：`+` 菜单、模型、语音 | 语音转为可编辑问题；附件可包含文档、Office、ZIP、图片和音频 |
| 智能学习 `/learning/new` | Web：附件、模型、语音；移动端：`+` 菜单、模型、语音 | 补充学习目标；可添加题目图片、音频或学习资料 |
| 资料库 `/library/:id` 上传弹窗 | 通用文件选择和拖拽 | 支持全部约定附件并进入后端解析、识别或索引，不显示独立照片或拍照按钮 |

响应式输入框规则：

- Web 端左侧只显示“上传附件”，该入口支持 PDF、DOC/DOCX、XLS/XLSX、PPT/PPTX、TXT、Markdown、ZIP、图片和音频；右侧固定为“选择模型 → 语音 → 发送”。
- 移动端输入框最左侧只显示圆形 `+`；点击后同一个竖向胶囊向上生长，按“附件、上传照片、拍照、+”排列且只显示图标。其中“附件”与 Web 支持相同格式，后两项是移动端相册和摄像头快捷入口；点击外部、按 Esc 或完成选择后收起。
- 移动端拍照使用 `<input type="file" accept="image/*" capture="environment">` 请求后置摄像头。
- 除 `AppInput` 外，其他页面不显示独立“上传照片”或“拍照”图标。资料库仍可通过通用上传文件入口选择图片。
- 视频明确不支持，文件选择器不声明 video MIME，前端检测到 `video/*` 时直接拒绝，不调用 Repository。
- 输入框每次最多 5 个附件；图片最大 10MB，音频最大 25MB，其余附件最大 21MB。正式后端必须再次按文件签名、实际 MIME 和大小校验。

#### 语音触发和状态

| 状态 | 触发条件 | 前端行为 | 退出路径 |
| --- | --- | --- | --- |
| idle | 初始、成功或失败结束 | 显示麦克风按钮 | 点击后请求权限 |
| requesting-permission | 用户点击麦克风 | 禁用重复点击 | 允许后进入 recording；拒绝后回 idle |
| recording | 获得麦克风流 | 显示秒数和停止按钮 | 再次点击或 120 秒到时停止 |
| transcribing | MediaRecorder 产出录音文件 | 调用 MediaRepository | 成功回填文字；失败保留原输入并回 idle |

- 识别文本只插入当前光标位置，用户确认后再发送，禁止识别完成后自动提交。
- 路由离开、组件销毁或请求取消时停止全部 MediaStream track，并中止转写请求。
- 权限拒绝、无麦克风、设备被占用、空录音、不支持格式和识别超时分别展示错误，不清空原输入。
- Mock 返回明确标记的模拟转写文本；正式 API 发送录音或上传音频及 metadata，由后端语音模型返回文字。
- 麦克风录音使用 `source=microphone`；选择音频附件使用 `source=upload`。上传音频不会自动发送，只有用户提交输入框或确认资料上传后才执行转写。
- 音频附件支持 MP3、WAV、M4A、AAC、OGG、FLAC 和浏览器产生的 audio/WebM；`video/webm` 必须拒绝。

#### 图片触发和状态

| 状态 | 触发条件 | 前端行为 | 退出路径 |
| --- | --- | --- | --- |
| selected | 选择照片或拍照完成 | 校验格式、10MB 和总数 5 张，显示缩略图 | 可删除、发送或取消 |
| uploading | 用户发送消息或确认资料上传 | 禁止重复提交，调用 MediaRepository | 成功取得 assetId；失败保留待发送内容 |
| uploaded/processing | 后端已保存，正在识别或索引 | 聊天可携 assetId 发送；资料库显示处理中 | 查询状态 |
| ready | 后端识别/索引完成 | 可作为聊天、资料库或学习上下文 | 正常使用 |
| failed | 上传、解码、识别或索引失败 | 显示原因和重试 | 重试或删除 |

- 支持 JPEG、PNG、WEBP、HEIC、HEIF，单图最大 10MB；服务端仍必须重新校验文件签名。
- Mock 只在 sessionStorage 保存资产元数据，缩略图只使用当前页面内存中的 Object URL，不保存真实图片内容。
- 正式环境先上传图片取得 `mediaAssetId`，聊天接口只发送 `mediaAssetIds`；图片二进制不转 Base64 塞进 JSON。
- 资料库和智能学习图片通过相同 MediaRepository 上传，后端根据 purpose 和 libraryId/learningProjectId 建立关联，避免同一文件重复上传。

#### 识别、意图和上下文

- 前端不做正式 OCR、语音识别或题目结构化，也不通过关键词规则决定模型。
- `purpose` 固定为 `chat-attachment`、`library-resource` 或 `learning-input`；`source` 固定为 `upload`、`camera` 或 `microphone`。
- 用户明确要求“识别文字”时使用 `mode=ocr`；明确要求“识别题目”时使用 `mode=question`；普通图片问答使用 `mode=auto`，由后端结合当前问题、图片和会话类型判断意图。
- 图片上下文默认只属于当前消息；跨轮引用由后端根据消息与媒体资产关联恢复，前端不能把识别文本永久拼入所有 history。
- conversationId、libraryId 和 learningProjectId 仅用于建立上下文，正式权限由后端逐个校验。
- 识别任务使用 pending、running、succeeded、failed、cancelled；刷新后可用 jobId 恢复查询，任务失败不删除原图。

完整 multipart 字段、DTO、状态机、错误码、重试和权限规则以 `docs/backend-api-contract.md` 第 18 节为准。

### 4.5 PPT 生成

#### 入口和上下文

| 入口 | 触发条件 | 跳转与上下文 |
| --- | --- | --- |
| 新对话欢迎页“生成 PPT” | 点击功能入口 | `/presentations/new`，携 conversationId、已选 libraryId 和 returnTo |
| 智能学习资源页 PPT 行 | PPT 未生成、失败或已有结果 | 新建时携 learningProjectId、learningResourceId、libraryId；已有 presentationId 时进入详情 |
| 资料库 PPT 聚合项 | externalKey 为 `presentation:{id}` | 进入 `/presentations/:id`，保留资料库 returnTo |

普通附件中的 PPT/PPTX 是“上传已有演示文件”，走资料库文档解析；“生成 PPT”是独立 PresentationRepository 流程，两者不能共用生成状态或把上传接口误当生成接口。

PPT 工作区的用户可见术语统一为“知识库”，包括配置、摘要、保存提示和下拉名称。为兼容现有前后端，代码字段仍使用 libraryId，路由仍使用 `/library`，不新增同义接口字段。

#### 四步流程

1. 配置：填写主题、标题、页数、受众、风格、模板、比例、资料库和补充要求；选择“先确认大纲”或“自动生成”。
2. 大纲：确认模式下编辑页面标题、要点、备注、布局和顺序；自动模式生成大纲后直接进入生成。
3. 生成：展示后端任务进度，可明确停止；刷新或重新进入详情时根据 activeJobId 恢复轮询。
4. 预览：API 有 previewImageUrl 时展示后端实际页面，没有时按结构化页面数据渲染；支持翻页、下载和保存到资料库。

#### 页面状态

| 状态 | 触发条件 | 前端行为 | 退出路径 |
| --- | --- | --- | --- |
| config | 新建路由 | 校验主题、3 至 30 页和配置 | 创建草稿并生成大纲 |
| outlining | 大纲任务 pending/running | 禁用重复创建，显示进度 | 成功进 outline；失败保留配置 |
| outline | 后端返回 outline_ready | 允许编辑、增删和排序 | 确认生成或返回配置 |
| generating | PPT 任务 pending/running | 展示非递减进度和停止 | 成功进 preview；取消/失败可重试 |
| preview | status=ready | 展示实际预览、下载、关联资料库 | 返回大纲形成新版本或返回来源页 |
| failed | 任务失败 | 显示后端稳定错误码对应文案 | 保留大纲并使用新 clientRequestId 重试 |
| cancelled | 用户停止 | 不删除 PPT 草稿和大纲 | 返回大纲或重新生成 |
| not-found/forbidden | 详情无权访问或不存在 | 不读取 Mock 兜底，不展示旧缓存 | 返回来源页 |

#### Mock 与正式环境

- MockPresentationRepository 把 PPT 实体和任务元数据写入按用户隔离的 sessionStorage，大纲和进度只用于演示。
- Mock 只在用户点击下载时通过 PptxGenJS 生成真实 PPTX；二进制不写 Storage。
- ApiPresentationRepository 固定调用后端 `/api/presentations` 端点，正式构建不导入 Mock 生成路径，也不直接出现讯飞字段。
- 正式 PPT 原件、逐页预览、Provider 任务和资料库关联全部由后端保存。前端只缓存当前 DTO 和交互状态。
- confirm 与 auto 使用相同后端状态机；auto 只是前端不暂停在大纲编辑步骤。

#### 倒退、刷新和异常

- 配置页未创建实体时返回来源页，不产生服务端草稿。
- 已创建实体后浏览器后退不会删除实体或取消任务；再次进入详情可恢复。
- 生成中返回大纲只改变页面步骤，不自动取消；只有点击“停止生成”才调用 cancel。
- 接口超时保留配置/大纲，不伪造 ready；重复点击由 isSaving 和后端 clientRequestId 双重限制。
- 学习来源生成成功后，Mock 回写 presentationId；API 模式重新获取后端学习项目，前端不自行伪造正式学习资源。
- 保存资料库后，聚合项使用 externalKey=`presentation:{id}`，首页与详情都可重新打开和下载。

完整 DTO、接口示例、状态机、讯飞 Provider 隐藏、权限、幂等和错误码见 `docs/backend-api-contract.md` 第 19 节。

## 5. AI 生成逻辑与接口边界

### 5.1 当前 Mock 实现

- 学习画像和确认稿：Mock Repository 调用 `mock/generators`；API Repository 已固定调用画像生成任务和确认稿接口。
- 学习方案、题目和原型评分：集中在 `mock/generators`，不再作为正式业务实现。
- Mock 业务实体：通过 Repository 写入按用户隔离的 `sessionStorage`。
- 正式数据源：通过 API Repository 请求后端，不读取 Mock Storage。
- PPT：Mock Repository 生成演示大纲和 PPTX；正式 API 只提交结构化配置，由后端调用 PPT Provider 并保存文件。
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

PPT 生成使用独立的 PresentationJob，但仍遵守“创建任务、查询状态、后端持久化、前端按实体 id 恢复”的同一原则。前端 Mock 中的页面标题和要点拼装不能作为正式提示词、模板映射或 Provider 算法。

题目必须由后端生成并保存，至少包含题目 id、版本、题型、题干、选项、答案、解析、来源、难度和生成状态。答案及评分规则不应依赖前端本地数据作为权威来源。

## 6. 存储方案

### 6.1 Mock 阶段推荐

| 数据 | 推荐位置 | 说明 |
| --- | --- | --- |
| Mock 登录 token、当前用户 | `sessionStorage` | 关闭标签页后结束 Mock 会话，不执行“记住我” |
| 主题、侧边栏折叠、视图模式、模型偏好 | `localStorage` | 无敏感信息，行为与正式环境一致 |
| 对话和消息 | `sessionStorage`，按用户和会话隔离 | 刷新可恢复，关闭标签页后无需保留 |
| 资料库、文件元数据 | `sessionStorage`，按用户隔离 | 不保存真实文件内容 |
| 图片/录音资产和识别任务元数据 | `sessionStorage`，按用户隔离 | 不保存真实二进制；关闭标签页后清除 |
| 学习项目、任务、题目、答题和错题 | `sessionStorage`，按用户隔离 | 模拟当前会话内的后端业务数据 |
| PPT 实体、大纲和任务元数据 | `sessionStorage`，按用户隔离 | 模拟刷新恢复；PPTX 二进制只在下载时生成，不持久化 |
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
| 图片、录音原件和识别结果 | 对象存储 + 后端数据库/任务表 | 否，只缓存当前交互状态 |
| 文档解析、向量化状态 | 后端任务/状态表 | 否，只展示状态 |
| 学习项目、阶段、任务、进度 | 后端数据库 | 否，只缓存当前项目 |
| 题目、答案、解析、答题结果、错题 | 后端数据库 | 否 |
| AI 生成任务 | 后端任务表 | 否，只保存当前 `jobId` 到内存；必要时短期保存 |
| PPT 配置、大纲、文件和预览 | 后端数据库 + 私有对象存储 | 否，只缓存当前 DTO；不得保存 Provider URL 或二进制 |
| UI 偏好 | `localStorage` | 是 |
| 未提交草稿、返回路由、生成 `jobId` | `sessionStorage` | 只用于临时恢复，不作为业务权威数据 |
| 访问令牌 | 后端签发的 Bearer JWT | 当前契约沿用现有后端 JWT；Cookie 方案如需启用必须另开契约版本同步迁移 |

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

## 7. 后端接口约束摘要

> 本节仅保留页面侧摘要。字段、路径、状态、错误、权限和兼容要求以 `docs/backend-api-contract.md` 为唯一权威来源。

### 7.1 通用返回结构

```ts
interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
  requestId?: string
  errorCode?: string
}
```

- 当前前端实体 id 使用 `number`。后端需要返回不超过 JavaScript 安全整数范围的整数；若后端决定使用字符串 id，应另行统一迁移，不能混用。
- 时间统一返回 ISO 8601 字符串。
- 枚举值由接口文档固定，前端不得依赖中文展示文案作为状态值。
- 当前主流程列表接口直接返回数组；未来需要分页时，前后端统一升级为 `items`、`page`、`pageSize`、`total`，不能只改单侧。
- 错误至少区分：参数错误、未登录、无权限、资源不存在、状态冲突、生成失败和服务异常。

### 7.2 对话接口

创建会话请求建议包含：

```ts
interface CreateConversationRequest {
  title?: string
  kbId?: number | null
  conversationType?: 'general' | 'learning-setup' | 'learning-tutor'
  learningProjectId?: number | null
  learningProjectName?: string
}
```

当前 API Repository 已发送学习项目关联字段。现有后端 `ConversationCreateReq` 仍只有 `kbId` 和 `title`，需要按正式契约扩展并持久化，否则学习助教关系在刷新或换设备后会丢失。

消息流接口需要稳定支持：`conversationId`、`question`、`model`、`kbId`、`history`、`parentId`、`turnId`、问题/答案版本、重新生成标识、被编辑消息 id 和附件引用。为兼容当前前后端，SSE 固定为未命名 `message` 文本增量、`finish` 引用数组和 `error` 错误事件，详细格式以 `docs/backend-api-contract.md` 为准。

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

### 7.5 媒体接口

- `POST /api/media/images`：上传照片或拍照文件，multipart 固定使用 `file` 和 JSON `metadata` 两个字段。
- `POST /api/media/audio/transcriptions`：上传麦克风录音或音频附件并同步返回转写文字；source 支持 microphone/upload，purpose 支持聊天、资料库和智能学习。
- `POST /api/media/images/:assetId/recognition-jobs`：创建 auto/OCR/question 图片识别任务。
- `GET /api/media/jobs/:jobId`：查询图片识别任务。
- `POST /api/chat/stream`：在原请求中增加 `mediaAssetIds: string[]`，引用已上传图片或已转写音频，不直接发送 Base64/二进制。

正式接口不得返回公开永久文件 URL；文件访问必须经过权限校验或短期签名 URL。详细字段和错误码见零猜测交接契约第 18 节。

### 7.6 PPT 接口

- `GET /api/presentations/templates`：获取本系统业务模板，不返回讯飞模板 ID。
- `POST /api/presentations`：创建带上下文和 clientRequestId 的 PPT 草稿。
- `POST /api/presentations/:id/outline-jobs`：创建大纲任务。
- `PUT /api/presentations/:id/outline`：保存用户确认或自动流程产生的大纲。
- `POST /api/presentations/:id/generation-jobs`：创建或重试 PPT 生成任务。
- `GET /api/presentations/jobs/:jobId`、`POST /api/presentations/jobs/:jobId/cancel`：查询和取消任务。
- `GET /api/presentations/:id`、`GET /api/presentations/:id/download`：刷新恢复和下载权威文件。
- `POST /api/presentations/:id/library`：把 ready PPT 关联到当前用户资料库。

前端不接讯飞 API；正式后端必须隐藏 Provider 密钥、任务 ID、模板 ID 和文件 URL。详细约束见零猜测交接契约第 19 节。

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
- `views/student/presentation/PresentationWorkspaceView.vue`
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
      presentation/
    admin/
    legacy/
  components/
    capture/
    common/
    layout/
    chat/
    library/
    learning/
    presentation/
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
| `components/capture` | `VoiceRecorder.vue`、`ImageCaptureUploader.vue`，封装浏览器设备与文件选择交互 |
| `components/chat` | 消息渲染、输入附件、思维导图和分段面板 |
| `components/library` | `LibraryKnowledgeCreateModal.vue`、`UploadMaterialModal.vue` |
| `components/learning` | `LearningQuestionCard.vue`、`LearningTutorPanel.vue`、`LearningRouteState.vue`、`LearningPlanDocument.vue`、`LearningProfileCard.vue`、`LearningProfileMenu.vue`、`LearningProjectResourceChips.vue`、`LearningMindMapPreview.vue` |
| `components/presentation` | `PresentationOutlineEditor.vue`、`PresentationSlidePreview.vue`，只负责 PPT 业务交互和展示 |
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
2. 将当前主线页面按 `chat/library/learning/presentation` 分组，逐项修正 import。
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
- [x] 增加语音输入、拍照和上传照片入口，并使用 MediaRepository 隔离 Mock/API。
- [x] 增加媒体共享类型、正式 API 调用、聊天 mediaAssetIds 和资料库图片关联。
- [x] 固定语音、图片、OCR/题目识别的正式后端契约、状态机、权限和错误码。
- [x] 统一输入框响应式布局：Web 仅附件，移动端使用 `+` 展开附件、照片和拍照。
- [x] 通用附件增加 DOC、Excel、PPT、ZIP 和音频，明确拒绝视频；音频复用 MediaRepository Mock/API 转写。
- [x] 移动端附件菜单改为竖向 pill，侧边栏折叠 pill 增加平滑动画、反馈和路由选中态。
- [x] 建立 Presentation 共享类型、Mock/API Repository 和用户隔离的 Mock 任务存储。
- [x] 完成 PPT 配置、大纲编辑、异步生成、取消/重试、预览、真实 PPTX 下载和资料库关联。
- [x] 新对话、智能学习资源和资料库统一接入 PPT 工作区，不再用聊天提示词或普通学习资源逻辑代替。
- [x] 补齐 PPT 零猜测后端接口、状态机、幂等、权限、讯飞 Provider 隐藏和错误码。
- [ ] 对完全无引用、无独有逻辑的文件二次确认后删除。

后续每一项涉及代码或目录调整时，应单独确认范围后执行，避免一次性移动造成大量不可控变更。
