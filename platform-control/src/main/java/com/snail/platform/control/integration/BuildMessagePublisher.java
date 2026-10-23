package com.snail.platform.control.integration;

import com.snail.platform.common.build.BuildMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static com.snail.platform.control.config.RabbitConfig.BUILD_EXCHANGE;
import static com.snail.platform.control.config.RabbitConfig.BUILD_ROUTING_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuildMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(BuildMessage message) {
        CorrelationData correlationData = new CorrelationData("run-" + message.runId());
        rabbitTemplate.convertAndSend(BUILD_EXCHANGE, BUILD_ROUTING_KEY, message, m -> {
            m.getMessageProperties().setMessageId(String.valueOf(message.runId()));
            m.getMessageProperties().setContentType("application/json");
            return m;
        }, correlationData);
        log.info("已投递构建消息 runId={}, pipelineId={}", message.runId(), message.pipelineId());
    }
}
