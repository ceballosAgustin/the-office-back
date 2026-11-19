package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder:the-office/products}")
    private String cloudinaryFolder = "the-office/products";

    /** Tipos de contenido de imagen aceptados para subir a Cloudinary. */
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/avif", "image/gif");

    public record CloudinaryUploadResult(String imageUrl, String publicId) {}

    @SuppressWarnings("unchecked")
    public CloudinaryUploadResult uploadImageWithDetails(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateImageContentType(file);

        try {
            String folder = StringUtils.hasText(cloudinaryFolder) ? cloudinaryFolder.trim() : "the-office/products";
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image"
                    )
            );

            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            return new CloudinaryUploadResult(secureUrl, publicId);
        } catch (IOException e) {
            log.error("Error al subir archivo a Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Error al procesar y subir la imagen del producto", e);
        }
    }

    private static void validateImageContentType(MultipartFile file) {
        String contentType = file.getContentType();
        String normalized = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new BusinessRuleException(
                    "Formato de imagen no permitido. Los formatos aceptados son JPEG, PNG, WebP, AVIF y GIF");
        }
    }

    @SuppressWarnings("unchecked")
    public boolean deleteImage(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            log.info("La imagen no posee public_id registrado; el archivo antiguo puede permanecer almacenado en Cloudinary.");
            return false;
        }

        try {
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            Object status = result != null ? result.get("result") : null;
            log.info("Eliminación en Cloudinary para public_id '{}': resultado={}", publicId, status);
            return "ok".equals(status);
        } catch (Exception e) {
            log.error("Fallo al eliminar recurso en Cloudinary con public_id '{}'. Requiere limpieza posterior: {}",
                    publicId, e.getMessage(), e);
            return false;
        }
    }
}
