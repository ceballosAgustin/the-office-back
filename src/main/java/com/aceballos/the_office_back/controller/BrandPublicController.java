package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.BrandResponse;
import com.aceballos.the_office_back.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandPublicController {

    private final BrandService brandService;

    /**
     * Endpoint público que devuelve las marcas activas con al menos un producto activo asociado,
     * ordenadas alfabéticamente por nombre para la navegación del navbar.
     * Ejemplo: GET /api/brands
     */
    @GetMapping
    public ResponseEntity<List<BrandResponse>> getActiveBrands() {
        return ResponseEntity.ok(brandService.getActiveBrands());
    }

    /**
     * Consulta pública de una marca por su slug.
     * Ejemplo: GET /api/brands/jordan
     */
    @GetMapping("/{slug}")
    public ResponseEntity<BrandResponse> getBrandBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(brandService.getBrandBySlug(slug));
    }
}

