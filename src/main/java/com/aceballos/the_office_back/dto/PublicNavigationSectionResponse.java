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
public class PublicNavigationSectionResponse {

    private Long id;
    private String code;
    private String label;
    private Integer orderIndex;
    private List<PublicNavigationColumnResponse> columns;
}

