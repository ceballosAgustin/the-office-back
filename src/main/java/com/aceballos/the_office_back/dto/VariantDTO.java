package com.aceballos.the_office_back.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class VariantDTO {

    private Long id;

    @NotBlank(message = "El talle es obligatorio")
    @Size(max = 50, message = "El talle no puede superar los 50 caracteres")
    private String size;

    @NotNull(message = "El stock del talle es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    @Builder.Default
    private Boolean isActive = true;

    public VariantDTO(Long id, String size, Integer stock) {
        this.id = id;
        this.size = size;
        this.stock = stock;
        this.isActive = true;
    }
}
