package com.tokenforge.api.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tokenforge.api.exceptions.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private static final String UPLOAD_FOLDER = "tokenforge";

    private static final String ALLOWED_FORMATS = "png,jpg,jpeg,webp,gif";

    private static final int UPLOAD_TIMEOUT_SECONDS = 20;

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret));
    }

    public Map<String, Object> buildSignedUploadParams() {
        long timestamp = System.currentTimeMillis() / 1000L;

        Map<String, Object> paramsToSign = new TreeMap<>();
        paramsToSign.put("timestamp", timestamp);
        paramsToSign.put("folder", UPLOAD_FOLDER);
        paramsToSign.put("allowed_formats", ALLOWED_FORMATS);

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cloud_name", cloudName);
        response.put("api_key", apiKey);
        response.put("timestamp", timestamp);
        response.put("folder", UPLOAD_FOLDER);
        response.put("allowed_formats", ALLOWED_FORMATS);
        response.put("signature", signature);
        return response;
    }

    public String uploadImage(String base64Image) {
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> uploadResult = cloudinary.uploader().upload(
                    base64Image,
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "folder", UPLOAD_FOLDER,
                            "timeout", UPLOAD_TIMEOUT_SECONDS,
                            "allowed_formats", ALLOWED_FORMATS
                    )
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