package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "O e-mail é obrigatório")
        String email,

        @NotBlank(message = "O código é obrigatório")
        String code
) {}
