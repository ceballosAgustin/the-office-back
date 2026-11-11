package com.aceballos.the_office_back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavSectionRequest {

    @Size(max = 50, message = "El código no puede superar los 50 caracteres")
    private String code;

    @NotBlank(message = "El nombre de la sección es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String label;

    private Integer orderIndex;

    private Boolean isVisible;
}

