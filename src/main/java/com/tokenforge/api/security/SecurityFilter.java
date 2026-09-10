package com.tokenforge.api.security;

import com.tokenforge.api.entities.User;
import com.tokenforge.api.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    public static final String VERIFIED_AUTHORITY = "VERIFIED";

    private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = recoverToken(request);

        if (token != null) {
            Claims claims = jwtService.validateToken(token);

            if (claims != null) {
                String email = claims.getSubject();
                Optional<User> userOptional = userRepository.findByEmail(email);

                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    if (isRevoked(user, claims)) {
                        log.warn("Token revogado (emitido antes da última troca de senha): {}", email);
                    } else {
                        var authorities = user.isEmailVerified()
                                ? List.<GrantedAuthority>of(new SimpleGrantedAuthority(VERIFIED_AUTHORITY))
                                : Collections.<GrantedAuthority>emptyList();

                        var authentication = new UsernamePasswordAuthenticationToken(
                                user, null, authorities
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Usuário autenticado: {} (e-mail verificado: {})", email, user.isEmailVerified());
                    }
                } else {
                    log.warn("Token válido mas usuário não encontrado: {}", email);
                }
            } else {
                log.warn("Token inválido ou expirado na requisição: {}", request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7).trim();
    }

    /**
     * Rejeita tokens emitidos antes de {@code user.getTokenValidAfter()}, que é atualizado
     * sempre que a senha é trocada/redefinida. Isso garante que um JWT vazado/roubado deixe
     * de funcionar assim que a vítima troca a senha, em vez de continuar válido pelas 24h
     * restantes de expiração do token.
     */
    private boolean isRevoked(User user, Claims claims) {
        if (user.getTokenValidAfter() == null) {
            return false;
        }
        if (claims.getIssuedAt() == null) {
            return true;
        }
        LocalDateTime issuedAt = LocalDateTime.ofInstant(claims.getIssuedAt().toInstant(), ZoneId.systemDefault());
        return issuedAt.isBefore(user.getTokenValidAfter());
    }
}