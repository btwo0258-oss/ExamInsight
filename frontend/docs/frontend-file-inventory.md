# 学生端前端文件清单

> 范围：新对话、资料库、智能学习、PPT 生成及详情页。
>
> 本清单只描述前端文件归属，不包含后端实现。

## 1. 主线路由页面

| 路由 | 页面文件 | 状态 |
| --- | --- | --- |
| `/chat`、`/chat/:id` | `src/views/student/chat/StudentChatView.vue` | 主线 |
| `/presentations/new`、`/presentations/:id` | `src/views/student/presentation/PresentationWorkspaceView.vue` | 主线 |
| `/library` | `src/views/student/library/LibraryHomeView.vue` | 主线 |
| `/library/:id` | `src/views/student/library/LibraryDetailView.vue` | 主线 |
| `/learning/new` | `src/views/student/learning/LearningHomeView.vue` | 主线 |
| `/learning/projects` | `src/views/student/learning/LearningProjectsView.vue` | 主线 |
| `/learning/:id` | `src/views/student/learning/LearningPlanView.vue` | 主线 |
| `/learning/:id/study` | `src/views/student/learning/LearningStudyView.vue` | 主线 |
| `/learning/:id/resources` | `src/views/student/learning/LearningResourcesView.vue` | 主线 |
| `/learning/:id/mistakes` | `src/views/student/learning/LearningMistakesView.vue` | 主线 |

## 2. 主线组件

| 目录 | 职责 |
| --- | --- |
| `src/components/layout` | 学生端整体布局、主侧边栏、学习详情布局 |
| `src/components/chat` | 消息列表、消息渲染、附件、分段查看和思维导图面板 |
| `src/components/capture` | 浏览器语音录制、拍照和图片选择；不包含正式 AI 识别算法 |
| `src/components/library` | 资料库创建和资料上传 |
| `src/components/learning` | 学习画像、方案文档、题目、助教、资源与导图预览 |
| `src/components/presentation` | PPT 大纲编辑和统一页面预览；不直接访问后端或讯飞 |
| `src/components/common` | 不依赖具体业务实体的按钮、输入、弹窗、状态和图标 |

主线页面可以依赖上述目录。主线目录不得依赖 `src/views/legacy` 或 `src/components/legacy`。

## 2.1 主线数据与状态文件

| 目录/文件 | 职责 |
| --- | --- |
| `src/repositories/*` | Mock/API 双实现和正式 HTTP 端点；页面不得直接选择数据源 |
| `src/types/contracts/*` | 前后端共享 DTO、任务和状态类型 |
| `src/mock/storage.ts` | Mock 业务数据的用户隔离 `sessionStorage` 访问 |
| `src/mock/generators/*` | 仅 Mock 使用的画像、方案、题目和评分演示逻辑 |
| `src/composables/useLearningPlanRoute.ts` | 学习详情路由统一加载、失败、无效 id 和重试 |
| `src/components/learning/LearningRouteState.vue` | 学习详情页共用加载、错误和不存在状态 |
| `src/types/contracts/media.ts` | 媒体资产、语音转写、图片识别任务、状态和前端限制 |
| `src/repositories/media.ts` | MediaRepository 接口及 Mock/API 两套实现 |
| `src/types/contracts/presentation.ts` | PPT 配置、上下文、大纲、预览、状态和异步任务 DTO |
| `src/repositories/presentation.ts` | PresentationRepository 及 Mock/API 两套实现；Mock 只在下载时生成 PPTX |
| `src/stores/presentation.ts` | 编排 PPT 创建、大纲、轮询、取消、恢复、下载和资料库关联 |
| `src/views/student/presentation/PresentationWorkspaceView.vue` | 路由页面；组织配置、大纲、生成进度和预览四步流程 |
| `src/components/presentation/PresentationOutlineEditor.vue` | 编辑页面标题、要点、备注、布局和顺序 |
| `src/components/presentation/PresentationSlidePreview.vue` | Mock 渲染结构化预览；API 有预览图时展示后端实际页面 |
| `src/repositories/__tests__/presentation.spec.ts` | 创建、大纲、生成、资料库关联和 PPTX 下载测试 |
| `tests/e2e/presentation-workflow.spec.ts` | 浏览器验证配置、大纲编辑、生成和预览主链路 |
| `src/components/capture/VoiceRecorder.vue` | 麦克风权限、录音状态、停止、取消和转写回填 |
| `src/components/capture/ImageCaptureUploader.vue` | 仅供移动端 AppInput 的 `+` 菜单使用，负责上传照片、后置摄像头调用和预校验 |
| `src/utils/file.ts` | 唯一附件规则入口：统一格式、MIME、大小、图片/音频判断和拍照来源标记；明确排除视频 |

媒体入口对现有主线文件的影响：

| 文件 | 新职责 |
| --- | --- |
| `src/components/common/AppInput.vue` | Web 左侧通用附件支持文档、Office、ZIP、图片和音频，右侧显示模型/语音/发送；移动端最左侧 `+` 与附件、照片、拍照图标共用一个向上生长的竖向 pill |
| `src/views/student/chat/StudentChatView.vue` | 启用聊天媒体入口并传递会话、资料库和学习项目上下文 |
| `src/views/student/learning/LearningHomeView.vue` | 启用学习目标补充的语音和图片入口 |
| `src/components/library/UploadMaterialModal.vue` | 仅保留通用上传/拖拽入口；支持与输入框一致的附件格式，但不显示独立照片或拍照图标 |
| `src/stores/message.ts` | 图片先上传、音频先转写为媒体资产，再把 mediaAssetIds 交给聊天接口；Office/ZIP 走 DocumentRepository |
| `src/stores/libraryResource.ts` | 图片和音频走 MediaRepository，其余支持附件走资料库资源上传，避免页面直接判断数据源 |
| `src/repositories/chat.ts` | 正式聊天请求增加 mediaAssetIds |
| `src/components/layout/StudentSidebar.vue` | 折叠 pill 增加平滑进出、hover/按压反馈和当前路由选中状态，不改变侧边栏路由结构 |

PPT 对现有主线文件的影响：

| 文件 | 新职责 |
| --- | --- |
| `src/router/index.ts` | 注册 `/presentations/new` 和 `/presentations/:id` |
| `src/stores/auth.ts` | Mock 未登录访客数据在当前标签页刷新时保留；API 模式仍清理遗留 guest Mock 数据 |
| `src/views/student/chat/StudentChatView.vue` | “生成 PPT”进入独立工作区，不再只填充提示词 |
| `src/views/student/learning/LearningResourcesView.vue` | PPT 资源可创建、恢复、查看和下载 |
| `src/stores/learning.ts` | Mock 完成后回写 presentationId；API 模式重新获取后端项目 |
| `src/stores/libraryResource.ts` | Mock 增加 externalKey=`presentation:{id}` 的资料库聚合项 |
| `src/views/student/library/LibraryHomeView.vue` | 从 PPT 聚合项进入 PPT 工作区，普通文件保持原选择逻辑 |
| `src/views/student/library/LibraryDetailView.vue` | PPT 文件开放预览入口并使用 PresentationRepository 下载 |
| `src/views/student/presentation/PresentationWorkspaceView.vue` | 用户可见术语统一显示“知识库”，内部仍复用 libraryId 和现有 Repository |
| `package.json`、`package-lock.json` | 增加 `pptxgenjs`，仅服务 Mock 的真实 PPTX 下载 |

主线 Store 只编排 Repository 和当前页面内存状态。正式模式不得从 `mock` 读取实体，也不得把项目、题目、答题或资源成功状态写入 Web Storage。

## 3. 已归档文件

以下文件没有当前主线路由或静态 import，但仍包含可参考或可复用逻辑，因此保留在 `legacy`：

| 文件 | 归档原因 |
| --- | --- |
| `src/views/legacy/ChatView.vue` | 旧版对话页面，已由 `StudentChatView.vue` 替代 |
| `src/views/legacy/LearningWorkspaceView.vue` | 旧版学习工作区，已由智能学习路由组替代 |
| `src/components/legacy/chat/MessageArea.vue` | 只服务旧版对话页面的组合容器 |
| `src/components/legacy/chat/ChatHeader.vue` | 只被旧版 `MessageArea.vue` 使用 |
| `src/components/legacy/chat/ChatWelcome.vue` | 旧版对话欢迎状态 |
| `src/components/legacy/library/LibrarySelectModal.vue` | 当前零引用，但保留资料库选择交互 |
| `src/components/legacy/learning/ProjectSelectModal.vue` | 当前零引用，但保留学习项目选择交互 |

归档文件不参与新功能开发。需要恢复时，应先确认接口和样式是否仍符合主线约束。

## 4. 仍有路由的非主线模块

以下模块不在本次三个主要功能范围内，但仍有 Router 引用，不能删除或归档：

- `src/views/KnowledgeBaseView.vue` 与 `src/components/knowledge/*`
- `src/views/ResourceCenterView.vue`
- `src/views/ExamAnalysisListView.vue`、`src/views/ExamAnalysisView.vue`
- `src/views/mindmap/*`
- `src/components/sidebar/*`
- `src/views/admin/*` 与 `src/layout_admin/*`

是否保留这些路由属于单独产品决策，不能仅凭当前主线没有入口就删除。

## 5. 删除候选

当前没有已经确认可以删除的源文件。

本次发现的零引用文件都存在独有页面或交互逻辑，已归档处理。后续只有同时满足以下条件时才进入删除候选：

1. 没有 Router、静态 import、动态 import 和测试引用。
2. 没有主线仍需复用的组件、样式或业务逻辑。
3. 已有替代实现，且恢复价值低。
4. 得到人工二次确认。

本轮重新检查后仍没有可直接删除的源文件。`legacy` 内文件是“可能复用”的隔离区，不进入生产主线路由；实际删除需另行确认，不能与功能重构同时执行。
