package com.tokenforge.api.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column
    private String password;

    @Column(nullable = false)
    private String username;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verification_code_expiry")
    private LocalDateTime verificationCodeExpiry;

    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts = 0;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    // Tokens JWT emitidos antes deste instante deixam de ser aceitos (ver JwtService/SecurityFilter).
    // Atualizado sempre que a senha é trocada/redefinida, para revogar sessões antigas.
    @Column(name = "token_valid_after")
    private LocalDateTime tokenValidAfter;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}