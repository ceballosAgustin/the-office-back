package com.aceballos.the_office_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank(message = "El nombre de contacto es obligatorio")
    private String contactName;

    @NotBlank(message = "El teléfono o WhatsApp de contacto es obligatorio")
    private String contactPhone;

    @Valid
    @NotEmpty(message = "La orden debe contener al menos un producto")
    @Builder.Default
    private List<OrderItemRequest> items = new ArrayList<>();
}
