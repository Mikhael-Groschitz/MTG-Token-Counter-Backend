package com.tokenforge.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokenforge.api.dto.AuthResponse;
import com.tokenforge.api.dto.GoogleAuthRequest;
import com.tokenforge.api.dto.LoginRequest;
import com.tokenforge.api.dto.RegisterRequest;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // URL pública do Google para verificar idTokens — sem dependência extra
    private static final String GOOGLE_TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Value("${google.client-id}")
    private String googleClientId;

    // ── Registro ──────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Este e-mail já está cadastrado em nossa forja.");
        }

        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(newUser);

        String token = jwtService.generateToken(newUser.getEmail());
        return new AuthResponse(token, newUser.getUsername(), newUser.getEmail());
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

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> {
                            existing.setGoogleId(googleId);
                            return userRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            User newUser = new User();
                            newUser.setUsername(username);
                            newUser.setEmail(email);
                            newUser.setGoogleId(googleId);
                            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                            return userRepository.save(newUser);
                        }));

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }

    // ── Helper: verifica idToken via API pública do Google ─

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