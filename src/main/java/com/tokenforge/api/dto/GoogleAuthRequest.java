package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank(message = "O token do Google é obrigatório")
        String idToken
) {}