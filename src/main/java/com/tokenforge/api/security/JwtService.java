package com.tokenforge.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${api.security.token.secret}")
    private String secret;

    // Padrão: 24 horas. Configurável via JWT_EXPIRATION no ambiente.
    @Value("${api.security.token.expiration:86400000}")
    private long jwtExpiration;

    private SecretKey getSignInKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter no mínimo 32 caracteres para garantir segurança HMAC-SHA256."
            );
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * Retorna as claims do token (inclui "sub" e "iat") se a assinatura e a expiração
     * forem válidas, ou null caso contrário. O "iat" é usado pelo SecurityFilter para
     * rejeitar tokens emitidos antes da última troca de senha do usuário.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}