package com.snail.platform.worker.executor;

import com.snail.platform.common.build.BuildStep;
import com.snail.platform.common.model.ExecutorType;
import com.snail.platform.worker.build.BuildContext;
import com.snail.platform.worker.build.LogSink;
import com.snail.platform.worker.k8s.K8sClientFactory;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * 基于 Kubernetes Job 的执行器。
 * <p>
 * 一个 Job 内用 initContainer 顺序执行 clone 与各步骤（不同步骤可使用不同镜像），
 * 最后的主容器把 /workspace 内声明的产物打包为 tar.gz 并以 base64 输出到 Pod 日志，
 * worker 轮询 Job 完成后读取日志还原产物。因此产物总体积受 Pod 日志上限约束。
 */
@Component
public class KubernetesBuildExecutor implements BuildExecutor {

    private static final String WORKSPACE = "/workspace";

    private final K8sClientFactory clientFactory;
    private final String gitImage;
    private final String toolImage;
    private final String workspaceSize;
    private final long pollIntervalSeconds;
    private final long timeoutSeconds;

    public KubernetesBuildExecutor(K8sClientFactory clientFactory,
                                   @Value("${platform.k8s.git-image:alpine/git:latest}") String gitImage,
                                   @Value("${platform.k8s.tool-image:alpine:3.20}") String toolImage,
                                   @Value("${platform.k8s.workspace-size:1Gi}") String workspaceSize,
                                   @Value("${platform.k8s.poll-interval-seconds:3}") long pollIntervalSeconds,
                                   @Value("${platform.k8s.timeout-seconds:1800}") long timeoutSeconds) {
        this.clientFactory = clientFactory;
        this.gitImage = gitImage;
        this.toolImage = toolImage;
        this.workspaceSize = workspaceSize;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ExecutorType type() {
        return ExecutorType.K8S;
    }

    @Override
    public void execute(BuildContext context, LogSink sink) throws Exception {
        if (!clientFactory.isConfigured()) {
            throw new IllegalStateException(
                    "Kubernetes 未配置 kubeconfig（platform.k8s.kubeconfig / KUBECONFIG / ~/.kube/config），无法使用 K8S 执行器");
        }
        if (context.getRepoUrl() == null || context.getRepoUrl().isBlank()) {
            throw new IllegalStateException("仓库未绑定 cloneUrl，无法执行构建");
        }
        String namespace = clientFactory.namespace();
        String pvcName = "build-workspace-" + context.getRunId();
        String jobName = "build-run-" + context.getRunId();
        List<BuildStep> steps = context.getSteps() == null ? List.of() : context.getSteps();

        try (KubernetesClient client = clientFactory.create()) {
            ensurePvc(client, namespace, pvcName);
            try {
                Job job = buildJob(context, steps, namespace, pvcName, jobName);
                client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
                sink.line("[k8s] 已创建 Job " + jobName + "（namespace=" + namespace + "）");
                boolean success = waitForJob(client, namespace, jobName);
                Pod pod = findPod(client, namespace, context.getRunId());
                dumpLogs(client, namespace, pod, steps.size(), sink);
                if (!success) {
                    throw new IllegalStateException("Kubernetes Job 执行失败，请查看构建日志");
                }
                extractArtifacts(client, namespace, pod, context, sink);
                sink.line("[k8s] Job 执行完成");
            } finally {
                deleteQuietly(client, namespace, jobName, pvcName);
            }
        }
    }

    private void ensurePvc(KubernetesClient client, String namespace, String pvcName) {
        PersistentVolumeClaim existing = client.persistentVolumeClaims()
                .inNamespace(namespace).withName(pvcName).get();
        if (existing != null) {
            return;
        }
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(pvcName)
                .withNamespace(namespace)
                .addToLabels("app", "platform-build")
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity(workspaceSize))
                .endResources()
                .endSpec()
                .build();
        client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();
    }

    private Job buildJob(BuildContext context, List<BuildStep> steps, String namespace,
                         String pvcName, String jobName) {
        List<Container> initContainers = new ArrayList<>();
        initContainers.add(cloneContainer(context));
        for (int i = 0; i < steps.size(); i++) {
            initContainers.add(stepContainer(context, steps.get(i), i));
        }
        Container export = new ContainerBuilder()
                .withName("export")
                .withImage(toolImage)
                .withWorkingDir(WORKSPACE)
                .withCommand("sh", "-lc")
                .withArgs(exportScript(steps))
                .addNewVolumeMount().withName("workspace").withMountPath(WORKSPACE).endVolumeMount()
                .build();
        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withNamespace(namespace)
                .addToLabels("app", "platform-build")
                .addToLabels("platform-build-run", String.valueOf(context.getRunId()))
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", "platform-build")
                .addToLabels("platform-build-run", String.valueOf(context.getRunId()))
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewVolume()
                .withName("workspace")
                .withNewPersistentVolumeClaim().withClaimName(pvcName).endPersistentVolumeClaim()
                .endVolume()
                .withInitContainers(initContainers)
                .withContainers(export)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private Container cloneContainer(BuildContext context) {
        boolean hasToken = context.getRepoToken() != null && !context.getRepoToken().isBlank();
        String repoUrl = context.getRepoUrl();
        String cloneTarget;
        if (hasToken && repoUrl != null && repoUrl.regionMatches(true, 0, "http", 0, 4)) {
            // 令牌通过环境变量注入，不写进 Job 定义，也不出现在日志中。
            String hostAndPath = repoUrl.substring(repoUrl.indexOf("://") + 3);
            cloneTarget = "\"https://${GIT_USERNAME}:${GIT_TOKEN}@" + hostAndPath + "\"";
        } else {
            cloneTarget = shellQuote(repoUrl);
        }
        StringBuilder script = new StringBuilder("git clone --depth 1 ");
        if (context.getBranch() != null && !context.getBranch().isBlank()) {
            script.append("--branch ").append(shellQuote(context.getBranch())).append(' ');
        }
        script.append(cloneTarget).append(' ').append(WORKSPACE);
        if (context.getCommitSha() != null && !context.getCommitSha().isBlank()) {
            script.append(" && cd ").append(WORKSPACE)
                    .append(" && (git checkout ").append(shellQuote(context.getCommitSha()))
                    .append(" || true)");
        }
        ContainerBuilder builder = new ContainerBuilder()
                .withName("clone")
                .withImage(gitImage)
                .withWorkingDir(WORKSPACE)
                .withCommand("sh", "-lc")
                .withArgs(script.toString())
                .addNewEnv().withName("GIT_TERMINAL_PROMPT").withValue("0").endEnv();
        if (hasToken) {
            String username = context.getRepoUsername() == null || context.getRepoUsername().isBlank()
                    ? "git" : context.getRepoUsername().trim();
            builder.addNewEnv().withName("GIT_USERNAME").withValue(username).endEnv()
                    .addNewEnv().withName("GIT_TOKEN").withValue(context.getRepoToken().trim()).endEnv();
        }
        return builder
                .addNewVolumeMount().withName("workspace").withMountPath(WORKSPACE).endVolumeMount()
                .build();
    }

    private Container stepContainer(BuildContext context, BuildStep step, int index) {
        String workDir = step.getWorkDir() == null || step.getWorkDir().isBlank()
                ? WORKSPACE : step.getWorkDir();
        if (!workDir.equals(WORKSPACE) && !workDir.startsWith(WORKSPACE + "/")) {
            throw new IllegalStateException("K8S 执行器要求步骤 " + step.getName()
                    + " 的 workDir 位于 " + WORKSPACE + " 之下，当前为 " + workDir);
        }
        ContainerBuilder builder = new ContainerBuilder()
                .withName("step-" + (index + 1))
                .withImage(step.getImage())
                .withWorkingDir(workDir)
                .withCommand("sh", "-lc")
                .withArgs(String.join("\n", step.getCommands()))
                .addNewVolumeMount().withName("workspace").withMountPath(WORKSPACE).endVolumeMount();
        context.getSecrets().forEach((name, value) ->
                builder.addNewEnv().withName(name).withValue(value).endEnv());
        return builder.build();
    }

    /**
     * 主容器脚本：把各步骤声明的产物复制到 /tmp/collect/step-N，
     * 再打包为 tar.gz 并 base64 输出到 Pod 日志。
     */
    private String exportScript(List<BuildStep> steps) {
        StringBuilder script = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < steps.size(); i++) {
            BuildStep step = steps.get(i);
            if (step.getArtifacts() == null || step.getArtifacts().isEmpty()) {
                continue;
            }
            List<String> paths = new ArrayList<>();
            String base = relativeWorkDir(step);
            for (String pattern : step.getArtifacts()) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                paths.add(base.isEmpty() ? pattern.trim() : base + "/" + pattern.trim());
            }
            if (paths.isEmpty()) {
                continue;
            }
            any = true;
            script.append("for f in ").append(String.join(" ", paths)).append("; do\n")
                    .append("  if [ -f \"$f\" ]; then\n")
                    .append("    d=\"/tmp/collect/step-").append(i + 1).append("/$(dirname \"$f\")\"\n")
                    .append("    mkdir -p \"$d\" && cp \"$f\" \"$d/\"\n")
                    .append("  fi\n")
                    .append("done\n");
        }
        if (!any) {
            return "echo ''";
        }
        script.append("if [ -d /tmp/collect ]; then cd /tmp/collect && tar czf - . | base64 | tr -d '\\n'; fi");
        return script.toString();
    }

    private String relativeWorkDir(BuildStep step) {
        String workDir = step.getWorkDir() == null || step.getWorkDir().isBlank()
                ? WORKSPACE : step.getWorkDir();
        if (WORKSPACE.equals(workDir)) {
            return "";
        }
        String relative = workDir.substring(WORKSPACE.length());
        return relative.startsWith("/") ? relative.substring(1) : relative;
    }

    private boolean waitForJob(KubernetesClient client, String namespace, String jobName)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
            if (job == null) {
                throw new IllegalStateException("Kubernetes Job 不存在: " + jobName);
            }
            if (job.getStatus() != null) {
                Integer succeeded = job.getStatus().getSucceeded();
                Integer failed = job.getStatus().getFailed();
                if (succeeded != null && succeeded > 0) {
                    return true;
                }
                if (failed != null && failed > 0) {
                    return false;
                }
            }
            Thread.sleep(pollIntervalSeconds * 1000);
        }
        throw new IllegalStateException("等待 Kubernetes Job 完成超时（" + timeoutSeconds + " 秒）");
    }

    private Pod findPod(KubernetesClient client, String namespace, Long runId) {
        List<Pod> pods = client.pods().inNamespace(namespace)
                .withLabel("platform-build-run", String.valueOf(runId))
                .list().getItems();
        if (pods.isEmpty()) {
            throw new IllegalStateException("未找到构建 Pod");
        }
        return pods.get(pods.size() - 1);
    }

    private void dumpLogs(KubernetesClient client, String namespace, Pod pod, int stepCount, LogSink sink) {
        String podName = pod.getMetadata().getName();
        List<String> containers = new ArrayList<>();
        containers.add("clone");
        for (int i = 1; i <= stepCount; i++) {
            containers.add("step-" + i);
        }
        for (String container : containers) {
            try {
                String log = client.pods().inNamespace(namespace).withName(podName)
                        .inContainer(container).getLog();
                if (log != null && !log.isBlank()) {
                    sink.line("[k8s] === " + container + " ===");
                    for (String line : log.split("\\R")) {
                        sink.line(line);
                    }
                }
            } catch (Exception ex) {
                sink.line("[k8s] 无法读取容器 " + container + " 日志: " + ex.getMessage());
            }
        }
    }

    private void extractArtifacts(KubernetesClient client, String namespace, Pod pod,
                                  BuildContext context, LogSink sink) {
        String podName = pod.getMetadata().getName();
        String log;
        try {
            log = client.pods().inNamespace(namespace).withName(podName)
                    .inContainer("export").getLog();
        } catch (Exception ex) {
            sink.line("[k8s] 警告: 无法读取产物导出日志: " + ex.getMessage());
            return;
        }
        if (log == null || log.isBlank()) {
            return;
        }
        String encoded = log.replaceAll("\\s", "");
        if (encoded.isEmpty()) {
            return;
        }
        try {
            byte[] gzip = Base64.getDecoder().decode(encoded);
            int count = untar(gzip, context.getArtifactDir());
            sink.line("[k8s] 已取回产物文件数: " + count);
        } catch (Exception ex) {
            sink.line("[k8s] 警告: 解析产物失败（可能超过 Pod 日志上限）: " + ex.getMessage());
        }
    }

    private int untar(byte[] gzip, Path artifactDir) throws IOException {
        int count = 0;
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip));
             TarArchiveInputStream tar = new TarArchiveInputStream(input)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isFile()) {
                    continue;
                }
                String name = entry.getName();
                while (name.startsWith("./")) {
                    name = name.substring(2);
                }
                if (name.isEmpty()) {
                    continue;
                }
                Path target = artifactDir.resolve(name).normalize();
                if (!target.startsWith(artifactDir)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    private void deleteQuietly(KubernetesClient client, String namespace, String jobName, String pvcName) {
        try {
            client.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
        } catch (Exception ignored) {
            // 清理失败不影响构建结果
        }
        try {
            client.persistentVolumeClaims().inNamespace(namespace).withName(pvcName).delete();
        } catch (Exception ignored) {
            // 同上
        }
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
