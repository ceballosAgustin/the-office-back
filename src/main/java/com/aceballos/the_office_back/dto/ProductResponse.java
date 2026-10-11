package com.aceballos.the_office_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private Integer baseStock;
    private Boolean isActive;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    @Builder.Default
    private List<ProductImageResponse> images = new ArrayList<>();

    @Builder.Default
    private List<VariantDTO> variants = new ArrayList<>();
}
