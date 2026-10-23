package com.company.platform.worker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 与 control 侧保持一致的拓扑声明，重复声明是幂等的。
 */
@Configuration
public class WorkerRabbitConfig {

    public static final String BUILD_QUEUE = "platform.build.queue";
    public static final String BUILD_EXCHANGE = "platform.build";
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
}
