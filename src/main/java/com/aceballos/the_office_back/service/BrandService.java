package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.BrandRequest;
import com.aceballos.the_office_back.dto.BrandResponse;
import com.aceballos.the_office_back.entity.Brand;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.BrandRepository;
import com.aceballos.the_office_back.repository.NavItemRepository;
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
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final NavItemRepository navItemRepository;

    /**
     * Devuelve las marcas activas que tienen al menos un producto activo asociado en el catálogo público,
     * ordenadas alfabéticamente por nombre.
     */
    @Transactional(readOnly = true)
    public List<BrandResponse> getActiveBrands() {
        return brandRepository.findActiveBrandsWithActiveProducts().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Listado administrativo de todas las marcas (activas e inactivas) ordenadas alfabéticamente.
     */
    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrandsAdmin() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca una marca por ID.
     */
    @Transactional(readOnly = true)
    public BrandResponse getBrandById(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + id));
        return mapToResponse(brand);
    }

    /**
     * Busca una marca por su slug.
     */
    @Transactional(readOnly = true)
    public BrandResponse getBrandBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalArgumentException("El slug de la marca es obligatorio");
        }
        String normalizedSlug = normalizeSlug(slug);
        Brand brand = brandRepository.findBySlug(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con slug: " + slug));
        return mapToResponse(brand);
    }

    /**
     * Crea una nueva marca con validación de nombre y slug único normalizado.
     */
    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        String name = validateAndNormalizeName(request);

        if (brandRepository.existsByNameIgnoreCase(name)) {
            throw new ResourceConflictException("Ya existe una marca con el nombre: " + name);
        }

        String rawSlug = StringUtils.hasText(request.getSlug()) ? request.getSlug() : name;
        String normalizedSlug = normalizeSlug(rawSlug);

        if (brandRepository.existsBySlug(normalizedSlug)) {
            throw new ResourceConflictException("Ya existe una marca con el slug: " + normalizedSlug);
        }

        boolean active = !Boolean.FALSE.equals(request.getIsActive());

        Brand brand = Brand.builder()
                .name(name)
                .slug(normalizedSlug)
                .isActive(active)
                .build();

        Brand saved = brandRepository.save(brand);
        log.info("Marca creada exitosamente: ID={}, name='{}', slug='{}', isActive={}",
                saved.getId(), saved.getName(), saved.getSlug(), saved.getIsActive());
        return mapToResponse(saved);
    }

    /**
     * Modifica los datos de una marca existente.
     */
    @Transactional
    public BrandResponse updateBrand(Long id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + id));

        String name = validateAndNormalizeName(request);

        if (brandRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResourceConflictException("Ya existe otra marca con el nombre: " + name);
        }

        String rawSlug = StringUtils.hasText(request.getSlug()) ? request.getSlug() : name;
        String normalizedSlug = normalizeSlug(rawSlug);

        if (brandRepository.existsBySlugAndIdNot(normalizedSlug, id)) {
            throw new ResourceConflictException("Ya existe otra marca con el slug: " + normalizedSlug);
        }

        brand.setName(name);
        brand.setSlug(normalizedSlug);
        if (request.getIsActive() != null) {
            brand.setIsActive(request.getIsActive());
        }

        Brand updated = brandRepository.save(brand);
        log.info("Marca ID={} actualizada: name='{}', slug='{}', isActive={}",
                updated.getId(), updated.getName(), updated.getSlug(), updated.getIsActive());
        return mapToResponse(updated);
    }

    /**
     * Activa o desactiva una marca. La desactivación no altera la asociación con los productos.
     */
    @Transactional
    public BrandResponse updateBrandStatus(Long id, Boolean isActive) {
        if (isActive == null) {
            throw new IllegalArgumentException("El estado isActive es obligatorio");
        }

        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + id));

        brand.setIsActive(isActive);
        Brand saved = brandRepository.save(brand);
        log.info("Estado de la marca ID={} ('{}') actualizado a isActive={}",
                saved.getId(), saved.getName(), saved.getIsActive());
        return mapToResponse(saved);
    }

    /**
     * Eliminación física únicamente si la marca no posee productos asociados.
     */
    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + id));

        long associatedProducts = productRepository.countByBrandId(id);
        if (associatedProducts > 0 || productRepository.existsByBrandId(id)) {
            throw new ResourceConflictException(
                    "No se puede eliminar la marca '" + brand.getName()
                            + "' porque tiene productos asociados (" + Math.max(associatedProducts, 1) + ").");
        }

        navItemRepository.deleteByBrandId(id);
        brandRepository.delete(brand);
        log.info("Marca ID={} ('{}') eliminada correctamente", brand.getId(), brand.getName());
    }

    private String validateAndNormalizeName(BrandRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessRuleException("El nombre de la marca es obligatorio");
        }
        String trimmedName = request.getName().trim();
        if (trimmedName.length() > 100) {
            throw new BusinessRuleException("El nombre de la marca no puede superar los 100 caracteres");
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

    public BrandResponse mapToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .isActive(brand.getIsActive())
                .build();
    }
}

