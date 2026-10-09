package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductPublicController {

    private final ProductService productService;

    /**
     * Catálogo público paginado de productos activos.
     * Ejemplo: GET /api/products?page=0&size=12&sort=id,desc
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getActiveProducts(
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.getActiveProducts(pageable));
    }

    /**
     * Búsqueda por título en el catálogo público.
     * Ejemplo: GET /api/products/search?title=remera&page=0&size=12
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(name = "title", required = false) String title,
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.searchActiveProducts(title, pageable));
    }

    /**
     * Detalle de un producto individual por ID.
     * Ejemplo: GET /api/products/5
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }
}
