package com.aceballos.the_office_back.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemPosition {

    @NotNull(message = "El ID del elemento es obligatorio")
    private Long id;

    @NotNull(message = "El índice de columna es obligatorio")
    private Integer columnIndex;

    @NotNull(message = "El índice de orden es obligatorio")
    private Integer orderIndex;
}

