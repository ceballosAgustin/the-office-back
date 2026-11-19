package com.aceballos.the_office_back.util;

import com.aceballos.the_office_back.exception.BusinessRuleException;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilidad para generar slugs de URL a partir de texto libre.
 */
public final class SlugUtils {

    private static final int MAX_SLUG_LENGTH = 100;

    private SlugUtils() {
    }

    /**
     * Normaliza un texto para ser utilizado como slug de URL:
     * remueve acentos/diacríticos, convierte a minúsculas, reemplaza caracteres no alfanuméricos
     * por guiones medios únicos y recorta guiones extremos.
     */
    public static String normalize(String input) {
        if (!StringUtils.hasText(input)) {
            throw new BusinessRuleException("No se pudo generar un slug válido a partir del valor proporcionado");
        }

        String decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        String slug = decomposed
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.isEmpty()) {
            throw new BusinessRuleException("No se pudo generar un slug válido a partir del valor proporcionado");
        }
        if (slug.length() > MAX_SLUG_LENGTH) {
            throw new BusinessRuleException("El slug no puede superar los 100 caracteres");
        }

        return slug;
    }
}
