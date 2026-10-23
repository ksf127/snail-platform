package com.company.platform.control;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.company.platform.control.mapper")
@EnableAsync
public class ControlApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlApplication.class, args);
    }
}
