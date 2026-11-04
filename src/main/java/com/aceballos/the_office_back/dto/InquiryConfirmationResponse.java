package com.aceballos.the_office_back.dto;

import com.aceballos.the_office_back.entity.InquiryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryConfirmationResponse {

    private Long inquiryId;
    private String inquiryNumber;
    private InquiryStatus status;
    private Long orderId;
    private String orderNumber;
    private OrderResponse order;
}

