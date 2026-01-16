package com.chessanalytics.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public String root() {
        return "Chess Analytics API is running";
    }

    @GetMapping("/favicon.ico")
    public void favicon() {
        // No-op to avoid 500 errors
    }

    @GetMapping("/healthz")
    public String health() {
        return "ok";
    }

}