package com.chessanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ChessAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessAnalyticsApplication.class, args);
    }
}
