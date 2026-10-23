-- 平台初始结构。所有时间使用 DATETIME，应用侧写入 LocalDateTime。
CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE project (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_key VARCHAR(32)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(500) NULL,
    owner_id    BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_key (project_key),
    CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE project_member (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    project_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member (project_id, user_id),
    CONSTRAINT fk_member_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE repo (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    project_id     BIGINT       NOT NULL,
    name           VARCHAR(64)  NOT NULL,
    provider       VARCHAR(16)  NOT NULL,
    external_id    VARCHAR(64)  NULL,
    clone_url      VARCHAR(512) NOT NULL,
    default_branch VARCHAR(64)  NOT NULL DEFAULT 'main',
    webhook_secret VARCHAR(128) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_repo_project (project_id),
    KEY idx_repo_external (external_id),
    CONSTRAINT fk_repo_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pipeline (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    project_id      BIGINT      NOT NULL,
    repo_id         BIGINT      NULL,
    name            VARCHAR(64) NOT NULL,
    branch          VARCHAR(64) NOT NULL DEFAULT 'main',
    trigger_type    VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    executor_type   VARCHAR(16) NOT NULL DEFAULT 'DOCKER',
    definition_json TEXT        NOT NULL,
    enabled         TINYINT(1)  NOT NULL DEFAULT 1,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pipeline_project (project_id),
    KEY idx_pipeline_repo (repo_id),
    CONSTRAINT fk_pipeline_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_repo FOREIGN KEY (repo_id) REFERENCES repo (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pipeline_run (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    pipeline_id   BIGINT       NOT NULL,
    build_no      INT          NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    commit_sha    VARCHAR(64)  NULL,
    branch        VARCHAR(64)  NOT NULL,
    trigger_type  VARCHAR(16)  NOT NULL,
    triggered_by  VARCHAR(64)  NULL,
    started_at    DATETIME     NULL,
    finished_at   DATETIME     NULL,
    log_object    VARCHAR(255) NULL,
    error_message VARCHAR(1000) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_build_no (pipeline_id, build_no),
    KEY idx_run_pipeline (pipeline_id, id),
    CONSTRAINT fk_run_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipeline (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE build_artifact (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    run_id       BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    object_key   VARCHAR(512) NOT NULL,
    size         BIGINT       NOT NULL DEFAULT 0,
    sha256       CHAR(64)     NULL,
    content_type VARCHAR(128) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_artifact_run (run_id),
    CONSTRAINT fk_artifact_run FOREIGN KEY (run_id) REFERENCES pipeline_run (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE deployment (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL,
    run_id      BIGINT       NULL,
    environment VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    image       VARCHAR(512) NOT NULL,
    replicas    INT          NOT NULL DEFAULT 1,
    note        VARCHAR(500) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_deployment_project (project_id),
    CONSTRAINT fk_deployment_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_deployment_run FOREIGN KEY (run_id) REFERENCES pipeline_run (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 种子数据：admin/admin123（BCrypt），角色 ADMIN
INSERT INTO app_user (username, password_hash, display_name, role, enabled, created_at)
VALUES ('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '平台管理员', 'ADMIN', 1, NOW());

INSERT INTO project (project_key, name, description, owner_id, status, created_at)
VALUES ('DEMO', '示例项目', '平台初始化示例项目，可直接删除', 1, 'ACTIVE', NOW());

INSERT INTO project_member (project_id, user_id, role)
VALUES (1, 1, 'ADMIN');
