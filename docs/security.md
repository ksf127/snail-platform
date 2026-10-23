# 安全说明

## 一、不可信构建是最大风险

流水线执行的是仓库里的任意命令，等价于「让别人在你的机器上跑代码」。因此：

- **控制面绝不执行构建**。控制面只写数据库、发消息；执行只发生在 `platform-worker`。
- **Docker 执行器等同于宿主 root**。worker 挂载了 `/var/run/docker.sock`，容器内的进程可以启动特权容器、挂载宿主目录。只应在专用构建节点启用，且该节点不承载控制面、数据库和其他业务。
- **需要强隔离时使用 K8s 执行器**，配合命名空间、ResourceQuota、NetworkPolicy 和最小 RBAC。
- 不要给 worker 挂载不必要的宿主目录；`WORKSPACE_HOST_DIR` 只指向构建工作区。

## 二、密钥与凭据

| 密钥 | 存放 | 要求 |
| --- | --- | --- |
| `JWT_SECRET` | Secret / 环境变量 | ≥ 32 字节随机值，轮换后旧 token 立即失效 |
| `INTERNAL_TOKEN` | Secret / 环境变量 | 仅 control 与 worker 共享，禁止下发到前端 |
| `GITEA_TOKEN` | Secret | 最小权限：仅目标组织的仓库与 webhook 权限 |
| `MINIO_ACCESS_KEY/SECRET_KEY` | Secret | 只授权 `platform-artifacts` bucket |
| `RABBITMQ_*` | Secret | 独立 vhost 与账号 |
| `DB_PASSWORD` | Secret | 最小权限账号，禁止 root |
| 仓库 `webhook_secret` | 数据库 | 每个仓库独立；验签失败直接拒绝 |

- 仓库密码使用 BCrypt 存储，登录失败不区分「用户不存在」和「密码错误」。
- Webhook 校验 `X-Gitea-Signature`（HMAC-SHA256，原始请求体），签名不对不进入业务逻辑。
- 内部接口 `/api/internal/**` 必须带 `X-Internal-Token`，token 错误直接 401。

## 三、网络隔离

```text
公网 / 办公网
   │  仅 8088（或网关）
   ▼
control  ──── MySQL / RabbitMQ / MinIO（内网）
   │
   │ 队列（单向）
   ▼
worker   ──── 构建节点 Docker / K8s 集群
   │
   └── 8089 不对公网暴露
```

- worker 的 HTTP 端口仅用于健康检查，不需要对外。
- 数据库、队列、对象存储不要映射到公网端口。
- 生产建议 control 前面加统一网关，做 TLS、限流与审计。

## 四、权限模型

| 角色 | 可做 |
| --- | --- |
| ADMIN | 全部，包括删除项目/仓库/流水线 |
| DEVELOPER | 创建与修改项目、仓库、流水线，触发与取消运行，执行发布 |
| VIEWER | 只读：查看列表、运行记录、日志与制品下载 |

- 方法级权限使用 `@PreAuthorize`，路径级白名单只放登录、Webhook、内部接口与 Actuator。
- 项目成员表记录成员角色，用于后续按项目隔离（当前接口级校验以全局角色为主）。

## 五、制品与日志

- 制品与日志只存在 MinIO，数据库只保存对象键与 SHA-256。
- 下载使用 1 小时预签名 URL，不暴露长期凭据。
- 日志可能包含仓库输出的敏感信息，谨慎授予 VIEWER 权限。

## 六、已知限制

- 未做服务端令牌吊销，登出仅清前端；缩短 `platform.jwt.expire-minutes` 可降低风险。
- 未内置审计日志表，敏感操作审计需在网关层或数据库层补充。
- 未做多租户硬隔离，跨项目访问控制依赖成员表与后续增强。
