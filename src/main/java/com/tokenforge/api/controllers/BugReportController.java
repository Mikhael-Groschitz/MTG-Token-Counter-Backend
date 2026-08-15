package com.tokenforge.api.controllers;

import com.tokenforge.api.entities.User;
import com.tokenforge.api.services.BugReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/bugs")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;

    @PostMapping
    public ResponseEntity<Void> submit(
            @RequestParam String title,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String severity,
            @RequestParam String description,
            @RequestParam(required = false) String steps,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String version,
            @RequestParam(value = "reporterEmail", required = false) String reporterEmail,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal User user
    ) {
        bugReportService.submit(title, module, severity, description, steps, environment, version, reporterEmail, files, user);
        return ResponseEntity.ok().build();
    }
}
