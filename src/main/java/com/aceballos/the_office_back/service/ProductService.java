package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.FilterOptionsResponse;
import com.aceballos.the_office_back.dto.ProductImageResponse;
import com.aceballos.the_office_back.dto.ProductRequest;
import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.VariantDTO;
import com.aceballos.the_office_back.entity.Brand;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductGender;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.BrandRepository;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.InquiryRepository;
import com.aceballos.the_office_back.repository.OrderItemRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
import com.aceballos.the_office_back.repository.ProductVariantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final OrderItemRepository orderItemRepository;
    private final InquiryRepository inquiryRepository;

    /**
     * Calcula la única fuente de verdad del stock general del producto:
     * la suma del stock de todas sus variantes activas.
     */
    public static int calculateActiveStock(Product product) {
        if (product == null || product.getVariants() == null) {
            return 0;
        }
        return product.getVariants().stream()
                .filter(v -> !Boolean.FALSE.equals(v.getIsActive()))
                .mapToInt(v -> v.getStock() != null ? v.getStock() : 0)
                .sum();
    }

    /**
     * Crea un producto recibiendo el JSON serializado como String y una lista de
     * archivos de imagen.
     */
    @Transactional
    public ProductResponse createProduct(String productJson, List<MultipartFile> files) {
        ProductRequest request = deserializeProductRequest(productJson);
        return createProduct(request, files);
    }

    /**
     * Sobrecarga directa con ProductRequest y lista de imágenes.
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest request, List<MultipartFile> files) {
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Categoría no encontrada con ID: " + request.getCategoryId()));
        }

        boolean isSneakers = isSneakersCategory(category);
        validateProductRequest(request, isSneakers);

        Product product = new Product();
        product.setTitle(request.getTitle().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setIsActive(true);
        product.setGender(request.getGender() != null ? request.getGender() : ProductGender.UNISEX);
        product.setCategory(category);

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Marca no encontrada con ID: " + request.getBrandId()));
            if (!Boolean.TRUE.equals(brand.getIsActive())) {
                throw new BusinessRuleException("La marca '" + brand.getName() + "' no se encuentra activa");
            }
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<ProductVariant> variants = new ArrayList<>();
            for (VariantDTO vDto : request.getVariants()) {
                ProductVariant variant = new ProductVariant();
                String normalizedSize = isSneakers
                        ? normalizeSneakerSize(vDto.getSize())
                        : vDto.getSize().trim();
                variant.setSize(normalizedSize);
                variant.setStock(vDto.getStock() != null ? vDto.getStock() : 0);
                variant.setIsActive(!Boolean.FALSE.equals(vDto.getIsActive()));
                variant.setProduct(product);
                variants.add(variant);
            }
            product.setVariants(variants);
        }

        product.setBaseStock(calculateActiveStock(product));

        List<String> uploadedPublicIds = new ArrayList<>();
        registerRollbackImageCleanup(uploadedPublicIds);

        try {
            appendUploadedImages(product, files, uploadedPublicIds);
            Product savedProduct = productRepository.save(product);
            return mapToAdminResponse(savedProduct);
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                cleanupUploadedImages(uploadedPublicIds);
            }
            throw ex;
        }
    }

    /**
     * Lista productos activos paginados para el catálogo público (compatibilidad
     * hacia atrás).
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getActiveProducts(Long categoryId, Pageable pageable) {
        return getActiveProducts(categoryId, null, null, null, null, pageable);
    }

    /**
     * Lista productos activos paginados con filtro opcional por categoría y marca
     * (compatibilidad hacia atrás).
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getActiveProducts(Long categoryId, String brandSlug, Pageable pageable) {
        return searchActiveProducts(null, categoryId, null, brandSlug, null, null, pageable);
    }

    /**
     * Lista productos activos paginados con filtros combinados completos.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getActiveProducts(
            Long categoryId,
            String categorySlug,
            String brandSlug,
            String gender,
            String variantSize,
            Pageable pageable) {
        return searchActiveProducts(null, categoryId, categorySlug, brandSlug, gender, variantSize, pageable);
    }

    /**
     * Busca productos activos por título para el catálogo público (compatibilidad
     * hacia atrás).
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchActiveProducts(String title, Long categoryId, Pageable pageable) {
        return searchActiveProducts(title, categoryId, null, null, null, null, pageable);
    }

    /**
     * Busca productos activos combinando opcionalmente título, categoría y marca
     * (compatibilidad hacia atrás).
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchActiveProducts(String title, Long categoryId, String brandSlug,
            Pageable pageable) {
        return searchActiveProducts(title, categoryId, null, brandSlug, null, null, pageable);
    }

    /**
     * Busca productos activos combinando título, categoría (por ID o slug), marca,
     * género y talle de variante con stock.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchActiveProducts(
            String title,
            Long categoryId,
            String categorySlug,
            String brandSlug,
            String gender,
            String variantSize,
            Pageable pageable) {

        Long resolvedCategoryId = resolveCategoryId(categoryId, categorySlug);
        if (resolvedCategoryId != null && resolvedCategoryId == -1L) {
            return Page.empty(pageable);
        }

        GenderFilterCriteria genderCriteria = resolveGenderCriteria(gender);
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : null;
        String normalizedBrandSlug = StringUtils.hasText(brandSlug) ? brandSlug.trim().toLowerCase(Locale.ROOT) : null;
        String normalizedVariantSize = resolveVariantSize(variantSize);

        return productRepository.findActiveProducts(
                resolvedCategoryId,
                normalizedBrandSlug,
                normalizedTitle,
                genderCriteria.hasFilter(),
                genderCriteria.genders(),
                normalizedVariantSize,
                pageable).map(this::mapToPublicResponse);
    }

    /**
     * Devuelve las opciones dinámicas de talles disponibles para los filtros
     * aplicados.
     * Solo considera productos activos y variantes activas con stock > 0.
     */
    @Transactional(readOnly = true)
    public FilterOptionsResponse getFilterOptions(
            Long categoryId,
            String categorySlug,
            String brandSlug,
            String gender,
            String title) {

        Long resolvedCategoryId = resolveCategoryId(categoryId, categorySlug);
        if (resolvedCategoryId != null && resolvedCategoryId == -1L) {
            return new FilterOptionsResponse(Collections.emptyList());
        }

        GenderFilterCriteria genderCriteria = resolveGenderCriteria(gender);
        String normalizedBrandSlug = StringUtils.hasText(brandSlug) ? brandSlug.trim().toLowerCase(Locale.ROOT) : null;
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : null;

        List<String> rawSizes = productVariantRepository.findAvailableSizesByFilters(
                resolvedCategoryId,
                normalizedBrandSlug,
                normalizedTitle,
                genderCriteria.hasFilter(),
                genderCriteria.genders());

        List<String> sortedSizes = sortSizesDeterministically(rawSizes);
        return new FilterOptionsResponse(sortedSizes);
    }

    /**
     * Resuelve el ID de categoría validando coherencia entre categoryId y
     * categorySlug.
     * Si no coinciden con la misma categoría, lanza BusinessRuleException (HTTP
     * 400).
     * Si el slug es inexistente, devuelve -1L para que la consulta retorne página
     * vacía sin error.
     */
    private Long resolveCategoryId(Long categoryId, String categorySlug) {
        boolean hasId = categoryId != null;
        boolean hasSlug = StringUtils.hasText(categorySlug);

        if (!hasId && !hasSlug) {
            return null;
        }

        String normalizedSlug = hasSlug ? categorySlug.trim().toLowerCase(Locale.ROOT) : null;

        if (hasId && hasSlug) {
            Category categoryById = categoryRepository.findById(categoryId).orElse(null);
            if (categoryById == null || !categoryById.getSlug().equalsIgnoreCase(normalizedSlug)) {
                throw new BusinessRuleException(
                        "El parámetro categoryId (" + categoryId + ") y category ('" + categorySlug
                                + "') no coinciden con la misma categoría");
            }
            return categoryById.getId();
        }

        if (hasId) {
            return categoryId;
        }

        Optional<Category> categoryBySlug = categoryRepository.findBySlug(normalizedSlug);
        if (categoryBySlug.isEmpty()) {
            return -1L;
        }
        return categoryBySlug.get().getId();
    }

    private record GenderFilterCriteria(boolean hasFilter, List<ProductGender> genders) {
    }

    private GenderFilterCriteria resolveGenderCriteria(String genderParam) {
        if (!StringUtils.hasText(genderParam)) {
            return new GenderFilterCriteria(false, List.of(ProductGender.UNISEX));
        }

        ProductGender gender;
        try {
            gender = ProductGender.fromString(genderParam);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("El parámetro de género no es válido: '" + genderParam
                    + "'. Valores permitidos: MEN, WOMEN, UNISEX.");
        }
        return switch (gender) {
            case MEN -> new GenderFilterCriteria(true, List.of(ProductGender.MEN, ProductGender.UNISEX));
            case WOMEN -> new GenderFilterCriteria(true, List.of(ProductGender.WOMEN, ProductGender.UNISEX));
            case UNISEX -> new GenderFilterCriteria(true, List.of(ProductGender.UNISEX));
        };
    }

    public static boolean isSneakersCategory(Category category) {
        return category != null && "sneakers".equalsIgnoreCase(category.getSlug());
    }

    public static String normalizeSneakerSize(String rawSize) {
        if (!StringUtils.hasText(rawSize)) {
            throw new BusinessRuleException("El talle de calzado es obligatorio y no puede estar vacío");
        }

        String clean = rawSize.trim();
        if (clean.toUpperCase(Locale.ROOT).endsWith("US")) {
            clean = clean.substring(0, clean.length() - 2).trim();
        }
        clean = clean.replace(',', '.');

        if (!clean.matches("^\\d+(\\.\\d+)?$")) {
            throw new BusinessRuleException(
                    "El talle de calzado '" + rawSize.trim() + "' no es un valor numérico válido. Debe ser un número mayor a 0 (ej: 7, 7.5, 8, 8.5)");
        }

        BigDecimal value;
        try {
            value = new BigDecimal(clean);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException(
                    "El talle de calzado '" + rawSize.trim() + "' tiene un formato numérico inválido");
        }

        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("El talle de calzado debe ser mayor a cero: " + rawSize.trim());
        }

        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > 1) {
            throw new BusinessRuleException(
                    "El talle de calzado '" + rawSize.trim() + "' admite como máximo un decimal significativo (ej: 7.5, 8.5)");
        }

        if (stripped.scale() <= 0) {
            return stripped.toBigInteger().toString();
        } else {
            return stripped.toPlainString();
        }
    }

    public static String toSneakerComparisonKey(String size) {
        if (!StringUtils.hasText(size)) {
            return "";
        }
        String clean = size.trim();
        if (clean.toUpperCase(Locale.ROOT).endsWith("US")) {
            clean = clean.substring(0, clean.length() - 2).trim();
        }
        clean = clean.replace(',', '.');
        if (clean.matches("^\\d+(\\.\\d+)?$")) {
            try {
                BigDecimal bd = new BigDecimal(clean);
                return bd.stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
            }
        }
        return size.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal tryParseNumericSize(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String clean = s.trim();
        if (clean.toUpperCase(Locale.ROOT).endsWith("US")) {
            clean = clean.substring(0, clean.length() - 2).trim();
        }
        clean = clean.replace(',', '.');
        if (!clean.matches("^\\d+(\\.\\d+)?$")) {
            return null;
        }
        try {
            BigDecimal bd = new BigDecimal(clean);
            if (bd.compareTo(BigDecimal.ZERO) > 0) {
                return bd;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private String resolveVariantSize(String variantSize) {
        if (!StringUtils.hasText(variantSize)) {
            return null;
        }
        String clean = variantSize.trim();
        if (clean.toUpperCase(Locale.ROOT).endsWith("US")) {
            clean = clean.substring(0, clean.length() - 2).trim();
        }
        String dotClean = clean.replace(',', '.');
        if (dotClean.matches("^\\d+(\\.\\d+)?$")) {
            try {
                BigDecimal bd = new BigDecimal(dotClean);
                if (bd.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal stripped = bd.stripTrailingZeros();
                    if (stripped.scale() <= 0) {
                        return stripped.toBigInteger().toString();
                    } else if (stripped.scale() == 1) {
                        return stripped.toPlainString();
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return clean;
    }

    public static List<String> sortSizesDeterministically(List<String> rawSizes) {
        if (rawSizes == null || rawSizes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> standardSizes = new HashMap<>();
        standardSizes.put("XXS", 1);
        standardSizes.put("XS", 2);
        standardSizes.put("S", 3);
        standardSizes.put("M", 4);
        standardSizes.put("L", 5);
        standardSizes.put("XL", 6);
        standardSizes.put("XXL", 7);
        standardSizes.put("2XL", 8);
        standardSizes.put("3XL", 9);

        Map<String, String> uniqueSizesMap = new LinkedHashMap<>();
        for (String s : rawSizes) {
            if (StringUtils.hasText(s)) {
                String trimmed = s.trim();
                BigDecimal numeric = tryParseNumericSize(trimmed);
                if (numeric != null) {
                    BigDecimal stripped = numeric.stripTrailingZeros();
                    String normalizedNumeric = (stripped.scale() <= 0)
                            ? stripped.toBigInteger().toString()
                            : stripped.toPlainString();
                    String key = stripped.toPlainString();
                    uniqueSizesMap.putIfAbsent(key, normalizedNumeric);
                } else {
                    uniqueSizesMap.putIfAbsent(trimmed.toUpperCase(Locale.ROOT), trimmed);
                }
            }
        }

        List<String> sizes = new ArrayList<>(uniqueSizesMap.values());
        sizes.sort((s1, s2) -> {
            BigDecimal n1 = tryParseNumericSize(s1);
            BigDecimal n2 = tryParseNumericSize(s2);
            if (n1 != null && n2 != null) {
                return n1.compareTo(n2);
            }
            if (n1 != null) {
                return -1;
            }
            if (n2 != null) {
                return 1;
            }

            String u1 = s1.trim().toUpperCase(Locale.ROOT);
            String u2 = s2.trim().toUpperCase(Locale.ROOT);

            Integer rank1 = standardSizes.get(u1);
            Integer rank2 = standardSizes.get(u2);
            if (rank1 != null && rank2 != null) {
                return Integer.compare(rank1, rank2);
            }
            if (rank1 != null) {
                return -1;
            }
            if (rank2 != null) {
                return 1;
            }

            return u1.compareTo(u2);
        });

        return sizes;
    }

    private static Double tryParseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Obtiene el detalle público de un producto activo por ID.
     * Rechaza productos desactivados y filtra variantes inactivas.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));
        return mapToPublicResponse(product);
    }

    /**
     * Listado administrativo paginado de productos activos e inactivos con filtros
     * opcionales.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAdminProducts(Boolean isActive, Long categoryId, String title, Pageable pageable) {
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : null;
        return productRepository.findAdminProducts(isActive, categoryId, normalizedTitle, pageable)
                .map(this::mapToAdminResponse);
    }

    /**
     * Detalle administrativo de un producto por ID, incluyendo inactivos y todas
     * sus variantes.
     */
    @Transactional(readOnly = true)
    public ProductResponse getAdminProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));
        return mapToAdminResponse(product);
    }

    /**
     * Actualiza un producto recibiendo el JSON serializado como String y lista de
     * archivos opcional.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, String productJson, List<MultipartFile> files) {
        ProductRequest request = deserializeProductRequest(productJson);
        return updateProduct(id, request, files);
    }

    /**
     * Actualiza los datos de un producto, reconcilia sus variantes por ID sin
     * perder referencias históricas,
     * recalcula el stock general desde las variantes activas y agrega nuevas
     * imágenes sin borrar las existentes.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request, List<MultipartFile> files) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));

        Category category;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Categoría no encontrada con ID: " + request.getCategoryId()));
            product.setCategory(category);
        } else {
            category = product.getCategory();
        }

        boolean isSneakers = isSneakersCategory(category);
        validateProductRequest(request, isSneakers);

        product.setTitle(request.getTitle().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        if (request.getGender() != null) {
            product.setGender(request.getGender());
        }

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Marca no encontrada con ID: " + request.getBrandId()));
            if (!Boolean.TRUE.equals(brand.getIsActive())) {
                throw new BusinessRuleException("La marca '" + brand.getName() + "' no se encuentra activa");
            }
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }

        reconcileVariants(product, request.getVariants(), isSneakers);
        product.setBaseStock(calculateActiveStock(product));

        List<String> uploadedPublicIds = new ArrayList<>();
        registerRollbackImageCleanup(uploadedPublicIds);

        try {
            appendUploadedImages(product, files, uploadedPublicIds);
            Product updatedProduct = productRepository.save(product);
            return mapToAdminResponse(updatedProduct);
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                cleanupUploadedImages(uploadedPublicIds);
            }
            throw ex;
        }
    }

    /**
     * Permite activar o desactivar un producto desde el panel administrativo.
     */
    @Transactional
    public ProductResponse updateProductStatus(Long id, Boolean isActive) {
        if (isActive == null) {
            throw new IllegalArgumentException("El estado isActive es obligatorio");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));
        product.setIsActive(isActive);
        Product saved = productRepository.save(product);
        return mapToAdminResponse(saved);
    }

    /**
     * Borrado lógico: Cambia el estado isActive a false para preservar el
     * historial.
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));
        product.setIsActive(false);
        productRepository.save(product);
    }

    /**
     * Elimina una fotografía individual del producto.
     * Si la imagen eliminada era la principal, asigna una nueva portada entre las
     * restantes.
     * La eliminación física en Cloudinary se ejecuta después del commit de la
     * transacción SQL.
     */
    @Transactional
    public ProductResponse deleteProductImage(Long productId, Long imageId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + productId));

        ProductImage targetImage = product.getImages().stream()
                .filter(img -> imageId.equals(img.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Imagen con ID " + imageId + " no encontrada en el producto con ID: " + productId));

        boolean wasPrimary = Boolean.TRUE.equals(targetImage.getIsPrimary());
        String publicIdToDelete = targetImage.getPublicId();

        product.getImages().remove(targetImage);

        // Ordenar y reindexar las imágenes restantes
        product.getImages().sort(
                Comparator.comparing(img -> img.getOrderIndex() != null ? img.getOrderIndex() : Integer.MAX_VALUE));
        for (int i = 0; i < product.getImages().size(); i++) {
            product.getImages().get(i).setOrderIndex(i);
        }

        // Si se eliminó la imagen principal y quedan otras imágenes, asignar la primera
        // como nueva portada
        if (wasPrimary && !product.getImages().isEmpty()) {
            boolean anyPrimary = product.getImages().stream().anyMatch(img -> Boolean.TRUE.equals(img.getIsPrimary()));
            if (!anyPrimary) {
                product.getImages().get(0).setIsPrimary(true);
            }
        }

        Product savedProduct = productRepository.save(product);

        schedulePostCommitCloudinaryDeletion(publicIdToDelete, imageId);

        return mapToAdminResponse(savedProduct);
    }

    /**
     * Establece una imagen específica del producto como su fotografía principal
     * (portada).
     */
    @Transactional
    public ProductResponse setPrimaryProductImage(Long productId, Long imageId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + productId));

        boolean existsInProduct = product.getImages().stream()
                .anyMatch(img -> imageId.equals(img.getId()));
        if (!existsInProduct) {
            throw new ResourceNotFoundException(
                    "Imagen con ID " + imageId + " no encontrada en el producto con ID: " + productId);
        }

        for (ProductImage img : product.getImages()) {
            img.setIsPrimary(imageId.equals(img.getId()));
        }

        Product savedProduct = productRepository.save(product);
        return mapToAdminResponse(savedProduct);
    }

    /**
     * Reordena la galería de fotografías de un producto de acuerdo con la lista de
     * IDs proporcionada.
     */
    @Transactional
    public ProductResponse reorderProductImages(Long productId, List<Long> orderedImageIds) {
        if (orderedImageIds == null || orderedImageIds.isEmpty()) {
            throw new IllegalArgumentException("La lista de IDs de imágenes no puede estar vacía");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + productId));

        Map<Long, ProductImage> imageMap = new HashMap<>();
        for (ProductImage img : product.getImages()) {
            imageMap.put(img.getId(), img);
        }

        Set<Long> uniqueIds = new HashSet<>(orderedImageIds);
        if (uniqueIds.size() != orderedImageIds.size()) {
            throw new BusinessRuleException("La lista de IDs de imágenes contiene identificadores duplicados");
        }

        if (uniqueIds.size() != imageMap.size() || !imageMap.keySet().containsAll(uniqueIds)) {
            throw new BusinessRuleException(
                    "La lista de IDs debe contener exactamente todas las imágenes pertenecientes al producto");
        }

        for (int i = 0; i < orderedImageIds.size(); i++) {
            ProductImage img = imageMap.get(orderedImageIds.get(i));
            img.setOrderIndex(i);
        }

        product.getImages().sort(Comparator.comparing(ProductImage::getOrderIndex));
        Product savedProduct = productRepository.save(product);
        return mapToAdminResponse(savedProduct);
    }

    private ProductRequest deserializeProductRequest(String productJson) {
        if (!StringUtils.hasText(productJson)) {
            throw new IllegalArgumentException("El JSON del producto es obligatorio");
        }
        try {
            return objectMapper.readValue(productJson, ProductRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error al deserializar JSON de producto: {}", e.getMessage());
            throw new IllegalArgumentException("El formato del JSON del producto es inválido", e);
        }
    }

    private void validateProductRequest(ProductRequest request, boolean isSneakers) {
        if (request == null) {
            throw new IllegalArgumentException("Los datos del producto son obligatorios");
        }

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        if (request.getVariants() != null) {
            Set<String> activeSizesInRequest = new HashSet<>();
            for (VariantDTO vDto : request.getVariants()) {
                if (vDto.getSize() == null || vDto.getSize().trim().isEmpty()) {
                    throw new BusinessRuleException("El talle de variante es obligatorio");
                }
                if (!Boolean.FALSE.equals(vDto.getIsActive())) {
                    String sizeKey;
                    if (isSneakers) {
                        if (vDto.getId() == null) {
                            normalizeSneakerSize(vDto.getSize());
                        }
                        sizeKey = toSneakerComparisonKey(vDto.getSize());
                    } else {
                        sizeKey = vDto.getSize().trim().toUpperCase(Locale.ROOT);
                    }

                    if (!activeSizesInRequest.add(sizeKey)) {
                        throw new BusinessRuleException(
                                "No se permiten variantes activas duplicadas para el mismo talle: "
                                        + vDto.getSize().trim());
                    }
                }
            }
        }
    }

    private void validateProductRequest(ProductRequest request) {
        validateProductRequest(request, false);
    }

    private void reconcileVariants(Product product, List<VariantDTO> incomingVariants, boolean isSneakers) {
        if (incomingVariants == null) {
            return;
        }

        if (product.getVariants() == null) {
            product.setVariants(new ArrayList<>());
        }

        Map<Long, ProductVariant> existingById = new HashMap<>();
        for (ProductVariant existing : product.getVariants()) {
            if (existing.getId() != null) {
                existingById.put(existing.getId(), existing);
            }
        }

        Set<Long> includedIds = new HashSet<>();

        for (VariantDTO vDto : incomingVariants) {
            int stock = vDto.getStock() != null ? vDto.getStock() : 0;
            boolean active = !Boolean.FALSE.equals(vDto.getIsActive());

            if (vDto.getId() != null) {
                if (!includedIds.add(vDto.getId())) {
                    throw new BusinessRuleException(
                            "El ID de variante " + vDto.getId() + " está duplicado en la petición");
                }

                ProductVariant existing = existingById.get(vDto.getId());
                if (existing == null) {
                    throw new BusinessRuleException(
                            "La variante con ID " + vDto.getId() + " no pertenece al producto con ID "
                                    + product.getId());
                }

                String incomingRawSize = vDto.getSize() != null ? vDto.getSize().trim() : "";
                String existingSize = existing.getSize() != null ? existing.getSize().trim() : "";

                if (!existingSize.equalsIgnoreCase(incomingRawSize)) {
                    String normalizedSize = isSneakers
                            ? normalizeSneakerSize(incomingRawSize)
                            : incomingRawSize;

                    if (!existingSize.equalsIgnoreCase(normalizedSize)) {
                        if (hasHistoricalReferences(existing.getId())) {
                            throw new BusinessRuleException(
                                    "No se puede cambiar el talle ('" + existing.getSize() + "' a '" + normalizedSize +
                                            "') de una variante que posee órdenes o consultas históricas. " +
                                            "Desactive la variante anterior y cree una nueva.");
                        }
                        existing.setSize(normalizedSize);
                    }
                }

                existing.setStock(stock);
                existing.setIsActive(active);
            } else {
                String normalizedSize = isSneakers
                        ? normalizeSneakerSize(vDto.getSize())
                        : vDto.getSize().trim();

                ProductVariant newVariant = new ProductVariant();
                newVariant.setSize(normalizedSize);
                newVariant.setStock(stock);
                newVariant.setIsActive(active);
                newVariant.setProduct(product);
                product.getVariants().add(newVariant);
            }
        }

        // Las variantes existentes omitidas en la colección se desactivan sin
        // eliminarse físicamente
        for (ProductVariant existing : existingById.values()) {
            if (!includedIds.contains(existing.getId())) {
                existing.setIsActive(false);
            }
        }

        // Verificar que entre las variantes activas resultantes no queden talles
        // duplicados
        Set<String> activeSizes = new HashSet<>();
        for (ProductVariant variant : product.getVariants()) {
            if (!Boolean.FALSE.equals(variant.getIsActive())) {
                String sizeKey = isSneakers
                        ? toSneakerComparisonKey(variant.getSize())
                        : variant.getSize().trim().toUpperCase(Locale.ROOT);
                if (!activeSizes.add(sizeKey)) {
                    throw new BusinessRuleException(
                            "No pueden existir dos variantes activas con el mismo talle: " + variant.getSize());
                }
            }
        }
    }

    private void reconcileVariants(Product product, List<VariantDTO> incomingVariants) {
        reconcileVariants(product, incomingVariants, isSneakersCategory(product.getCategory()));
    }

    private boolean hasHistoricalReferences(Long variantId) {
        if (variantId == null) {
            return false;
        }
        return orderItemRepository.existsByProductVariantId(variantId)
                || inquiryRepository.existsInquiryItemByVariantId(variantId);
    }

    private void appendUploadedImages(Product product, List<MultipartFile> files, List<String> uploadedPublicIds) {
        if (files == null || files.isEmpty()) {
            return;
        }

        if (product.getImages() == null) {
            product.setImages(new ArrayList<>());
        }

        boolean hasPrimary = product.getImages().stream()
                .anyMatch(img -> Boolean.TRUE.equals(img.getIsPrimary()));
        int nextIndex = product.getImages().stream()
                .mapToInt(img -> img.getOrderIndex() != null ? img.getOrderIndex() : 0)
                .max()
                .orElse(-1) + 1;

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                CloudinaryService.CloudinaryUploadResult uploadResult = cloudinaryService.uploadImageWithDetails(file);
                if (uploadResult != null && uploadResult.imageUrl() != null) {
                    if (StringUtils.hasText(uploadResult.publicId())) {
                        uploadedPublicIds.add(uploadResult.publicId());
                    }
                    ProductImage productImage = ProductImage.builder()
                            .imageUrl(uploadResult.imageUrl())
                            .publicId(uploadResult.publicId())
                            .isPrimary(!hasPrimary)
                            .orderIndex(nextIndex)
                            .product(product)
                            .build();
                    product.getImages().add(productImage);
                    hasPrimary = true;
                    nextIndex++;
                }
            }
        }
    }

    private void registerRollbackImageCleanup(List<String> uploadedPublicIds) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK && !uploadedPublicIds.isEmpty()) {
                        cleanupUploadedImages(uploadedPublicIds);
                    }
                }
            });
        }
    }

    private void cleanupUploadedImages(List<String> uploadedPublicIds) {
        for (String publicId : uploadedPublicIds) {
            log.warn("Ejecutando limpieza compensatoria en Cloudinary para public_id '{}' tras fallo en persistencia",
                    publicId);
            cloudinaryService.deleteImage(publicId);
        }
    }

    private void schedulePostCommitCloudinaryDeletion(String publicId, Long imageId) {
        if (!StringUtils.hasText(publicId)) {
            log.info(
                    "La imagen ID {} fue eliminada de la base de datos. No poseía public_id registrado, por lo que el archivo antiguo puede permanecer almacenado en Cloudinary.",
                    imageId);
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cloudinaryService.deleteImage(publicId);
                }
            });
        } else {
            cloudinaryService.deleteImage(publicId);
        }
    }

    public ProductResponse mapToPublicResponse(Product product) {
        return mapToResponse(product, false);
    }

    public ProductResponse mapToAdminResponse(Product product) {
        return mapToResponse(product, true);
    }

    public ProductResponse mapToResponse(Product product) {
        return mapToResponse(product, true);
    }

    private ProductResponse mapToResponse(Product product, boolean includeInactiveVariants) {
        List<VariantDTO> variantDTOs = new ArrayList<>();
        if (product.getVariants() != null) {
            variantDTOs = product.getVariants().stream()
                    .filter(v -> includeInactiveVariants || !Boolean.FALSE.equals(v.getIsActive()))
                    .map(v -> VariantDTO.builder()
                            .id(v.getId())
                            .size(v.getSize())
                            .stock(v.getStock())
                            .isActive(!Boolean.FALSE.equals(v.getIsActive()))
                            .build())
                    .collect(Collectors.toList());
        }

        List<ProductImageResponse> imageDTOs = new ArrayList<>();
        if (product.getImages() != null) {
            imageDTOs = product.getImages().stream()
                    .sorted(Comparator
                            .comparing(img -> img.getOrderIndex() != null ? img.getOrderIndex() : Integer.MAX_VALUE))
                    .map(img -> ProductImageResponse.builder()
                            .id(img.getId())
                            .imageUrl(img.getImageUrl())
                            .publicId(img.getPublicId())
                            .isPrimary(img.getIsPrimary())
                            .orderIndex(img.getOrderIndex())
                            .build())
                    .collect(Collectors.toList());
        }

        return ProductResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .baseStock(calculateActiveStock(product))
                .isActive(product.getIsActive())
                .gender(product.getGender() != null ? product.getGender() : ProductGender.UNISEX)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .brandSlug(product.getBrand() != null ? product.getBrand().getSlug() : null)
                .images(imageDTOs)
                .variants(variantDTOs)
                .build();
    }
}
