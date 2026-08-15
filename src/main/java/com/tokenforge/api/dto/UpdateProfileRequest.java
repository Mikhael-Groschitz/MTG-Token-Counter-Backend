package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "O nome de usuário não pode estar em branco")
        @Size(min = 3, max = 20, message = "O nome de usuário deve ter entre 3 e 20 caracteres")
        String username,

        String currentPassword,

        @Size(min = 8, message = "A nova senha deve ter no mínimo 8 caracteres")
        String newPassword
) {}
