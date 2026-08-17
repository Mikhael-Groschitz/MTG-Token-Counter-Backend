package com.tokenforge.api.services;

import com.tokenforge.api.dto.TokenRequestDTO;
import com.tokenforge.api.dto.TokenResponseDTO;
import com.tokenforge.api.entities.Token;
import com.tokenforge.api.entities.User;
import com.tokenforge.api.exceptions.BusinessRuleException;
import com.tokenforge.api.exceptions.ResourceNotFoundException;
import com.tokenforge.api.repositories.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int MAX_TOKENS_PER_USER = 5;

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final TokenRepository tokenRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<TokenResponseDTO> findAllByUser(User user) {
        return tokenRepository.findByOwnerId(user.getId())
                .stream()
                .map(TokenResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public TokenResponseDTO saveToken(TokenRequestDTO dto, User owner) {
        // Valida o limite de tokens por usuário
        long tokenCount = tokenRepository.countByOwnerId(owner.getId());
        if (tokenCount >= MAX_TOKENS_PER_USER) {
            throw new BusinessRuleException(
                    "Limite de " + MAX_TOKENS_PER_USER + " tokens atingido. " +
                            "Remova um token antes de criar um novo."
            );
        }

        Token token = new Token();
        updateTokenFields(token, dto);
        token.setOwner(owner);

        // Cloudinary: base64 → URL permanente; URL externa → salva direto
        token.setImageUrl(resolveImageUrl(dto.imageUrl()));

        Token savedToken = tokenRepository.save(token);
        return TokenResponseDTO.fromEntity(savedToken);
    }

    @Transactional
    public TokenResponseDTO updateToken(String id, TokenRequestDTO dto, User user) {
        validateId(id);
        Token token = tokenRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Token não encontrado na sua biblioteca."
                ));

        // Atualiza campos de texto primeiro
        updateTokenFields(token, dto);

        // Depois resolve a imagem (sobrescreve imageUrl se necessário)
        token.setImageUrl(resolveImageUrl(dto.imageUrl()));

        Token updatedToken = tokenRepository.save(token);
        return TokenResponseDTO.fromEntity(updatedToken);
    }

    @Transactional
    public void deleteToken(String id, User user) {
        validateId(id);
        Token token = tokenRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Token não encontrado ou acesso negado."
                ));
        tokenRepository.delete(token);
    }

    // ── Helpers ───────────────────────────────────────────

    private void validateId(String id) {
        if (id == null || !UUID_PATTERN.matcher(id).matches()) {
            throw new ResourceNotFoundException("Token não encontrado na sua biblioteca.");
        }
    }

    private void updateTokenFields(Token token, TokenRequestDTO dto) {
        token.setName(dto.name());
        token.setTypeLine(dto.typeLine());
        token.setColor(dto.color());
        token.setColorIdentity(dto.colorIdentity());
        token.setPower(dto.power());
        token.setToughness(dto.toughness());
        token.setAbilities(dto.abilities());
        token.setLayout(dto.layout());
    }

    /**
     * Se a imagem for base64 (data:image/...), faz upload no Cloudinary
     * e retorna a URL permanente. Caso contrário, retorna a URL diretamente.
     */
    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return imageUrl;
        }
        if (imageUrl.startsWith("data:image")) {
            return cloudinaryService.uploadImage(imageUrl);
        }
        if (!imageUrl.startsWith("https://")) {
            throw new BusinessRuleException("A URL da imagem deve começar com https://.");
        }
        return imageUrl;
    }
}