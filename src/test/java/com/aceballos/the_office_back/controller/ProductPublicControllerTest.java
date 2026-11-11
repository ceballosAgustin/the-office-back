package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.exception.GlobalExceptionHandler;
import com.aceballos.the_office_back.security.JwtAuthFilter;
import com.aceballos.the_office_back.security.JwtUtils;
import com.aceballos.the_office_back.security.SecurityConfig;
import com.aceballos.the_office_back.security.UserDetailsServiceImpl;
import com.aceballos.the_office_back.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductPublicController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
class ProductPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void getActiveProducts_WithoutFilters_ShouldReturn200AndIncludeBrandFields() throws Exception {
        ProductResponse p1 = ProductResponse.builder()
                .id(1L)
                .title("Remera Jordan Retro")
                .price(new BigDecimal("150.00"))
                .isActive(true)
                .categoryId(1L)
                .categoryName("Remeras")
                .categorySlug("remeras")
                .brandId(5L)
                .brandName("Jordan")
                .brandSlug("jordan")
                .build();

        when(productService.getActiveProducts(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1)));

        mockMvc.perform(get("/api/products?page=0&size=12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Remera Jordan Retro"))
                .andExpect(jsonPath("$.content[0].price").isNumber())
                .andExpect(jsonPath("$.content[0].price").value(150.00))
                .andExpect(jsonPath("$.content[0].categoryId").value(1))
                .andExpect(jsonPath("$.content[0].categorySlug").value("remeras"))
                .andExpect(jsonPath("$.content[0].brandId").value(5))
                .andExpect(jsonPath("$.content[0].brandName").value("Jordan"))
                .andExpect(jsonPath("$.content[0].brandSlug").value("jordan"));
    }

    @Test
    void getActiveProducts_WithBrandAndCategoryFilters_ShouldPassParametersCorrectly() throws Exception {
        ProductResponse p1 = ProductResponse.builder()
                .id(1L)
                .title("Remera Jordan Retro")
                .price(new BigDecimal("150.00"))
                .isActive(true)
                .categoryId(1L)
                .brandId(5L)
                .brandSlug("jordan")
                .build();

        when(productService.getActiveProducts(eq(1L), eq("jordan"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1)));

        mockMvc.perform(get("/api/products?brand=jordan&categoryId=1&page=0&size=12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].brandSlug").value("jordan"))
                .andExpect(jsonPath("$.content[0].categoryId").value(1));
    }

    @Test
    void searchProducts_WithTitleBrandAndCategory_ShouldPassParametersCorrectly() throws Exception {
        ProductResponse p1 = ProductResponse.builder()
                .id(1L)
                .title("Buzo Jordan Flight")
                .price(new BigDecimal("220.00"))
                .isActive(true)
                .categoryId(2L)
                .brandId(5L)
                .brandSlug("jordan")
                .build();

        when(productService.searchActiveProducts(eq("flight"), eq(2L), eq("jordan"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1)));

        mockMvc.perform(get("/api/products/search?title=flight&brand=jordan&categoryId=2&page=0&size=12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Buzo Jordan Flight"))
                .andExpect(jsonPath("$.content[0].brandSlug").value("jordan"))
                .andExpect(jsonPath("$.content[0].categoryId").value(2));
    }
}

