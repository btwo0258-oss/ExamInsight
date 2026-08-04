# ExamInsight V2 数据模型规范

> 状态：逻辑模型冻结稿，物理 DDL 的唯一设计依据  
> 目标数据库：MySQL 8  
> 新 Schema：`examinsight_v2`  
> 本文不要求一次性创建全部表；实施必须按 [MIGRATION_AND_ACCEPTANCE.md](./MIGRATION_AND_ACCEPTANCE.md) 的纵向切片推进。字段类型、键和删除顺序以 [PHYSICAL_SCHEMA.md](./PHYSICAL_SCHEMA.md) 为准。

## 1. 建模原则

1. MySQL 是业务事实的唯一来源；对象存储、Elasticsearch 和 Redis 均为从属设施。
2. 项目目标、范围、计划、任务、作答、掌握度和错题等核心状态必须关系化，不能塞入通用 JSON。
3. 会被确认、引用或用于重新计算的内容必须版本化且不可变。
4. “当前版本”只是根对象上的指针，不能覆盖历史版本。
5. AI 输出先成为候选数据，通过校验和用户确认后才能进入当前业务状态。
6. 资料进入多个知识库或项目时只增加关联，不复制原文件、不重复解析。
7. 项目读取已锁定的资料版本和解析结果，不实时读取知识库最新内容。
8. 每个写操作必须验证所有权、状态、版本和幂等键。
9. 派生数据必须能从原始证据重算；缓存字段不是业务事实。
10. 不为“未来也许有用”建立第二套并行领域模型。

## 2. 通用字段与约束

### 2.1 标识符

- 内部主键：`BIGINT UNSIGNED`，仅用于数据库关联。
- 对外 ID：`external_id CHAR(26)`，使用单调 ULID，设置唯一索引。
- API、URL、日志和客户端缓存只使用外部 ID，不暴露连续内部主键。
- 关联表如无独立生命周期，可使用联合唯一键，不强制生成外部 ID。

### 2.2 时间

- 服务器时间统一保存为 UTC `DATETIME(3)`。
- 每张有生命周期的表至少包含 `created_at`、`updated_at`。
- 计划日期保存为项目时区下的 `DATE`，项目同时保存 IANA 时区，例如 `Asia/Shanghai`。
- 不将浏览器本地时间直接作为可信完成时间或计费时间。

### 2.3 状态与并发

- 不使用 MySQL `ENUM`；使用 `VARCHAR`、Java 枚举和必要的 `CHECK` 约束。
- 可编辑根对象包含 `row_version BIGINT`，使用乐观锁。
- HTTP 更新使用 `ETag/If-Match`；冲突返回 `409 VERSION_CONFLICT`。
- 创建、确认、提交作答、重试任务和删除等写操作支持 `Idempotency-Key`。

### 2.4 所有权

- 用户私有根对象必须直接包含 `user_id`，不能仅靠多级 JOIN 推导所有权。
- 项目子对象同时保存 `project_id`；服务端验证 `project.user_id == current_user_id`。
- 关联两个私有对象时，必须验证它们属于同一用户和允许的领域范围。
- 管理员访问用户内容不绕过业务审计，必须经过限时访问授权。

### 2.5 删除规则

- 用户可恢复删除使用 `deleted_at` 和删除状态，不立即物理删除。
- 根对象默认使用 `RESTRICT`；纯关联表可以 `ON DELETE CASCADE`。
- 对象存储、分块、向量和派生索引由删除任务按顺序清理。
- 永久删除完成后写入不含用户内容的墓碑记录，阻止备份恢复造成数据复活。

## 3. 存储职责

| 设施 | 负责内容 | 不得作为 |
|---|---|---|
| MySQL | 用户、权限、资料元数据、版本、计划、任务、作答、账本、审计 | 大文件存储 |
| 对象存储 | 上传原件、生成文件、导出包、渲染产物 | 权限和业务状态真相 |
| Elasticsearch | 文本检索、向量检索和可重建索引 | 原始资料或唯一检索证据 |
| Redis | 限流、锁、心跳、短期会话辅助、SSE 游标 | 永久任务状态、余额、计划状态 |

## 4. 平台基础与异步任务

| 表 | 责任 |
|---|---|
| `async_job` | 所有异步执行的唯一状态、进度、错误和取消事实 |
| `async_job_attempt` | 每次领取、执行、心跳、失败和重试记录 |
| `outbox_event` | 业务事务提交后可靠投递领域事件 |
| `idempotency_record` | 写请求幂等键、请求摘要和响应摘要 |
| `domain_audit_event` | 项目、范围、计划等领域对象的不可变修改审计 |
| `legacy_import_map` | 旧库记录到 V2 对象的幂等迁移映射，仅供迁移与审计 |
| `model_provider` | 模型供应商及启用状态，不存明文密钥 |
| `model_definition` | 可调用模型、能力、上下文限制和供应商映射 |

`async_job` 核心字段：

```text
external_id, user_id, job_type, aggregate_type, aggregate_id
status, progress_current, progress_total, stage_key
priority, idempotency_key, cancellable
scheduled_at, started_at, heartbeat_at, finished_at
error_code, safe_error_message, row_version
```

状态固定为：

```text
QUEUED → RUNNING → SUCCEEDED
                ↘ RETRY_WAIT → RUNNING
                ↘ FAILED
QUEUED/RUNNING → CANCELLING → CANCELLED
```

业务表如 `generation_job`、`data_export_job`、`deletion_job` 只保存领域参数和 `async_job_id`，不得复制另一份可被客户端写入的执行状态。

## 5. 用户、登录与安全

| 表 | 责任 |
|---|---|
| `app_user` | 用户账户根、邮箱规范化值和账户状态 |
| `user_credential` | Argon2id 密码哈希、策略版本和修改时间 |
| `user_profile` | 昵称、头像等非敏感资料 |
| `user_setting` | 主题、语言、时区及产品偏好 |
| `user_device` | 风险判断需要的匿名设备记录 |
| `auth_session` | 服务端不透明会话、过期、撤销和最近活动 |
| `email_verification` | 注册、登录升级验证等验证码挑战 |
| `email_delivery` | 邮件发送状态、模板和供应商回执 |
| `password_reset_token` | 一次性高熵密码重置令牌摘要 |
| `security_event` | 登录失败、限流、验证、会话撤销等安全事件 |
| `admin_user` | 与普通用户隔离的管理员身份 |
| `admin_mfa_credential` | 管理员 TOTP/Passkey 凭据 |
| `admin_recovery_code` | 一次性恢复码摘要 |
| `admin_session` | 更短生命周期的管理员会话 |

约束：

- `app_user.normalized_email` 全局唯一。
- `app_user.age_gate_acknowledged_at` 只记录用户确认年满 18 周岁的时间，不收集出生日期。
- 密码只保存 Argon2id 编码结果，不单独保存盐。
- 邮箱验证码、验证成功后的一次性注册证明和重置令牌只保存摘要；验证码有用途、有效期、尝试次数和消费时间，同邮箱同用途只允许一个待处理挑战。
- 公开注册在邮箱验证完成前不创建 `app_user`；注册证明只能消费一次，账户、默认资料、凭据和 Session 必须在同一事务中创建。
- Session Cookie 使用 `__Host-examinsight_session`，数据库只保存随机令牌摘要。
- `auth_session.token_version` 用于单会话轮换；`app_user.session_version` 用于全部退出、改密和密码重置后的全局失效。
- 用户凭据、普通会话和管理员凭据不得共表。
- 公开 Beta 管理员必须通过启用用户验证的 Passkey 登录；TOTP 只能作为追加验证，不能单独建立管理员 Session。

## 6. 隐私、导出与删除

| 表 | 责任 |
|---|---|
| `processing_purpose` | 数据处理目的目录 |
| `privacy_notice_version` | 隐私声明版本与内容摘要 |
| `privacy_notice_acknowledgement` | 用户确认过的声明版本 |
| `user_consent` | 可撤回的独立同意记录 |
| `processor`、`processor_version` | 邮件、模型、存储等外部处理方版本 |
| `privacy_request`、`privacy_request_event` | 查阅、导出、更正、删除请求及审计 |
| `data_export_job` | 导出范围、对象存储包和过期时间 |
| `account_deletion_request` | 账户删除申请、七日撤销窗口和状态 |
| `deletion_job`、`deletion_item` | 跨数据库、对象存储和索引的删除执行清单 |
| `data_tombstone` | 防止备份恢复后数据复活的匿名墓碑 |
| `retention_policy`、`retention_run` | 保留规则及执行结果 |
| `legal_hold` | 合法保留例外，不保存无关用户内容 |
| `admin_access_case`、`admin_access_grant`、`admin_access_audit` | 管理员限时、限范围访问用户数据 |

权利请求、导出和账户删除不是三套重复流程：`POST /privacy/exports` 原子创建 `privacy_request(EXPORT)` 与 `data_export_job`；账户删除原子创建 `privacy_request(DELETION)` 与 `account_deletion_request`。其他查阅、更正、限制和反对请求只进入通用权利请求流程。公开 Beta 内部处理目标为 15 天，不把它声明成跨司法辖区统一法定期限。

删除状态：

```text
ACTIVE → TRASHED → PURGE_SCHEDULED → PURGING → PURGED
                                      ↘ COMPLETED_WITH_RETENTION
```

默认规则：回收站 30 天；账户删除申请 7 天可撤销；导出下载链接 24 小时、导出包 7 天；备份最长保留 30 天并在恢复后重放墓碑。删除任务逐项覆盖 MySQL、对象存储、搜索索引和缓存；依法保留项必须绑定一种明确依据。用户根记录被清除后，权利请求、删除任务和管理员审计只通过 HMAC `subject_hash` 保留必要关联，不保存邮箱或原始对象标识。产品事件、AI 元数据、账本和审计的完整保留期限见 [PHYSICAL_SCHEMA.md 第 22.6 节](./PHYSICAL_SCHEMA.md#226-数据保留期限)；以后调整必须创建新的 `retention_policy` 版本。

## 7. 文件、资料和知识库

| 表 | 责任 |
|---|---|
| `upload_session` | 分片上传、大小、校验和和过期时间 |
| `storage_object` | 对象存储 Key、哈希、MIME、大小和清理状态 |
| `asset` | 用户拥有的逻辑资料，负责名称和生命周期 |
| `asset_version` | 不可变文件/文本版本、来源、AI 标识和当前处理状态 |
| `asset_parse_result` | 某资料版本的一次解析结果和解析器版本 |
| `document_chunk` | 解析结果的文本分块、页码和定位信息 |
| `embedding_record` | 分块在特定模型/索引版本下的向量索引记录 |
| `knowledge_base` | 用户命名的资料集合 |
| `knowledge_base_asset` | 知识库和资料的多对多关系 |

关键关系：

```text
asset 1 ── N asset_version
asset_version 1 ── N asset_parse_result
asset_parse_result 1 ── N document_chunk
document_chunk 1 ── N embedding_record
knowledge_base N ── N asset
```

约束：

- 相同内容可通过哈希复用底层 `storage_object`，但不同用户仍拥有独立 `asset` 权限对象。
- `storage_object.owner_user_id` 只记录首次写入来源；下载、关联和删除权限必须沿 `asset → asset_version` 判断，跨用户去重不得形成存在性或时延侧信道。
- 同一 `upload_session` 最多产生一个 `asset_version`，重复完成请求返回原结果；批量上传只是每个文件各建一个会话，不增加第二套批次事实。
- `asset.current_version_id` 只在新版本确认可用后切换。
- 更新文件内容创建新 `asset_version`，不覆盖旧版本。
- `asset_version` 的内容事实恰好来自对象存储文件或纯文本之一；文件解析正文只进入 `document_chunk`，不复制回版本正文。
- 安全扫描失败或不可用时对象保持隔离；解析失败保留已确认安全的原文件并允许重试，不能生成空的成功解析结果。
- MySQL 保存分块正文和向量索引元数据；实际向量位于可重建检索索引中，检索服务不是授权或内容事实来源。
- 项目使用具体版本时，删除资料只能进入回收站；项目锁定版本在项目有效期内保持可解释。
- `asset_version`、`learning_resource_version` 和 `message` 对 AI 生成内容保存生成标记、运行引用和面向用户的标识；不额外创建重复的标识表。
- 知识库只保存用户命名的集合和 `asset` 关联，不保存文件、文本、资料版本、解析结果或向量；加入多个知识库不会重复上传或解析。
- 关联表通过知识库和资料的组合所有权外键拒绝跨用户挂载；不能把“前端只展示自己的资料”当作授权边界。
- 活动与归档知识库的规范化名称在同一用户内唯一；进入回收站后释放名称，但恢复时若名称已被复用必须由用户改名后再恢复。
- 知识库进入回收站时保留资料关联；移除关联或永久删除知识库只删除关系，不删除个人资料。资料进入回收站也不立即移除关系，读取时按资料状态降级展示。
- 知识库引用逻辑资料并跟随其当前可用版本；只有学习项目会锁定具体资料版本和解析结果，知识库变化不得静默改写已确认项目依据。

个人资料库不是一张表，其查询定义为：

```text
SELECT 用户拥有且未永久删除的 asset
```

## 8. 对话、消息和 AI 执行

| 表 | 责任 |
|---|---|
| `conversation` | 普通或学习会话根，可选绑定项目或一个知识库 |
| `conversation_branch` | 编辑消息后的分支树和当前分支 |
| `message` | 不可变用户/助手/工具消息 |
| `message_part` | 文本及展示型结构片段 |
| `assistant_response_group` | 同一用户消息的多个重新生成回答及选中项 |
| `message_attachment` | 消息使用的具体资料版本 |
| `message_citation` | 助手回答对分块、题目、范围或资源的引用 |
| `ai_run` | 一次 AI 编排运行和最终状态 |
| `ai_context_snapshot` | 本次运行使用的上下文清单、版本和摘要 |
| `retrieval_run`、`retrieval_result` | 检索查询、过滤条件、命中和排序证据 |
| `capability_definition` | 首页能力状态、入口和额度策略 |
| `ai_tool_call` | 工具调用参数摘要、结果摘要和副作用类型 |
| `pending_action` | AI 提出的待用户确认业务操作 |

消息规则：

- `message` 创建后不更新正文；编辑创建新用户消息和新分支。
- 重新生成创建新的助手消息，不覆盖旧消息。
- `message_part` 的 JSON 只允许表达 UI 展示结构；引用、附件、计划修改和工具调用必须使用类型化表。
- 不保存模型隐藏思维链、供应商密钥、原始系统提示或不必要的完整上下文副本。

`ai_context_snapshot` 保存“使用了哪些对象和版本”，必要的文本摘要可 JSON 化，但不复制所有原始资料。这样可以复现依据，又不会制造第二套资料存储。

## 9. 学习项目、目标、依据与范围

| 表 | 责任 |
|---|---|
| `learning_project` | 项目根、名称、图标、时区、阶段和当前版本指针 |
| `project_source_set` | 一次确认的学习依据版本 |
| `project_source_set_item` | 锁定的资料版本和解析结果 |
| `exam_target_version` | 考试日期、目标和时间预算的不可变版本 |
| `availability_rule` | 每周可学习日和时间段 |
| `blackout_date` | 不可学习日期 |
| `concept`、`concept_alias`、`concept_relation` | 项目内规范知识点及关系 |
| `scope_version` | 一次考试范围候选或确认版本 |
| `scope_node` | 范围树节点、权重、优先级和是否纳入 |
| `scope_evidence` | 节点与资料分块/大纲证据的关系 |
| `scope_conflict`、`scope_conflict_evidence` | 资料之间的冲突及处理结果 |
| `exam_format_version` | 题型和试卷结构版本 |
| `exam_section`、`exam_section_rule` | 试卷分区、分值、题量和规则 |

`learning_project` 不保存一大块准备状态 JSON。准备状态按当前指针推导：

```text
无目标版本                   → TARGET_REQUIRED
目标已确认、无依据版本       → SOURCES_REQUIRED
依据已确认、无范围版本       → SCOPE_REQUIRED
范围已确认、诊断未决定       → DIAGNOSTIC_REQUIRED
诊断完成/跳过、无计划候选    → PLAN_REQUIRED
计划已确认、资源配置未确认   → RESOURCE_CONFIG_REQUIRED
以上均完成                   → READY
```

项目只允许一个可选基础知识库，但 `project_source_set_item` 可以来自个人资料库中的任意资料。

## 10. 题目、测评、评分和掌握度

### 10.1 题目

| 表 | 责任 |
|---|---|
| `question`、`question_version` | 题目根和不可变版本 |
| `question_option` | 选择题选项 |
| `question_answer_key` | 服务端答案根，不随普通题目接口返回 |
| `question_correct_option` | 正确选项集合 |
| `question_accepted_answer` | 可接受的短文本答案 |
| `question_rubric_item` | 分项评分标准 |
| `question_concept` | 题目覆盖概念及权重 |
| `question_citation` | 题目和答案依据 |
| `question_validation_run`、`question_validation_issue` | 结构、证据、重复、正确性等质量校验 |

题目发布状态：

```text
DRAFT → VALIDATING → VALID | INVALID | NEEDS_REVIEW
VALID → PUBLISHED
PUBLISHED → WITHDRAWN
```

只有 `PUBLISHED` 版本可进入正式测评。被使用的版本不可编辑；发现错误时撤回并发布新版本。

### 10.2 测评与作答

| 表 | 责任 |
|---|---|
| `assessment` | 诊断、练习、测验或模拟试卷根 |
| `assessment_blueprint_version` | 出题目标的不可变版本 |
| `assessment_blueprint_concept`、`assessment_blueprint_rule` | 概念覆盖、题型、难度和题量规则 |
| `assessment_version`、`assessment_item` | 已发布试卷版本和固定题序 |
| `assessment_attempt` | 用户一次开始、提交或超时的尝试 |
| `assessment_response` | 每道题的作答状态、用时和短文本答案 |
| `response_selected_option` | 多选/单选类答案的选项关联；不为单值文本答案重复建表 |
| `response_grade` | 自动/人工/重新评分版本 |
| `adaptive_selection_step` | 自适应选题时每步依据 |

提交后的 `assessment_version`、`assessment_item`、题目版本和原始答案均不可修改。重新评分创建新 `response_grade`，并明确当前有效评分。

### 10.3 诊断和掌握度

| 表 | 责任 |
|---|---|
| `diagnostic_result`、`diagnostic_concept_result` | 一次诊断及各概念结论 |
| `mastery_model_version` | 掌握度算法和阈值版本 |
| `mastery_evidence` | 诊断、练习、复习等可重算证据 |
| `concept_mastery_snapshot` | 某时点的掌握度、置信度和证据截止点 |

掌握度标签固定为：

```text
UNKNOWN / WEAK / BASIC / PROFICIENT / MASTERED
```

标签与 `confidence` 分开保存。没有证据时只能是 `UNKNOWN`，不能用用户未作答等同于薄弱。

## 11. 差距分析、计划与任务

| 表 | 责任 |
|---|---|
| `gap_analysis`、`gap_analysis_item` | 基于固定输入版本形成的差距和优先级 |
| `learning_plan`、`learning_plan_version` | 计划根、候选/当前/历史版本 |
| `plan_stage` | 计划阶段，不承担每日状态 |
| `learning_task`、`learning_task_version` | 任务身份和不可变内容版本 |
| `task_concept` | 任务覆盖概念及预期提升 |
| `task_dependency` | 任务前置条件 |
| `task_completion_rule` | 阅读、作答、正确率、时长等完成规则 |

一个项目只有一个 `learning_plan` 根，使用 `learning_plan(project_id)` 唯一约束；候选、当前和历史都由 `learning_plan_version` 表达，项目根不再重复保存当前计划版本指针。

计划版本状态：

```text
DRAFT → GENERATING → CANDIDATE → CONFIRMED
                         ↘ REJECTED
CONFIRMED → SUPERSEDED
```

重排计划只能改变尚未执行的未来任务。正在执行和已经完成的任务保留原版本，通过新计划版本引用其执行结果。

## 12. 学习资源与生成

| 表 | 责任 |
|---|---|
| `resource_configuration_version`、`resource_configuration_rule` | 用户确认的生成范围、题量、难度和滚动策略 |
| `resource_requirement` | 计划任务所需的资源需求 |
| `learning_resource`、`learning_resource_version` | 项目资源根和不可变版本 |
| `resource_asset_link` | 文件型资源与 `asset_version` 的关系 |
| `resource_assessment_link` | 练习、测验、模拟卷资源与测评版本关系 |
| `resource_source_link` | 生成资源使用的项目依据 |
| `resource_concept`、`resource_citation` | 覆盖概念和可展示引用 |
| `resource_fulfillment` | 一个需求由哪个资源版本满足 |
| `generation_batch`、`generation_job` | 用户一次生成确认及各生成项 |

所有生成成果先成为当前项目的 `learning_resource`。只有文件型成果才同时创建用户拥有的 `asset`，因此出现在个人资料库：

```text
文件型讲义/导图/PPT/图片
→ learning_resource + asset + resource_asset_link

练习/测验/模拟试卷
→ learning_resource + assessment + resource_assessment_link
```

测评型资源不会为了进入个人资料库而伪造一份文件。任何生成成果都不会自动成为项目学习依据，也不会自动加入知识库。用户选择“加入知识库”只对文件型成果增加关联；选择“作为后续学习依据”必须创建新的项目依据候选版本并重新确认影响。

## 13. 任务执行、会话和每日统计

| 表 | 责任 |
|---|---|
| `task_execution` | 用户对某任务的一次开始、暂停、完成、跳过或提前执行 |
| `completion_evaluation`、`completion_rule_result` | 任务完成标准判定及逐条结果 |
| `learning_session`、`learning_session_segment` | 一次学习会话及有效/无效时间段 |
| `task_resource_progress` | 阅读页数、观看位置、题目完成等资源进度 |
| `daily_learning_stat` | 按用户项目日期汇总的有效学习秒数 |
| `daily_summary` | 每日完成情况和当时的不可变展示摘要 |

`daily_learning_stat` 是可重建汇总，不接受客户端直接设置。唯一输入是经过服务端校验的会话片段、作答和任务执行事件。

## 14. 错题本

| 表 | 责任 |
|---|---|
| `wrongbook_entry` | 用户、项目、题目/概念维度的错题处理状态 |
| `mistake_occurrence` | 每次错误、有效评分和错误原因 |
| `mistake_correction` | 用户订正内容和确认状态 |
| `mistake_review_round` | 立即变式及 D1/D3/D7 复习安排和结果 |

同一道题或同一概念反复出错时更新统一错题条目，但保留每次 `mistake_occurrence`。题目失效或重评分后，错误事实和复习安排可被重新计算。

## 15. 模型路由、成本、额度与评测

| 表 | 责任 |
|---|---|
| `model_policy_version`、`model_policy_route` | 按 FAST/REASONING/VALIDATOR/EMBEDDING/OCR/IMAGE 路由模型 |
| `prompt_template`、`prompt_version` | 版本化提示模板、输入输出 Schema 摘要 |
| `model_invocation` | 每次供应商调用、时延、Token、错误和关联业务 |
| `provider_pricing_version`、`provider_price_component` | 不同时间和计价维度的供应商成本 |
| `ai_usage_ledger` | 只追加的实际用量和成本账本 |
| `quota_policy_version`、`quota_rate_component` | 面向用户的额度扣减规则 |
| `quota_account`、`quota_grant` | 用户余额账户和免费额度批次 |
| `quota_reservation`、`quota_transaction` | 预占、结算、释放、退款和调整 |
| `evaluation_dataset`、`evaluation_case` | 版本化离线评测集和用例 |
| `evaluation_run`、`evaluation_result` | 模型/提示版本的评测运行和结果 |
| `product_event` | 版本化事件名、主体、匿名会话和属性 |

计费链路：

```text
估算用户额度
→ 原子预占
→ 执行 AI 调用
→ 写实际 Token/供应商成本账本
→ 按用户规则结算
→ 释放剩余额度
```

平台内部重试不重复向用户扣费；无可用结果的失败请求释放用户额度，但供应商实际成本仍进入运营账本。

## 16. JSON 使用边界

允许 JSON：

- 不可变 AI 上下文清单和摘要。
- 供应商原始响应的脱敏诊断片段。
- 消息的展示型结构片段。
- 产品事件的低风险扩展属性。
- 每日总结当时的展示快照。
- 异步任务的非核心执行参数和安全结果摘要。

禁止 JSON：

- 项目准备状态、目标、资料关联和考试范围。
- 计划、阶段、任务、依赖和完成规则。
- 题目、选项、答案、作答和评分。
- 错题状态、复习计划和掌握度证据。
- 用户额度余额、Token、成本和删除完成状态。

## 17. 索引与性能基线

所有外键列必须有索引。额外重点索引：

- `app_user(normalized_email)` 唯一。
- `auth_session(token_hash)` 唯一；`(user_id, status, expires_at)`。
- `asset(user_id, deleted_at, updated_at)`。
- `document_chunk(parse_result_id, sequence_no)` 唯一。
- `knowledge_base_asset(knowledge_base_id, asset_id)` 唯一。
- `project_source_set_item(source_set_id, asset_version_id)` 唯一。
- `scope_node(scope_version_id, parent_id, sort_order)`。
- `assessment_attempt(user_id, assessment_version_id, status)`。
- `assessment_response(attempt_id, assessment_item_id)` 唯一。
- `concept_mastery_snapshot(project_id, concept_id, calculated_at)`。
- `learning_task_version(plan_version_id, planned_date, sort_order)`。
- `learning_session_segment(session_id, started_at)`。
- `wrongbook_entry(user_id, project_id, status, next_review_at)`。
- `async_job(status, scheduled_at, priority)`。
- `model_invocation(user_id, created_at)`、`ai_usage_ledger(user_id, occurred_at)`。
- `product_event(event_name, occurred_at)` 和 `(user_id, occurred_at)`。

Beta 阶段不做表分区。只有在真实数据证明单表规模和查询延迟达到阈值后，才为事件、调用账本等追加时间分区或归档策略。

## 18. 循环指针与建表顺序

以下指针在根表首次创建时允许为空，并在最后的迁移中补外键：

```text
asset.current_version_id
asset_version.active_parse_result_id
user_profile.avatar_asset_id
learning_project.active_target_version_id
learning_project.active_source_set_id
learning_project.active_scope_version_id
learning_project.active_diagnostic_result_id
learning_project.active_resource_config_version_id
scope_version.exam_format_version_id
conversation.active_branch_id
conversation_branch.active_run_id
question.current_version_id
assessment.current_version_id
learning_plan.current_version_id
learning_resource.current_version_id
assistant_response_group.selected_message_id
prompt_template.current_version_id
```

切换指针的服务方法必须在同一事务内验证：

1. 子版本存在且状态可确认。
2. 根对象和子版本属于同一用户、项目及聚合。
3. `row_version` 未发生冲突。
4. 当前指针变化已写入 `domain_audit_event`。
5. 必要的后续事件已写入 `outbox_event`。

## 19. 数据模型明确不包含的旧结构

V2 不接受以下旧设计作为兼容字段：

```text
learning_project.payload_json
learning_project.setup_state_json
learning_project.active_generation_json
learning_project.exercise_drafts_json
```

旧 `learning_plan/stage/task/exercise/mistake/activity/resource` 也不直接迁移为新表数据。它们只能作为迁移分析来源，不能成为 V2 的第二套真相。
