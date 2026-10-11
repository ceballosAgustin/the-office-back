package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ProductImageResponse;
import com.aceballos.the_office_back.dto.ProductRequest;
import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.VariantDTO;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    /**
     * Crea un producto recibiendo el JSON serializado como String y una lista de archivos de imagen.
     */
    @Transactional
    public ProductResponse createProduct(String productJson, List<MultipartFile> files) {
        ProductRequest request;
        try {
            request = objectMapper.readValue(productJson, ProductRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error al deserializar JSON de producto: {}", e.getMessage());
            throw new IllegalArgumentException("El formato del JSON del producto es inválido", e);
        }

        return createProduct(request, files);
    }

    /**
     * Sobrecarga directa con ProductRequest y lista de imágenes.
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest request, List<MultipartFile> files) {
        Product product = new Product();
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setBaseStock(request.getBaseStock() != null ? request.getBaseStock() : 0);
        product.setIsActive(true);

        // Asignación de categoría si viene especificada
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.getCategoryId()));
            product.setCategory(category);
        }

        // Subida de múltiples imágenes y asociación a ProductImage
        if (files != null && !files.isEmpty()) {
            List<ProductImage> productImages = new ArrayList<>();
            int index = 0;
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String uploadedUrl = cloudinaryService.uploadImage(file);
                    if (uploadedUrl != null) {
                        ProductImage productImage = ProductImage.builder()
                                .imageUrl(uploadedUrl)
                                .isPrimary(index == 0) // La primera imagen se marca como portada
                                .orderIndex(index)
                                .product(product)
                                .build();
                        productImages.add(productImage);
                        index++;
                    }
                }
            }
            product.setImages(productImages);
        }

        // Creación y asociación de variantes de talle/stock
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<ProductVariant> variants = new ArrayList<>();
            for (VariantDTO vDto : request.getVariants()) {
                ProductVariant variant = new ProductVariant();
                variant.setSize(vDto.getSize());
                variant.setStock(vDto.getStock() != null ? vDto.getStock() : 0);
                variant.setProduct(product);
                variants.add(variant);
            }
            product.setVariants(variants);
        }

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    /**
     * Lista productos activos paginados con filtro opcional por categoría.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getActiveProducts(Long categoryId, Pageable pageable) {
        if (categoryId != null) {
            return productRepository.findByIsActiveTrueAndCategoryId(categoryId, pageable)
                    .map(this::mapToResponse);
        }
        return productRepository.findByIsActiveTrue(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Busca productos activos por título con paginación y filtro opcional por categoría.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchActiveProducts(String title, Long categoryId, Pageable pageable) {
        if (title == null || title.trim().isEmpty()) {
            return getActiveProducts(categoryId, pageable);
        }
        if (categoryId != null) {
            return productRepository.findByTitleContainingIgnoreCaseAndIsActiveTrueAndCategoryId(title.trim(), categoryId, pageable)
                    .map(this::mapToResponse);
        }
        return productRepository.findByTitleContainingIgnoreCaseAndIsActiveTrue(title.trim(), pageable)
                .map(this::mapToResponse);
    }

    /**
     * Obtiene el detalle de un producto por ID.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));
        return mapToResponse(product);
    }

    /**
     * Actualiza un producto recibiendo el JSON serializado como String y lista de archivos opcional.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, String productJson, List<MultipartFile> files) {
        ProductRequest request;
        try {
            request = objectMapper.readValue(productJson, ProductRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error al deserializar JSON de actualización de producto: {}", e.getMessage());
            throw new IllegalArgumentException("El formato del JSON del producto es inválido", e);
        }

        return updateProduct(id, request, files);
    }

    /**
     * Actualiza los datos de un producto y opcionalmente añade/reemplaza imágenes.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request, List<MultipartFile> files) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));

        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        if (request.getBaseStock() != null) {
            product.setBaseStock(request.getBaseStock());
        }

        // Actualizar categoría si se proporciona
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.getCategoryId()));
            product.setCategory(category);
        }

        // Si se proveen nuevas imágenes, se procesan y se agregan a la colección
        if (files != null && !files.isEmpty()) {
            product.getImages().clear();
            int index = 0;
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String uploadedUrl = cloudinaryService.uploadImage(file);
                    if (uploadedUrl != null) {
                        ProductImage productImage = ProductImage.builder()
                                .imageUrl(uploadedUrl)
                                .isPrimary(index == 0)
                                .orderIndex(index)
                                .product(product)
                                .build();
                        product.getImages().add(productImage);
                        index++;
                    }
                }
            }
        }

        // Actualizar variantes si se incluyen en la petición
        if (request.getVariants() != null) {
            product.getVariants().clear();
            for (VariantDTO vDto : request.getVariants()) {
                ProductVariant variant = new ProductVariant();
                variant.setSize(vDto.getSize());
                variant.setStock(vDto.getStock() != null ? vDto.getStock() : 0);
                variant.setProduct(product);
                product.getVariants().add(variant);
            }
        }

        Product updatedProduct = productRepository.save(product);
        return mapToResponse(updatedProduct);
    }

    /**
     * Borrado lógico: Cambia el estado isActive a false para preservar el historial.
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID: " + id));
        product.setIsActive(false);
        productRepository.save(product);
    }

    /**
     * Mapeo de entidad a DTO de respuesta.
     */
    public ProductResponse mapToResponse(Product product) {
        List<VariantDTO> variantDTOs = new ArrayList<>();
        if (product.getVariants() != null) {
            variantDTOs = product.getVariants().stream()
                    .map(v -> VariantDTO.builder()
                            .id(v.getId())
                            .size(v.getSize())
                            .stock(v.getStock())
                            .build())
                    .collect(Collectors.toList());
        }

        List<ProductImageResponse> imageDTOs = new ArrayList<>();
        if (product.getImages() != null) {
            imageDTOs = product.getImages().stream()
                    .map(img -> ProductImageResponse.builder()
                            .id(img.getId())
                            .imageUrl(img.getImageUrl())
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
                .baseStock(product.getBaseStock())
                .isActive(product.getIsActive())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .images(imageDTOs)
                .variants(variantDTOs)
                .build();
    }
}
