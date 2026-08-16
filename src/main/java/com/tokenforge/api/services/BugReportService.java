package com.tokenforge.api.services;

import com.tokenforge.api.entities.User;
import com.tokenforge.api.exceptions.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int TEXT_FIELD_MAX_LENGTH = 5000;

    private final EmailService emailService;

    public void submit(
            String title,
            String module,
            String severity,
            String description,
            String steps,
            String occurredAt,
            String reporterEmail,
            List<MultipartFile> files,
            User user
    ) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("O título é obrigatório.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleException("A descrição é obrigatória.");
        }
        if (user == null && reporterEmail != null && !reporterEmail.isBlank() && !isValidEmail(reporterEmail.trim())) {
            throw new BusinessRuleException("O e-mail informado é inválido.");
        }

        checkMaxLength("Título", title, TITLE_MAX_LENGTH);
        checkMaxLength("Módulo", module, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Severidade", severity, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Descrição", description, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Passos para reproduzir", steps, TEXT_FIELD_MAX_LENGTH);

        String subject = "[TokenForge] Bug report: " + title;
        String body = buildBody(title, module, severity, description, steps, occurredAt, files, reporterEmail, user);
        String replyTo = user != null ? user.getEmail() : reporterEmail;

        emailService.sendBugReport(subject, body, files, replyTo);
    }

    private String buildBody(
            String title, String module, String severity, String description,
            String steps, String occurredAt, List<MultipartFile> files,
            String reporterEmail, User user
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Título: ").append(title).append("\n");
        sb.append("Módulo: ").append(orDefault(module, "-")).append("\n");
        sb.append("Severidade: ").append(orDefault(severity, "-")).append("\n");
        sb.append("Data do ocorrido: ").append(orDefault(occurredAt, "-")).append("\n");
        sb.append("Reportado por: ").append(reporterInfo(user, reporterEmail)).append("\n\n");
        sb.append("Descrição:\n").append(description).append("\n\n");
        if (steps != null && !steps.isBlank()) {
            sb.append("Passos para reproduzir:\n").append(steps).append("\n\n");
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

    private boolean isValidEmail(String email) {
        int at = email.indexOf('@');
        int dot = email.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < email.length() - 1 && !email.contains(" ");
    }

    private String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private void checkMaxLength(String fieldLabel, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessRuleException(
                    "O campo \"" + fieldLabel + "\" excede o tamanho máximo de " + maxLength + " caracteres.");
        }
    }
}
