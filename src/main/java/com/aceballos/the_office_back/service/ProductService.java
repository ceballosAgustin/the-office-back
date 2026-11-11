package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ProductImageResponse;
import com.aceballos.the_office_back.dto.ProductRequest;
import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.VariantDTO;
import com.aceballos.the_office_back.entity.Brand;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.BrandRepository;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.InquiryRepository;
import com.aceballos.the_office_back.repository.OrderItemRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
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
        validateProductRequest(request);

        Product product = new Product();
        product.setTitle(request.getTitle().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setIsActive(true);

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Categoría no encontrada con ID: " + request.getCategoryId()));
            product.setCategory(category);
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

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<ProductVariant> variants = new ArrayList<>();
            for (VariantDTO vDto : request.getVariants()) {
                ProductVariant variant = new ProductVariant();
                variant.setSize(vDto.getSize().trim());
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
     * Lista productos activos paginados para el catálogo público (solo expone
     * variantes activas).
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getActiveProducts(Long categoryId, Pageable pageable) {
        return getActiveProducts(categoryId, null, pageable);
    }

    /**
     * Lista productos activos paginados con filtro opcional por categoría y marca.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getActiveProducts(Long categoryId, String brandSlug, Pageable pageable) {
        return searchActiveProducts(null, categoryId, brandSlug, pageable);
    }

    /**
     * Busca productos activos por título para el catálogo público (solo expone
     * variantes activas).
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchActiveProducts(String title, Long categoryId, Pageable pageable) {
        return searchActiveProducts(title, categoryId, null, pageable);
    }

    /**
     * Busca productos activos combinando opcionalmente título, categoría y marca.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchActiveProducts(String title, Long categoryId, String brandSlug,
            Pageable pageable) {
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : null;
        String normalizedBrandSlug = StringUtils.hasText(brandSlug) ? brandSlug.trim().toLowerCase(Locale.ROOT) : null;
        return productRepository.findActiveProducts(categoryId, normalizedBrandSlug, normalizedTitle, pageable)
                .map(this::mapToPublicResponse);
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
        validateProductRequest(request);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));

        product.setTitle(request.getTitle().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Categoría no encontrada con ID: " + request.getCategoryId()));
            product.setCategory(category);
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

        reconcileVariants(product, request.getVariants());
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

    private void validateProductRequest(ProductRequest request) {
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
                if (!Boolean.FALSE.equals(vDto.getIsActive())) {
                    String normalizedSize = vDto.getSize().trim().toUpperCase();
                    if (!activeSizesInRequest.add(normalizedSize)) {
                        throw new BusinessRuleException(
                                "No se permiten variantes activas duplicadas para el mismo talle: "
                                        + vDto.getSize().trim());
                    }
                }
            }
        }
    }

    private void reconcileVariants(Product product, List<VariantDTO> incomingVariants) {
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
            String normalizedSize = vDto.getSize().trim();
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

                if (!existing.getSize().trim().equalsIgnoreCase(normalizedSize)) {
                    if (hasHistoricalReferences(existing.getId())) {
                        throw new BusinessRuleException(
                                "No se puede cambiar el talle ('" + existing.getSize() + "' a '" + normalizedSize +
                                        "') de una variante que posee órdenes o consultas históricas. " +
                                        "Desactive la variante anterior y cree una nueva.");
                    }
                    existing.setSize(normalizedSize);
                }

                existing.setStock(stock);
                existing.setIsActive(active);
            } else {
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
                String sizeKey = variant.getSize().trim().toUpperCase();
                if (!activeSizes.add(sizeKey)) {
                    throw new BusinessRuleException(
                            "No pueden existir dos variantes activas con el mismo talle: " + variant.getSize());
                }
            }
        }
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
