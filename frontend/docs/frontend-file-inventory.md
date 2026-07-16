# 学生端前端文件清单

> 范围：新对话、资料库、智能学习及详情页。
>
> 本清单只描述前端文件归属，不包含后端实现。

## 1. 主线路由页面

| 路由 | 页面文件 | 状态 |
| --- | --- | --- |
| `/chat`、`/chat/:id` | `src/views/student/chat/StudentChatView.vue` | 主线 |
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
| `src/components/capture/VoiceRecorder.vue` | 麦克风权限、录音状态、停止、取消和转写回填 |
| `src/components/capture/ImageCaptureUploader.vue` | 上传照片、后置摄像头调用、格式/大小/数量预校验 |
| `src/utils/mediaFile.ts` | 在当前页面内存中标记图片来自上传或摄像头，并统一判断图片文件 |

媒体入口对现有主线文件的影响：

| 文件 | 新职责 |
| --- | --- |
| `src/components/common/AppInput.vue` | 组合文档附件、语音、上传照片和拍照入口；失败时保留待发送内容 |
| `src/views/student/chat/StudentChatView.vue` | 启用聊天媒体入口并传递会话、资料库和学习项目上下文 |
| `src/views/student/learning/LearningHomeView.vue` | 启用学习目标补充的语音和图片入口 |
| `src/components/library/UploadMaterialModal.vue` | 增加拍照入口，图片由资料库 Store 交给 MediaRepository |
| `src/stores/message.ts` | 图片先上传为媒体资产，再把 mediaAssetIds 交给聊天接口 |
| `src/stores/libraryResource.ts` | 图片走媒体上传，文档继续走资料库资源上传，避免重复上传 |
| `src/repositories/chat.ts` | 正式聊天请求增加 mediaAssetIds |

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
