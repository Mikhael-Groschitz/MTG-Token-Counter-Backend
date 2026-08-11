package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FacebookAuthRequest(
        @NotBlank(message = "O token de acesso do Facebook é obrigatório")
        String accessToken
) {}
