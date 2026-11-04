package com.aceballos.the_office_back.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmInquiryRequest {

    @Size(max = 150, message = "El nombre de contacto no puede superar los 150 caracteres")
    private String contactName;

    @Size(max = 50, message = "El teléfono de contacto no puede superar los 50 caracteres")
    private String contactPhone;
}

