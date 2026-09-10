package com.tokenforge.api.controllers;

import com.tokenforge.api.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final CloudinaryService cloudinaryService;

    @PostMapping("/signature")
    public ResponseEntity<Map<String, Object>> signature() {
        return ResponseEntity.ok(cloudinaryService.buildSignedUploadParams());
    }
}
