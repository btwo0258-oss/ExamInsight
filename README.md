<div align="center">

# ✦ Aether ✦

**智能知识库问答平台**

*基于 RAG 检索增强生成技术 · 让 AI 精准回答你文档中的问题*

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue.js-3.5-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.11-005571?logo=elasticsearch&logoColor=white)](https://www.elastic.co/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

🌐 [在线演示](http://8.136.218.249) · 📖 [API 文档](http://localhost:8080/doc.html) · 🐛 [问题反馈](https://github.com/xdx921/LLM/issues)

</div>

---

## 📸 功能预览

> 💡 **管理员账号：** admin / admin123 &nbsp;&nbsp;|&nbsp;&nbsp; **普通用户：** 自行注册体验

<table>
<tr>
<td width="50%">

**🤖 RAG 智能问答**
- 上传文档 → 自动解析向量化
- 提问时精准检索相关片段
- 通义千问大模型生成回答
- SSE 逐字流式输出

</td>
<td width="50%">

**📝 问答版本管理**
- 编辑问题重新生成回答
- 不满意可重新生成新版本
- 左右箭头切换历史版本
- 问答始终配对展示

</td>
</tr>
<tr>
<td width="50%">

**🧠 思维导图**
- 创建/编辑/重命名/删除
- 节点拖拽、连接线绘制
- 可关联知识库归类管理
- 导出 JSON 备份

</td>
<td width="50%">

**🛡️ 后台管理**
- 数据统计仪表盘（6项指标）
- 用户封禁 / 解封
- 忘记密码申请 → 黄点提醒
- 一键重置用户密码

</td>
</tr>
</table>

---

## 🛠️ 技术架构
┌─────────────────────────────────────────────────────────────┐
│ 前端 (Vue 3) │
│ Vue 3 + Vite + Element Plus + Pinia + PDF.js + Axios │
├─────────────────────────────────────────────────────────────┤
│ SSE 流式通信 / REST API │
├─────────────────────────────────────────────────────────────┤
│ 后端 (Spring Boot) │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│ │ 用户管理 │ │ 知识库 │ │ 文档管理 │ │ 智能问答 │ │
│ │ JWT认证 │ │ CRUD │ │ Tika解析 │ │ RAG + SSE │ │
│ └──────────┘ └──────────┘ │ 向量化 │ │ 多轮对话 │ │
│ ┌──────────┐ ┌──────────┐ │ 预览下载 │ │ 版本管理 │ │
│ │ 思维导图 │ │ 后台管理 │ └──────────┘ └──────────────┘ │
│ └──────────┘ └──────────┘ │
├──────────────────┬──────────────────┬────────────────────────┤
│ MySQL 8.0 │ Elasticsearch │ 阿里云 DashScope │
│ 数据持久化 │ 8.11 向量检索 │ 大模型 + Embedding │
└──────────────────┴──────────────────┴────────────────────────┘

text


<table>
<tr>
<th>层级</th>
<th>技术</th>
<th>版本</th>
<th>用途</th>
</tr>
<tr><td rowspan="6">前端</td><td>Vue.js</td><td>3.5.13</td><td>前端框架</td></tr>
<tr><td>Vite</td><td>7.3.1</td><td>构建工具</td></tr>
<tr><td>Element Plus</td><td>2.9.1</td><td>UI 组件库</td></tr>
<tr><td>Pinia</td><td>2.3.0</td><td>状态管理</td></tr>
<tr><td>Axios</td><td>1.7.9</td><td>HTTP 客户端</td></tr>
<tr><td>PDF.js</td><td>4.9.155</td><td>PDF 在线预览</td></tr>
<tr><td rowspan="6">后端</td><td>Spring Boot</td><td>3.2.5</td><td>后端框架</td></tr>
<tr><td>MyBatis-Plus</td><td>3.5.7</td><td>ORM 持久层</td></tr>
<tr><td>Elasticsearch</td><td>8.11.0</td><td>向量存储 + kNN 检索</td></tr>
<tr><td>DashScope SDK</td><td>2.16.4</td><td>通义千问 + Embedding</td></tr>
<tr><td>Apache Tika</td><td>2.9.1</td><td>文档解析</td></tr>
<tr><td>MySQL</td><td>8.0</td><td>关系型数据库</td></tr>
</table>

---

## ✨ 功能清单

<table>
<tr>
<td>

**👤 用户模块**
- [x] 注册 / 登录（BCrypt + JWT）
- [x] 个人信息管理
- [x] 主题切换（亮色/暗色）
- [x] 默认模型选择
- [x] 忘记密码申请

</td>
<td>

**📚 知识库管理**
- [x] 多知识库创建 / 编辑 / 删除
- [x] 文档数 + 思维导图数统计
- [x] 级联删除（含 ES 向量数据）
- [x] 自定义图标颜色

</td>
</tr>
<tr>
<td>

**📄 文档管理**
- [x] 上传（PDF / Word / MD / TXT）
- [x] Apache Tika 自动解析
- [x] 文本分块（500字 / 100字重叠）
- [x] DashScope Embedding 向量化
- [x] 在线预览（PDF.js / HTML）
- [x] 原始文件下载

</td>
<td>

**🤖 智能问答**
- [x] RAG 检索增强生成
- [x] SSE 逐字流式输出
- [x] 通用对话（含文件上传）
- [x] 多轮对话上下文管理
- [x] 引用来源溯源展示
- [x] 消息一键复制

</td>
</tr>
<tr>
<td>

**🔄 版本管理**
- [x] 编辑问题重新生成
- [x] 重新生成回答
- [x] 左右箭头版本切换
- [x] 问答配对同步展示
- [x] 版本感知上下文构建

</td>
<td>

**🧠 思维导图**
- [x] 创建 / 重命名 / 删除
- [x] 节点增删（子节点/同级）
- [x] 拖拽调整 / 连接线
- [x] 画布居中
- [x] 关联知识库管理
- [x] 导出 JSON

</td>
</tr>
<tr>
<td>

**🛡️ 后台管理**
- [x] 数据统计仪表盘（6项指标）
- [x] 用户封禁 / 解封
- [x] 密码重置（默认123456）
- [x] 忘记密码黄点提醒

</td>
<td>

**⚙️ 系统配置**
- [x] Prompt 模板管理
- [x] RAG 参数动态调整
- [x] 无需重启即时生效

</td>
</tr>
</table>

---

## 📁 项目结构
Aether/
│
├── 📂 backend/ # Spring Boot 后端
│ ├── 📂 src/main/java/com/example/llm/
│ │ ├── 📂 controller/ # API 接口层
│ │ │ └── 📂 admin/ # 后台管理接口
│ │ ├── 📂 service/ # 业务逻辑层
│ │ │ └── 📂 impl/ # 接口实现
│ │ ├── 📂 mapper/ # MyBatis-Plus 数据访问
│ │ ├── 📂 entity/ # 数据库实体（10张表）
│ │ ├── 📂 dto/ # 请求参数 DTO
│ │ ├── 📂 vo/ # 响应数据 VO
│ │ ├── 📂 config/ # 配置（ES/Swagger/Web/Async）
│ │ ├── 📂 interceptor/ # JWT 认证拦截器
│ │ ├── 📂 component/ # 文档解析器 / 文本分块器
│ │ ├── 📂 exception/ # 全局异常处理
│ │ └── 📂 utils/ # JWT 工具类
│ ├── 📄 application.yaml # 应用配置
│ └── 📄 pom.xml # Maven 依赖
│
├── 📂 frontend/ # Vue 3 前端
│ ├── 📂 src/
│ │ ├── 📂 components/ # 通用组件 + 业务组件
│ │ ├── 📂 views/ # 页面视图
│ │ ├── 📂 stores/ # Pinia 状态管理
│ │ ├── 📂 api/ # Axios 接口封装
│ │ └── 📂 utils/ # 工具函数
│ ├── 📄 package.json
│ └── 📄 vite.config.ts
│
├── 📂 DataBase/ # 数据库脚本
│ └── 📄 *.sql # 建表 + 初始化数据
│
├── 📄 .gitignore
└── 📄 README.md

text


---

## 🚀 快速启动

### 环境要求

| 环境 | 版本 | 必需 |
|------|------|------|
| JDK | 17+ | ✅ |
| Node.js | 18+ | ✅ |
| MySQL | 8.0+ | ✅ |
| Elasticsearch | 8.11.x | ✅ |
| Maven | 3.9+ | ✅ |
| Docker | 20+ | 推荐 |

### Step 1 · 数据库

```bash
mysql -u root -p -e "CREATE DATABASE aether DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p aether < DataBase/aether.sql
Step 2 · Elasticsearch
Bash

docker run -d --name es \
  -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "ES_JAVA_OPTS=-Xms1g -Xmx2g" \
  -e "xpack.security.enabled=false" \
  elasticsearch:8.11.0
Step 3 · 后端配置
编辑 backend/src/main/resources/application.yaml：

YAML

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aether?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的密码

dashscope:
  api-key: 你的DashScope_API_Key    # 👉 https://bailian.console.aliyun.com 申请
Step 4 · 启动后端
Bash

cd backend
mvn spring-boot:run
启动成功 → http://localhost:8080   |   API 文档 → http://localhost:8080/doc.html

Step 5 · 启动前端
Bash

cd frontend
npm install
npm run dev
启动成功 → http://localhost:5173

🗄️ 数据库设计
共 10 张表，核心表关系如下：

text

user ──┬── user_settings          (1:1 个人设置)
       ├── knowledge_base ──┬── document ── document_chunk   (知识库→文档→分块)
       │                    └── mind_map                     (知识库→思维导图)
       ├── conversation ── message                           (对话→消息)
       └── password_reset_request                            (密码重置申请)

system_config                                                (独立配置表)
表名	记录数	说明
user	—	用户表（角色：user / admin）
user_settings	—	主题、默认模型
knowledge_base	—	知识库（含 doc_count、mind_map_count）
document	—	文档元信息（文件名/类型/大小/状态）
document_chunk	—	文本分块（MySQL 备份，主数据在 ES）
conversation	—	对话（可关联知识库）
message	—	消息（含 turn_id / q_version / a_version）
system_config	9	Prompt 模板 + RAG 参数
mind_map	—	思维导图（JSON 存储节点数据）
password_reset_request	—	忘记密码申请
🔌 API 概览
共 44 个接口：

模块	数量	核心接口
用户模块	8	注册、登录、忘记密码
知识库管理	5	CRUD、级联删除
文档管理	7	上传、解析提取、预览、下载
对话管理	5	创建、消息历史、删除
智能问答	2	SSE 流式对话（核心）、标题生成
思维导图	5	CRUD、关联知识库
系统配置	2	获取、更新
后台管理	10	仪表盘、用户管理、封禁、密码重置
📖 完整接口文档：启动后端后访问 http://localhost:8080/doc.html

👥 团队
成员	角色	职责
肖杜萱	后端开发	全部后端模块、数据库设计、RAG 核心流程、服务器部署
王紫涵	前端开发	全部前端页面、UI/交互设计、SSE 流式渲染、测试
<div align="center">
⭐ 如果觉得有帮助，欢迎 Star ⭐

本项目仅用于学习交流，不得用于商业用途

</div> ```