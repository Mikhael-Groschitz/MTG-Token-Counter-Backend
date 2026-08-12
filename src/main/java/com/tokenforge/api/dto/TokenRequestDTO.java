package com.tokenforge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequestDTO(
        @NotBlank(message = "O nome do token é obrigatório")
        String name,
        String typeLine,
        String color,
        String colorIdentity,
        String power,
        String toughness,
        String abilities,
        String imageUrl,
        String layout
) {}