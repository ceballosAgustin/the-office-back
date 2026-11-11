package com.aceballos.the_office_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "El título del producto es obligatorio")
    @Size(max = 200, message = "El título del producto no puede superar los 200 caracteres")
    private String title;

    private String description;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a cero")
    private BigDecimal price;

    /**
     * Campo legado opcional y no autoritativo.
     * El stock real del producto se calcula siempre como la suma del stock de sus variantes activas.
     */
    @Min(value = 0, message = "El stock base no puede ser negativo")
    private Integer baseStock;

    private Long categoryId;
    private Long brandId;

    @Valid
    private List<VariantDTO> variants;
}
