package com.aceballos.the_office_back.dto;

import com.aceballos.the_office_back.entity.NavTargetType;
import com.aceballos.the_office_back.entity.ProductGender;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicNavigationItemResponse {

    private Long id;
    private String label;
    private NavTargetType targetType;
    private Integer orderIndex;

    private Long brandId;
    private String brandName;
    private String brandSlug;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    private ProductGender gender;

    private String customUrl;
}

