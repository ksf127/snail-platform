-- 配置即代码：流水线可指向仓库内的 YAML 配置文件，构建时按该分支/提交解析。
ALTER TABLE pipeline ADD COLUMN config_path VARCHAR(255) NULL AFTER definition_json;

-- 触发时解析后的定义快照，保证 worker 执行与校验结果一致。
ALTER TABLE pipeline_run ADD COLUMN resolved_definition JSON NULL AFTER commit_sha;

-- 项目级构建变量/凭据，value_cipher 为 AES-GCM 加密后的文本，接口只返回掩码。
CREATE TABLE project_secret (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    project_id   BIGINT       NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    value_cipher TEXT         NOT NULL,
    description  VARCHAR(255) NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_secret (project_id, name),
    CONSTRAINT fk_secret_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

-- 仓库访问凭据（私有仓库 clone / 读取 YAML 用），token 加密存储。
CREATE TABLE repo_credential (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    repo_id      BIGINT       NOT NULL,
    username     VARCHAR(128) NULL,
    token_cipher TEXT         NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_repo_credential (repo_id),
    CONSTRAINT fk_credential_repo FOREIGN KEY (repo_id) REFERENCES repo (id) ON DELETE CASCADE
);
