package com.snail.platform.control.integration;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 解析 kubeconfig。未配置时不抛异常，由调用方决定走 PREVIEW 模式。
 */
@Slf4j
@Component
public class K8sClientFactory {

    private final String configuredKubeconfig;
    private final String namespace;

    public K8sClientFactory(@Value("${platform.k8s.kubeconfig:}") String configuredKubeconfig,
                            @Value("${platform.k8s.namespace:platform}") String namespace) {
        this.configuredKubeconfig = configuredKubeconfig;
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }

    public boolean isConfigured() {
        return resolveKubeconfig() != null;
    }

    public KubernetesClient create() {
        Path path = resolveKubeconfig();
        if (path == null) {
            throw new IllegalStateException("Kubernetes 未配置：请设置 platform.k8s.kubeconfig 或 KUBECONFIG 环境变量，"
                    + "或放置 ~/.kube/config");
        }
        try {
            Config config = Config.fromKubeconfig(Files.readString(path));
            if (config.getNamespace() == null || config.getNamespace().isBlank()) {
                config.setNamespace(namespace);
            }
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception ex) {
            throw new IllegalStateException("加载 kubeconfig 失败: " + ex.getMessage(), ex);
        }
    }

    private Path resolveKubeconfig() {
        if (configuredKubeconfig != null && !configuredKubeconfig.isBlank()) {
            Path path = Path.of(configuredKubeconfig);
            return Files.isRegularFile(path) ? path : null;
        }
        String env = System.getenv("KUBECONFIG");
        if (env != null && !env.isBlank()) {
            for (String part : env.split(java.io.File.pathSeparator)) {
                Path path = Path.of(part);
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
        }
        Path home = Path.of(System.getProperty("user.home"), ".kube", "config");
        return Files.isRegularFile(home) ? home : null;
    }
}
