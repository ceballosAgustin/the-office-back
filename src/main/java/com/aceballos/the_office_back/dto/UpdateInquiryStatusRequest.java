package com.aceballos.the_office_back.dto;

import com.aceballos.the_office_back.entity.InquiryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInquiryStatusRequest {

    @NotNull(message = "El nuevo estado es obligatorio")
    private InquiryStatus status;
}

