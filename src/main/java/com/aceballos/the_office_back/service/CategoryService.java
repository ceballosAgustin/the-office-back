package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.CategoryRequest;
import com.aceballos.the_office_back.dto.CategoryResponse;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con slug: " + slug));
        return mapToResponse(category);
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = validateAndNormalizeName(request);
        String rawSlug = StringUtils.hasText(request.getSlug()) ? request.getSlug() : name;
        String normalizedSlug = normalizeSlug(rawSlug);

        if (categoryRepository.existsBySlug(normalizedSlug)) {
            throw new ResourceConflictException("Ya existe una categoría con el slug: " + normalizedSlug);
        }

        Category category = Category.builder()
                .name(name)
                .slug(normalizedSlug)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Categoría creada exitosamente: ID={}, name='{}', slug='{}'", saved.getId(), saved.getName(), saved.getSlug());
        return mapToResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));

        String name = validateAndNormalizeName(request);
        String rawSlug = StringUtils.hasText(request.getSlug()) ? request.getSlug() : name;
        String normalizedSlug = normalizeSlug(rawSlug);

        if (categoryRepository.existsBySlugAndIdNot(normalizedSlug, id)) {
            throw new ResourceConflictException("Ya existe otra categoría con el slug: " + normalizedSlug);
        }

        category.setName(name);
        category.setSlug(normalizedSlug);

        Category updated = categoryRepository.save(category);
        log.info("Categoría ID={} actualizada: name='{}', slug='{}'", updated.getId(), updated.getName(), updated.getSlug());
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));

        long associatedProducts = productRepository.countByCategoryId(id);
        if (associatedProducts > 0 || productRepository.existsByCategoryId(id)) {
            throw new ResourceConflictException(
                    "No se puede eliminar la categoría '" + category.getName()
                            + "' porque tiene productos asociados (" + Math.max(associatedProducts, 1) + ").");
        }

        categoryRepository.delete(category);
        log.info("Categoría ID={} ('{}') eliminada correctamente", category.getId(), category.getName());
    }

    private String validateAndNormalizeName(CategoryRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessRuleException("El nombre de la categoría es obligatorio");
        }
        String trimmedName = request.getName().trim();
        if (trimmedName.length() > 100) {
            throw new BusinessRuleException("El nombre de la categoría no puede superar los 100 caracteres");
        }
        return trimmedName;
    }

    /**
     * Normaliza un texto para ser utilizado como slug de URL:
     * remueve acentos/diacríticos, convierte a minúsculas, reemplaza caracteres no alfanuméricos
     * por guiones medios únicos y recorta guiones extremos.
     */
    public static String normalizeSlug(String input) {
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
        if (slug.length() > 100) {
            throw new BusinessRuleException("El slug no puede superar los 100 caracteres");
        }

        return slug;
    }

    public CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .build();
    }
}
