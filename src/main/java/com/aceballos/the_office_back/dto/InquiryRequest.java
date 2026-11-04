package com.aceballos.the_office_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class InquiryRequest {

    @Size(max = 150, message = "El nombre de contacto no puede superar los 150 caracteres")
    private String contactName;

    @Valid
    @NotEmpty(message = "La consulta debe contener al menos un producto")
    @Builder.Default
    private List<InquiryItemRequest> items = new ArrayList<>();
}

