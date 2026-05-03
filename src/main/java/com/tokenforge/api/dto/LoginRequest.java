package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "O identificador (e-mail ou usuário) é obrigatório")
        String identifier,

        @NotBlank(message = "A senha é obrigatória")
        String password
) {}