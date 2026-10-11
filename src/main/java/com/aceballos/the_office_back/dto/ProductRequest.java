package com.aceballos.the_office_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ProductRequest {

    @NotBlank(message = "El título del producto es obligatorio")
    private String title;

    private String description;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a cero")
    private BigDecimal price;

    @NotNull(message = "El stock base es obligatorio")
    @Min(value = 0, message = "El stock base no puede ser negativo")
    private Integer baseStock;

    private Long categoryId;

    @Valid
    @Builder.Default
    private List<VariantDTO> variants = new ArrayList<>();
}
