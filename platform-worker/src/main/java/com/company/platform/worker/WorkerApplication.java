package com.company.platform.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 执行面入口。构建消息由 RabbitMQ 驱动，运行状态与制品通过控制面内部接口回报。
 */
@SpringBootApplication
@EnableScheduling
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
