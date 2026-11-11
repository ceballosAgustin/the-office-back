package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.*;
import com.aceballos.the_office_back.entity.NavTargetType;
import com.aceballos.the_office_back.exception.GlobalExceptionHandler;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.security.JwtAuthFilter;
import com.aceballos.the_office_back.security.JwtUtils;
import com.aceballos.the_office_back.security.SecurityConfig;
import com.aceballos.the_office_back.security.UserDetailsServiceImpl;
import com.aceballos.the_office_back.service.NavigationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {NavigationPublicController.class, NavigationAdminController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
class NavigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NavigationService navigationService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    // =========================================================================
    // ENDPOINT PÚBLICO (Prueba 22)
    // =========================================================================

    @Test
    @DisplayName("22. GET /api/navigation es público y devuelve la estructura de secciones visibles")
    void publicNavigation_ShouldBeAccessibleWithoutAuthentication() throws Exception {
        PublicNavigationItemResponse item = PublicNavigationItemResponse.builder()
                .id(1L)
                .label("Nike")
                .targetType(NavTargetType.BRAND)
                .brandId(10L)
                .brandName("Nike")
                .brandSlug("nike")
                .orderIndex(0)
                .build();

        PublicNavigationColumnResponse col = PublicNavigationColumnResponse.builder()
                .columnIndex(0)
                .items(List.of(item))
                .build();

        PublicNavigationSectionResponse sec = PublicNavigationSectionResponse.builder()
                .id(1L)
                .code("brands")
                .label("Marcas")
                .orderIndex(0)
                .columns(List.of(col))
                .build();

        when(navigationService.getPublicNavigation()).thenReturn(List.of(sec));

        mockMvc.perform(get("/api/navigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("brands"))
                .andExpect(jsonPath("$[0].label").value("Marcas"))
                .andExpect(jsonPath("$[0].columns[0].columnIndex").value(0))
                .andExpect(jsonPath("$[0].columns[0].items[0].brandSlug").value("nike"));
    }

    // =========================================================================
    // SEGURIDAD Y ENDPOINTS ADMINISTRATIVOS (Pruebas 23 y 24)
    // =========================================================================

    @Test
    @DisplayName("24. Operaciones administrativas sin autenticación deben responder 401 Unauthorized")
    void adminEndpoints_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/navigation"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/navigation/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\": \"Nueva Sección\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/navigation/sections/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("24. Operaciones administrativas con rol USER deben responder 403 Forbidden")
    @WithMockUser(roles = "USER")
    void adminEndpoints_WithRoleUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/navigation"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/navigation/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\": \"Nueva Sección\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("23 y 24. GET /api/admin/navigation con rol ADMIN debe responder 200 OK con toda la configuración")
    @WithMockUser(roles = "ADMIN")
    void adminGetNavigation_WithRoleAdmin_ShouldReturn200() throws Exception {
        NavSectionResponse sec = NavSectionResponse.builder()
                .id(1L)
                .code("brands")
                .label("Marcas")
                .orderIndex(0)
                .isVisible(true)
                .items(List.of())
                .build();

        when(navigationService.getAllAdminSections()).thenReturn(List.of(sec));

        mockMvc.perform(get("/api/admin/navigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("brands"));
    }

    @Test
    @DisplayName("Crear sección con rol ADMIN responde 201 Created")
    @WithMockUser(roles = "ADMIN")
    void adminCreateSection_Success() throws Exception {
        NavSectionRequest req = NavSectionRequest.builder()
                .code("apparel")
                .label("Indumentaria")
                .isVisible(true)
                .build();

        NavSectionResponse res = NavSectionResponse.builder()
                .id(5L)
                .code("apparel")
                .label("Indumentaria")
                .orderIndex(4)
                .isVisible(true)
                .items(List.of())
                .build();

        when(navigationService.createSection(any(NavSectionRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/admin/navigation/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.code").value("apparel"));
    }

    @Test
    @DisplayName("Agregar ítem que supera el límite responde 409 Conflict")
    @WithMockUser(roles = "ADMIN")
    void adminAddItem_WhenLimitExceeded_ShouldReturn409() throws Exception {
        NavItemRequest req = NavItemRequest.builder()
                .targetType(NavTargetType.BRAND)
                .brandId(10L)
                .columnIndex(0)
                .isVisible(true)
                .build();

        when(navigationService.addItem(eq(1L), any(NavItemRequest.class)))
                .thenThrow(new ResourceConflictException("La sección de marcas no puede tener más de 8 marcas visibles"));

        mockMvc.perform(post("/api/admin/navigation/sections/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La sección de marcas no puede tener más de 8 marcas visibles"));
    }

    @Test
    @DisplayName("Eliminar sección responde 200 OK")
    @WithMockUser(roles = "ADMIN")
    void adminDeleteSection_Success() throws Exception {
        mockMvc.perform(delete("/api/admin/navigation/sections/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sección y sus elementos eliminados correctamente"));
    }

    @Test
    @DisplayName("Reordenar secciones responde 200 OK")
    @WithMockUser(roles = "ADMIN")
    void adminReorderSections_Success() throws Exception {
        ReorderSectionsRequest req = new ReorderSectionsRequest(List.of(2L, 1L));

        NavSectionResponse sec2 = NavSectionResponse.builder().id(2L).orderIndex(0).build();
        NavSectionResponse sec1 = NavSectionResponse.builder().id(1L).orderIndex(1).build();

        when(navigationService.reorderSections(any(ReorderSectionsRequest.class)))
                .thenReturn(List.of(sec2, sec1));

        mockMvc.perform(patch("/api/admin/navigation/sections/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[1].id").value(1L));
    }
}

