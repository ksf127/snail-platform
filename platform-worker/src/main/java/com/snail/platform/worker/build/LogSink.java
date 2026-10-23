package com.snail.platform.worker.build;

/** 构建日志输出端，Docker 子进程与 K8s Pod 日志最终都写到这里。 */
@FunctionalInterface
public interface LogSink {

    void line(String line);

    default void lines(Iterable<String> lines) {
        for (String line : lines) {
            line(line);
        }
    }
}
