# 架构说明

## 为什么不是一堆微服务

平台自身是内部系统，用户量和并发都远低于业务系统，拆成 repo / pipeline / deploy / project 等独立服务只会增加运维成本。当前采用 **控制面 + 执行面** 两进程：

- 控制面 `platform-control`：所有写操作、权限判断、元数据持久化、对外 API。
- 执行面 `platform-worker`：只做一件事——消费构建消息，在隔离环境中执行不可信代码。

只要构建量上来，worker 可以水平扩容（同一队列多消费者），控制面仍是单实例即可。真正需要拆分时，边界已经按「是否执行不可信代码」划好了。

## 模块职责

| 模块 | 内容 | 不允许做什么 |
| --- | --- | --- |
| `platform-common` | `ApiResult`、`BuildMessage`、`PipelineDefinition`、枚举 | 不依赖 Spring Web、不访问数据库 |
| `platform-control` | controller → service → mapper，安全过滤器，Gitea/MinIO/K8s 集成 | 不执行构建命令 |
| `platform-worker` | RabbitMQ 监听、执行器、日志与产物上传 | 不直接改数据库，状态通过内部接口回写 |

控制面访问 worker 不可达，worker 只通过 `CONTROL_URL` + `X-Internal-Token` 调用控制面的 `/api/internal/**`。

## 一次构建的完整数据流

1. 用户在「流水线」页点击触发，或 Gitea push 触发 `/api/webhooks/gitea`。
2. 控制面校验权限/签名，创建 `pipeline_run`（`QUEUED`，build_no 自增），向 RabbitMQ 发送 `BuildMessage`。
3. worker 消费消息，调用 `/api/internal/runs/{id}/context` 获取仓库地址、分支、commit、steps。
4. worker 将状态置为 `RUNNING`，按 `executor_type` 选择执行器：
   - Docker：`git clone` 到工作目录，每个 step 执行 `docker run --rm -v <source>:<workDir> <image> sh -lc <commands>`。
   - K8s：创建 Job（git 拉取 initContainer + 工具镜像），轮询 Pod 日志。
   - Jenkins：触发已有 Job 并轮询 console 输出。
5. 每个 step 结束后按 `artifacts` 通配符收集文件，上传 MinIO 并登记 `build_artifact`（含大小、SHA-256）。
6. 日志上传到 MinIO，状态回写 `SUCCESS` / `FAILED`，清理工作目录。
7. 消息处理失败且超过重试时进入死信队列 `platform.build.queue.dlq`，可在 RabbitMQ 控制台重投。

## 队列与可靠性

```text
exchange: platform.build (direct, durable)
  routing: build.run  ──▶  queue: platform.build.queue
                                 │ 消费失败
                                 ▼
exchange: platform.build.dlx ──▶ queue: platform.build.queue.dlq
```

- 生产端开启 publisher confirms / returns，投递失败会抛错，不会静默丢消息。
- 消费端 `prefetch=1`、`default-requeue-rejected=false`，避免毒消息无限重投。
- 运行状态的最终一致性由 worker 三次重试回写保证；控制面不依赖 worker 存活。

## 数据模型

| 表 | 作用 |
| --- | --- |
| `app_user` / `project_member` | 账号与项目成员角色 |
| `project` | 项目 |
| `repo` | 仓库绑定（Gitea 或手工 cloneUrl），保存 webhook secret |
| `pipeline` | 流水线定义（steps JSON）、分支、触发方式、执行器 |
| `pipeline_run` | 每次运行，含 build_no、状态、commit、日志对象键 |
| `build_artifact` | 制品索引，对象键指向 MinIO |
| `deployment` | 发布单：环境、镜像、副本数、状态 |

表结构由 Flyway 管理，脚本位置 `platform-control/src/main/resources/db/migration`。

## 扩展点

- **新增执行器**：实现 `BuildExecutor`（`type()` + `execute()`），注册为 Bean，`ExecutorResolver` 会自动收集。
- **新增代码托管**：实现类似 `GiteaClient` 的客户端并新增 webhook 控制器，仓库表用 `provider` 区分。
- **新增环境**：`DeploymentEnvironment` 枚举 + 前端下拉，发布流程不变。
- **前端新增页面**：`web/src/pages` 新增组件，在 `Layout` 菜单注册即可。

## 已知边界

- 登出/令牌撤销未做服务端黑名单，依赖 JWT 过期时间（默认 12 小时）。
- 发布单只在 K8s 客户端可用时真正执行，否则返回命令预览。
- 未内置多租户，隔离靠项目成员表。
