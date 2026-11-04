package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.ReorderProductImagesRequest;
import com.aceballos.the_office_back.dto.UpdateProductStatusRequest;
import com.aceballos.the_office_back.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProductAdminController {

    private final ProductService productService;

    /**
     * Listado paginado de productos activos e inactivos para el panel de administración.
     * Ejemplo: GET /api/admin/products?isActive=false&categoryId=1&title=remera&page=0&size=12
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAdminProducts(
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "title", required = false) String title,
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.getAdminProducts(isActive, categoryId, title, pageable));
    }

    /**
     * Detalle completo de un producto (activo o inactivo) con todas sus variantes e imágenes.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getAdminProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getAdminProductById(id));
    }

    /**
     * Crear producto con imágenes mediante multipart/form-data.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @RequestParam("product") String productJson,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        ProductResponse created = productService.createProduct(productJson, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Actualizar datos, variantes y/o agregar imágenes a un producto existente.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestParam("product") String productJson,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        ProductResponse updated = productService.updateProduct(id, productJson, files);
        return ResponseEntity.ok(updated);
    }

    /**
     * Activar o desactivar un producto (isActive = true / false).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> updateProductStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductStatusRequest request) {
        return ResponseEntity.ok(productService.updateProductStatus(id, request.getIsActive()));
    }

    /**
     * Borrado lógico de un producto (isActive = false).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("message", "Producto desactivado correctamente"));
    }

    /**
     * Eliminar una fotografía individual de la galería de un producto.
     */
    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<ProductResponse> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        return ResponseEntity.ok(productService.deleteProductImage(productId, imageId));
    }

    /**
     * Establecer una fotografía específica como la portada (isPrimary = true) del producto.
     */
    @PatchMapping("/{productId}/images/{imageId}/primary")
    public ResponseEntity<ProductResponse> setPrimaryProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        return ResponseEntity.ok(productService.setPrimaryProductImage(productId, imageId));
    }

    /**
     * Reordenar la galería de fotografías del producto mediante una lista ordenada de IDs.
     */
    @PatchMapping("/{productId}/images/order")
    public ResponseEntity<ProductResponse> reorderProductImages(
            @PathVariable Long productId,
            @Valid @RequestBody ReorderProductImagesRequest request) {
        return ResponseEntity.ok(productService.reorderProductImages(productId, request.getImageIds()));
    }
}
