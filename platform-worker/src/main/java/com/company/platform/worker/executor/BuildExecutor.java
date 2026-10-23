package com.company.platform.worker.executor;

import com.company.platform.common.model.ExecutorType;
import com.company.platform.worker.build.BuildContext;
import com.company.platform.worker.build.LogSink;

/** 构建执行器。实现类负责克隆代码、执行步骤，并在成功时把产物放入 ctx.artifactDir。 */
public interface BuildExecutor {

    ExecutorType type();

    void execute(BuildContext context, LogSink sink) throws Exception;
}
