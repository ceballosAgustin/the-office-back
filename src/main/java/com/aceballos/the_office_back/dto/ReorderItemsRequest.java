package com.aceballos.the_office_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderItemsRequest {

    @NotEmpty(message = "La lista de elementos a reordenar no puede estar vacía")
    @Valid
    private List<OrderItemPosition> items;
}

