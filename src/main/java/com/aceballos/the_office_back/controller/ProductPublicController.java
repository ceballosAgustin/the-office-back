package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.FilterOptionsResponse;
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
     * Catálogo público paginado de productos activos con filtros combinados:
     * categoría (por ID o slug), marca, género y talle de variante con stock disponible.
     * Ejemplo: GET /api/products?category=sneakers&brand=nike&gender=MEN&variantSize=42&page=0&size=12
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getActiveProducts(
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "variantSize", required = false) String variantSize,
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.getActiveProducts(categoryId, category, brand, gender, variantSize, pageable));
    }

    /**
     * Búsqueda por título en el catálogo público con filtros combinados opcionales.
     * Ejemplo: GET /api/products/search?title=retro&category=sneakers&brand=jordan&gender=MEN&variantSize=42&page=0&size=12
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "variantSize", required = false) String variantSize,
            @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.searchActiveProducts(title, categoryId, category, brand, gender, variantSize, pageable));
    }

    /**
     * Devuelve las opciones de talles disponibles (stock > 0 y variante activa)
     * calculadas para los filtros aplicados (categoría, marca, género y búsqueda).
     * Ejemplo: GET /api/products/filter-options?category=sneakers&brand=nike&gender=MEN
     */
    @GetMapping("/filter-options")
    public ResponseEntity<FilterOptionsResponse> getFilterOptions(
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "title", required = false) String title) {
        return ResponseEntity.ok(productService.getFilterOptions(categoryId, category, brand, gender, title));
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
