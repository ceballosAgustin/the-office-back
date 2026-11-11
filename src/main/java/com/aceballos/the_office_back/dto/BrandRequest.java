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
public class BrandRequest {

    @NotBlank(message = "El nombre de la marca es obligatorio")
    @Size(max = 100, message = "El nombre de la marca no puede superar los 100 caracteres")
    private String name;

    @Size(max = 100, message = "El slug no puede superar los 100 caracteres")
    private String slug;

    private Boolean isActive;
}

