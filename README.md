# snail-platform

内部研发平台：把「代码托管、流水线执行、制品存储、任务队列」统一到一个入口，并附带账号、项目、发布管理。

控制面只做编排与记录，构建在独立 worker 中执行，不可信代码不会进入控制面进程。

## 能力

| 能力 | 实现 |
| --- | --- |
| 账号与权限 | 用户名密码登录、JWT、ADMIN / DEVELOPER / VIEWER 三种角色 |
| 项目管理 | 项目、成员、状态 |
| 代码托管 | 对接 Gitea（创建仓库、Webhook push 触发），也支持手工绑定任意 cloneUrl |
| 流水线 | 可视化编辑 steps JSON、手动触发、Webhook 触发、取消、运行历史 |
| 构建执行 | Docker（默认）/ Kubernetes Job / Jenkins 三种执行器，控制面与执行面分离 |
| 制品存储 | MinIO，日志与产物统一按 run 归档，SHA-256 校验，预签名下载 |
| 任务队列 | RabbitMQ 持久化队列 + 死信队列，publisher confirms，worker 单条预取 |
| 发布管理 | 发布单 + 环境（DEV/TEST/PROD），K8s 集群内直接更新 Deployment 镜像；未配置集群时返回命令预览 |
| 可观测性 | Actuator、Prometheus 指标、健康检查 |

## 架构

```text
                       ┌──────────────────────────────┐
   浏览器  ──────────▶  │  web (Vite + React)          │
                       └──────────────┬───────────────┘
                                      │ /api (JWT)
                       ┌──────────────▼───────────────┐
                       │  platform-control  :8088     │
                       │  账号 / 项目 / 仓库 / 流水线  │
                       │  运行记录 / 制品索引 / 发布单 │
                       └───┬────────┬────────┬────────┘
                           │        │        │
              ┌────────────▼──┐ ┌───▼────┐ ┌─▼─────────────┐
              │ MySQL         │ │ MinIO  │ │ RabbitMQ      │
              │ 元数据        │ │ 制品   │ │ build.run     │
              └───────────────┘ └───▲────┘ └─┬─────────────┘
                                      │        │ 消费
                       ┌──────────────┴────────▼─────────┐
                       │  platform-worker  :8089         │
                       │  拉代码 → 执行 steps → 上传产物  │
                       │  Docker / K8s Job / Jenkins     │
                       └──────────────┬──────────────────┘
                                      │ 不可信构建
                       ┌──────────────▼──────────────────┐
                       │  隔离的构建节点（docker.sock）  │
                       └─────────────────────────────────┘
```

## 目录结构

```text
snail-platform/
├── pom.xml                      # 聚合 POM
├── platform-common/             # 控制面与执行面共享：ApiResult、消息体、DSL、枚举
├── platform-control/            # 控制面：Web、安全、业务服务、Gitea/MinIO/K8s 集成、Flyway
├── platform-worker/             # 执行面：RabbitMQ 消费者、三种执行器、制品上传
├── web/                         # 前端：登录、项目、仓库、流水线、运行详情、发布
├── .github/workflows/ci.yml     # 后端测试打包 + 前端 lint/typecheck/build + compose 校验
├── docker-compose.yml           # MySQL / RabbitMQ / MinIO / Gitea / control / worker
└── .env.example                 # 全部可配置项
```

## 快速启动

前置：Docker、Docker Compose v2、本机可用的 `git`。

```bash
cp .env.example .env
docker compose up -d --build
```

启动后：

| 组件 | 地址 | 默认账号 |
| --- | --- | --- |
| 平台前端（容器外本地开发） | http://localhost:5173 | admin / admin123 |
| 控制面 API | http://localhost:8088 | 同上 |
| Gitea | http://localhost:3000 | 首次注册即管理员 |
| MinIO 控制台 | http://localhost:9001 | platform / platform123 |
| RabbitMQ 管理台 | http://localhost:15672 | platform / platform123 |
| Jenkins（可选） | http://localhost:8081 | admin / admin123 |

前端本地开发：

```bash
cd web
npm install
npm run dev     # 5173，/api 代理到 8088
```

后端本地开发：

```bash
mvn -B -ntp test        # 单测 + 架构测试，不需要 MySQL/RabbitMQ/MinIO
mvn -B -ntp package     # 生成 platform-control / platform-worker 可执行 jar
```

## Gitea 首次配置

1. 打开 http://localhost:3000 完成初始化注册，第一个账号即为管理员。
2. 在 Gitea 生成访问令牌（Settings → Applications → Generate Token，勾选 `repo`、`admin:repo_hook`）。
3. 写入 `.env`：`GITEA_URL=http://gitea:3000`、`GITEA_TOKEN=<token>`，然后 `docker compose up -d control`。
4. 在平台「仓库」页选择 Gitea 创建或绑定仓库，系统会写入 `webhook_secret`。
5. 在 Gitea 仓库 Settings → Webhooks 添加 `http://control:8088/api/webhooks/gitea`，Content Type 选 `application/json`，Secret 填平台生成的 secret，事件勾选 Push。

没有 Gitea 时可直接用「绑定仓库」填任意 `cloneUrl`（provider=GENERIC），手动触发流水线。

## 流水线定义

```json
{
  "steps": [
    {
      "name": "编译",
      "image": "maven:3.9-eclipse-temurin-17",
      "commands": ["mvn -B -ntp -DskipTests package"],
      "workDir": "/workspace",
      "artifacts": ["target/*.jar"]
    }
  ]
}
```

详见 [docs/pipeline-dsl.md](docs/pipeline-dsl.md)。

## 执行器

| 类型 | 说明 | 必需配置 |
| --- | --- | --- |
| DOCKER（默认） | worker 调用宿主机 Docker，每个 step 一个容器 | worker 挂载 `/var/run/docker.sock`，`WORKSPACE_HOST_DIR` 与容器内路径一致 |
| K8S | 每个构建创建 Job，拉取 Pod 日志 | `K8S_KUBECONFIG`（或集群内 ServiceAccount） |
| JENKINS | 触发已有 Jenkins Job 并轮询结果 | `JENKINS_URL`、`JENKINS_USER`、`JENKINS_TOKEN`、`JENKINS_JOB` |

执行器未配置时会在运行日志中给出明确失败原因，不会返回假成功。发布管理在未接入 K8s 时返回命令预览。

## 文档

- [架构说明](docs/architecture.md)
- [接口清单](docs/api.md)
- [流水线 DSL](docs/pipeline-dsl.md)
- [部署与执行器](docs/deployment.md)
- [安全说明](docs/security.md)

## 安全提示

构建会执行仓库里的任意命令。默认的 Docker 执行器把 `docker.sock` 暴露给 worker，等同于宿主 root 权限，**只能部署在专用构建节点**，不要与控制面共用同一个 Docker daemon。详见 [docs/security.md](docs/security.md)。
