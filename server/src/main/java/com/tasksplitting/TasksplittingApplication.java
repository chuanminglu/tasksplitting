package com.tasksplitting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class TasksplittingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TasksplittingApplication.class, args);
    }

    /** Injectable clock so tests can control "now" without touching static time sources. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
