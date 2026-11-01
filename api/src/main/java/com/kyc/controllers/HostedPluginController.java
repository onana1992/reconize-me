package com.kyc.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HostedPluginController {

    private static final MediaType JAVASCRIPT = MediaType.parseMediaType("text/javascript;charset=UTF-8");

    private final String source;

    public HostedPluginController() throws IOException {
        this.source = new ClassPathResource("plugin.js").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping("/v1/plugin.js")
    public ResponseEntity<String> plugin() {
        return ResponseEntity.ok()
                .contentType(JAVASCRIPT)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(source);
    }
}
