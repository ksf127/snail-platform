# snail-platform

内部研发平台：把「代码托管、流水线执行、制品存储、任务队列」统一到一个入口，并附带账号、项目、发布管理。

控制面只做编排与记录，构建在独立 worker 中执行，不可信代码不会进入控制面进程。

## 能力

| 能力 | 实现 |
| --- | --- |
| 账号与权限 | 用户名密码登录、JWT、ADMIN / DEVELOPER / VIEWER 三种角色 |
| 项目管理 | 项目、成员、状态 |
| 代码托管 | 对接 Gitea（创建仓库、Webhook push 触发），也支持手工绑定任意 cloneUrl；GitHub push 同样可触发 |
| 流水线 | YAML / JSON 可视化编辑、配置即代码（仓库内 `.snail/pipeline.yml`）、多分支（精确分支或 `*`）、按团队归属、手动 / Webhook 触发、运行历史 |
| 构建变量 | 项目级 secrets 加密入库，接口与日志只出现掩码，构建时注入容器环境变量 |
| 并发控制 | 同一流水线排队上限（默认 3），Webhook 同仓库/分支/commit 去重 |
| 多团队协作 | 项目内登记分支归属（团队 / 负责人 / 说明），流水线列表按分支回填团队并支持团队筛选 |
| 多环境发布 | 项目级环境配置（DEV/TEST/PROD 可增删改），每个环境独立 K8s namespace 与默认副本，发布单按环境固化 namespace |
| 构建执行 | Docker（默认）/ Kubernetes Job / Jenkins 三种执行器，控制面与执行面分离 |
| 制品存储 | MinIO，日志与产物统一按 run 归档，SHA-256 校验，预签名下载 |
| 任务队列 | RabbitMQ 持久化队列 + 死信队列，publisher confirms，worker 单条预取 |
| 发布管理 | 发布单 + 项目环境，K8s 集群内按环境 namespace 更新 Deployment 镜像；未配置集群时返回命令预览 |
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

## GitHub Webhook

1. 在「仓库」页手工绑定 GitHub 仓库：Clone URL 填 `https://github.com/<owner>/<repo>.git`，可同时填写用户名与 PAT（私有仓库必填）。
2. 在 GitHub 仓库 Settings → Webhooks 添加 `http://<control-host>:8088/api/webhooks/github`：
   - Content type 选 `application/json`；
   - Secret 填平台生成并保存的 webhook secret；
   - 事件勾选 `Just the push event`。
3. 平台校验 `X-Hub-Signature-256`（HMAC-SHA256，原始请求体），匹配仓库后按分支触发 `WEBHOOK` 流水线；`ping` 事件返回空列表。

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

## 配置即代码与构建变量

### 流水线 YAML（仓库优先）

流水线可填写「配置路径」（如 `.snail/pipeline.yml`）。触发时 control 会从该仓库的目标分支 / commit 读取 YAML，校验后写入本次 run 快照，worker 按快照执行：

- 配置在仓库时以仓库文件为准，平台内 JSON 仅作预览；路径留空则沿用平台定义。
- 读取失败、YAML 非法或步骤不完整会在触发前报错，不会产生「幽灵运行」。
- GitHub 走 Contents API（公开仓库匿名可读，私有仓库需配置访问凭据）；Gitea 走原生 raw API。
- 流水线编辑页可「读取当前 YAML / 从仓库同步 / 导入 YAML」，导入不会改变 configPath。

### 项目构建变量（secrets）

项目详情页「构建变量」维护构建期环境变量：

- 变量名需匹配 `[A-Za-z_][A-Za-z0-9_]{0,63}`，同一项目内唯一。
- 值使用 AES-256-GCM 加密存储；列表只返回 `****1234` 形式的掩码。
- 构建时解密并注入 Docker `-e` / K8s 容器环境变量 / Jenkins 构建参数，日志中出现的变量值会被替换为掩码。
- 加密密钥来自 `PLATFORM_CRYPTO_KEY`；未配置时从 `JWT_SECRET` 派生，生产环境应显式配置。

### 并发与去重

- `platform.build.max-queued-per-pipeline`（默认 3）：同一流水线 QUEUED 达到上限后，新的触发返回 409。
- Webhook 对同一 pipeline + 分支 + commit 且状态为 QUEUED/RUNNING/SUCCESS 的运行直接跳过，避免重投重复构建。
- 实际执行串行仍由 worker `prefetch=1` 保证，排队上限用于抑制无上限堆积。

## 多分支与多环境

### 一个项目多个分支、多个团队维护

项目详情页的「分支团队」页签登记每个分支的维护方：

| 字段 | 说明 |
| --- | --- |
| 分支 | 必填，与流水线的 `branch` 对应；`*`（全部分支）不登记 |
| 维护团队 | 团队名，用于流水线列表筛选与识别 |
| 负责人 | 从平台用户中选择，可留空 |
| 说明 | 自由备注 |

规则：

- 创建或修改流水线时，只要 `branch` 是具体分支，系统自动在 `project_branch` 登记，避免分支清单缺失。
- 分支改名会级联更新该项目的 `pipeline.branch`，已登记的分支不会出现「流水线还在旧分支」的脏数据。
- 流水线列表新增「团队」列，并支持按团队筛选；团队筛选按「项目 + 分支」精确匹配，跨项目同名分支不会互相串数据。
- Webhook 触发仍按 `repo + triggerType=WEBHOOK` 匹配流水线，再按 `*` 或精确分支过滤，与团队登记解耦。

### 一个项目多个环境（开发 / 测试 / 生产）

新建项目时自动生成三套默认环境，可在项目详情页「环境配置」页签增删改：

| 环境 | 默认名称 | 默认 namespace | 默认副本 | 排序 |
| --- | --- | --- | --- | --- |
| `DEV` | 开发环境 | `<projectKey>` | 1 | 10 |
| `TEST` | 测试环境 | `<projectKey>-test` | 1 | 20 |
| `PROD` | 生产环境 | `<projectKey>-prod` | 2 | 30 |

规则：

- 环境编码创建后不可修改；可停用，停用后发布表单不再展示。
- 创建发布单时必须选择该项目已启用的环境，系统把该环境的 namespace 与默认副本固化到发布单上。
- 执行发布单时使用发布单自身的 namespace（未配置 kubeconfig 时仍返回命令预览，不会假成功）。
- 未配置任何环境时，创建发布单会提示「请先在环境配置中启用至少一个环境」。

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
