package com.tokenforge.api.services;

import com.tokenforge.api.dto.BugReportRequest;
import com.tokenforge.api.entities.User;
import com.tokenforge.api.exceptions.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private static final int MAX_ATTACHMENTS = 5;

    private static final int MAX_SUBJECT_LENGTH = 150;

    private final EmailService emailService;

    public void submit(BugReportRequest request, List<MultipartFile> files, User user) {
        if (files != null && files.size() > MAX_ATTACHMENTS) {
            throw new BusinessRuleException(
                    "Envie no máximo " + MAX_ATTACHMENTS + " arquivos por report."
            );
        }

        String subject = "[TokenForge] Bug report: " + sanitizeSubject(request.title());
        String body = buildBody(request, files, user);
        String replyTo = user != null ? user.getEmail() : request.reporterEmail();

        emailService.sendBugReport(subject, body, files, replyTo);
    }

    private String sanitizeSubject(String title) {
        if (title == null) {
            return "(sem título)";
        }
        String singleLine = title.replaceAll("[\\r\\n]+", " ").trim();
        if (singleLine.isEmpty()) {
            return "(sem título)";
        }
        return singleLine.length() > MAX_SUBJECT_LENGTH
                ? singleLine.substring(0, MAX_SUBJECT_LENGTH) + "…"
                : singleLine;
    }

    private String buildBody(BugReportRequest request, List<MultipartFile> files, User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("Título: ").append(request.title()).append("\n");
        sb.append("Módulo: ").append(orDefault(request.module(), "-")).append("\n");
        sb.append("Severidade: ").append(orDefault(request.severity(), "-")).append("\n");
        sb.append("Data do ocorrido: ").append(orDefault(request.occurredAt(), "-")).append("\n");
        sb.append("Reportado por: ").append(reporterInfo(user, request.reporterEmail())).append("\n\n");
        sb.append("Descrição:\n").append(request.description()).append("\n\n");
        if (request.steps() != null && !request.steps().isBlank()) {
            sb.append("Passos para reproduzir:\n").append(request.steps()).append("\n\n");
        }
        if (files != null && !files.isEmpty()) {
            sb.append("Anexos: ").append(files.size()).append(" arquivo(s) em anexo neste e-mail.\n");
        }
        return sb.toString();
    }

    private String reporterInfo(User user, String reporterEmail) {
        if (user != null) {
            return user.getUsername() + " <" + user.getEmail() + "> (ID: " + user.getId() + ")";
        }
        if (reporterEmail != null && !reporterEmail.isBlank()) {
            return reporterEmail.trim() + " (usuário não autenticado)";
        }
        return "não informado (usuário não autenticado)";
    }

    private String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
