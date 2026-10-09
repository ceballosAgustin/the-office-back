package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ProductRequest;
import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.VariantDTO;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductVariant;
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
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    /**
     * Crea un producto recibiendo el JSON serializado como String y el archivo de imagen.
     */
    @Transactional
    public ProductResponse createProduct(String productJson, MultipartFile file) {
        ProductRequest request;
        try {
            request = objectMapper.readValue(productJson, ProductRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error al deserializar JSON de producto: {}", e.getMessage());
            throw new IllegalArgumentException("El formato del JSON del producto es inválido", e);
        }

        return createProduct(request, file);
    }

    /**
     * Sobrecarga directa con ProductRequest ya tipado y el archivo de imagen opcional.
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest request, MultipartFile file) {
        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            imageUrl = cloudinaryService.uploadImage(file);
        }

        Product product = new Product();
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setBaseStock(request.getBaseStock() != null ? request.getBaseStock() : 0);
        product.setImageUrl(imageUrl);
        product.setIsActive(true);

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
     * Lista productos activos paginados para el catálogo público.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        return productRepository.findByIsActiveTrue(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Busca productos activos por título con paginación para el catálogo público.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchActiveProducts(String title, Pageable pageable) {
        if (title == null || title.trim().isEmpty()) {
            return getActiveProducts(pageable);
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
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));
        return mapToResponse(product);
    }

    /**
     * Actualiza un producto recibiendo el JSON serializado como String y el archivo opcional.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, String productJson, MultipartFile file) {
        ProductRequest request;
        try {
            request = objectMapper.readValue(productJson, ProductRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error al deserializar JSON de actualización de producto: {}", e.getMessage());
            throw new IllegalArgumentException("El formato del JSON del producto es inválido", e);
        }

        return updateProduct(id, request, file);
    }

    /**
     * Actualiza los datos de un producto y opcionalmente reemplaza la imagen si se envía un archivo nuevo.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request, MultipartFile file) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));

        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        if (request.getBaseStock() != null) {
            product.setBaseStock(request.getBaseStock());
        }

        // Si se provee una nueva imagen, se sube y actualiza la URL
        if (file != null && !file.isEmpty()) {
            String newImageUrl = cloudinaryService.uploadImage(file);
            product.setImageUrl(newImageUrl);
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
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));
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

        return ProductResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .baseStock(product.getBaseStock())
                .imageUrl(product.getImageUrl())
                .isActive(product.getIsActive())
                .variants(variantDTOs)
                .build();
    }
}
