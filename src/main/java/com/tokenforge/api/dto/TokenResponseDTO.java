package com.tokenforge.api.dto;

import com.tokenforge.api.entities.Token;

public record TokenResponseDTO(
        String id,
        String name,
        String typeLine,
        String color,
        String colorIdentity,
        String power,
        String toughness,
        String abilities,
        String imageUrl,
        String layout
) {
    // Método facilitador para converter a Entidade no DTO
    public static TokenResponseDTO fromEntity(Token token) {
        return new TokenResponseDTO(
                token.getId(),
                token.getName(),
                token.getTypeLine(),
                token.getColor(),
                token.getColorIdentity(),
                token.getPower(),
                token.getToughness(),
                token.getAbilities(),
                token.getImageUrl(),
                token.getLayout()
        );
    }
}