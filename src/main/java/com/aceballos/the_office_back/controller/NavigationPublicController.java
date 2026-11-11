package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.PublicNavigationSectionResponse;
import com.aceballos.the_office_back.service.NavigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/navigation")
@RequiredArgsConstructor
public class NavigationPublicController {

    private final NavigationService navigationService;

    /**
     * Devuelve la configuración pública y dinámica del navbar:
     * secciones e ítems visibles ordenados con sus columnas y destinos resueltos.
     * Ejemplo: GET /api/navigation
     */
    @GetMapping
    public ResponseEntity<List<PublicNavigationSectionResponse>> getPublicNavigation() {
        return ResponseEntity.ok(navigationService.getPublicNavigation());
    }
}

