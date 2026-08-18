package com.tokenforge.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokenforge.api.dto.AuthResponse;
import com.tokenforge.api.dto.ForgotPasswordRequest;
import com.tokenforge.api.dto.GoogleAuthRequest;
import com.tokenforge.api.dto.LoginRequest;
import com.tokenforge.api.dto.RegisterRequest;
import com.tokenforge.api.dto.ResendVerificationRequest;
import com.tokenforge.api.dto.ResetPasswordRequest;
import com.tokenforge.api.dto.UpdateProfileRequest;
import com.tokenforge.api.dto.VerifyEmailRequest;
import com.tokenforge.api.entities.User;
import com.tokenforge.api.exceptions.BusinessRuleException;
import com.tokenforge.api.exceptions.EmailNotVerifiedException;
import com.tokenforge.api.repositories.UserRepository;
import com.tokenforge.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String GOOGLE_TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private static final String PROVIDER_LOCAL = "local";
    private static final String PROVIDER_GOOGLE = "google";

    private static final int VERIFICATION_CODE_TTL_MINUTES = 15;
    private static final int RESET_TOKEN_TTL_MINUTES = 30;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── Registro ──────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Este e-mail já está cadastrado em nossa forja.");
        }

        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        applyNewVerificationCode(newUser);
        userRepository.save(newUser);

        emailService.sendVerificationCode(newUser.getEmail(), newUser.getVerificationCode(), VERIFICATION_CODE_TTL_MINUTES);

        String token = jwtService.generateToken(newUser.getEmail());
        return new AuthResponse(token, newUser.getUsername(), newUser.getEmail(), PROVIDER_LOCAL);
    }

    // ── Verificação de e-mail ──────────────────────────────

    public void verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessRuleException("Código inválido ou expirado."));

        if (user.isEmailVerified()) {
            return;
        }

        if (user.getVerificationAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            throw new BusinessRuleException(
                    "Número máximo de tentativas excedido. Solicite um novo código.");
        }

        boolean codeMatches = user.getVerificationCode() != null
                && user.getVerificationCode().equals(request.code())
                && user.getVerificationCodeExpiry() != null
                && user.getVerificationCodeExpiry().isAfter(LocalDateTime.now());

        if (!codeMatches) {
            user.setVerificationAttempts(user.getVerificationAttempts() + 1);
            userRepository.save(user);
            throw new BusinessRuleException("Código inválido ou expirado.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiry(null);
        user.setVerificationAttempts(0);
        userRepository.save(user);
    }

    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            if (user.isEmailVerified()) {
                return;
            }

            applyNewVerificationCode(user);
            userRepository.save(user);
            emailService.sendVerificationCode(user.getEmail(), user.getVerificationCode(), VERIFICATION_CODE_TTL_MINUTES);
        });
    }

    // ── Recuperação de senha ───────────────────────────────

    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
            userRepository.save(user);

            String resetLink = frontendUrl + "/redefinir-senha?token=" + token;
            emailService.sendPasswordResetLink(user.getEmail(), resetLink, RESET_TOKEN_TTL_MINUTES);
        });
    }

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .orElseThrow(() -> new BusinessRuleException("Link de recuperação inválido ou expirado."));

        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Link de recuperação inválido ou expirado.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    // ── Atualização de perfil ──────────────────────────────

    public AuthResponse updateProfile(User authenticatedUser, UpdateProfileRequest request) {
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new BusinessRuleException("Usuário não encontrado."));

        boolean wantsPasswordChange = request.newPassword() != null && !request.newPassword().isBlank();

        if (wantsPasswordChange) {
            if (!PROVIDER_LOCAL.equals(resolveProvider(user))) {
                throw new BusinessRuleException(
                        "Contas conectadas via Google não possuem senha própria " +
                                "e não podem alterá-la por aqui.");
            }
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new BusinessRuleException("Informe sua senha atual para definir uma nova senha.");
            }
            if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new BusinessRuleException("Senha atual incorreta.");
            }
        }

        user.setUsername(request.username().trim());
        if (wantsPasswordChange) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }
        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getUsername(), user.getEmail(), resolveProvider(user));
    }

    private void applyNewVerificationCode(User user) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setVerificationCode(code);
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(VERIFICATION_CODE_TTL_MINUTES));
        user.setVerificationAttempts(0);
    }

    // ── Login tradicional ─────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.identifier(), request.identifier())
                .orElseThrow(() -> new BusinessRuleException(
                        "Credenciais inválidas. Verifique seu e-mail/usuário e senha."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessRuleException(
                    "Credenciais inválidas. Verifique seu e-mail/usuário e senha.");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(user.getEmail());
        }

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getUsername(), user.getEmail(), resolveProvider(user));
    }

    // ── Login com Google ──────────────────────────────────

    public AuthResponse googleAuth(GoogleAuthRequest request) {
        JsonNode payload = verifyGoogleIdToken(request.idToken());

        // Valida que o token foi emitido para o nosso app
        String aud = payload.path("aud").asText();
        if (!googleClientId.equals(aud)) {
            throw new BusinessRuleException("Token do Google não pertence a esta aplicação.");
        }

        String email    = payload.path("email").asText();
        String googleId = payload.path("sub").asText();
        String name     = payload.path("name").asText();
        String username = (!name.isBlank()) ? name : email.split("@")[0];

        return socialAuth(
                "Google", email, username, googleId,
                userRepository.findByGoogleId(googleId),
                User::getGoogleId, User::setGoogleId
        );
    }

    private AuthResponse socialAuth(
            String providerLabel,
            String email,
            String username,
            String providerId,
            Optional<User> existingByProviderId,
            Function<User, String> providerIdGetter,
            BiConsumer<User, String> providerIdSetter
    ) {
        User user = existingByProviderId
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> {
                            if (providerIdGetter.apply(existing) == null) {
                                throw new BusinessRuleException(
                                        "Já existe uma conta com este e-mail. Faça login com sua senha " +
                                                "para acessar; a vinculação com o " + providerLabel +
                                                " poderá ser feita depois."
                                );
                            }
                            return existing;
                        })
                        .orElseGet(() -> {
                            User newUser = new User();
                            newUser.setUsername(username);
                            newUser.setEmail(email);
                            providerIdSetter.accept(newUser, providerId);
                            newUser.setEmailVerified(true);
                            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                            return userRepository.save(newUser);
                        }));

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getUsername(), user.getEmail(), resolveProvider(user));
    }

    private String resolveProvider(User user) {
        if (user.getGoogleId() != null) return PROVIDER_GOOGLE;
        return PROVIDER_LOCAL;
    }

    private JsonNode verifyGoogleIdToken(String idToken) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_TOKEN_INFO_URL + idToken))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log.warn("Google tokeninfo retornou status {}: {}", response.statusCode(), response.body());
                throw new BusinessRuleException("Token do Google inválido ou expirado.");
            }

            return objectMapper.readTree(response.body());

        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao verificar token do Google: {}", e.getMessage(), e);
            throw new BusinessRuleException("Erro ao verificar autenticação com o Google.");
        }
    }

}