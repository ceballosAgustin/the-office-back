package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.service.ProductService;
import lombok.RequiredArgsConstructor;
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
     * Actualizar datos y/o imágenes de un producto existente.
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
     * Borrado lógico de un producto (isActive = false).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("message", "Producto desactivado correctamente"));
    }
}
