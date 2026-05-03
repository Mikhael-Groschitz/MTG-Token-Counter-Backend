package com.tokenforge.api.dto;

public record AuthResponse(
        String token,
        String username,
        String email
) {}