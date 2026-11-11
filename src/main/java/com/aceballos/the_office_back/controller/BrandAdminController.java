package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.BrandRequest;
import com.aceballos.the_office_back.dto.BrandResponse;
import com.aceballos.the_office_back.dto.UpdateBrandStatusRequest;
import com.aceballos.the_office_back.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BrandAdminController {

    private final BrandService brandService;

    /**
     * Listar todas las marcas, incluyendo inactivas, en orden alfabético.
     * Ejemplo: GET /api/admin/brands
     */
    @GetMapping
    public ResponseEntity<List<BrandResponse>> getAllBrands() {
        return ResponseEntity.ok(brandService.getAllBrandsAdmin());
    }

    /**
     * Detalle de una marca por ID.
     * Ejemplo: GET /api/admin/brands/1
     */
    @GetMapping("/{id}")
    public ResponseEntity<BrandResponse> getBrandById(@PathVariable Long id) {
        return ResponseEntity.ok(brandService.getBrandById(id));
    }

    /**
     * Crear una nueva marca.
     * Ejemplo: POST /api/admin/brands
     */
    @PostMapping
    public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody BrandRequest request) {
        BrandResponse created = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Editar una marca existente por su ID.
     * Ejemplo: PUT /api/admin/brands/1
     */
    @PutMapping("/{id}")
    public ResponseEntity<BrandResponse> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandRequest request) {
        BrandResponse updated = brandService.updateBrand(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Activar o desactivar una marca.
     * Ejemplo: PATCH /api/admin/brands/1/status con body {"isActive": false}
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<BrandResponse> updateBrandStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandStatusRequest request) {
        return ResponseEntity.ok(brandService.updateBrandStatus(id, request.getIsActive()));
    }

    /**
     * Eliminar físicamente una marca si no posee productos asociados.
     * Ejemplo: DELETE /api/admin/brands/1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(Map.of("message", "Marca eliminada correctamente"));
    }
}

