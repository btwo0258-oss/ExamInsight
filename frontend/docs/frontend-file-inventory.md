# 学生端前端文件清单

> 范围：新对话、资料库、智能学习、PPT/电子表格生成及详情页。
>
> 本清单只描述前端文件归属，不包含后端实现。
>
> 更新日期：2026-07-18

## 1. 主线路由页面

| 路由 | 页面文件 | 状态 |
| --- | --- | --- |
| `/chat`、`/chat/:id` | `src/views/student/chat/StudentChatView.vue` | 主线 |
| `/presentations/new`、`/presentations/:id` | `src/views/student/presentation/PresentationWorkspaceView.vue` | 主线 |
| `/spreadsheets/new`（进入对话） | `src/views/student/chat/StudentChatView.vue` | 主线；不提供独立电子表格编辑/详情页 |
| `/library` | `src/views/student/library/LibraryHomeView.vue` | 主线 |
| `/library/:id` | `src/views/student/library/LibraryDetailView.vue` | 主线 |
| `/resources/:resourceId/preview` | `src/views/student/resource/ResourcePreviewView.vue` | 主线统一只读预览 |
| `/learning/new`、`/learning/setup/:id` | `src/views/student/chat/StudentChatView.vue` | 主线；新建与方案制定历史统一使用聊天、画像 Card 和确认文档 |
| `/learning/projects` | `src/views/student/learning/LearningProjectsView.vue` | 主线 |
| `/learning/:id` | `src/views/student/learning/LearningPlanView.vue` | 主线 |
| `/learning/:id/study` | `src/views/student/learning/LearningStudyView.vue` | 主线 |
| `/learning/:id/resources` | `src/views/student/learning/LearningResourcesView.vue` | 主线 |
| `/learning/:id/mistakes` | `src/views/student/learning/LearningMistakesView.vue` | 主线 |

## 2. 主线组件

| 目录 | 职责 |
| --- | --- |
| `src/components/layout` | 学生端整体布局、主侧边栏、学习详情布局 |
| `src/components/chat` | 消息列表、Markdown/代码渲染、输入附件和分段查看；不再包含思维导图抽屉 |
| `src/components/artifact` | 对话生成文件的统一附件卡片，以及与编辑页共用真实渲染规则的只读思维导图预览 |
| `src/components/capture` | 浏览器语音录制、拍照和图片选择；不包含正式 AI 识别算法 |
| `src/components/library` | 知识库创建和全局资料上传 |
| `src/components/learning` | 学习画像、方案文档、题目、助教、资源与导图预览 |
| `src/components/presentation` | PPT 对话确认/结果卡、大纲编辑和统一页面预览；不直接访问后端或讯飞 |
| `src/components/common` | 不依赖具体业务实体的按钮、输入、选择浮层、弹窗、状态和图标 |

主线页面可以依赖上述目录。旧 `src/views/legacy` 和 `src/components/legacy` 已删除，不得重新作为新功能依赖。

## 2.1 主线数据与状态文件

| 目录/文件 | 职责 |
| --- | --- |
| `src/repositories/*` | Mock/API 双实现和正式 HTTP 端点；页面不得直接选择数据源 |
| `src/types/contracts/*` | 前后端共享 DTO、任务和状态类型 |
| `src/mock/storage.ts` | Mock 业务数据的用户隔离 `sessionStorage` 访问 |
| `src/mock/generators/*` | 仅 Mock 使用的画像、方案、题目和评分演示逻辑 |
| `src/types/contracts/learning.ts` | 智能学习正式项目/阶段/任务/资源/题目/错题 DTO 及请求响应；知识库可空，方案输入使用 sourceResourceIds/mediaAssetIds，题组使用批量答案请求 |
| `src/repositories/learning.ts` | 智能学习 Mock/API 双 Repository；除项目 CRUD、任务和答题外，提供 `/resources/generated` 正式幂等端点，把生成文件回写项目资源包 |
| `src/stores/learning.ts` | 编排项目状态、学习行为与生成资源闭环；Mock 按 artifactId/resourceId upsert 项目资源，API 调正式回写接口并采用后端返回的项目聚合 |
| `src/stores/learningTutor.ts` | 建立项目助教会话；Mock 提供模拟摘要，API 仅传 project/stage/task/exercise 上下文 ID |
| `src/mock/generators/learning.ts` | Mock 方案、题目、默认方案/思维导图、按需资源内容和原型评分；API 模式不得调用 |
| `src/mock/generators/__tests__/learningWorkflow.spec.ts` | 覆盖空知识库、项目 ready 状态、默认资源、题量和任务题目关联 |
| `src/composables/useLearningPlanRoute.ts` | 学习详情路由统一加载、失败、无效 id 和重试 |
| `src/components/learning/LearningRouteState.vue` | 学习详情页共用加载、错误和不存在状态 |
| `src/components/common/AppSelectMenu.vue` | 主线共用选择浮层；负责锚点定位、视口避让、选中/禁用、点击外部和 Esc 关闭、展开动画及可选底部创建动作，不读取业务 Store |
| `src/types/contracts/media.ts` | 媒体资产、语音转写、图片识别任务、状态和前端限制 |
| `src/types/contracts/artifact.ts` | 所有聊天生成文件共用的状态、项目资源位、文件信息和轻量预览契约；思维导图携完整树与 renderConfig，表格不是独立卡片类型 |
| `src/components/artifact/ArtifactCard.vue` | 图片、思维导图、DOCX、PDF、XLSX、PPTX 等生成结果的统一卡片；操作固定为编辑（支持时）→下载→预览，预览位于最右 |
| `src/components/artifact/MindMapStaticPreview.vue` | 对话和统一资源预览共用的 simple-mind-map 只读画布；读取与编辑页相同的完整树和后端 renderConfig |
| `src/utils/mindMapTheme.ts` | 保留节点样式并解析思维导图渲染配置；绿色 logicalStructure 只作为 Mock 缺省，不覆盖正式返回主题 |
| `src/utils/resourcePreviewSync.ts` | 保存后在当前页面和同源标签页广播 resourceId/preview/version，驱动已打开的统一预览立即刷新 |
| `src/utils/artifact.ts` | artifact upsert 及旧 PPT/Spreadsheet 卡到统一附件的兼容转换 |
| `src/repositories/media.ts` | MediaRepository 接口及 Mock/API 两套实现 |
| `src/types/contracts/presentation.ts` | PPT 配置、消息卡、上下文、大纲、预览、状态和异步任务 DTO |
| `src/repositories/presentation.ts` | PresentationRepository 及 Mock/API 两套实现；包含草稿取消、关联持久化，Mock 只在下载时生成 PPTX |
| `src/stores/presentation.ts` | 编排 PPT 草稿、关联、大纲、轮询、取消、恢复和下载 |
| `src/views/student/presentation/PresentationWorkspaceView.vue` | 路由页面；组织配置、大纲、生成进度和预览四步流程 |
| `src/components/presentation/PresentationOutlineEditor.vue` | 编辑页面标题、要点、备注、布局和顺序 |
| `src/components/presentation/PresentationSlidePreview.vue` | Mock 渲染结构化预览；API 有预览图时展示后端实际页面 |
| `src/components/presentation/PresentationChatCard.vue` | 只渲染 PPT 生成前 proposal 配置特例；页数旁可选择/新建关联知识库，生成结果改用统一 ArtifactCard |
| `src/utils/presentation.ts` | PresentationDto 与消息卡转换、工作区路由参数组装；被消息与工作区共同复用 |
| `src/utils/stream.ts` | 解析现有 SSE 事件边界；聊天 Repository 在同一流上消费文本、PPT proposal 和统一 artifact 事件 |
| `src/repositories/__tests__/presentation.spec.ts` | 创建、大纲、生成、自动资源 ID 和 PPTX 下载测试 |
| `src/repositories/__tests__/formalGeneratedResourceContract.spec.ts` | 正式 API 的项目资源回写、PPT 关联与单附件稳定重试端点测试 |
| `src/stores/__tests__/learningResourceSync.spec.ts` | Mock 项目资源新增、更新和幂等去重测试 |
| `tests/e2e/presentation-workflow.spec.ts` | 浏览器验证配置、大纲编辑、生成和预览主链路 |
| `src/types/contracts/spreadsheet.ts` | 对话生成请求、消息任务卡、只读工作簿、上下文、状态和任务 DTO |
| `src/repositories/spreadsheet.ts` | SpreadsheetRepository 及 Mock/API 双实现；创建即启动任务，Mock 完成时自动归档并在下载时生成 XLSX |
| `src/stores/spreadsheet.ts` | 编排直接生成、轮询、取消、失败重试、刷新恢复和下载 |
| `src/utils/spreadsheet.ts` | SpreadsheetDto 与迁移期旧消息卡转换；新对话结果使用只读 artifact，不生成 editorRoute |
| `src/repositories/__tests__/spreadsheet.spec.ts` | 对话直达生成、上下文、自动资料库归档和 XLSX 下载测试 |
| `src/types/contracts/library.ts` | 全局资源 DTO；统一 `resourceId`、`knowledgeBaseId`、`projectId`、预览状态和分类型大小限制 |
| `src/repositories/libraryResource.ts` | `/api/resources` Mock/API 双实现，处理上传、关联、重命名、统一预览、下载和删除；Mock 原文件只保存在当前标签页内存 |
| `src/views/student/resource/ResourcePreviewView.vue` | 唯一主线只读预览工作区；资料库、知识库详情、智能学习资源包和聊天生成文件都按 resourceId 进入 |
| `src/stores/message.ts` | 消费现有 SSE；ready artifact 在 Mock/API 分别执行同义归档/刷新与项目回写；单附件重试保持 artifact/resource 身份；思维导图保存后同步完整树与配置 |
| `src/utils/resourcePreview.ts` | 统一生成资源预览路由，携带受控来源和内部 returnTo |
| `src/components/capture/VoiceRecorder.vue` | 麦克风权限、录音状态、停止、取消和转写回填 |
| `src/components/capture/ImageCaptureUploader.vue` | 仅供移动端 AppInput 的 `+` 菜单使用，负责上传照片、后置摄像头调用和预校验 |
| `src/utils/file.ts` | 唯一附件规则入口：统一格式、MIME、大小、图片/音频判断和拍照来源标记；明确排除视频 |

媒体入口对现有主线文件的影响：

| 文件 | 新职责 |
| --- | --- |
| `src/components/common/AppInput.vue` | Web 左侧通用附件支持文档、Office、ZIP、图片和音频，右侧显示模型/语音/发送；移动端最左侧 `+` 与附件、照片、拍照图标共用一个向上生长的竖向 pill |
| `src/views/student/chat/StudentChatView.vue` | 启用普通聊天和学习方案对话的媒体入口，传递会话、知识库、学习项目及来源资源上下文 |
| `src/components/library/UploadMaterialModal.vue` | 仅保留通用上传/拖拽入口；支持与输入框一致的附件格式，但不显示独立照片或拍照图标 |
| `src/stores/message.ts` | 图片先上传、音频先转写为媒体资产，再把 mediaAssetIds 交给聊天接口；Office/ZIP 走 DocumentRepository |
| `src/stores/libraryResource.ts` | 图片和音频走 MediaRepository，其余支持附件走资料库资源上传，避免页面直接判断数据源 |
| `src/repositories/chat.ts` | 正式聊天支持 mediaAssetIds、两种 clientAction 及 presentation/spreadsheet SSE；Mock 意图模拟仅留在 Mock 实现 |
| `src/components/layout/StudentSidebar.vue` | 折叠 pill 增加平滑进出、hover/按压反馈和当前路由选中状态，不改变侧边栏路由结构 |

智能学习主线文件的最终职责：

| 文件 | 职责 |
| --- | --- |
| `src/views/student/chat/StudentChatView.vue` | `/learning/new` 与 `/learning/setup/:id` 创建主线；按“需求对话 → 学习画像 Card → 可编辑确认文档 → 用户确认 → 异步生成项目”推进，并用 setupId、项目草稿和活动 job 恢复状态 |
| `src/views/student/learning/LearningProjectsView.vue` | 项目列表、状态筛选和入口；draft/configuring 返回 `/learning/new?projectId=:id` |
| `src/views/student/learning/LearningPlanView.vue` | 项目聚合详情、阶段入口、画像、资源摘要和项目助教抽屉 |
| `src/views/student/learning/LearningStudyView.vue` | 阅读、题组交卷、追加练习、案例结果与任务推进；没有绕过完成规则的手动完成按钮 |
| `src/views/student/learning/LearningMistakesView.vue` | 错题重答、掌握状态、巩固题组生成和批量交卷 |
| `src/views/student/learning/LearningResourcesView.vue` | 默认/按需资源生成、下载、统一预览跳转和 generating 刷新恢复；不再维护页面私有预览弹窗 |
| `src/components/learning/LearningTutorPanel.vue` | 复用对话消息展示和输入，向 Store 提供当前阶段/任务/题目，不自行拼正式 RAG |
| `src/components/layout/StudentSidebar.vue` | 项目新建、重命名、删除与导航；历史 learning-setup 会话进入 `/learning/setup/:conversationId?projectId=:projectId` 并恢复原 Card 对话 |
| `src/stores/libraryResource.ts` | 所有学习上传/生成文件进入全局资料库；项目生成后补关联，项目删除时只解除 projectId |
| `src/repositories/chat.ts`、`src/stores/message.ts` | 学习助教正式请求透传 projectId/stageId/taskId/exerciseId，API 不注入前端 tutorContext |

统一选择浮层对现有主线文件的影响：

| 文件 | 新职责 |
| --- | --- |
| `src/views/student/presentation/PresentationWorkspaceView.vue` | 受众、风格、关联知识库使用共用浮层；知识库菜单可打开既有新建知识库弹窗 |
| `src/components/presentation/PresentationOutlineEditor.vue` | 页面版式使用共用浮层，编辑值仍为 `PresentationSlideLayout` |
| `src/components/library/UploadMaterialModal.vue` | 归属知识库使用共用浮层，并复用 `LibraryKnowledgeCreateModal.vue` |
| `src/components/learning/LearningProfileCard.vue` | 画像字段使用紧凑 Card 和学习画像菜单编辑，不再保留独立大表单创建页 |
| `src/views/student/learning/LearningPlanView.vue` | 方案调整的目标类型使用共用浮层 |
| `src/views/student/learning/LearningStudyView.vue` | 追加练习难度使用共用浮层 |
| `src/components/learning/LearningQuestionCard.vue` | 代码语言使用紧凑型共用浮层 |

聊天知识库、模型选择、资料库菜单、项目状态和错题筛选原本已经是 `ui-menu-panel` 浮层，继续保留现有业务组件；不为视觉统一复制第二套业务逻辑。主线完整弹窗仍保持弹窗结构，只有弹窗内部的选择控件改用共用浮层。

PPT、电子表格和统一资源模型对现有主线文件的影响：

| 文件 | 新职责 |
| --- | --- |
| `src/router/index.ts` | 注册 PPT 生成工作区、电子表格对话入口和 `/resources/:resourceId/preview`；不注册电子表格详情/编辑页，旧 `/knowledge` 重定向主线 `/library` |
| `src/stores/auth.ts` | Mock 未登录访客数据在当前标签页刷新时保留；API 模式仍清理遗留 guest Mock 数据 |
| `src/App.vue` | Mock 未登录访客启动时重新获取 sessionStorage 中的会话、知识库和分析实体；API 未登录状态不请求业务接口 |
| `src/views/student/chat/StudentChatView.vue` | 欢迎页展示四个快捷入口；移除消息气泡思维导图按钮、聊天思维导图抽屉及其页面状态 |
| `src/components/chat/message/MessageBubble.vue` | 渲染 Markdown/代码、唯一 PPT proposal 特例和统一 ArtifactCard；旧表格/PPT 结果在显示边界转换为 artifact |
| `src/stores/message.ts` | 保存/恢复 artifacts，消费现有 SSE 上的 artifact upsert；Mock ready 写资料库并回写项目包，API ready 刷新后端权威资料库并调用项目回写端点；保留旧结构化字段兼容读取 |
| `src/components/layout/StudentSidebar.vue` | 首次挂载立即同步已加载项目，空 Store 时调用统一 fetchPlans；Mock/API 项目都显示在智能学习树中 |
| `src/views/student/learning/LearningResourcesView.vue` | PPT 资源可创建和恢复；所有 ready 资源通过 resourceId 进入统一预览，操作顺序为预览、下载、生成/重试 |
| `src/stores/learning.ts` | 所有文件类型按统一输入回写项目资源包；learningResourceId 可命中既有资源位，为空则按 artifactId/resourceId 新增或更新；API 使用正式幂等接口 |
| `src/stores/libraryResource.ts` | Mock/API 统一全局资源；生成完成按 externalKey 去重并自动归档 |
| `src/views/student/library/LibraryHomeView.vue` | 来源/文件类型组合筛选；卡片/列表正文点击统一预览，圆形/方形选择区才切换批量选择 |
| `src/views/student/library/LibraryDetailView.vue` | ready 文件整行和眼睛按钮进入统一预览；下载仍使用对应 Repository |
| `src/views/student/presentation/PresentationWorkspaceView.vue` | 用户可见术语统一显示“知识库”；按 sourceMessageId 恢复配置，持久化关联，并把 ready 资源同步资料库、知识库、项目资源包和原消息卡 |
| `package.json`、`package-lock.json` | `pptxgenjs`、`exceljs`、`jszip`、`pdf-lib` 分别服务 Mock 的真实 PPTX/XLSX/DOCX/PDF 下载；正式模式仍下载后端文件 |

主线 Store 只编排 Repository 和当前页面内存状态。正式模式不得从 `mock` 读取实体，也不得把项目、题目、答题或资源成功状态写入 Web Storage。

## 3. 已清理的替代实现

2026-07-18 完成人工二次确认后，以下前端文件已删除：

| 分类 | 删除内容 | 删除依据 |
| --- | --- | --- |
| 旧知识库实现 | `KnowledgeBaseView.vue`、`KnowledgeBaseCard.vue`、`KnowledgeBaseList.vue`、`KnowledgeBaseDetail.vue`、对应列表测试、旧 `stores/document.ts` | `/knowledge/*` 已重定向 `/library/*`；列表、详情和文件预览均有主线替代 |
| 旧版页面与组件 | `views/legacy/*`、`components/legacy/*` | 无 Router、主线 import 或测试依赖；对话和学习工作区已有主线实现 |
| 空壳与重复源码 | 空 `CodeBlock.vue`、`ReasoningBlock.vue`、`EmptyState.vue`、`LoadingDots.vue`，未使用 `api/index.ts`、`utils/time.ts`、重复 `views/admin/index.vue` 和异常 `src/layouts` | 零引用、无独有逻辑或已有实际路由实现 |
| 临时文件 | `fix_imports.cjs`、`out.txt`、Playwright 官网示例、已提交的 `.vite/deps` 缓存 | 不属于产品功能、正式测试或构建输入 |
| 已统一的聊天特例 | `components/chat/MindMapPanel.vue`、`components/spreadsheet/SpreadsheetChatCard.vue` | 思维导图不再使用聊天抽屉；电子表格生成结果与其他文件共用 `ArtifactCard.vue` |
| 已删除的电子表格页面 | `views/student/spreadsheet/SpreadsheetWorkspaceView.vue`、`/spreadsheets/:id` | 电子表格不提供编辑或独立详情页，统一进入资源只读预览 |

`RobotAI-Learning-Icon-Black.svg` 与 `RobotAI-Learning-Icon-Color.svg` 经产品确认继续保留。`src/api/document.ts` 和 `src/repositories/document.ts` 仍服务试卷分析与附件链路，也继续保留。

## 4. 仍有路由的非主线模块

以下模块不在本次三个主要功能范围内，但仍有 Router 引用，不能删除或归档：

- `src/views/ResourceCenterView.vue`
- `src/views/ExamAnalysisListView.vue`、`src/views/ExamAnalysisView.vue`
- `src/views/mindmap/*`
- `src/components/sidebar/*`
- `src/views/admin/*` 与 `src/layout_admin/*`

是否保留这些路由属于单独产品决策，不能仅凭当前主线没有入口就删除。

## 5. 后续删除规则

此前已删除 `src/components/knowledge/DocumentPreviewModal.vue` 及对应零引用方法；本轮进一步删除了第 3 节列出的旧知识库和 `legacy` 实现。后续文件只有同时满足以下条件时才进入删除候选：

1. 没有 Router、静态 import、动态 import 和测试引用。
2. 没有主线仍需复用的组件、样式或业务逻辑。
3. 已有替代实现，且恢复价值低。
4. 得到人工二次确认。

当前静态入口复查只剩 `src/types/simple-mind-map.d.ts` 不通过普通 import 可达；它是 TypeScript 自动加载的模块声明，且对应插件仍被思维导图功能使用，因此不是删除候选。
