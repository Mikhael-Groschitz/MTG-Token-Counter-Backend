package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank(message = "O e-mail é obrigatório")
        String email
) {}
