package com.snail.platform.control.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.build.BuildStep;
import com.snail.platform.common.build.PipelineDefinition;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.integration.RepoFileClient;
import com.snail.platform.control.mapper.RepoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流水线配置即代码：解析 YAML、校验步骤，并在触发时解析出本次构建真正使用的定义。
 * 配置在仓库时，以该分支/提交的 YAML 为准；未配置 configPath 时沿用平台内 JSON 定义。
 */
@Service
@RequiredArgsConstructor
public class PipelineConfigService {

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private final ObjectMapper objectMapper;
    private final RepoMapper repoMapper;
    private final RepoCredentialService repoCredentialService;
    private final RepoFileClient repoFileClient;

    public PipelineDefinition parseYaml(String yaml) {
        if (!StringUtils.hasText(yaml)) {
            throw BizException.badRequest("YAML 内容不能为空");
        }
        PipelineDefinition definition;
        try {
            definition = YAML.readValue(yaml, PipelineDefinition.class);
        } catch (Exception ex) {
            throw BizException.badRequest("YAML 解析失败: " + rootMessage(ex));
        }
        return normalize(definition);
    }

    public String toYaml(String definitionJson) {
        try {
            PipelineDefinition definition = objectMapper.readValue(definitionJson, PipelineDefinition.class);
            return YAML.writeValueAsString(definition);
        } catch (Exception ex) {
            throw BizException.badRequest("流水线定义无法转换为 YAML: " + rootMessage(ex));
        }
    }

    public String toYaml(PipelineDefinition definition) {
        try {
            return YAML.writeValueAsString(definition);
        } catch (Exception ex) {
            throw BizException.badRequest("流水线定义无法转换为 YAML: " + rootMessage(ex));
        }
    }

    public String writeJson(PipelineDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (Exception ex) {
            throw BizException.badRequest("流水线定义序列化失败");
        }
    }

    public PipelineDefinition readJson(String definitionJson) {
        if (!StringUtils.hasText(definitionJson)) {
            return new PipelineDefinition();
        }
        try {
            return objectMapper.readValue(definitionJson, PipelineDefinition.class);
        } catch (Exception ex) {
            throw BizException.badRequest("流水线定义无法解析: " + rootMessage(ex));
        }
    }

    /** 校验并补全默认值：image/commands 必填，workDir/artifacts/name 自动补全。 */
    public PipelineDefinition normalize(PipelineDefinition definition) {
        if (definition == null) {
            throw BizException.badRequest("流水线定义为空");
        }
        if (definition.getSteps() == null || definition.getSteps().isEmpty()) {
            throw BizException.badRequest("流水线至少需要一个 step");
        }
        List<BuildStep> steps = definition.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            BuildStep step = steps.get(i);
            if (step == null) {
                throw BizException.badRequest("第 " + (i + 1) + " 个 step 为空");
            }
            if (!StringUtils.hasText(step.getImage())) {
                throw BizException.badRequest("第 " + (i + 1) + " 个 step 缺少 image");
            }
            if (step.getCommands() == null || step.getCommands().isEmpty()) {
                throw BizException.badRequest("第 " + (i + 1) + " 个 step 缺少 commands");
            }
            if (!StringUtils.hasText(step.getName())) {
                step.setName("step-" + (i + 1));
            }
            if (!StringUtils.hasText(step.getWorkDir())) {
                step.setWorkDir("/workspace");
            }
            if (step.getArtifacts() == null) {
                step.setArtifacts(List.of());
            }
        }
        return definition;
    }

    /**
     * 解析本次构建实际使用的定义。
     * 配置在仓库时优先使用 commitSha（保证与该次提交一致），否则使用分支最新内容。
     */
    public Resolved resolve(PipelineEntity pipeline, String branch, String commitSha) {
        if (!StringUtils.hasText(pipeline.getConfigPath())) {
            return new Resolved(normalize(readJson(pipeline.getDefinitionJson())),
                    pipeline.getDefinitionJson(), "平台定义");
        }
        RepoEntity repo = pipeline.getRepoId() == null ? null : repoMapper.selectById(pipeline.getRepoId());
        if (repo == null) {
            throw BizException.badRequest("流水线配置指向仓库文件，但未绑定仓库");
        }
        String ref = StringUtils.hasText(commitSha) ? commitSha.trim()
                : (StringUtils.hasText(branch) ? branch.trim() : pipeline.getBranch());
        RepoCredentialService.Credential credential = repoCredentialService.find(repo.getId());
        String token = credential == null ? null : credential.token();
        String yaml = repoFileClient.fetch(repo, ref, pipeline.getConfigPath(), token);
        PipelineDefinition definition = parseYaml(yaml);
        return new Resolved(definition, writeJson(definition),
                "仓库 " + pipeline.getConfigPath() + "@" + ref);
    }

    public String normalizeConfigPath(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String path = raw.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isBlank()) {
            return null;
        }
        if (path.contains("..")) {
            throw BizException.badRequest("配置路径不能包含 ..");
        }
        if (path.length() > 255) {
            throw BizException.badRequest("配置路径不能超过 255 个字符");
        }
        return path;
    }

    private String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? ex.getClass().getSimpleName() : cause.getMessage();
    }

    public record Resolved(PipelineDefinition definition, String json, String source) {
    }
}
