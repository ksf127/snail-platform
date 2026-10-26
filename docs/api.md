# 接口清单

- 基础路径：`http://localhost:8088`
- 统一响应：`{"code":0,"message":"ok","data":...}`，业务失败 `code != 0`，HTTP 状态同时给出（400/401/403/404/500）
- 除登录、Webhook、内部接口、Actuator 外，所有 `/api/**` 需要 `Authorization: Bearer <token>`
- 角色：`ADMIN`、`DEVELOPER`、`VIEWER`

## 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录，body `{"username":"admin","password":"admin123"}`，返回 token 与用户信息 |
| GET | `/api/auth/me` | 当前登录用户 |

## 项目

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/projects` | 登录 | 项目列表 |
| GET | `/api/projects/{id}` | 登录 | 项目详情 |
| POST | `/api/projects` | ADMIN/DEVELOPER | `{"key":"DEMO","name":"示例","description":""}`，key 需字母开头、2–32 位 |
| PUT | `/api/projects/{id}` | ADMIN/DEVELOPER | 修改名称/描述 |
| DELETE | `/api/projects/{id}` | ADMIN | 删除项目（级联仓库、流水线、运行记录） |
| GET | `/api/projects/{id}/members` | 登录 | 成员列表 |
| POST | `/api/projects/{id}/members` | ADMIN/DEVELOPER | `{"userId":2,"role":"DEVELOPER"}` |

## 仓库

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/repos?projectId=1` | 登录 | 仓库列表 |
| POST | `/api/repos` | ADMIN/DEVELOPER | 在 Gitea 创建仓库：`{"projectId":1,"name":"demo","description":"","privateRepo":true,"defaultBranch":"main","webhookSecret":""}` |
| POST | `/api/repos/bind` | ADMIN/DEVELOPER | 绑定已有仓库：`{"projectId":1,"name":"demo","cloneUrl":"https://github.com/o/r.git","defaultBranch":"main","webhookSecret":"...","username":"o","token":"PAT"}`；token 可省略 |
| PUT | `/api/repos/{id}/credential` | ADMIN/DEVELOPER | 设置访问凭据：`{"username":"o","token":"PAT"}`，token 加密存储 |
| DELETE | `/api/repos/{id}/credential` | ADMIN/DEVELOPER | 删除访问凭据 |
| GET | `/api/repos/{id}/webhook-secret` | ADMIN | 查看仓库 webhook secret（用于 Gitea / GitHub 配置） |
| DELETE | `/api/repos/{id}` | ADMIN | 解除绑定（不删除远端仓库） |

## 项目构建变量（secrets）

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/secrets?projectId=1` | 登录 | 变量列表，`value` 只返回 `****1234` 掩码 |
| POST | `/api/secrets` | ADMIN/DEVELOPER | 新增：`{"projectId":1,"name":"NPM_TOKEN","value":"...","description":""}` |
| PUT | `/api/secrets/{id}` | ADMIN/DEVELOPER | 修改；`value` 留空表示不修改 |
| DELETE | `/api/secrets/{id}` | ADMIN | 删除 |

## 流水线

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/pipelines?projectId=1&branch=main&team=后端组` | 登录 | 流水线列表；`branch` 精确过滤，`team` 按「项目 + 分支」归属过滤 |
| GET | `/api/pipelines/{id}` | 登录 | 详情 |
| POST | `/api/pipelines` | ADMIN/DEVELOPER | 创建，字段见下 |
| PUT | `/api/pipelines/{id}` | ADMIN/DEVELOPER | 修改（不允许移动项目） |
| DELETE | `/api/pipelines/{id}` | ADMIN | 删除 |
| POST | `/api/pipelines/{id}/trigger` | ADMIN/DEVELOPER | 手动触发，body `{"branch":"main","commitSha":null}` 可省略；排队数达上限返回 409 |
| GET | `/api/pipelines/{id}/runs` | 登录 | 该流水线的运行记录 |
| GET | `/api/pipelines/{id}/yaml` | 登录 | 导出当前定义为 YAML，返回 `yaml / definitionJson / configPath / source` |
| PUT | `/api/pipelines/{id}/yaml` | ADMIN/DEVELOPER | 导入 YAML，body `{"yaml":"steps: ..."}`，校验后覆盖平台定义 |
| POST | `/api/pipelines/{id}/yaml/sync?ref=main` | ADMIN/DEVELOPER | 从仓库 `configPath` 读取 YAML 并写回平台定义；`ref` 省略用流水线分支 |

创建/修改请求体：

```json
{
  "projectId": 1,
  "repoId": 1,
  "name": "构建并打包",
  "branch": "main",
  "triggerType": "MANUAL",
  "executorType": "DOCKER",
  "definitionJson": "{\"steps\":[{\"name\":\"编译\",\"image\":\"maven:3.9-eclipse-temurin-17\",\"commands\":[\"mvn -B -ntp -DskipTests package\"],\"artifacts\":[\"target/*.jar\"]}]}",
  "configPath": ".snail/pipeline.yml",
  "enabled": true
}
```

`configPath` 为空时 `definitionJson` 必填；非空时允许 `definitionJson` 为空，触发时以仓库 YAML 为准。

`triggerType`：`MANUAL` / `WEBHOOK`；`executorType`：`DOCKER` / `K8S` / `JENKINS`。

创建/修改会自动登记 `branch` 到项目分支归属（`*` 除外）。

## 分支团队

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/branches?projectId=1&team=后端组&keyword=ma` | 登录 | 分支归属列表，含负责人名称 |
| POST | `/api/branches` | ADMIN/DEVELOPER | 登记：`{"projectId":1,"branch":"main","team":"后端组","ownerId":2,"description":""}` |
| PUT | `/api/branches/{id}` | ADMIN/DEVELOPER | 修改；分支改名会级联更新同项目 `pipeline.branch` |
| DELETE | `/api/branches/{id}` | ADMIN/DEVELOPER | 删除登记（不影响流水线） |

约束：同一项目内 `branch` 唯一；`branch` 不能为 `*`；`ownerId` 必须是已存在用户。

## 环境配置

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/environments?projectId=1&enabled=true` | 登录 | 环境列表，按 `sortOrder` 排序 |
| POST | `/api/environments` | ADMIN/DEVELOPER | 新增：`{"projectId":1,"name":"UAT","displayName":"预发环境","namespace":"demo-uat","defaultReplicas":1,"description":"","sortOrder":40,"enabled":true}` |
| PUT | `/api/environments/{id}` | ADMIN/DEVELOPER | 修改；`name` 不可变更 |
| DELETE | `/api/environments/{id}` | ADMIN/DEVELOPER | 删除（该环境下已有发布单不受影响） |

新建项目自动生成 `DEV` / `TEST` / `PROD` 三套默认环境（namespace 为 `<projectKey>`、`<projectKey>-test`、`<projectKey>-prod`）。

## 运行记录

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/runs?pipelineId=1&limit=50` | 登录 | 运行列表（limit 默认 50） |
| GET | `/api/runs/{id}` | 登录 | 运行详情 |
| POST | `/api/runs/{id}/cancel` | ADMIN/DEVELOPER | 取消 |
| GET | `/api/runs/{id}/log` | 登录 | 返回 `{"url":"<MinIO 预签名地址>"}`，1 小时有效 |
| GET | `/api/runs/{id}/artifacts` | 登录 | 制品列表（文件名、大小、SHA-256） |
| GET | `/api/artifacts/{id}/download` | 登录 | 返回制品预签名下载地址 |

## 发布

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/deployments?projectId=1` | 登录 | 发布单列表 |
| POST | `/api/deployments` | ADMIN/DEVELOPER | 创建：`{"projectId":1,"runId":3,"environment":"DEV","image":"registry/app:1.0.0","replicas":2,"note":""}` |
| POST | `/api/deployments/{id}/execute` | ADMIN/DEVELOPER | 执行；K8s 未配置时返回命令预览并标记为预览 |

`environment`：取项目「环境配置」中的启用环境编码（默认 `DEV` / `TEST` / `PROD`，也可自定义）。创建时服务端会校验环境存在且启用，并把该环境的 `namespace` 与默认副本固化到发布单；`replicas` 省略时使用环境默认值。

## 仪表盘

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/dashboard` | 项目数、仓库数、流水线数、运行数、成功率、最近 10 次运行 |

## Webhook

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/webhooks/gitea` | Gitea push 事件。校验 `X-Gitea-Signature`（HMAC-SHA256），按仓库 secret 匹配后触发启用的 `WEBHOOK` 流水线 |
| POST | `/api/webhooks/github` | GitHub push 事件。校验 `X-Hub-Signature-256`（HMAC-SHA256），`X-GitHub-Event: ping` 直接返回空列表，仅处理 `push` |

## 内部接口（worker → control）

需要请求头 `X-Internal-Token: <INTERNAL_TOKEN>`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/internal/runs/{id}/context` | 获取仓库地址、分支、commit、steps（优先使用 run 快照）、executorType、projectKey、仓库凭据与项目 secrets |
| POST | `/api/internal/runs/{id}/status` | `{"status":"RUNNING|SUCCESS|FAILED|CANCELED","errorMessage":null,"logObject":"runs/1/build.log"}` |
| POST | `/api/internal/runs/{id}/artifacts` | 登记制品：文件名、对象键、大小、SHA-256、contentType |

## 健康检查

| 路径 | 说明 |
| --- | --- |
| `GET /actuator/health` | 存活/就绪 |
| `GET /actuator/prometheus` | Prometheus 指标 |
