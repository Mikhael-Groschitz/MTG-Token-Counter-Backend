package com.tokenforge.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokenforge.api.exceptions.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private static final Set<String> ALLOWED_ATTACHMENT_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif",
            "text/plain", "application/pdf"
    );

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.from-email}")
    private String fromAddress;

    @Value("${app.bugreport-email:}")
    private String bugReportRecipientOverride;

    @Value("${resend.template.verification-id}")
    private String verificationTemplateId;

    @Value("${resend.template.password-reset-id}")
    private String passwordResetTemplateId;

    public void sendVerificationCode(String to, String code, int expiresInMinutes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", to);
        payload.put("template", Map.of(
                "id", verificationTemplateId,
                "variables", Map.of("CODE", code, "EXPIRES_IN_MINUTES", expiresInMinutes)
        ));
        trySend(payload);
    }

    public void sendPasswordResetLink(String to, String resetLink, int expiresInMinutes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", to);
        payload.put("template", Map.of(
                "id", passwordResetTemplateId,
                "variables", Map.of("RESET_URL", resetLink, "EXPIRES_IN_MINUTES", expiresInMinutes)
        ));
        trySend(payload);
    }

    public void sendBugReport(String subject, String body, List<MultipartFile> attachments, String replyTo) {
        String recipient = (bugReportRecipientOverride != null && !bugReportRecipientOverride.isBlank())
                ? bugReportRecipientOverride
                : fromAddress;

        Map<String, Object> payload = basePayload(recipient, subject);
        payload.put("text", body);
        if (replyTo != null && !replyTo.isBlank()) {
            payload.put("reply_to", replyTo.trim());
        }

        if (attachments != null && !attachments.isEmpty()) {
            List<Map<String, String>> attachmentPayload = new ArrayList<>();
            for (MultipartFile file : attachments) {
                Map<String, String> attachment = buildAttachment(file);
                if (attachment != null) {
                    attachmentPayload.add(attachment);
                }
            }
            if (!attachmentPayload.isEmpty()) {
                payload.put("attachments", attachmentPayload);
            }
        }

        try {
            send(payload);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Falha ao enviar bug report por e-mail: {}", e.getMessage(), e);
            throw new BusinessRuleException("Não foi possível enviar seu report agora. Tente novamente em instantes.");
        }
    }

    private Map<String, Object> basePayload(String to, String subject) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", to);
        payload.put("subject", subject);
        return payload;
    }

    private Map<String, String> buildAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException(
                    "Tipo de arquivo não permitido nos anexos. " +
                            "Envie apenas imagens (PNG, JPEG, WEBP, GIF), PDF ou texto simples.");
        }
        try {
            Map<String, String> attachment = new LinkedHashMap<>();
            attachment.put("filename", sanitizeFilename(file.getOriginalFilename()));
            attachment.put("content", Base64.getEncoder().encodeToString(file.getBytes()));
            return attachment;
        } catch (IOException e) {
            throw new BusinessRuleException("Não foi possível ler um dos arquivos anexados.");
        }
    }

    private void trySend(Map<String, Object> payload) {
        try {
            send(payload);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Falha ao enviar e-mail para {}: {}", payload.get("to"), e.getMessage(), e);
        }
    }

    private void send(Map<String, Object> payload) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_API_URL))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            log.error("Resend retornou status {} ao enviar para {}: {}",
                    response.statusCode(), payload.get("to"), response.body());
            throw new IOException("Resend respondeu com status " + response.statusCode());
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "anexo";
        }
        String base = Paths.get(filename).getFileName().toString();
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
