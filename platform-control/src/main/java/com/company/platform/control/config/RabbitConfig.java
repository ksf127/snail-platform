package com.company.platform.control.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 构建任务拓扑。queue 与 dlq 都是 durable，消息持久化由 RabbitTemplate 默认的 deliveryMode 保证。
 */
@Configuration
public class RabbitConfig {

    public static final String BUILD_EXCHANGE = "platform.build";
    public static final String BUILD_QUEUE = "platform.build.queue";
    public static final String BUILD_ROUTING_KEY = "build.run";
    public static final String DLX_EXCHANGE = "platform.build.dlx";
    public static final String DLQ_QUEUE = "platform.build.dlq";
    public static final String DLQ_ROUTING_KEY = "build.run.dlq";

    @Bean
    public DirectExchange buildExchange() {
        return new DirectExchange(BUILD_EXCHANGE, true, false);
    }

    @Bean
    public Queue buildQueue() {
        return QueueBuilder.durable(BUILD_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding buildBinding(Queue buildQueue, DirectExchange buildExchange) {
        return BindingBuilder.bind(buildQueue).to(buildExchange).with(BUILD_ROUTING_KEY);
    }

    @Bean
    public DirectExchange buildDlx() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue buildDlq() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding buildDlqBinding(Queue buildDlq, DirectExchange buildDlx) {
        return BindingBuilder.bind(buildDlq).to(buildDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // 只记录确认失败；控制面在触发时会先落库，消息失败由运维从 DLQ / 未消费运行中排查
                org.slf4j.LoggerFactory.getLogger(RabbitConfig.class)
                        .error("构建消息未被 broker 确认: correlation={}, cause={}",
                                correlationData == null ? null : correlationData.getId(), cause);
            }
        });
        template.setReturnsCallback(returned ->
                org.slf4j.LoggerFactory.getLogger(RabbitConfig.class)
                        .error("构建消息无法路由: exchange={}, routingKey={}, replyText={}",
                                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
