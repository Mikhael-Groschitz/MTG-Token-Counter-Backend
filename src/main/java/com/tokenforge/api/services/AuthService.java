package com.tokenforge.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokenforge.api.dto.AuthResponse;
import com.tokenforge.api.dto.FacebookAuthRequest;
import com.tokenforge.api.dto.ForgotPasswordRequest;
import com.tokenforge.api.dto.GoogleAuthRequest;
import com.tokenforge.api.dto.LoginRequest;
import com.tokenforge.api.dto.RegisterRequest;
import com.tokenforge.api.dto.ResendVerificationRequest;
import com.tokenforge.api.dto.ResetPasswordRequest;
import com.tokenforge.api.dto.VerifyEmailRequest;
import com.tokenforge.api.entities.User;
import com.tokenforge.api.exceptions.BusinessRuleException;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    private static final String FACEBOOK_DEBUG_TOKEN_URL = "https://graph.facebook.com/debug_token";
    private static final String FACEBOOK_ME_URL = "https://graph.facebook.com/me";

    private static final int VERIFICATION_CODE_TTL_MINUTES = 15;
    private static final int RESET_TOKEN_TTL_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${facebook.app-id}")
    private String facebookAppId;

    @Value("${facebook.app-secret}")
    private String facebookAppSecret;

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

        emailService.sendVerificationCode(newUser.getEmail(), newUser.getVerificationCode());

        String token = jwtService.generateToken(newUser.getEmail());
        return new AuthResponse(token, newUser.getUsername(), newUser.getEmail());
    }

    // ── Verificação de e-mail ──────────────────────────────

    public void verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessRuleException("Código inválido ou expirado."));

        if (user.isEmailVerified()) {
            return;
        }

        if (user.getVerificationCode() == null
                || !user.getVerificationCode().equals(request.code())
                || user.getVerificationCodeExpiry() == null
                || user.getVerificationCodeExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Código inválido ou expirado.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiry(null);
        userRepository.save(user);
    }

    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            if (user.isEmailVerified()) {
                return;
            }

            applyNewVerificationCode(user);
            userRepository.save(user);
            emailService.sendVerificationCode(user.getEmail(), user.getVerificationCode());
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
            emailService.sendPasswordResetLink(user.getEmail(), resetLink);
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

    private void applyNewVerificationCode(User user) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setVerificationCode(code);
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(VERIFICATION_CODE_TTL_MINUTES));
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

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getUsername(), user.getEmail());
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

    // ── Login com Facebook ─────────────────────────────────

    public AuthResponse facebookAuth(FacebookAuthRequest request) {
        JsonNode profile = verifyFacebookAccessToken(request.accessToken());

        String facebookId = profile.path("id").asText();
        String email       = profile.path("email").asText(null);
        String name        = profile.path("name").asText();

        if (email == null || email.isBlank()) {
            throw new BusinessRuleException(
                    "Não foi possível obter o e-mail da sua conta do Facebook. " +
                            "Verifique se a permissão de e-mail foi concedida e tente novamente."
            );
        }

        String username = (!name.isBlank()) ? name : email.split("@")[0];

        return socialAuth(
                "Facebook", email, username, facebookId,
                userRepository.findByFacebookId(facebookId),
                User::getFacebookId, User::setFacebookId
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
        return new AuthResponse(token, user.getUsername(), user.getEmail());
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

    private JsonNode verifyFacebookAccessToken(String accessToken) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String appAccessToken = facebookAppId + "|" + facebookAppSecret;

            String debugUrl = FACEBOOK_DEBUG_TOKEN_URL
                    + "?input_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                    + "&access_token=" + URLEncoder.encode(appAccessToken, StandardCharsets.UTF_8);

            HttpResponse<String> debugResponse = client.send(
                    HttpRequest.newBuilder().uri(URI.create(debugUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (debugResponse.statusCode() != 200) {
                log.warn("Facebook debug_token retornou status {}: {}", debugResponse.statusCode(), debugResponse.body());
                throw new BusinessRuleException("Token do Facebook inválido ou expirado.");
            }

            JsonNode debugData = objectMapper.readTree(debugResponse.body()).path("data");
            boolean valid = debugData.path("is_valid").asBoolean(false);
            String tokenAppId = debugData.path("app_id").asText("");

            if (!valid || !facebookAppId.equals(tokenAppId)) {
                throw new BusinessRuleException("Token do Facebook não pertence a esta aplicação.");
            }

            String profileUrl = FACEBOOK_ME_URL
                    + "?fields=id,email,name"
                    + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

            HttpResponse<String> profileResponse = client.send(
                    HttpRequest.newBuilder().uri(URI.create(profileUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (profileResponse.statusCode() != 200) {
                log.warn("Facebook /me retornou status {}: {}", profileResponse.statusCode(), profileResponse.body());
                throw new BusinessRuleException("Não foi possível obter seu perfil do Facebook.");
            }

            return objectMapper.readTree(profileResponse.body());

        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao verificar token do Facebook: {}", e.getMessage(), e);
            throw new BusinessRuleException("Erro ao verificar autenticação com o Facebook.");
        }
    }
}