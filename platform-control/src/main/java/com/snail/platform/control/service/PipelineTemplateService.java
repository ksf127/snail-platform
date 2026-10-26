package com.snail.platform.control.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.model.ExecutorType;
import com.snail.platform.common.model.PipelineSide;
import com.snail.platform.common.model.TriggerType;
import com.snail.platform.control.dto.PipelineTemplateView;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.integration.GiteaClient;
import com.snail.platform.control.mapper.RepoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 流水线步骤模板：按仓库类型生成 definitionJson，避免手写 JSON。
 * Gitea 已配置时探测 package.json / pom.xml / build.gradle；否则按仓库名推断；都失败时兜底通用模板。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineTemplateService {

    private static final List<String> STACKS = List.of("NODE", "MAVEN", "GRADLE", "GENERIC");
    private static final List<String> FRONTEND_HINTS = List.of("web", "ui", "front", "admin", "console", "portal");
    private static final List<String> BACKEND_HINTS = List.of("api", "service", "server", "backend", "platform");

    private final RepoMapper repoMapper;
    private final GiteaClient giteaClient;
    private final ObjectMapper objectMapper;

    public PipelineTemplateView suggest(Long repoId, String stack, String name) {
        return suggest(repoId, stack, name, null, null);
    }

    /**
     * 生成单条流水线的步骤模板。
     * side 为空时按技术栈推断；指定 FRONTEND 时用 Node 模板并把 workDir 指向前端子目录，
     * 指定 BACKEND 时 workDir 固定为仓库根目录 /workspace。
     */
    public PipelineTemplateView suggest(Long repoId, String stack, String name,
                                        String side, String frontendDir) {
        RepoEntity repo = repoId == null ? null : repoMapper.selectById(repoId);
        String requestedSide = normalizeSide(side);
        String requested = normalizeStack(stack);

        Detection detection;
        if (PipelineSide.FRONTEND.name().equals(requestedSide)) {
            detection = new Detection("NODE", "使用「前端 Node」模板");
        } else if (PipelineSide.BACKEND.name().equals(requestedSide)) {
            if (requested == null || "NODE".equals(requested)) {
                String detected = detect(repo).stack();
                requested = "NODE".equals(detected) ? "MAVEN" : detected;
            }
            detection = new Detection(requested, "使用「" + label(requested) + "」模板");
        } else {
            detection = requested == null
                    ? detect(repo)
                    : new Detection(requested, "使用「" + label(requested) + "」模板");
        }

        String actualSide = requestedSide != null ? requestedSide
                : ("NODE".equals(detection.stack()) ? PipelineSide.FRONTEND.name() : PipelineSide.BACKEND.name());
        String workDir = PipelineSide.FRONTEND.name().equals(actualSide)
                ? WORKSPACE + "/" + normalizeDir(frontendDir) : WORKSPACE;
        String branch = repo == null || !StringUtils.hasText(repo.getDefaultBranch())
                ? "main" : repo.getDefaultBranch();
        String pipelineName = StringUtils.hasText(name)
                ? name.trim() : defaultName(repo, actualSide, requestedSide);

        return new PipelineTemplateView(detection.stack(), actualSide, pipelineName, branch,
                TriggerType.MANUAL.name(), ExecutorType.DOCKER.name(),
                toJson(definition(detection.stack(), workDir)), detection.source());
    }

    /** 未填名称时给出建议：自动识别用 xxx-build，指定端时用 xxx-web / xxx-api。 */
    private String defaultName(RepoEntity repo, String side, String requestedSide) {
        if (repo == null) {
            return "build";
        }
        if (!StringUtils.hasText(requestedSide)) {
            return repo.getName() + "-build";
        }
        return repo.getName() + (PipelineSide.FRONTEND.name().equals(side) ? "-web" : "-api");
    }

    /**
     * 单仓（前后端同一个仓库）模板：一次给出前端、后端两条流水线。
     * 前端 workDir 指向 frontendDir 子目录，后端留在仓库根目录，各自构建自己的产物。
     */
    public List<PipelineTemplateView> suggestMonorepo(RepoEntity repo, String frontendDir, String backendStack) {
        if (repo == null) {
            throw BizException.badRequest("单仓模板需要先选择关联仓库");
        }
        String dir = normalizeDir(frontendDir);
        String branch = StringUtils.hasText(repo.getDefaultBranch()) ? repo.getDefaultBranch() : "main";
        String base = StringUtils.hasText(repo.getName()) ? repo.getName() : "app";
        String backend = normalizeStack(backendStack);
        if (backend == null || "NODE".equals(backend)) {
            String detected = detect(repo).stack();
            backend = "NODE".equals(detected) ? "MAVEN" : detected;
        }
        return List.of(
                new PipelineTemplateView("NODE", PipelineSide.FRONTEND.name(), base + "-web", branch,
                        TriggerType.MANUAL.name(), ExecutorType.DOCKER.name(),
                        toJson(definition("NODE", WORKSPACE + "/" + dir)),
                        "单仓模板：前端子目录 " + dir),
                new PipelineTemplateView(backend, PipelineSide.BACKEND.name(), base + "-api", branch,
                        TriggerType.MANUAL.name(), ExecutorType.DOCKER.name(),
                        toJson(definition(backend, WORKSPACE)),
                        "单仓模板：后端仓库根目录"));
    }

    private static final String WORKSPACE = "/workspace";

    /** 子目录只允许相对路径片段，避免跳出 /workspace。 */
    private String normalizeDir(String frontendDir) {
        String value = StringUtils.hasText(frontendDir) ? frontendDir.trim().replace('\\', '/') : "web";
        value = value.replaceAll("^/+", "").replaceAll("/+$", "");
        if (!StringUtils.hasText(value)) {
            value = "web";
        }
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                throw BizException.badRequest("前端子目录不能包含 ..");
            }
        }
        return value;
    }

    private Detection detect(RepoEntity repo) {
        if (repo == null) {
            return new Detection("GENERIC", "未选择仓库，使用通用模板");
        }
        Detection detected = detectFromGitea(repo);
        if (detected != null) {
            return detected;
        }
        String key = repo.getName() == null ? "" : repo.getName().toLowerCase(Locale.ROOT);
        if (FRONTEND_HINTS.stream().anyMatch(key::contains)) {
            return new Detection("NODE", "按仓库名推断为前端项目");
        }
        if (BACKEND_HINTS.stream().anyMatch(key::contains)) {
            return new Detection("MAVEN", "按仓库名推断为后端项目");
        }
        return new Detection("GENERIC", "未识别到构建文件，使用通用模板");
    }

    /** 通过 Gitea 内容接口读取仓库根目录文件；未配置或读取失败时返回 null 交给名称推断。 */
    private Detection detectFromGitea(RepoEntity repo) {
        if (!giteaClient.isConfigured() || !"GITEA".equalsIgnoreCase(repo.getProvider())) {
            return null;
        }
        String[] ownerRepo = splitOwnerRepo(repo.getCloneUrl());
        if (ownerRepo == null) {
            return null;
        }
        try {
            if (giteaClient.fileExists(ownerRepo[0], ownerRepo[1], "package.json", repo.getDefaultBranch())) {
                return new Detection("NODE", "检测到 package.json");
            }
            if (giteaClient.fileExists(ownerRepo[0], ownerRepo[1], "pom.xml", repo.getDefaultBranch())) {
                return new Detection("MAVEN", "检测到 pom.xml");
            }
            for (String file : List.of("build.gradle", "build.gradle.kts")) {
                if (giteaClient.fileExists(ownerRepo[0], ownerRepo[1], file, repo.getDefaultBranch())) {
                    return new Detection("GRADLE", "检测到 " + file);
                }
            }
        } catch (RuntimeException ex) {
            log.debug("探测仓库构建文件失败 repo={}: {}", repo.getId(), ex.getMessage());
        }
        return null;
    }

    private Map<String, Object> definition(String stack, String workDir) {
        return Map.of("steps", switch (stack) {
            case "NODE" -> List.of(
                    step("install", "node:20-alpine",
                            List.of("if [ -f package-lock.json ]; then npm ci; else npm install; fi"), List.of(), workDir),
                    step("build", "node:20-alpine", List.of("npm run build"), List.of("dist", "build"), workDir));
            case "MAVEN" -> List.of(
                    step("build", "maven:3.9-eclipse-temurin-21",
                            List.of("mvn -B -DskipTests package"), List.of("target/*.jar"), workDir));
            case "GRADLE" -> List.of(
                    step("build", "gradle:8.10-jdk21",
                            List.of("gradle build -x test --no-daemon"), List.of("build/libs/*.jar"), workDir));
            default -> List.of(step("smoke", "alpine:3.20", List.of("ls -la"), List.of(), workDir));
        });
    }

    private Map<String, Object> step(String name, String image, List<String> commands, List<String> artifacts,
                                     String workDir) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", name);
        step.put("image", image);
        step.put("commands", commands);
        step.put("workDir", workDir);
        step.put("artifacts", artifacts);
        return step;
    }

    /** cloneUrl -> [owner, repo]，支持 http(s)://host/owner/repo(.git)。 */
    private String[] splitOwnerRepo(String cloneUrl) {
        if (!StringUtils.hasText(cloneUrl)) {
            return null;
        }
        String path = cloneUrl.trim().replaceAll("\\.git$", "");
        int schemaEnd = path.indexOf("://");
        if (schemaEnd >= 0) {
            path = path.substring(schemaEnd + 3);
        }
        int slash = path.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String[] parts = path.substring(slash + 1).split("/");
        return parts.length < 2 ? null : new String[]{parts[parts.length - 2], parts[parts.length - 1]};
    }

    private String normalizeStack(String stack) {
        if (!StringUtils.hasText(stack) || "AUTO".equalsIgnoreCase(stack.trim())) {
            return null;
        }
        String value = stack.trim().toUpperCase(Locale.ROOT);
        if (!STACKS.contains(value)) {
            throw BizException.badRequest("不支持的模板类型: " + stack);
        }
        return value;
    }

    private String normalizeSide(String side) {
        if (!StringUtils.hasText(side)) {
            return null;
        }
        try {
            return PipelineSide.valueOf(side.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("所属端只能是 FRONTEND 或 BACKEND");
        }
    }

    private String label(String stack) {
        return switch (stack) {
            case "NODE" -> "前端 Node";
            case "MAVEN" -> "后端 Maven";
            case "GRADLE" -> "后端 Gradle";
            default -> "通用";
        };
    }

    private String toJson(Map<String, Object> definition) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(definition);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("生成流水线模板失败", ex);
        }
    }

    private record Detection(String stack, String source) {
    }
}
