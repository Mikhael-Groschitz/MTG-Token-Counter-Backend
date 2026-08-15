package com.tokenforge.api.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tokenforge.api.exceptions.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret));
    }

    public String uploadImage(String base64Image) {
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> uploadResult = cloudinary.uploader().upload(
                    base64Image, ObjectUtils.emptyMap()
            );

            Object secureUrl = uploadResult.get("secure_url");
            if (secureUrl == null) {
                throw new BusinessRuleException("Upload concluído mas URL não retornada pelo Cloudinary.");
            }
            return secureUrl.toString();

        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException e) {
            log.error("Erro ao fazer upload da imagem para o Cloudinary: {}", e.getMessage(), e);
            throw new BusinessRuleException("Erro ao processar a imagem. Verifique o formato e tente novamente.");
        } catch (RuntimeException e) {
            log.error("Erro ao comunicar com o Cloudinary: {}", e.getMessage(), e);
            throw new BusinessRuleException("Erro ao conectar com o serviço de imagens. Verifique a configuração do Cloudinary.");
        }
    }
}