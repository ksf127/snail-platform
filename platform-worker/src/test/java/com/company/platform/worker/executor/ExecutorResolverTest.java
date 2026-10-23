package com.company.platform.worker.executor;

import com.company.platform.common.model.ExecutorType;
import com.company.platform.worker.build.BuildContext;
import com.company.platform.worker.build.LogSink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutorResolverTest {

    private final BuildExecutor docker = executor(ExecutorType.DOCKER);
    private final BuildExecutor k8s = executor(ExecutorType.K8S);
    private final ExecutorResolver resolver = new ExecutorResolver(List.of(docker, k8s), "DOCKER");

    @Test
    void resolvesDeclaredExecutor() {
        assertThat(resolver.resolve("K8S")).isSameAs(k8s);
        assertThat(resolver.resolve("docker")).isSameAs(docker);
    }

    @Test
    void fallsBackToDefaultForBlankOrUnknownValue() {
        assertThat(resolver.resolve(null)).isSameAs(docker);
        assertThat(resolver.resolve("")).isSameAs(docker);
        assertThat(resolver.resolve("not-an-executor")).isSameAs(docker);
    }

    @Test
    void failsWhenExecutorNotImplemented() {
        assertThatThrownBy(() -> resolver.resolve("JENKINS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JENKINS");
    }

    private static BuildExecutor executor(ExecutorType type) {
        return new BuildExecutor() {
            @Override
            public ExecutorType type() {
                return type;
            }

            @Override
            public void execute(BuildContext context, LogSink sink) {
                // 测试替身，不做任何事
            }
        };
    }
}
