package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequestDTO(
        @NotBlank(message = "O nome do token é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @Size(max = 100, message = "O tipo deve ter no máximo 100 caracteres")
        String typeLine,

        @Size(max = 50, message = "A cor deve ter no máximo 50 caracteres")
        String color,

        @Size(max = 50, message = "A identidade de cor deve ter no máximo 50 caracteres")
        String colorIdentity,

        @Size(max = 20, message = "O poder deve ter no máximo 20 caracteres")
        String power,

        @Size(max = 20, message = "A resistência deve ter no máximo 20 caracteres")
        String toughness,

        @Size(max = 2000, message = "As habilidades devem ter no máximo 2000 caracteres")
        String abilities,

        @Size(max = 7_000_000, message = "A imagem excede o tamanho máximo permitido")
        String imageUrl,

        @Size(max = 50, message = "O layout deve ter no máximo 50 caracteres")
        String layout
) {}