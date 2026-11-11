package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.*;
import com.aceballos.the_office_back.service.NavigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/navigation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NavigationAdminController {

    private final NavigationService navigationService;

    // =========================================================================
    // ENDPOINTS DE SECCIONES
    // =========================================================================

    /**
     * Obtener toda la configuración del menú, incluyendo secciones e ítems ocultos.
     * Ejemplo: GET /api/admin/navigation
     */
    @GetMapping
    public ResponseEntity<List<NavSectionResponse>> getAllSections() {
        return ResponseEntity.ok(navigationService.getAllAdminSections());
    }

    /**
     * Obtener una sección por ID.
     * Ejemplo: GET /api/admin/navigation/sections/1
     */
    @GetMapping("/sections/{id}")
    public ResponseEntity<NavSectionResponse> getSectionById(@PathVariable Long id) {
        return ResponseEntity.ok(navigationService.getSectionById(id));
    }

    /**
     * Crear una nueva sección en el navbar.
     * Ejemplo: POST /api/admin/navigation/sections
     */
    @PostMapping("/sections")
    public ResponseEntity<NavSectionResponse> createSection(@Valid @RequestBody NavSectionRequest request) {
        NavSectionResponse created = navigationService.createSection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Modificar una sección existente.
     * Ejemplo: PUT /api/admin/navigation/sections/1
     */
    @PutMapping("/sections/{id}")
    public ResponseEntity<NavSectionResponse> updateSection(
            @PathVariable Long id,
            @Valid @RequestBody NavSectionRequest request) {
        NavSectionResponse updated = navigationService.updateSection(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Modificar el estado de visibilidad de una sección.
     * Ejemplo: PATCH /api/admin/navigation/sections/1/status con body {"isVisible": false}
     */
    @PatchMapping("/sections/{id}/status")
    public ResponseEntity<NavSectionResponse> updateSectionStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNavStatusRequest request) {
        NavSectionResponse updated = navigationService.updateSectionStatus(id, request.getIsVisible());
        return ResponseEntity.ok(updated);
    }

    /**
     * Eliminar una sección y sus ítems de navegación en cascada.
     * Jamás elimina Brand, Category ni Product.
     * Ejemplo: DELETE /api/admin/navigation/sections/1
     */
    @DeleteMapping("/sections/{id}")
    public ResponseEntity<Map<String, String>> deleteSection(@PathVariable Long id) {
        navigationService.deleteSection(id);
        return ResponseEntity.ok(Map.of("message", "Sección y sus elementos eliminados correctamente"));
    }

    /**
     * Reordenar secciones del navbar.
     * Ejemplo: PATCH /api/admin/navigation/sections/order con body {"sectionIds": [2, 1, 3]}
     */
    @PatchMapping("/sections/order")
    public ResponseEntity<List<NavSectionResponse>> reorderSections(
            @Valid @RequestBody ReorderSectionsRequest request) {
        return ResponseEntity.ok(navigationService.reorderSections(request));
    }

    // =========================================================================
    // ENDPOINTS DE ELEMENTOS (NAV_ITEMS)
    // =========================================================================

    /**
     * Agregar un elemento a una sección existente.
     * Ejemplo: POST /api/admin/navigation/sections/1/items
     */
    @PostMapping("/sections/{sectionId}/items")
    public ResponseEntity<NavItemResponse> addItem(
            @PathVariable Long sectionId,
            @Valid @RequestBody NavItemRequest request) {
        NavItemResponse created = navigationService.addItem(sectionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Modificar un elemento existente.
     * Ejemplo: PUT /api/admin/navigation/items/5
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<NavItemResponse> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody NavItemRequest request) {
        NavItemResponse updated = navigationService.updateItem(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Modificar el estado de visibilidad de un elemento.
     * Ejemplo: PATCH /api/admin/navigation/items/5/status con body {"isVisible": false}
     */
    @PatchMapping("/items/{id}/status")
    public ResponseEntity<NavItemResponse> updateItemStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNavStatusRequest request) {
        NavItemResponse updated = navigationService.updateItemStatus(id, request.getIsVisible());
        return ResponseEntity.ok(updated);
    }

    /**
     * Eliminar un elemento de navegación.
     * Jamás elimina Brand, Category ni Product.
     * Ejemplo: DELETE /api/admin/navigation/items/5
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Map<String, String>> deleteItem(@PathVariable Long id) {
        navigationService.deleteItem(id);
        return ResponseEntity.ok(Map.of("message", "Elemento de navegación eliminado correctamente"));
    }

    /**
     * Reordenar elementos y columnas dentro de una sección.
     * Ejemplo: PATCH /api/admin/navigation/sections/1/items/order
     */
    @PatchMapping("/sections/{sectionId}/items/order")
    public ResponseEntity<List<NavItemResponse>> reorderItems(
            @PathVariable Long sectionId,
            @Valid @RequestBody ReorderItemsRequest request) {
        return ResponseEntity.ok(navigationService.reorderItems(sectionId, request));
    }
}

