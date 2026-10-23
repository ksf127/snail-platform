package com.snail.platform.worker.executor;

import com.snail.platform.common.model.ExecutorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 按流水线声明的执行器类型选择实现，未声明或非法时回退到默认执行器。 */
@Component
public class ExecutorResolver {

    private final Map<ExecutorType, BuildExecutor> executors = new EnumMap<>(ExecutorType.class);
    private final ExecutorType defaultType;

    public ExecutorResolver(List<BuildExecutor> executors,
                            @Value("${platform.executor.default:DOCKER}") String defaultExecutor) {
        for (BuildExecutor executor : executors) {
            this.executors.put(executor.type(), executor);
        }
        this.defaultType = parse(defaultExecutor, ExecutorType.DOCKER);
    }

    public BuildExecutor resolve(String executorType) {
        ExecutorType type = executorType == null || executorType.isBlank()
                ? defaultType
                : parse(executorType, defaultType);
        BuildExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalStateException("执行器未实现: " + type);
        }
        return executor;
    }

    private ExecutorType parse(String value, ExecutorType fallback) {
        try {
            return ExecutorType.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
