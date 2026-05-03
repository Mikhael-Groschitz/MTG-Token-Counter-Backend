package com.tokenforge.api.repositories;

import com.tokenforge.api.entities.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {

    List<Token> findByOwnerId(String ownerId);

    Optional<Token> findByIdAndOwnerId(String id, String ownerId);

    long countByOwnerId(String ownerId);
}