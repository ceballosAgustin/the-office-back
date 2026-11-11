package com.aceballos.the_office_back.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNavStatusRequest {

    @NotNull(message = "El estado isVisible es obligatorio")
    private Boolean isVisible;
}

