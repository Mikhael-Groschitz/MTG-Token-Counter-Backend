package com.tokenforge.api.services;

import com.tokenforge.api.exceptions.BusinessRuleException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final Set<String> ALLOWED_ATTACHMENT_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif",
            "text/plain", "application/pdf"
    );

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.bugreport-email:}")
    private String bugReportRecipientOverride;

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("TokenForge — Confirme seu e-mail");
        message.setText(
                "Bem-vindo à Forja!\n\n" +
                        "Use o código abaixo para verificar seu e-mail:\n\n" +
                        code + "\n\n" +
                        "Este código expira em 15 minutos. Se você não criou uma conta, ignore este e-mail."
        );
        trySend(message);
    }

    public void sendPasswordResetLink(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("TokenForge — Recuperação de senha");
        message.setText(
                "Recebemos uma solicitação para redefinir sua senha.\n\n" +
                        "Clique no link abaixo para escolher uma nova senha:\n\n" +
                        resetLink + "\n\n" +
                        "Este link expira em 30 minutos. Se você não solicitou isso, ignore este e-mail."
        );
        trySend(message);
    }

    private void trySend(SimpleMailMessage message) {
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Falha ao enviar e-mail para {}: {}", message.getTo(), e.getMessage(), e);
        }
    }

    public void sendBugReport(String subject, String body, List<MultipartFile> attachments, String replyTo) {
        String recipient = (bugReportRecipientOverride != null && !bugReportRecipientOverride.isBlank())
                ? bugReportRecipientOverride
                : fromAddress;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo.trim());
            }

            if (attachments != null) {
                for (MultipartFile file : attachments) {
                    attachIfValid(helper, file);
                }
            }

            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("Falha ao enviar bug report por e-mail: {}", e.getMessage(), e);
            throw new BusinessRuleException("Não foi possível enviar seu report agora. Tente novamente em instantes.");
        }
    }

    private void attachIfValid(MimeMessageHelper helper, MultipartFile file) throws MessagingException {
        if (file == null || file.isEmpty()) {
            return;
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException(
                    "Tipo de arquivo não permitido nos anexos. " +
                            "Envie apenas imagens (PNG, JPEG, WEBP, GIF), PDF ou texto simples.");
        }
        helper.addAttachment(sanitizeFilename(file.getOriginalFilename()), file);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "anexo";
        }
        String base = Paths.get(filename).getFileName().toString();
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
