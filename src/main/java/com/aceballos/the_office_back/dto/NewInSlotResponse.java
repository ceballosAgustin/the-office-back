package com.aceballos.the_office_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Posición ocupada de la sección "Recién ingresados", usada por el panel
 * administrativo para mostrar qué lugares están en uso y por qué producto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewInSlotResponse {

    private Integer position;
    private Long productId;
    private String title;
    private Boolean isActive;
    private String imageUrl;
}
