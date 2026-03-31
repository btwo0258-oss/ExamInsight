\# Aether — 智能知识库问答平台



基于 RAG（检索增强生成）技术的文档问答平台，支持用户上传文档构建私有知识库，通过向量检索 + 大模型生成实现精准问答。



\## 项目预览



\- \*\*在线地址：\*\* http://8.136.218.249

\- \*\*管理员账号：\*\* admin / admin123

\- \*\*测试账号：\*\* 自行注册



\## 技术栈



\### 后端



| 技术 | 版本 | 说明 |

|------|------|------|

| Java | 17 | 编程语言 |

| Spring Boot | 3.2.5 | 后端框架 |

| MyBatis-Plus | 3.5.7 | ORM 框架 |

| Elasticsearch | 8.11.0 | 向量存储与 kNN 语义检索 |

| 阿里云 DashScope SDK | 2.16.4 | 通义千问大模型 + Embedding 向量化 |

| Apache Tika | 2.9.1 | 多格式文档解析（PDF/Word/MD/TXT） |

| MySQL | 8.0 | 关系型数据库 |

| JWT | - | 用户认证 |



\### 前端



| 技术 | 版本 | 说明 |

|------|------|------|

| Vue.js | 3.5.13 | 前端框架 |

| Vite | 7.3.1 | 构建工具 |

| Element Plus | 2.9.1 | UI 组件库 |

| Pinia | 2.3.0 | 状态管理 |

| Axios | 1.7.9 | HTTP 客户端 |

| PDF.js | 4.9.155 | PDF 在线预览 |



\## 功能模块



\- ✅ \*\*用户管理\*\* — 注册/登录/JWT 认证/个人设置

\- ✅ \*\*知识库管理\*\* — 创建/编辑/删除知识库，文档与思维导图统计

\- ✅ \*\*文档管理\*\* — 上传/解析/分块/向量化/在线预览/下载（支持 PDF/Word/MD/TXT）

\- ✅ \*\*智能问答（RAG）\*\* — 文档向量化 → ES kNN 检索 → Prompt 构建 → 通义千问生成

\- ✅ \*\*SSE 流式输出\*\* — 逐字实时输出，类 ChatGPT 交互体验

\- ✅ \*\*通用对话\*\* — 不关联知识库的自由对话，支持上传文件（≤5个）

\- ✅ \*\*多轮对话\*\* — 自动携带历史上下文，支持连续追问

\- ✅ \*\*问答版本管理\*\* — 编辑问题重新生成 / 重新生成回答 / 版本左右切换

\- ✅ \*\*引用来源展示\*\* — 展示 AI 回答所引用的文档片段（文档名/块序号/相似度/原文）

\- ✅ \*\*思维导图\*\* — 创建/编辑/重命名/删除，支持关联知识库，节点拖拽/连接线/居中

\- ✅ \*\*后台管理\*\* — 数据统计仪表盘/用户封禁解封/密码重置/忘记密码申请

\- ✅ \*\*系统配置\*\* — Prompt 模板/RAG 参数动态调整，无需重启



\## 项目结构



&#x20;   Aether/

&#x20;   ├── backend/                  # 后端代码（Spring Boot）

&#x20;   │   ├── src/main/java/        # Java 源码

&#x20;   │   │   └── com/example/llm/

&#x20;   │   │       ├── controller/    # 控制器（API 接口）

&#x20;   │   │       ├── service/       # 业务逻辑层

&#x20;   │   │       ├── mapper/        # 数据访问层（MyBatis-Plus）

&#x20;   │   │       ├── entity/        # 数据库实体类

&#x20;   │   │       ├── dto/           # 请求参数封装

&#x20;   │   │       ├── vo/            # 响应数据封装

&#x20;   │   │       ├── config/        # 配置类（ES/Swagger/Web）

&#x20;   │   │       ├── interceptor/   # JWT 认证拦截器

&#x20;   │   │       └── component/     # 文档解析/文本分块组件

&#x20;   │   ├── src/main/resources/

&#x20;   │   │   └── application.yaml   # 应用配置文件

&#x20;   │   └── pom.xml                # Maven 依赖

&#x20;   ├── frontend/                 # 前端代码（Vue 3）

&#x20;   │   ├── src/

&#x20;   │   │   ├── components/        # 页面组件

&#x20;   │   │   ├── views/             # 页面视图

&#x20;   │   │   ├── stores/            # Pinia 状态管理

&#x20;   │   │   ├── api/               # 接口封装

&#x20;   │   │   └── utils/             # 工具函数

&#x20;   │   ├── package.json           # npm 依赖

&#x20;   │   └── vite.config.ts         # Vite 配置

&#x20;   ├── DataBase/                 # 数据库文件

&#x20;   │   └── \*.sql                  # MySQL 建表与初始化脚本

&#x20;   ├── .gitignore

&#x20;   └── README.md



\## 环境要求



| 环境 | 版本要求 |

|------|---------|

| JDK | 17+ |

| Node.js | 18+ |

| MySQL | 8.0+ |

| Elasticsearch | 8.11.x |

| Maven | 3.9+ |



\## 快速启动



\### 1. 数据库准备



创建数据库并导入初始化脚本：



&#x20;   mysql -u root -p -e "CREATE DATABASE aether DEFAULT CHARACTER SET utf8mb4;"

&#x20;   mysql -u root -p aether < DataBase/aether.sql



\### 2. 启动 Elasticsearch



Docker 方式启动：



&#x20;   docker run -d --name es -p 9200:9200 -p 9300:9300 \\

&#x20;     -e "discovery.type=single-node" \\

&#x20;     -e "ES\_JAVA\_OPTS=-Xms1g -Xmx2g" \\

&#x20;     -e "xpack.security.enabled=false" \\

&#x20;     elasticsearch:8.11.0



\### 3. 配置后端



修改 `backend/src/main/resources/application.yaml`：



&#x20;   spring:

&#x20;     datasource:

&#x20;       url: jdbc:mysql://localhost:3306/aether?useUnicode=true\&characterEncoding=utf8\&serverTimezone=Asia/Shanghai

&#x20;       username: root

&#x20;       password: 你的MySQL密码



&#x20;   dashscope:

&#x20;     api-key: 你的阿里云DashScope API Key



> API Key 在 https://bailian.console.aliyun.com 申请



\### 4. 启动后端



&#x20;   cd backend

&#x20;   mvn spring-boot:run



或者在 IDEA 中直接运行 `LlmApplication.java`，启动成功后访问 http://localhost:8080



\### 5. 启动前端



&#x20;   cd frontend

&#x20;   npm install

&#x20;   npm run dev



启动成功后访问 http://localhost:5173



\## API 文档



启动后端后访问 Knife4j 接口文档：http://localhost:8080/doc.html



\## 数据库说明



共 10 张数据表：



| 表名 | 说明 |

|------|------|

| user | 用户表（含角色：user/admin） |

| user\_settings | 用户设置表（主题/默认模型） |

| knowledge\_base | 知识库表 |

| document | 文档表 |

| document\_chunk | 文档分块表（MySQL 侧备份，主数据在 ES） |

| conversation | 对话表 |

| message | 消息表（含版本管理字段 turnId/qVersion/aVersion） |

| system\_config | 系统配置表（Prompt 模板/RAG 参数） |

| mind\_map | 思维导图表 |

| password\_reset\_request | 密码重置申请表 |



\## 团队成员



| 姓名 | 角色 | 职责 |

|------|------|------|

| 肖杜萱 | 后端开发 | 全部后端模块、数据库设计、服务器部署 |

| 王紫涵 | 前端开发 | 全部前端页面、UI 设计、测试 |



\## License



本项目仅用于学习交流，不得用于商业用途。

