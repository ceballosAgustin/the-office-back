package com.aceballos.the_office_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavSectionResponse {

    private Long id;
    private String code;
    private String label;
    private Integer orderIndex;
    private Boolean isVisible;
    private List<NavItemResponse> items;
}

