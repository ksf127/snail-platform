package com.snail.platform.worker.build;

import com.snail.platform.common.build.BuildStep;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 按步骤声明的 glob 收集产物，复制到 artifactDir/step-N 下。
 * glob 相对于该步骤的 workDir（默认 /workspace，即源码根目录），例如 target/*.jar；
 * 单仓场景下 workDir=/workspace/web 时，dist 即 web/dist。
 */
@Component
public class ArtifactCollector {

    private static final String WORKSPACE = "/workspace";

    public List<Path> collect(List<BuildStep> steps, Path sourceDir, Path artifactDir, LogSink log)
            throws IOException {
        List<Path> collected = new ArrayList<>();
        if (steps == null || steps.isEmpty()) {
            return collected;
        }
        Map<Path, Path> copies = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            BuildStep step = steps.get(i);
            if (step.getArtifacts() == null || step.getArtifacts().isEmpty()) {
                continue;
            }
            Path stepDir = artifactDir.resolve("step-" + (i + 1));
            Path base = stepBase(sourceDir, step);
            for (String pattern : step.getArtifacts()) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                for (Path source : match(base, pattern.trim())) {
                    Path relative = sourceDir.relativize(source);
                    Path target = stepDir.resolve(relative.toString());
                    copies.putIfAbsent(source, target);
                }
            }
        }
        for (Map.Entry<Path, Path> entry : copies.entrySet()) {
            Path target = entry.getValue();
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(entry.getKey(), target, StandardCopyOption.REPLACE_EXISTING);
            collected.add(target);
            log.line("[artifact] " + sourceDir.relativize(entry.getKey()).toString().replace(File.separatorChar, '/'));
        }
        return collected;
    }

    /** 步骤 workDir 对应的源码目录；workDir 越界时退回源码根目录。 */
    private Path stepBase(Path sourceDir, BuildStep step) {
        String workDir = step.getWorkDir();
        if (workDir == null || workDir.isBlank() || WORKSPACE.equals(workDir)) {
            return sourceDir;
        }
        String relative = workDir.startsWith(WORKSPACE + "/")
                ? workDir.substring(WORKSPACE.length() + 1) : workDir;
        Path resolved = sourceDir.resolve(relative).normalize();
        return resolved.startsWith(sourceDir) ? resolved : sourceDir;
    }

    private List<Path> match(Path base, String pattern) throws IOException {
        String cleaned = pattern.startsWith("./") ? pattern.substring(2) : pattern;
        Path direct = base.resolve(cleaned);
        List<Path> matches = new ArrayList<>();
        if (!containsGlob(cleaned) && Files.isDirectory(direct)) {
            try (Stream<Path> walk = Files.walk(direct)) {
                walk.filter(Files::isRegularFile).forEach(matches::add);
            }
            return matches;
        }
        String glob = "glob:" + cleaned.replace('/', File.separatorChar);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob);
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(base.relativize(path)))
                    .forEach(matches::add);
        }
        return matches;
    }

    private boolean containsGlob(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0
                || pattern.indexOf('[') >= 0 || pattern.indexOf('{') >= 0;
    }
}
