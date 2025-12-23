package com.sjdt.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchedulerJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerJavaApplication.class, args);
    }

}
