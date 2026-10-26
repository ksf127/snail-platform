# 部署与执行器

## 一、docker compose（推荐起步）

```bash
cp .env.example .env      # 修改密码、令牌等
docker compose up -d --build
```

包含服务：`mysql`、`rabbitmq`、`minio`、`minio-init`（建 bucket）、`gitea`、`control`、`worker`。Jenkins 使用 profile 单独开启：

```bash
docker compose --profile jenkins up -d
```

### 关键路径约束

Docker 执行器通过 `docker run -v <宿主机路径>:/workspace` 挂载源码，因此：

- worker 必须挂载 `/var/run/docker.sock`（compose 已配置）。
- `WORKSPACE_HOST_DIR` 必须是 **宿主机** 上真实存在的路径，且与 worker 内的 `WORKSPACE_DIR` 指向同一目录（compose 用 `${WORKSPACE_HOST_DIR:-./workspace}:/workspace` 保证）。

如果 worker 运行在容器里但 Docker 在另一台机器上，Docker 执行器不可用，请改用 K8s 执行器。

## 二、环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `platform_db` / `platform` / `platform123` | MySQL |
| `RABBITMQ_HOST` / `PORT` / `USERNAME` / `PASSWORD` / `VHOST` | localhost / 5672 / platform / platform123 / `/` | 队列 |
| `MINIO_ENDPOINT` / `ACCESS_KEY` / `SECRET_KEY` / `BUCKET` | http://localhost:9000 / platform / platform123 / platform-artifacts | 制品 |
| `GITEA_URL` / `GITEA_TOKEN` | 空 | 代码托管；为空时只能手工绑定仓库 |
| `JWT_SECRET` | dev 值 | 生产必须替换，至少 32 字节 |
| `PLATFORM_CRYPTO_KEY` | 空 | 构建变量 / 仓库 token 的 AES-256-GCM 密钥；为空时由 `JWT_SECRET` 派生，生产必须显式配置并备份 |
| `INTERNAL_TOKEN` | dev-internal-token | control 与 worker 的内部调用令牌 |
| `MAX_QUEUED_PER_PIPELINE` | 3 | 同一流水线允许排队的运行上限，超过后触发返回 409 |
| `EXECUTOR_DEFAULT` | DOCKER | 未声明执行器时的回退值 |
| `EXECUTOR_TIMEOUT_SECONDS` | 1800 | 单个步骤/整个远端构建的超时 |
| `K8S_KUBECONFIG` / `K8S_NAMESPACE` | 空 / platform | K8s 执行器与发布；发布优先用环境配置的 namespace，为空时才回退到 `K8S_NAMESPACE` |
| `JENKINS_URL` / `USER` / `TOKEN` / `JOB` | 空 | Jenkins 执行器 |

完整清单见 `.env.example`。

## 三、三种执行器

### Docker

```text
git clone → docker run --rm -v <source>:<workDir> <image> sh -lc "<commands>"
```

- 优点：零额外基础设施，最接近本地构建。
- 代价：worker 拥有宿主机 Docker 控制权，等同 root。
- 适用：单机、内部可信代码。

### Kubernetes

- 每个构建创建一个 Job，initContainer 串行执行 git clone 与各 step，使用 PVC 共享工作区。
- 需要 worker 具备创建 Job/Pod/PVC 的 RBAC（示例见 `deploy/` 下的 k8s 清单思路，按集群实际调整）。
- 产物经 Pod 日志回传，体积受日志上限约束（通常几百 KB 到数 MB），大产物建议改用对象存储直传步骤。
- 适用：多租户、需要隔离和弹性。

### Jenkins

- 约定 Job 接收参数 `REPO_URL`、`BRANCH`、`COMMIT_SHA`、`RUN_ID`，并归档产物。
- worker 通过 `POST /job/{job}/buildWithParameters` 以 **表单体** 提交参数（不走 URL query，避免凭据进入访问日志），轮询 `lastBuild`，拉取 `consoleText` 与归档文件。
- 构建变量与仓库凭据通过参数下发：
  - `GIT_USERNAME` / `GIT_TOKEN`：仓库访问凭据，仅在仓库配置了 token 时传入；
  - `SECRETS_JSON`：项目构建变量的 JSON 对象（`{"NPM_TOKEN":"..."}`），没有变量时不传，使用 Job 默认值 `{}`。
- Job 需把这三个参数声明为 `password` 类型（UI 掩码），并在执行前注入环境变量；建议安装 `mask-passwords` 插件兜底控制台输出。
- 适用：已有 Jenkins 流水线与凭据体系，不想迁移。
- `JENKINS_URL` 或 `JENKINS_JOB` 为空时，运行会直接失败并给出明确原因。

Job 参数与注入示例（Jenkinsfile）：

```groovy
pipeline {
  agent any
  parameters {
    string(name: 'REPO_URL')
    string(name: 'BRANCH', defaultValue: 'main')
    string(name: 'COMMIT_SHA', defaultValue: '')
    string(name: 'RUN_ID')
    password(name: 'GIT_USERNAME', defaultValue: '')
    password(name: 'GIT_TOKEN', defaultValue: '')
    password(name: 'SECRETS_JSON', defaultValue: '{}')
  }
  stages {
    stage('build') {
      steps {
        script {
          def secrets = new groovy.json.JsonSlurper().parseText(params.SECRETS_JSON ?: '{}')
          // 需要 mask-passwords 插件；未安装时 Job 内不要 echo 变量值
          wrap([$class: 'MaskPasswordsBuildWrapper',
                varPasswordPairs: secrets.collect { k, v -> [var: k, password: v] }]) {
            secrets.each { k, v -> env[k] = v }
            env.GIT_USERNAME = params.GIT_USERNAME
            env.GIT_TOKEN = params.GIT_TOKEN
            // 后续 sh 步骤使用 $NPM_TOKEN 等变量
          }
        }
      }
    }
  }
  post {
    always { archiveArtifacts artifacts: '**/target/*.jar', allowEmptyArchive: true }
  }
}
```

安全边界：

- `password` 参数只在 Job 配置页掩码，构建参数仍保存在 Jenkins build 记录中；拥有 Job 读取权限的人可通过 API 看到。对高敏感环境，建议改用 Jenkins Credentials：由运维在 Jenkins 预建 `string` / `usernamePassword` 凭据，平台只传凭据 ID（后续可扩展 `CREDENTIALS_MAP` 参数），Job 用 `withCredentials` 绑定，避免明文进入构建参数。
- worker 侧 `MaskingLogSink` 仍会对拉回的 `consoleText` 做二次脱敏并写入 MinIO，但 Jenkins 自身的控制台历史不会被回写。
- Jenkins 由 `--profile jenkins` 启动，Job 的 `REPO_URL` 必须能从 Jenkins 容器访问（localhost 指向容器自身，需用宿主机地址或 compose 服务名）。

## 四、多环境发布

发布以「项目环境」为单位，每个环境对应一个 K8s namespace：

| 环境编码 | 默认 namespace | 默认副本 |
| --- | --- | --- |
| `DEV` | `<projectKey>` | 1 |
| `TEST` | `<projectKey>-test` | 1 |
| `PROD` | `<projectKey>-prod` | 2 |

- 环境在项目详情页「环境配置」维护，新建项目自动生成三套默认值，可以增删改或停用。
- 创建发布单时选择已启用环境，服务端把 `namespace` 与默认副本固化到发布单；之后改环境配置不会影响历史发布单。
- 执行发布单调用 `K8sDeploymentOperator.updateImage(namespace, deploymentName, image, replicas)`，`deploymentName` 使用项目 key。
- 未配置 `K8S_KUBECONFIG` 时返回命令预览（`mode=PREVIEW`）；配置了但 namespace/Deployment 不存在时返回明确的失败原因，不会假成功。
- 发布单列表展示环境与 namespace，可按环境筛选。

## 五、生产部署建议

1. **控制面与 worker 分开部署**，worker 单独一台构建节点或独立命名空间。
2. 控制面只暴露 8088（或经网关），worker 的 8089 不对公网开放。
3. MySQL、RabbitMQ、MinIO 使用外部托管或持久卷，不要用 compose 默认密码。
4. `JWT_SECRET`、`INTERNAL_TOKEN`、`GITEA_TOKEN` 通过 Secret 注入。
5. 为 worker 设置资源上限（CPU/内存/并发），RabbitMQ `prefetch=1` 已限制单 worker 并发。
6. 定期清理 MinIO 中过期 run 前缀，日志与制品按项目设置保留期。
7. 前端 `web` 构建后可用 Nginx 托管 `dist`，并将 `/api` 反向代理到控制面。

## 五、前端发布

```bash
cd web
npm ci
npm run build      # 产物 web/dist
```

Nginx 关键配置：

```nginx
location /api/ {
    proxy_pass http://control:8088/api/;
    proxy_set_header Host $host;
}
location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;
}
```

## 六、升级

- 表结构通过 Flyway 自动迁移，新增脚本放 `platform-control/src/main/resources/db/migration`，版本号递增。
- 队列与交换机为 durable，升级期间消息不丢失；先停 worker 再停 control 可避免状态回写失败。
- 从旧版本升级到含 `V3__branch_team_environment.sql` 的版本时，Flyway 会自动：
  - 建 `project_branch` / `project_environment`，并按现有流水线的分支回填分支归属；
  - 为每个项目补齐 `DEV` / `TEST` / `PROD` 三套环境；
  - 给 `deployment` 增加 `namespace`，并按 `environment` 回填历史发布单的 namespace。
- 从旧版本升级到含 `V4__pipeline_yaml_secrets.sql` 的版本时：
  - `pipeline` 增加 `config_path`（默认空，仍使用平台定义）；
  - `pipeline_run` 增加 `resolved_definition` 快照；
  - 新建 `project_secret` 与 `repo_credential`，历史 cloneUrl 中如带 token 不会被自动拆分，建议重新绑定或补录凭据；
  - 配置 `PLATFORM_CRYPTO_KEY` 后再录入 secrets，避免后续更换密钥导致旧密文不可读。
