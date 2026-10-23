package com.company.platform.worker.k8s;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * kubeconfig 解析顺序：platform.k8s.kubeconfig → KUBECONFIG → ~/.kube/config。
 */
@Component
public class K8sClientFactory {

    private final String configuredKubeconfig;
    private final String namespace;

    public K8sClientFactory(@Value("${platform.k8s.kubeconfig:}") String configuredKubeconfig,
                            @Value("${platform.k8s.namespace:platform}") String namespace) {
        this.configuredKubeconfig = configuredKubeconfig == null ? "" : configuredKubeconfig.trim();
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }

    public boolean isConfigured() {
        return resolveKubeconfig() != null;
    }

    public KubernetesClient create() {
        Path kubeconfig = resolveKubeconfig();
        if (kubeconfig == null) {
            throw new IllegalStateException(
                    "Kubernetes 未配置 kubeconfig（platform.k8s.kubeconfig / KUBECONFIG / ~/.kube/config）");
        }
        try {
            Config config = Config.fromKubeconfig(Files.readString(kubeconfig));
            if (config.getNamespace() == null || config.getNamespace().isBlank()) {
                config.setNamespace(namespace);
            }
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception ex) {
            throw new IllegalStateException("加载 kubeconfig 失败: " + kubeconfig + " - " + ex.getMessage(), ex);
        }
    }

    private Path resolveKubeconfig() {
        if (!configuredKubeconfig.isBlank()) {
            Path path = Paths.get(configuredKubeconfig);
            return Files.isRegularFile(path) ? path : null;
        }
        String env = System.getenv("KUBECONFIG");
        if (env != null && !env.isBlank()) {
            for (String part : env.split(java.io.File.pathSeparator)) {
                Path path = Paths.get(part.trim());
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
        }
        Path home = Paths.get(System.getProperty("user.home", "."), ".kube", "config");
        return Files.isRegularFile(home) ? home : null;
    }
}
