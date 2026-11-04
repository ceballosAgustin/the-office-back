package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.ProductImageResponse;
import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.VariantDTO;
import com.aceballos.the_office_back.security.JwtAuthFilter;
import com.aceballos.the_office_back.security.JwtUtils;
import com.aceballos.the_office_back.security.SecurityConfig;
import com.aceballos.the_office_back.security.UserDetailsServiceImpl;
import com.aceballos.the_office_back.service.ProductService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import com.aceballos.the_office_back.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ProductAdminController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class})
class ProductAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void adminProductRoutes_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/products/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminProductRoutes_WithUserRole_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/products/10/images/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAdminProducts_And_ReactivateProduct_WithAdminRole_ShouldReturn200() throws Exception {
        ProductResponse product = ProductResponse.builder()
                .id(10L)
                .title("Remera Oversize")
                .price(new BigDecimal("28500.00"))
                .baseStock(5)
                .isActive(false)
                .variants(List.of(new VariantDTO(101L, "M", 5, true)))
                .images(List.of(new ProductImageResponse(1L, "https://example.com/1.jpg", "pub1", true, 0)))
                .build();

        when(productService.getAdminProducts(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        mockMvc.perform(get("/api/admin/products?isActive=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].isActive").value(false));

        ProductResponse reactivated = ProductResponse.builder()
                .id(10L)
                .title("Remera Oversize")
                .price(new BigDecimal("28500.00"))
                .baseStock(5)
                .isActive(true)
                .build();

        when(productService.updateProductStatus(10L, true)).thenReturn(reactivated);

        mockMvc.perform(patch("/api/admin/products/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_WhenConstraintViolationOccurs_ShouldReturn400WithFieldErrors() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ProductRequest invalidReq = ProductRequest.builder()
                .title("")
                .price(new BigDecimal("-5"))
                .build();
        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(invalidReq);

        when(productService.createProduct(any(String.class), any()))
                .thenThrow(new ConstraintViolationException(violations));

        mockMvc.perform(multipart("/api/admin/products")
                        .param("product", "{\"title\":\"\",\"price\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void imageManagementEndpoints_WithAdminRole_ShouldReturn200() throws Exception {
        ProductResponse updated = ProductResponse.builder()
                .id(10L)
                .title("Remera Oversize")
                .images(List.of(
                        new ProductImageResponse(2L, "https://example.com/2.jpg", "pub2", true, 0),
                        new ProductImageResponse(1L, "https://example.com/1.jpg", "pub1", false, 1)
                ))
                .build();

        when(productService.setPrimaryProductImage(10L, 2L)).thenReturn(updated);
        when(productService.reorderProductImages(eq(10L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/products/10/images/2/primary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(2))
                .andExpect(jsonPath("$.images[0].isPrimary").value(true));

        mockMvc.perform(patch("/api/admin/products/10/images/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\": [2, 1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].orderIndex").value(0));
    }
}

