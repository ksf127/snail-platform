package com.snail.platform.control.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class K8sDeploymentOperator {

    private final K8sClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    /** 按环境指定的 namespace 更新镜像与副本；未指定时回退到全局 namespace。 */
    public void updateImage(String namespace, String deploymentName, String image, int replicas) {
        String target = namespace == null || namespace.isBlank() ? clientFactory.namespace() : namespace;
        try (KubernetesClient client = clientFactory.create()) {
            Deployment deployment = client.apps().deployments().inNamespace(target)
                    .withName(deploymentName).get();
            if (deployment == null) {
                throw new IllegalStateException("K8s Deployment 不存在: " + target + "/" + deploymentName);
            }
            List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();
            if (containers == null || containers.isEmpty()) {
                throw new IllegalStateException("Deployment 未定义容器: " + target + "/" + deploymentName);
            }
            Map<String, Object> containerPatch = new LinkedHashMap<>();
            containerPatch.put("name", containers.get(0).getName());
            containerPatch.put("image", image);

            Map<String, Object> podSpec = new LinkedHashMap<>();
            podSpec.put("containers", List.of(containerPatch));
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("spec", podSpec);
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("replicas", replicas);
            spec.put("template", template);
            Map<String, Object> patch = Map.of("spec", spec);

            client.apps().deployments().inNamespace(target).withName(deploymentName)
                    .patch(PatchContext.of(PatchType.STRATEGIC_MERGE), objectMapper.writeValueAsString(patch));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("更新 K8s Deployment 失败: " + ex.getMessage(), ex);
        }
    }
}
