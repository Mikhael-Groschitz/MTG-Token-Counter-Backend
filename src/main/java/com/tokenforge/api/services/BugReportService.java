package com.tokenforge.api.services;

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
            String environment,
            String version,
            List<MultipartFile> files
    ) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("O título é obrigatório.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleException("A descrição é obrigatória.");
        }

        checkMaxLength("Título", title, TITLE_MAX_LENGTH);
        checkMaxLength("Módulo", module, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Severidade", severity, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Descrição", description, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Passos para reproduzir", steps, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Ambiente", environment, TEXT_FIELD_MAX_LENGTH);
        checkMaxLength("Versão", version, TEXT_FIELD_MAX_LENGTH);

        String subject = "[TokenForge] Bug report: " + title;
        String body = buildBody(title, module, severity, description, steps, environment, version, files);

        emailService.sendBugReport(subject, body, files);
    }

    private String buildBody(
            String title, String module, String severity, String description,
            String steps, String environment, String version, List<MultipartFile> files
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Título: ").append(title).append("\n");
        sb.append("Módulo: ").append(orDefault(module, "-")).append("\n");
        sb.append("Severidade: ").append(orDefault(severity, "-")).append("\n");
        sb.append("Ambiente: ").append(orDefault(environment, "-")).append("\n");
        sb.append("Versão: ").append(orDefault(version, "-")).append("\n\n");
        sb.append("Descrição:\n").append(description).append("\n\n");
        if (steps != null && !steps.isBlank()) {
            sb.append("Passos para reproduzir:\n").append(steps).append("\n\n");
        }
        if (files != null && !files.isEmpty()) {
            sb.append("Anexos: ").append(files.size()).append(" arquivo(s) em anexo neste e-mail.\n");
        }
        return sb.toString();
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
