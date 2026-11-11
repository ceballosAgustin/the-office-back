package com.aceballos.the_office_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandResponse {

    private Long id;
    private String name;
    private String slug;
    private Boolean isActive;
}

