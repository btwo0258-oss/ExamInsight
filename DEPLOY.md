# ExamInsight 阿里云部署指南

本指南适用于一台中国香港地域的 Ubuntu 24.04 ECS。部署使用 Docker Compose，公网只开放 22、80，以及以后启用 HTTPS 时使用的 443 端口。

## 1. 购买 ECS

- 地域：中国香港
- 操作系统：Ubuntu 24.04 LTS 64 位，x86_64
- 配置：至少 4 核 8 GB
- 系统盘：至少 100 GB SSD
- 公网带宽：建议 5 Mbps
- 安全组入方向：22、80；暂时不开放 3306、6379、8080、9200

## 2. 登录服务器并安装 Docker

在阿里云控制台复制公网 IP，然后从本机终端连接：

```bash
ssh root@服务器公网IP
```

在服务器执行：

```bash
apt update
apt install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker version
docker compose version
```

## 3. 上传项目

如果服务器可以访问项目的 Git 仓库：

```bash
git clone <你的仓库地址> /opt/examinsight
cd /opt/examinsight
```

如果仓库是私有的，也可以先使用阿里云控制台的文件传输功能或 SFTP 将整个项目上传到 `/opt/examinsight`。

## 4. 填写生产环境变量

```bash
cd /opt/examinsight
cp .env.example .env
nano .env
```

必须替换以下内容：

- `MYSQL_PASSWORD`：应用数据库密码
- `MYSQL_ROOT_PASSWORD`：不同于应用密码的 MySQL root 密码
- `PUBLIC_ORIGIN`：例如 `http://8.8.8.8`，替换为真实公网 IP
- `JWT_SECRET`：JWT 签名密钥
- `DASHSCOPE_API_KEY`：需要聊天和向量功能时填写
- 讯飞变量：只在使用对应图片、语音和 PPT 功能时填写

可以在服务器生成随机密码或密钥：

```bash
openssl rand -base64 32
openssl rand -hex 32
```

`.env` 包含真实密钥，已经被 Git 忽略，禁止提交到仓库或发送给他人。

## 5. 构建并启动

```bash
cd /opt/examinsight
docker compose up -d --build
docker compose ps
```

第一次启动需要下载并构建多个镜像，通常会等待几分钟。查看启动进度：

```bash
docker compose logs -f --tail=100
```

看到服务都正常后按 `Ctrl+C` 退出日志，不会停止服务。浏览器访问：

```text
http://服务器公网IP
```

数据库首次启动时只导入表结构，不导入仓库中的本地用户、历史对话和 Windows 文件路径。可在网页中注册一个新用户进行测试。

## 6. 常用维护命令

```bash
# 查看状态
docker compose ps

# 查看最近日志
docker compose logs --tail=200

# 重启全部服务
docker compose restart

# 拉取代码后重新构建
git pull
docker compose up -d --build

# 停止服务但保留数据库和上传文件
docker compose down
```

不要执行 `docker compose down -v`，其中的 `-v` 会删除数据库、Elasticsearch 索引和上传文件。

## 7. 数据库备份

```bash
cd /opt/examinsight
docker compose exec -T mysql sh -c 'exec mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' > examinsight-backup.sql
```

部署成功并绑定域名后，再配置 HTTPS 和 443 端口。
