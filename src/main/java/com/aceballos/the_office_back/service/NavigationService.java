package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.*;
import com.aceballos.the_office_back.entity.Brand;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.entity.NavItem;
import com.aceballos.the_office_back.entity.NavSection;
import com.aceballos.the_office_back.entity.NavTargetType;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.BrandRepository;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.NavItemRepository;
import com.aceballos.the_office_back.repository.NavSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    public static final String BRANDS_SECTION_CODE = "brands";
    public static final int MAX_VISIBLE_SECTIONS = 5;
    public static final int MAX_VISIBLE_BRANDS_TOTAL = 8;
    public static final int MAX_VISIBLE_BRANDS_PER_COLUMN = 4;

    private final NavSectionRepository navSectionRepository;
    private final NavItemRepository navItemRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Devuelve el árbol público de navegación: solo secciones e ítems visibles,
     * agrupados por columnas y con destinos válidos (marcas activas con productos activos).
     */
    @Transactional(readOnly = true)
    public List<PublicNavigationSectionResponse> getPublicNavigation() {
        List<NavSection> visibleSections = navSectionRepository.findVisibleSectionsWithItems();
        Set<Long> validBrandIds = brandRepository.findActiveBrandsWithActiveProducts().stream()
                .map(Brand::getId)
                .collect(Collectors.toSet());

        List<PublicNavigationSectionResponse> response = new ArrayList<>();

        for (NavSection section : visibleSections) {
            List<NavItem> validItems = section.getItems().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getIsVisible()))
                    .filter(item -> isValidPublicDestination(item, validBrandIds))
                    .sorted(Comparator.comparing(NavItem::getColumnIndex).thenComparing(NavItem::getOrderIndex))
                    .toList();

            Map<Integer, List<NavItem>> itemsByColumn = validItems.stream()
                    .collect(Collectors.groupingBy(
                            NavItem::getColumnIndex,
                            TreeMap::new,
                            Collectors.toList()
                    ));

            List<PublicNavigationColumnResponse> columns = new ArrayList<>();
            for (Map.Entry<Integer, List<NavItem>> entry : itemsByColumn.entrySet()) {
                List<PublicNavigationItemResponse> itemResponses = entry.getValue().stream()
                        .map(this::mapToPublicItemResponse)
                        .collect(Collectors.toList());

                columns.add(PublicNavigationColumnResponse.builder()
                        .columnIndex(entry.getKey())
                        .items(itemResponses)
                        .build());
            }

            response.add(PublicNavigationSectionResponse.builder()
                    .id(section.getId())
                    .code(section.getCode())
                    .label(section.getLabel())
                    .orderIndex(section.getOrderIndex())
                    .columns(columns)
                    .build());
        }

        return response;
    }

    private boolean isValidPublicDestination(NavItem item, Set<Long> validBrandIds) {
        if (item.getTargetType() == null) {
            return false;
        }
        return switch (item.getTargetType()) {
            case BRAND -> item.getBrand() != null
                    && Boolean.TRUE.equals(item.getBrand().getIsActive())
                    && validBrandIds.contains(item.getBrand().getId());
            case CATEGORY -> item.getCategory() != null;
            case CUSTOM_LINK -> StringUtils.hasText(item.getCustomUrl());
        };
    }

    private PublicNavigationItemResponse mapToPublicItemResponse(NavItem item) {
        PublicNavigationItemResponse.PublicNavigationItemResponseBuilder builder = PublicNavigationItemResponse.builder()
                .id(item.getId())
                .targetType(item.getTargetType())
                .orderIndex(item.getOrderIndex());

        switch (item.getTargetType()) {
            case BRAND -> {
                Brand brand = item.getBrand();
                builder.label(brand.getName())
                        .brandId(brand.getId())
                        .brandName(brand.getName())
                        .brandSlug(brand.getSlug());
            }
            case CATEGORY -> {
                Category cat = item.getCategory();
                String label = StringUtils.hasText(item.getLabel()) ? item.getLabel() : cat.getName();
                builder.label(label)
                        .categoryId(cat.getId())
                        .categoryName(cat.getName())
                        .categorySlug(cat.getSlug());
            }
            case CUSTOM_LINK -> {
                builder.label(item.getLabel())
                        .customUrl(item.getCustomUrl());
            }
        }

        return builder.build();
    }

    // =========================================================================
    // CRUD ADMINISTRATIVO DE SECCIONES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<NavSectionResponse> getAllAdminSections() {
        return navSectionRepository.findAllSectionsWithItems().stream()
                .map(this::mapToSectionResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NavSectionResponse getSectionById(Long id) {
        NavSection section = navSectionRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sección de navegación no encontrada con ID: " + id));
        return mapToSectionResponse(section);
    }

    @Transactional
    public NavSectionResponse createSection(NavSectionRequest request) {
        if (request == null || !StringUtils.hasText(request.getLabel())) {
            throw new BusinessRuleException("El nombre de la sección es obligatorio");
        }
        String label = request.getLabel().trim();
        if (label.length() > 100) {
            throw new BusinessRuleException("El nombre de la sección no puede superar los 100 caracteres");
        }

        String rawCode = StringUtils.hasText(request.getCode()) ? request.getCode() : label;
        String normalizedCode = CategoryService.normalizeSlug(rawCode);

        if (navSectionRepository.existsByCode(normalizedCode)) {
            throw new ResourceConflictException("Ya existe una sección con el código: " + normalizedCode);
        }

        boolean isVisible = !Boolean.FALSE.equals(request.getIsVisible());
        if (isVisible && navSectionRepository.countByIsVisibleTrue() >= MAX_VISIBLE_SECTIONS) {
            throw new ResourceConflictException("No se pueden tener más de " + MAX_VISIBLE_SECTIONS
                    + " secciones visibles en la barra de navegación");
        }

        int orderIndex = request.getOrderIndex() != null ? request.getOrderIndex() : (int) navSectionRepository.count();

        NavSection section = NavSection.builder()
                .code(normalizedCode)
                .label(label)
                .orderIndex(orderIndex)
                .isVisible(isVisible)
                .items(new ArrayList<>())
                .build();

        NavSection saved = navSectionRepository.save(section);
        log.info("Sección de navegación creada: ID={}, code='{}', label='{}', isVisible={}",
                saved.getId(), saved.getCode(), saved.getLabel(), saved.getIsVisible());
        return mapToSectionResponse(saved);
    }

    @Transactional
    public NavSectionResponse updateSection(Long id, NavSectionRequest request) {
        NavSection section = navSectionRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sección de navegación no encontrada con ID: " + id));

        if (request == null || !StringUtils.hasText(request.getLabel())) {
            throw new BusinessRuleException("El nombre de la sección es obligatorio");
        }
        String label = request.getLabel().trim();
        if (label.length() > 100) {
            throw new BusinessRuleException("El nombre de la sección no puede superar los 100 caracteres");
        }

        if (request.getIsVisible() != null && request.getIsVisible() && !Boolean.TRUE.equals(section.getIsVisible())) {
            if (navSectionRepository.countByIsVisibleTrueAndIdNot(id) >= MAX_VISIBLE_SECTIONS) {
                throw new ResourceConflictException("No se pueden tener más de " + MAX_VISIBLE_SECTIONS
                        + " secciones visibles en la barra de navegación");
            }
        }

        section.setLabel(label);
        if (request.getOrderIndex() != null) {
            section.setOrderIndex(request.getOrderIndex());
        }
        if (request.getIsVisible() != null) {
            section.setIsVisible(request.getIsVisible());
        }

        NavSection updated = navSectionRepository.save(section);
        log.info("Sección de navegación actualizada: ID={}, code='{}', label='{}', isVisible={}",
                updated.getId(), updated.getCode(), updated.getLabel(), updated.getIsVisible());
        return mapToSectionResponse(updated);
    }

    @Transactional
    public NavSectionResponse updateSectionStatus(Long id, Boolean isVisible) {
        if (isVisible == null) {
            throw new IllegalArgumentException("El estado isVisible es obligatorio");
        }
        NavSection section = navSectionRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sección de navegación no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(isVisible) && !Boolean.TRUE.equals(section.getIsVisible())) {
            if (navSectionRepository.countByIsVisibleTrueAndIdNot(id) >= MAX_VISIBLE_SECTIONS) {
                throw new ResourceConflictException("No se pueden tener más de " + MAX_VISIBLE_SECTIONS
                        + " secciones visibles en la barra de navegación");
            }
        }

        section.setIsVisible(isVisible);
        NavSection saved = navSectionRepository.save(section);
        log.info("Estado de sección ID={} actualizado a isVisible={}", saved.getId(), saved.getIsVisible());
        return mapToSectionResponse(saved);
    }

    @Transactional
    public void deleteSection(Long id) {
        NavSection section = navSectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sección de navegación no encontrada con ID: " + id));

        navSectionRepository.delete(section);
        log.info("Sección de navegación ID={} ('{}') eliminada junto con sus elementos asociados",
                section.getId(), section.getCode());
    }

    @Transactional
    public List<NavSectionResponse> reorderSections(ReorderSectionsRequest request) {
        if (request == null || request.getSectionIds() == null || request.getSectionIds().isEmpty()) {
            throw new BusinessRuleException("La lista de IDs de secciones es obligatoria");
        }

        List<Long> ids = request.getSectionIds();
        Set<Long> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new BusinessRuleException("La lista contiene IDs de sección duplicados");
        }

        List<NavSection> existing = navSectionRepository.findAll();
        Map<Long, NavSection> map = existing.stream().collect(Collectors.toMap(NavSection::getId, Function.identity()));

        for (Long id : ids) {
            if (!map.containsKey(id)) {
                throw new ResourceNotFoundException("Sección no encontrada con ID: " + id);
            }
        }

        for (int i = 0; i < ids.size(); i++) {
            NavSection s = map.get(ids.get(i));
            s.setOrderIndex(i);
        }
        navSectionRepository.saveAll(map.values());

        return getAllAdminSections();
    }

    // =========================================================================
    // CRUD ADMINISTRATIVO DE ELEMENTOS (NAV_ITEMS)
    // =========================================================================

    @Transactional
    public NavItemResponse addItem(Long sectionId, NavItemRequest request) {
        NavSection section = navSectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sección de navegación no encontrada con ID: " + sectionId));

        if (request == null || request.getTargetType() == null) {
            throw new BusinessRuleException("El tipo de destino es obligatorio");
        }

        boolean isBrandsSection = BRANDS_SECTION_CODE.equalsIgnoreCase(section.getCode());
        Integer columnIndex = request.getColumnIndex();
        if (columnIndex == null) {
            throw new BusinessRuleException("El índice de columna es obligatorio");
        }

        if (isBrandsSection) {
            if (columnIndex != 0 && columnIndex != 1) {
                throw new BusinessRuleException("La sección de marcas solo admite las columnas 0 y 1");
            }
        } else {
            if (columnIndex < 0) {
                throw new BusinessRuleException("El índice de columna no puede ser negativo");
            }
        }

        boolean isVisible = !Boolean.FALSE.equals(request.getIsVisible());

        // Validar límites de marcas visibles en sección brands
        if (isBrandsSection && request.getTargetType() == NavTargetType.BRAND && isVisible) {
            long totalVisible = navItemRepository.countVisibleBrandsBySectionId(sectionId);
            if (totalVisible >= MAX_VISIBLE_BRANDS_TOTAL) {
                throw new ResourceConflictException("La sección de marcas no puede tener más de "
                        + MAX_VISIBLE_BRANDS_TOTAL + " marcas visibles");
            }
            long colVisible = navItemRepository.countVisibleBrandsBySectionIdAndColumnIndex(sectionId, columnIndex);
            if (colVisible >= MAX_VISIBLE_BRANDS_PER_COLUMN) {
                throw new ResourceConflictException("No se pueden tener más de "
                        + MAX_VISIBLE_BRANDS_PER_COLUMN + " marcas visibles en la columna " + columnIndex);
            }
        }

        NavItem item = new NavItem();
        item.setSection(section);
        item.setColumnIndex(columnIndex);
        item.setOrderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 0);
        item.setIsVisible(isVisible);

        applyTargetTypeFields(item, request, sectionId, null);

        NavItem saved = navItemRepository.save(item);
        log.info("Elemento de navegación creado: ID={}, sectionId={}, targetType={}",
                saved.getId(), sectionId, saved.getTargetType());
        return mapToItemResponse(saved);
    }

    @Transactional
    public NavItemResponse updateItem(Long id, NavItemRequest request) {
        NavItem item = navItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elemento de navegación no encontrado con ID: " + id));

        if (request == null || request.getTargetType() == null) {
            throw new BusinessRuleException("El tipo de destino es obligatorio");
        }

        NavSection section = item.getSection();
        boolean isBrandsSection = BRANDS_SECTION_CODE.equalsIgnoreCase(section.getCode());
        Integer targetCol = request.getColumnIndex() != null ? request.getColumnIndex() : item.getColumnIndex();
        boolean targetVisible = request.getIsVisible() != null ? request.getIsVisible() : item.getIsVisible();

        if (isBrandsSection) {
            if (targetCol != 0 && targetCol != 1) {
                throw new BusinessRuleException("La sección de marcas solo admite las columnas 0 y 1");
            }
            if (targetVisible && request.getTargetType() == NavTargetType.BRAND) {
                long totalVisibleExcl = navItemRepository.countVisibleBrandsBySectionIdExcludingItem(section.getId(), id);
                if (totalVisibleExcl >= MAX_VISIBLE_BRANDS_TOTAL) {
                    throw new ResourceConflictException("La sección de marcas no puede tener más de "
                            + MAX_VISIBLE_BRANDS_TOTAL + " marcas visibles");
                }
                long colVisibleExcl = navItemRepository.countVisibleBrandsBySectionIdAndColumnIndexExcludingItem(
                        section.getId(), targetCol, id);
                if (colVisibleExcl >= MAX_VISIBLE_BRANDS_PER_COLUMN) {
                    throw new ResourceConflictException("No se pueden tener más de "
                            + MAX_VISIBLE_BRANDS_PER_COLUMN + " marcas visibles en la columna " + targetCol);
                }
            }
        } else {
            if (targetCol < 0) {
                throw new BusinessRuleException("El índice de columna no puede ser negativo");
            }
        }

        item.setColumnIndex(targetCol);
        if (request.getOrderIndex() != null) {
            item.setOrderIndex(request.getOrderIndex());
        }
        item.setIsVisible(targetVisible);

        applyTargetTypeFields(item, request, section.getId(), id);

        NavItem updated = navItemRepository.save(item);
        log.info("Elemento de navegación actualizado: ID={}, sectionId={}, targetType={}",
                updated.getId(), section.getId(), updated.getTargetType());
        return mapToItemResponse(updated);
    }

    @Transactional
    public NavItemResponse updateItemStatus(Long id, Boolean isVisible) {
        if (isVisible == null) {
            throw new IllegalArgumentException("El estado isVisible es obligatorio");
        }
        NavItem item = navItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elemento de navegación no encontrado con ID: " + id));

        NavSection section = item.getSection();
        boolean isBrandsSection = BRANDS_SECTION_CODE.equalsIgnoreCase(section.getCode());

        if (Boolean.TRUE.equals(isVisible) && !Boolean.TRUE.equals(item.getIsVisible())) {
            if (isBrandsSection && item.getTargetType() == NavTargetType.BRAND) {
                long totalVisible = navItemRepository.countVisibleBrandsBySectionId(section.getId());
                if (totalVisible >= MAX_VISIBLE_BRANDS_TOTAL) {
                    throw new ResourceConflictException("La sección de marcas no puede tener más de "
                            + MAX_VISIBLE_BRANDS_TOTAL + " marcas visibles");
                }
                long colVisible = navItemRepository.countVisibleBrandsBySectionIdAndColumnIndex(
                        section.getId(), item.getColumnIndex());
                if (colVisible >= MAX_VISIBLE_BRANDS_PER_COLUMN) {
                    throw new ResourceConflictException("No se pueden tener más de "
                            + MAX_VISIBLE_BRANDS_PER_COLUMN + " marcas visibles en la columna " + item.getColumnIndex());
                }
            }
        }

        item.setIsVisible(isVisible);
        NavItem saved = navItemRepository.save(item);
        log.info("Estado del elemento ID={} actualizado a isVisible={}", saved.getId(), saved.getIsVisible());
        return mapToItemResponse(saved);
    }

    @Transactional
    public void deleteItem(Long id) {
        NavItem item = navItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elemento de navegación no encontrado con ID: " + id));

        navItemRepository.delete(item);
        log.info("Elemento de navegación ID={} eliminado", id);
    }

    @Transactional
    public List<NavItemResponse> reorderItems(Long sectionId, ReorderItemsRequest request) {
        NavSection section = navSectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sección de navegación no encontrada con ID: " + sectionId));

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La lista de elementos a reordenar es obligatoria");
        }

        List<OrderItemPosition> positions = request.getItems();
        Set<Long> uniqueIds = new HashSet<>();
        for (OrderItemPosition pos : positions) {
            if (!uniqueIds.add(pos.getId())) {
                throw new BusinessRuleException("La lista contiene elementos duplicados: ID " + pos.getId());
            }
        }

        List<NavItem> sectionItems = navItemRepository.findBySectionIdOrderByColumnIndexAscOrderIndexAsc(sectionId);
        Map<Long, NavItem> itemMap = sectionItems.stream().collect(Collectors.toMap(NavItem::getId, Function.identity()));

        for (OrderItemPosition pos : positions) {
            if (!itemMap.containsKey(pos.getId())) {
                throw new BusinessRuleException("El elemento con ID " + pos.getId()
                        + " no pertenece a la sección con ID " + sectionId);
            }
        }

        boolean isBrandsSection = BRANDS_SECTION_CODE.equalsIgnoreCase(section.getCode());
        if (isBrandsSection) {
            Map<Integer, Integer> visibleCountByColumn = new HashMap<>();
            for (OrderItemPosition pos : positions) {
                int col = pos.getColumnIndex();
                if (col != 0 && col != 1) {
                    throw new BusinessRuleException("La sección de marcas solo admite las columnas 0 y 1");
                }
                NavItem it = itemMap.get(pos.getId());
                if (Boolean.TRUE.equals(it.getIsVisible()) && it.getTargetType() == NavTargetType.BRAND) {
                    int updatedCount = visibleCountByColumn.merge(col, 1, Integer::sum);
                    if (updatedCount > MAX_VISIBLE_BRANDS_PER_COLUMN) {
                        throw new ResourceConflictException("No se pueden tener más de "
                                + MAX_VISIBLE_BRANDS_PER_COLUMN + " marcas visibles en la columna " + col);
                    }
                }
            }
        }

        for (OrderItemPosition pos : positions) {
            NavItem it = itemMap.get(pos.getId());
            it.setColumnIndex(pos.getColumnIndex());
            it.setOrderIndex(pos.getOrderIndex());
        }
        navItemRepository.saveAll(itemMap.values());

        return navItemRepository.findBySectionIdOrderByColumnIndexAscOrderIndexAsc(sectionId).stream()
                .map(this::mapToItemResponse)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // HELPERS DE VALIDACIÓN Y MAPEO
    // =========================================================================

    private void applyTargetTypeFields(NavItem item, NavItemRequest request, Long sectionId, Long currentItemId) {
        NavTargetType targetType = request.getTargetType();
        item.setTargetType(targetType);

        switch (targetType) {
            case BRAND -> {
                if (request.getBrandId() == null) {
                    throw new BusinessRuleException("El ID de la marca es obligatorio para el destino BRAND");
                }
                if (request.getCategoryId() != null || StringUtils.hasText(request.getCustomUrl())) {
                    throw new BusinessRuleException("Un elemento de tipo BRAND no debe tener category ni customUrl");
                }
                Brand brand = brandRepository.findById(request.getBrandId())
                        .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + request.getBrandId()));

                boolean duplicate = currentItemId != null
                        ? navItemRepository.existsBySectionIdAndBrandIdAndIdNot(sectionId, brand.getId(), currentItemId)
                        : navItemRepository.existsBySectionIdAndBrandId(sectionId, brand.getId());

                if (duplicate) {
                    throw new ResourceConflictException("La marca ya se encuentra asignada a esta sección");
                }

                item.setBrand(brand);
                item.setCategory(null);
                item.setCustomUrl(null);
                item.setLabel(null);
            }
            case CATEGORY -> {
                if (request.getCategoryId() == null) {
                    throw new BusinessRuleException("El ID de la categoría es obligatorio para el destino CATEGORY");
                }
                if (request.getBrandId() != null || StringUtils.hasText(request.getCustomUrl())) {
                    throw new BusinessRuleException("Un elemento de tipo CATEGORY no debe tener brand ni customUrl");
                }
                Category category = categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.getCategoryId()));

                boolean duplicate = currentItemId != null
                        ? navItemRepository.existsBySectionIdAndCategoryIdAndIdNot(sectionId, category.getId(), currentItemId)
                        : navItemRepository.existsBySectionIdAndCategoryId(sectionId, category.getId());

                if (duplicate) {
                    throw new ResourceConflictException("La categoría ya se encuentra asignada a esta sección");
                }

                item.setCategory(category);
                item.setBrand(null);
                item.setCustomUrl(null);
                item.setLabel(StringUtils.hasText(request.getLabel()) ? request.getLabel().trim() : null);
            }
            case CUSTOM_LINK -> {
                if (request.getBrandId() != null || request.getCategoryId() != null) {
                    throw new BusinessRuleException("Un elemento de tipo CUSTOM_LINK no debe tener brand ni category");
                }
                if (!StringUtils.hasText(request.getLabel())) {
                    throw new BusinessRuleException("La etiqueta es obligatoria para un enlace personalizado");
                }
                validateCustomUrl(request.getCustomUrl());

                item.setBrand(null);
                item.setCategory(null);
                item.setLabel(request.getLabel().trim());
                item.setCustomUrl(request.getCustomUrl().trim());
            }
        }
    }

    public static void validateCustomUrl(String customUrl) {
        if (!StringUtils.hasText(customUrl)) {
            throw new BusinessRuleException("La URL personalizada es obligatoria");
        }
        String trimmed = customUrl.trim();
        if (trimmed.length() > 500) {
            throw new BusinessRuleException("La URL personalizada no puede superar los 500 caracteres");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:") || lower.startsWith("file:")) {
            throw new BusinessRuleException("La URL personalizada no es segura o contiene un protocolo no permitido");
        }
        if (trimmed.startsWith("/")) {
            if (trimmed.startsWith("//")) {
                throw new BusinessRuleException("Las rutas relativas no pueden comenzar con //");
            }
        } else if (lower.startsWith("https://")) {
            // URL externa válida con HTTPS
        } else {
            throw new BusinessRuleException("Las URLs externas deben utilizar HTTPS y las rutas internas deben comenzar con /");
        }
    }

    public NavSectionResponse mapToSectionResponse(NavSection section) {
        List<NavItemResponse> items = section.getItems() != null
                ? section.getItems().stream()
                .sorted(Comparator.comparing(NavItem::getColumnIndex).thenComparing(NavItem::getOrderIndex))
                .map(this::mapToItemResponse)
                .collect(Collectors.toList())
                : Collections.emptyList();

        return NavSectionResponse.builder()
                .id(section.getId())
                .code(section.getCode())
                .label(section.getLabel())
                .orderIndex(section.getOrderIndex())
                .isVisible(section.getIsVisible())
                .items(items)
                .build();
    }

    public NavItemResponse mapToItemResponse(NavItem item) {
        NavItemResponse.NavItemResponseBuilder builder = NavItemResponse.builder()
                .id(item.getId())
                .sectionId(item.getSection() != null ? item.getSection().getId() : null)
                .targetType(item.getTargetType())
                .columnIndex(item.getColumnIndex())
                .orderIndex(item.getOrderIndex())
                .isVisible(item.getIsVisible())
                .customUrl(item.getCustomUrl());

        if (item.getTargetType() == NavTargetType.BRAND && item.getBrand() != null) {
            Brand b = item.getBrand();
            builder.brandId(b.getId())
                    .brandName(b.getName())
                    .brandSlug(b.getSlug())
                    .brandIsActive(b.getIsActive())
                    .label(b.getName());
        } else if (item.getTargetType() == NavTargetType.CATEGORY && item.getCategory() != null) {
            Category c = item.getCategory();
            builder.categoryId(c.getId())
                    .categoryName(c.getName())
                    .categorySlug(c.getSlug())
                    .label(StringUtils.hasText(item.getLabel()) ? item.getLabel() : c.getName());
        } else if (item.getTargetType() == NavTargetType.CUSTOM_LINK) {
            builder.label(item.getLabel());
        }

        return builder.build();
    }
}

