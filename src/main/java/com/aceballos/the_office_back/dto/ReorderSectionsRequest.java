package com.aceballos.the_office_back.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderSectionsRequest {

    @NotEmpty(message = "La lista de identificadores de secciones no puede estar vacía")
    private List<Long> sectionIds;
}

