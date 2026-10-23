package com.snail.platform.worker.listener;

import com.snail.platform.common.build.BuildMessage;
import com.snail.platform.worker.config.WorkerRabbitConfig;
import com.snail.platform.worker.service.BuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 构建消息入口。基础设施异常向上抛出，由队列配置转入死信；
 * 构建本身失败已在 BuildService 内落为 FAILED。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuildMessageListener {

    private final BuildService buildService;

    @RabbitListener(queues = WorkerRabbitConfig.BUILD_QUEUE)
    public void onMessage(BuildMessage message) {
        log.info("收到构建消息 runId={} pipelineId={}", message.runId(), message.pipelineId());
        buildService.execute(message);
    }
}
