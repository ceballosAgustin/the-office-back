package com.aceballos.the_office_back.dto;

import com.aceballos.the_office_back.entity.NavTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavItemResponse {

    private Long id;
    private Long sectionId;
    private NavTargetType targetType;
    private String label;
    private Integer columnIndex;
    private Integer orderIndex;
    private Boolean isVisible;

    private Long brandId;
    private String brandName;
    private String brandSlug;
    private Boolean brandIsActive;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    private String customUrl;
}

