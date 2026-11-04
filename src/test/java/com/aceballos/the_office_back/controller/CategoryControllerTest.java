package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.CategoryRequest;
import com.aceballos.the_office_back.dto.CategoryResponse;
import com.aceballos.the_office_back.exception.GlobalExceptionHandler;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.security.JwtAuthFilter;
import com.aceballos.the_office_back.security.JwtUtils;
import com.aceballos.the_office_back.security.SecurityConfig;
import com.aceballos.the_office_back.security.UserDetailsServiceImpl;
import com.aceballos.the_office_back.service.CategoryService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CategoryPublicController.class, CategoryAdminController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void publicEndpoints_ShouldBeAccessibleWithoutAuthentication() throws Exception {
        CategoryResponse c1 = CategoryResponse.builder().id(1L).name("Remeras").slug("remeras").build();

        when(categoryService.getAllCategories()).thenReturn(List.of(c1));
        when(categoryService.getCategoryBySlug("remeras")).thenReturn(c1);

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Remeras"))
                .andExpect(jsonPath("$[0].slug").value("remeras"));

        mockMvc.perform(get("/api/categories/remeras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.slug").value("remeras"));
    }

    @Test
    void adminEndpoints_WhenUnauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Remeras", "remeras"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Remeras", "remeras"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/categories/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminEndpoints_WhenUserRoleIsNotAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Remeras", "remeras"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_WithAdminRole_ShouldReturn201Created() throws Exception {
        CategoryResponse created = CategoryResponse.builder()
                .id(1L)
                .name("Remeras")
                .slug("remeras")
                .build();

        when(categoryService.createCategory(any(CategoryRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Remeras", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Remeras"))
                .andExpect(jsonPath("$.slug").value("remeras"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_WithInvalidFields_ShouldReturn400BadRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_WhenDuplicateSlug_ShouldReturn409Conflict() throws Exception {
        when(categoryService.createCategory(any(CategoryRequest.class)))
                .thenThrow(new ResourceConflictException("Ya existe una categoría con el slug: remeras"));

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Remeras", "remeras"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ya existe una categoría con el slug: remeras"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCategory_WithAdminRole_ShouldReturn200Ok() throws Exception {
        CategoryResponse updated = CategoryResponse.builder()
                .id(1L)
                .name("Remeras Oversize")
                .slug("remeras-oversize")
                .build();

        when(categoryService.updateCategory(eq(1L), any(CategoryRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/admin/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Remeras Oversize", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Remeras Oversize"))
                .andExpect(jsonPath("$.slug").value("remeras-oversize"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCategory_WithoutProductsAndWithAssociatedProducts_ShouldReturn200And409Respectively() throws Exception {
        doNothing().when(categoryService).deleteCategory(3L);
        doThrow(new ResourceConflictException("No se puede eliminar la categoría 'Remeras' porque tiene productos asociados (4)."))
                .when(categoryService).deleteCategory(1L);

        mockMvc.perform(delete("/api/admin/categories/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Categoría eliminada correctamente"));

        mockMvc.perform(delete("/api/admin/categories/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("No se puede eliminar la categoría 'Remeras' porque tiene productos asociados (4)."));
    }
}

