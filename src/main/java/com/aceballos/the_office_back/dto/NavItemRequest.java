package com.aceballos.the_office_back.dto;

import com.aceballos.the_office_back.entity.NavTargetType;
import com.aceballos.the_office_back.entity.ProductGender;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavItemRequest {

    @NotNull(message = "El tipo de destino es obligatorio")
    private NavTargetType targetType;

    private Long brandId;

    private Long categoryId;

    private ProductGender gender;

    @Size(max = 500, message = "La URL no puede superar los 500 caracteres")
    private String customUrl;

    @Size(max = 100, message = "La etiqueta no puede superar los 100 caracteres")
    private String label;

    @NotNull(message = "El índice de columna es obligatorio")
    private Integer columnIndex;

    private Integer orderIndex;

    private Boolean isVisible;
}

