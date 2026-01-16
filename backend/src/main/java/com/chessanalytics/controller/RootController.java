package com.chessanalytics.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
    
    // Explicit OPTIONS handler (Render needs this)
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }

}