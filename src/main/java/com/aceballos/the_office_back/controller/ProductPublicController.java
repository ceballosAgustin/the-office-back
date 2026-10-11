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
     * Catálogo público paginado de productos activos con filtro opcional por categoría.
     * Ejemplo: GET /api/products?categoryId=1&page=0&size=12&sort=price,asc
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getActiveProducts(
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.getActiveProducts(categoryId, pageable));
    }

    /**
     * Búsqueda por título en el catálogo público con filtro opcional por categoría.
     * Ejemplo: GET /api/products/search?title=remera&categoryId=2&page=0&size=12
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.searchActiveProducts(title, categoryId, pageable));
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
