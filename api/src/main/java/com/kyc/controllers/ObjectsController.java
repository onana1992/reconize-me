package com.kyc.controllers;

import com.kyc.ports.ObjectStoragePort;
import com.kyc.services.MediaQuality;
import com.kyc.web.ApiException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/v1/objects")
public class ObjectsController {

    private final ObjectStoragePort objectStorage;

    public ObjectsController(ObjectStoragePort objectStorage) {
        this.objectStorage = objectStorage;
    }

    @PutMapping
    public void put(
            @RequestParam String key,
            @RequestParam long exp,
            @RequestParam String sig,
            @RequestParam String method,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        if (!"PUT".equalsIgnoreCase(method) || !objectStorage.verifySignature("PUT", key, exp, sig)) {
            throw ApiException.notFound("Object not found");
        }
        byte[] body = request.getInputStream().readAllBytes();
        String contentType = MediaQuality.sniff(body);
        if (contentType == null) {
            throw ApiException.validation("Unsupported media type", java.util.List.of());
        }
        objectStorage.write(key, body, contentType);
        response.setStatus(204);
    }

    @GetMapping
    public void get(
            @RequestParam String key,
            @RequestParam long exp,
            @RequestParam String sig,
            @RequestParam String method,
            HttpServletResponse response)
            throws IOException {
        if (!"GET".equalsIgnoreCase(method) || !objectStorage.verifySignature("GET", key, exp, sig)) {
            throw ApiException.notFound("Object not found");
        }
        if (!objectStorage.exists(key)) {
            throw ApiException.notFound("Object not found");
        }
        byte[] body = objectStorage.read(key);
        String type = MediaQuality.sniff(body);
        response.setContentType(type == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : type);
        response.setHeader("Cache-Control", "private, max-age=60");
        response.getOutputStream().write(body);
    }
}
