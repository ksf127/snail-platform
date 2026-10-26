# 流水线 DSL

流水线定义保存在 `pipeline.definition_json`，是一个 JSON 字符串或对象，结构只有一层 `steps`。

## 最小示例

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

## 字段

| 字段 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `steps[].name` | 是 | — | 步骤名，写入日志 |
| `steps[].image` | 是 | — | 容器镜像；Docker 执行器每步起一个容器 |
| `steps[].commands` | 是 | `[]` | 以 `sh -lc "cmd1 && cmd2"` 的形式拼接执行，任一命令非 0 即失败 |
| `steps[].workDir` | 否 | `/workspace` | 容器内工作目录，同时作为源码挂载点 |
| `steps[].artifacts` | 否 | `[]` | 相对 `workDir` 的 glob，构建结束后上传 MinIO |

## 多步骤示例（前后端）

```json
{
  "steps": [
    {
      "name": "后端打包",
      "image": "maven:3.9-eclipse-temurin-17",
      "commands": ["mvn -B -ntp -DskipTests package"],
      "workDir": "/workspace",
      "artifacts": ["*/target/*.jar"]
    },
    {
      "name": "前端构建",
      "image": "node:20-alpine",
      "commands": ["npm ci", "npm run build"],
      "workDir": "/workspace/web",
      "artifacts": ["dist/**"]
    }
  ]
}
```

## YAML 与配置即代码

流水线在平台内仍保存为 JSON，但编辑页支持直接编辑 YAML（`GET/PUT /api/pipelines/{id}/yaml`）。同一份结构可以放在仓库中，由流水线的 `config_path` 指定：

```yaml
steps:
  - name: 编译
    image: maven:3.9-eclipse-temurin-21
    commands:
      - mvn -B -ntp -DskipTests package
    workDir: /workspace
    artifacts:
      - target/*.jar
```

规则：

- `config_path` 为空：使用平台内 JSON 定义。
- `config_path` 非空：触发时读取该仓库目标分支（有 commitSha 时用 commitSha）的 YAML，校验后写入 `pipeline_run.resolved_definition` 快照，worker 只执行快照。
- 读取失败、YAML 语法错误、缺少 `image` / `commands` 都会在触发前返回 4xx，不会创建运行记录。
- 平台内 JSON 仍保留作预览；只有执行「导入 YAML」才会覆盖平台定义，且不会清空 `config_path`。
- GitHub 私有仓库或高频读取需要为仓库配置访问凭据（PAT）；Gitea 使用 `repo_credential` 中的 token。

## 执行语义

- **Docker**：源码 clone 到宿主机工作目录，每个 step 单独 `docker run --rm -v <source>:<workDir>`。step 之间共享同一份源码，因此可以前一步装依赖、后一步打包。
- **K8s**：所有 step 以 initContainer 串行执行，共享同一个 PVC；产物由主容器打包成 tar.gz 输出到 Pod 日志后由 worker 还原。受 Pod 日志大小限制，适合中小产物。
- **Jenkins**：step 内容不作为 Jenkins 脚本下发，仅把 `REPO_URL`、`BRANCH`、`COMMIT_SHA`、`RUN_ID` 传给约定 Job；产物从 Jenkins 归档中按 `artifacts` 匹配下载。

## 注意事项

- 不要在 `commands` 里写交互式命令，容器没有 TTY。
- 构建环境变量 = 镜像自带环境 + 项目「构建变量」（secrets）注入。Docker 以 `-e NAME=value` 传入，K8s 写入容器 env；Jenkins 执行器当前未注入平台 secrets。
- 日志会脱敏：URL 中的 userinfo 与已知 token / secret 值会替换为掩码，但仓库命令自行打印的其他敏感信息仍需自行规避。
- `artifacts` 使用 JDK `PathMatcher` 的 glob 语法，`**` 表示任意层级。
- 每次运行工作目录为 `<WORKSPACE_DIR>/run-<runId>`，结束后自动清理。
- 同一流水线同时只允许有限排队（默认 3 个 QUEUED），Webhook 对同 commit 的重复推送会跳过。
