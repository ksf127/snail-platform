-- 多分支团队：一个项目可有多个分支，不同分支由不同团队/负责人维护。
CREATE TABLE project_branch (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL,
    branch      VARCHAR(64)  NOT NULL,
    team        VARCHAR(64)  NULL,
    owner_id    BIGINT       NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_branch (project_id, branch),
    KEY idx_project_branch_team (project_id, team),
    CONSTRAINT fk_branch_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_branch_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 多环境：每个项目维护自己的环境清单，发布时按环境取 namespace 与默认副本。
CREATE TABLE project_environment (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    project_id       BIGINT       NOT NULL,
    name             VARCHAR(32)  NOT NULL,
    display_name     VARCHAR(64)  NOT NULL,
    namespace        VARCHAR(64)  NOT NULL,
    default_replicas INT          NOT NULL DEFAULT 1,
    description      VARCHAR(255) NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_env (project_id, name),
    KEY idx_project_env_project (project_id),
    CONSTRAINT fk_env_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 发布单固化创建时的 namespace，避免环境配置变更影响历史记录回放。
ALTER TABLE deployment ADD COLUMN namespace VARCHAR(64) NULL AFTER environment;

-- 回填：把已有流水线使用的分支登记到分支表（通配符 * 不登记）。
INSERT INTO project_branch (project_id, branch, created_at, updated_at)
SELECT DISTINCT p.project_id, p.branch, NOW(), NOW()
FROM pipeline p
WHERE p.branch IS NOT NULL AND p.branch <> '*';

-- 回填：已有项目补齐 DEV / TEST / PROD 三套默认环境。
INSERT INTO project_environment (project_id, name, display_name, namespace, default_replicas,
                                 description, sort_order, enabled, created_at, updated_at)
SELECT id, 'DEV', '开发环境', LOWER(project_key), 1, '默认开发环境', 10, 1, NOW(), NOW()
FROM project;

INSERT INTO project_environment (project_id, name, display_name, namespace, default_replicas,
                                 description, sort_order, enabled, created_at, updated_at)
SELECT id, 'TEST', '测试环境', CONCAT(LOWER(project_key), '-test'), 1, '默认测试环境', 20, 1, NOW(), NOW()
FROM project;

INSERT INTO project_environment (project_id, name, display_name, namespace, default_replicas,
                                 description, sort_order, enabled, created_at, updated_at)
SELECT id, 'PROD', '生产环境', CONCAT(LOWER(project_key), '-prod'), 2, '默认生产环境', 30, 1, NOW(), NOW()
FROM project;

-- 回填：已有发布单按环境写入对应 namespace。
UPDATE deployment d
    JOIN project p ON p.id = d.project_id
SET d.namespace = CASE d.environment
                       WHEN 'TEST' THEN CONCAT(LOWER(p.project_key), '-test')
                       WHEN 'PROD' THEN CONCAT(LOWER(p.project_key), '-prod')
                       ELSE LOWER(p.project_key)
    END
WHERE d.namespace IS NULL;
