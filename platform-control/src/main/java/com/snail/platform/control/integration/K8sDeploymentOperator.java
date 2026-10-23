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

    public void updateImage(String deploymentName, String image, int replicas) {
        try (KubernetesClient client = clientFactory.create()) {
            String namespace = clientFactory.namespace();
            Deployment deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName).get();
            if (deployment == null) {
                throw new IllegalStateException("K8s Deployment 不存在: " + namespace + "/" + deploymentName);
            }
            List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();
            if (containers == null || containers.isEmpty()) {
                throw new IllegalStateException("Deployment 未定义容器: " + namespace + "/" + deploymentName);
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

            client.apps().deployments().inNamespace(namespace).withName(deploymentName)
                    .patch(PatchContext.of(PatchType.STRATEGIC_MERGE), objectMapper.writeValueAsString(patch));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("更新 K8s Deployment 失败: " + ex.getMessage(), ex);
        }
    }
}
