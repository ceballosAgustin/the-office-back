package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.BrandRequest;
import com.aceballos.the_office_back.dto.BrandResponse;
import com.aceballos.the_office_back.dto.UpdateBrandStatusRequest;
import com.aceballos.the_office_back.exception.GlobalExceptionHandler;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.security.JwtAuthFilter;
import com.aceballos.the_office_back.security.JwtUtils;
import com.aceballos.the_office_back.security.SecurityConfig;
import com.aceballos.the_office_back.security.UserDetailsServiceImpl;
import com.aceballos.the_office_back.service.BrandService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BrandPublicController.class, BrandAdminController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void publicEndpoints_ShouldBeAccessibleWithoutAuthentication() throws Exception {
        BrandResponse b1 = BrandResponse.builder().id(1L).name("Nike").slug("nike").isActive(true).build();

        when(brandService.getActiveBrands()).thenReturn(List.of(b1));
        when(brandService.getBrandBySlug("nike")).thenReturn(b1);

        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Nike"))
                .andExpect(jsonPath("$[0].slug").value("nike"));

        mockMvc.perform(get("/api/brands/nike"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Nike"))
                .andExpect(jsonPath("$.slug").value("nike"));
    }

    @Test
    void adminEndpoints_WithoutAuthentication_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/brands"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Adidas\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/brands/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminEndpoints_WithUserRole_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/brands"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Adidas\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/brands/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoints_WithAdminRole_ShouldAllowCrudAndReturnCorrectStatuses() throws Exception {
        BrandResponse created = BrandResponse.builder().id(10L).name("Puma").slug("puma").isActive(true).build();
        when(brandService.createBrand(any(BrandRequest.class))).thenReturn(created);

        BrandRequest createReq = BrandRequest.builder().name("Puma").build();

        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Puma"))
                .andExpect(jsonPath("$.slug").value("puma"))
                .andExpect(jsonPath("$.isActive").value(true));

        BrandResponse updated = BrandResponse.builder().id(10L).name("Puma Motorsport").slug("puma-motorsport").isActive(true).build();
        when(brandService.updateBrand(eq(10L), any(BrandRequest.class))).thenReturn(updated);

        BrandRequest updateReq = BrandRequest.builder().name("Puma Motorsport").build();

        mockMvc.perform(put("/api/admin/brands/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Puma Motorsport"));

        BrandResponse deactivated = BrandResponse.builder().id(10L).name("Puma Motorsport").slug("puma-motorsport").isActive(false).build();
        when(brandService.updateBrandStatus(10L, false)).thenReturn(deactivated);

        mockMvc.perform(patch("/api/admin/brands/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBrandStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        doThrow(new ResourceConflictException("No se puede eliminar la marca 'Puma' porque tiene productos asociados (2)."))
                .when(brandService).deleteBrand(10L);

        mockMvc.perform(delete("/api/admin/brands/10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("No se puede eliminar la marca 'Puma' porque tiene productos asociados (2)."));
    }
}

