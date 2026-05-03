package com.tokenforge.api.controllers;

import com.tokenforge.api.dto.TokenRequestDTO;
import com.tokenforge.api.dto.TokenResponseDTO;
import com.tokenforge.api.entities.User;
import com.tokenforge.api.services.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @GetMapping
    public ResponseEntity<List<TokenResponseDTO>> getAllTokens(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.findAllByUser(user));
    }

    @PostMapping
    public ResponseEntity<TokenResponseDTO> createToken(
            @Valid @RequestBody TokenRequestDTO requestDTO,
            @AuthenticationPrincipal User user) {

        TokenResponseDTO savedToken = tokenService.saveToken(requestDTO, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedToken);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TokenResponseDTO> updateToken(
            @PathVariable String id,
            @Valid @RequestBody TokenRequestDTO requestDTO,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(tokenService.updateToken(id, requestDTO, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteToken(@PathVariable String id, @AuthenticationPrincipal User user) {
        tokenService.deleteToken(id, user);
        return ResponseEntity.noContent().build();
    }
}