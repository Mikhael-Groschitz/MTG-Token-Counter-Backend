package com.tokenforge.api.repositories;

import com.tokenforge.api.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByFacebookId(String facebookId);
    Optional<User> findByUsernameOrEmail(String username, String email);
    Optional<User> findByResetToken(String resetToken);

    boolean existsByEmail(String email);
}