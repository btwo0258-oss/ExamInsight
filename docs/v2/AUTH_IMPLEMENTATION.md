# ExamInsight V2 认证实现边界

> 状态：注册、登录、阿里云滑块适配、浏览器 Session/CSRF 与普通用户 UI 第一纵向链路已实现。本文记录真实代码状态，不把待办能力写成已完成。

## 1. 已实现范围

公共入口统一为 `/api/v2/auth`：

```text
POST /registration-challenges
POST /registration-challenges/{challengeId}/verify-email
POST /register
POST /login
POST /logout
POST /logout-all
GET  /session
GET  /csrf
```

旧 `/api/user/register`、`/api/user/login`、管理员手工重置密码和 JWT 签发代码已删除。其他旧 `/api/**` 在完成 V2 迁移前由拦截器返回 `410 LEGACY_API_DISABLED`，不能拿 V2 用户 ID 访问旧库数据。

浏览器端不再把 token 或用户资料写入 `localStorage/sessionStorage`。启动时通过 `GET /session` 恢复登录状态；Axios、流式请求、文件下载和预览统一携带 Session Cookie，修改请求携带 CSRF。未按 V2 权限模型重建的管理员入口已从公开路由隐藏。

## 2. 注册状态机

```text
邮箱、设备标识、人机验证令牌
→ 服务端验证人机令牌
→ Redis 校验邮箱/设备/IP/全局频率与 60 秒冷却
→ V003 创建 PENDING 邮箱挑战和 QUEUED 邮件投递记录
→ SMTP 发送 6 位验证码
→ 投递成功标记 SENT；失败标记 FAILED 并使挑战失效
→ 用户提交验证码
→ 错误累计，最多 5 次后 LOCKED
→ 正确后签发 10 分钟一次性注册证明，数据库只存 HMAC 摘要
→ 注册事务原子创建 app_user、user_credential、user_profile、user_setting、user_device、auth_session
→ 消费注册证明并把挑战改为 CONSUMED
```

邮箱验证前不会创建 `app_user`。验证码、注册证明、Session、CSRF 和设备原值都不会写入数据库；验证码邮件正文也不会写入日志或 `email_delivery`。

## 3. 登录与反滥用

- 登录失败统一返回 `INVALID_CREDENTIALS`，不区分邮箱不存在或密码错误。
- 同账户 15 分钟内累计失败；连续失败 3 次后必须提交人机验证令牌。
- 第 5、6、7 次及以后失败分别进入 1、5、15 分钟等待。
- IP 失败桶为 100 次/15 分钟，适配高校共享出口网络。
- Redis 不可用时拒绝新注册和新登录；已有 Session 的数据库验证不依赖 Redis。
- 密码在 NFC 规范化后使用 Argon2id：64 MiB、3 次迭代、并行度 1、16 字节盐和 32 字节输出。
- 密码长度固定为 15–128 个 Unicode 字符，允许普通空格，不允许控制字符、常见密码、产品名或邮箱本地部分。

## 4. Session 与 CSRF

- 浏览器只保存 32 字节随机不透明 Session Cookie，名称为 `EXAMINSIGHT_SESSION`。
- Cookie 为 `HttpOnly`、`SameSite=Lax`；公开部署必须配置 `Secure=true`。
- 数据库 `auth_session.token_hash` 只保存 HMAC-SHA256 摘要，不再签发 JWT。
- Session 无活动 24 小时过期、绝对 30 天过期、活动期间每 24 小时自动轮换令牌。
- 同账户最多 5 个活动 Session；超限时撤销最旧 Session。
- 修改请求必须携带 `X-CSRF-Token`，其原值由 `GET /csrf` 返回并同步到可读的 `XSRF-TOKEN` Cookie；数据库只保存摘要。
- `logout` 只撤销当前 Session；`logout-all` 撤销全部 Session 并递增 `app_user.session_version`。

## 5. 数据源与事务边界

- 旧 MyBatis 模块继续使用 Primary `spring.datasource`，避免本阶段扩大修改范围。
- V2 认证只使用 `app.v2.datasource` 和 `v2TransactionManager`。
- 账户、凭据、默认资料、设置、设备、Session 和注册证明消费处于同一个 V2 事务。
- SMTP 属于外部副作用，不放在数据库事务中；投递失败通过新的事务写入 `FAILED` 并使挑战失效。

## 6. 必需配置与安全降级

```text
V2_DB_URL
V2_DB_USERNAME
V2_DB_PASSWORD
AUTH_HASH_SECRET          至少 32 字符，建议 32 随机字节的十六进制值
AUTH_COOKIE_SECURE        公开 HTTPS 环境必须为 true
REDIS_HOST / REDIS_PORT
HUMAN_VERIFICATION_MODE  公开环境设为 aliyun
ALIYUN_CAPTCHA_SCENE_ID
ALIYUN_CAPTCHA_ENDPOINT
ALIYUN_CAPTCHA_PREFIX     仅供前端构建使用，不属于秘密
ALIYUN_CAPTCHA_REGION
ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET
SMTP_HOST / SMTP_PORT
SMTP_USERNAME / SMTP_PASSWORD
AUTH_MAIL_FROM
```

公开环境使用 `HUMAN_VERIFICATION_MODE=aliyun`。服务端通过阿里云默认凭据链读取最小权限 RAM 凭据，并固定校验服务端配置的 SceneId；客户端返回的 `captchaVerifyParam` 不作修改，且只允许使用一次。`disabled`、SceneId/凭据缺失、服务超时、SMTP 未配置或 Redis 故障时均安全失败，不存在开发后门或验证码绕过参数。`remote` 模式仅保留给兼容 reCAPTCHA 验签协议的替代供应商。

前端注册状态固定为：邮箱与协议确认 → 阿里云滑块 → 发送邮件 → 6 位验证码 → 昵称、15–128 字符密码与年龄确认 → 创建账户。登录只在服务端返回 `HUMAN_VERIFICATION_REQUIRED` 后唤起滑块，不让所有正常登录承担额外交互和计费。

反向代理不得任意信任客户端传入的 `X-Forwarded-For`。当前代码使用 Servlet 已确认的远端地址；部署时只能由受信任代理覆写来源地址。

## 7. 尚未实现，禁止展示

以下能力仍属于认证阶段待办，当前前端不得展示为可用：

1. `POST /login-challenges/{id}/verify-email` 高风险登录邮箱升级验证和风险评分器。
2. `POST /password-reset-requests`、`POST /password-resets` 邮件链接找回密码。
3. 泄漏密码在线检查；当前只执行本地常见密码和账户相关弱密码检查，`compromised_checked_at` 保持为空。
4. 认证秘密签发端点的 `Idempotency-Key` 重放策略。注册证明和 Session 原值不能明文持久化，因此该策略冻结前，不把这些端点标记为已满足公开 Beta 的通用幂等契约。
5. V2 用户资料修改和主题云同步接口；当前资料弹窗只读、主题只保存在本机。
6. 可正式勾选的用户协议、隐私政策正文与独立页面。

阿里云真实 Prefix、SceneId、RAM 凭据和域名尚未配置，因此当前完成的是可编译、可测试、失败关闭的接入代码，不代表已经通过供应商线上场景验收。

这些能力完成前不能降低验证标准或恢复旧 JWT/管理员重置流程作为降级方案。
