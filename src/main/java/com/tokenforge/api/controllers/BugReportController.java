package com.tokenforge.api.controllers;

import com.tokenforge.api.dto.BugReportRequest;
import com.tokenforge.api.entities.User;
import com.tokenforge.api.services.BugReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
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
            @Valid @ModelAttribute BugReportRequest request,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal User user
    ) {
        bugReportService.submit(request, files, user);
        return ResponseEntity.ok().build();
    }
}
