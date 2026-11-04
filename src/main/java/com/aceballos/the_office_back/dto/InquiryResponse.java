package com.aceballos.the_office_back.dto;

import com.aceballos.the_office_back.entity.InquiryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryResponse {

    private Long id;
    private String inquiryNumber;
    private InquiryStatus status;
    private String contactName;
    private LocalDateTime createdAt;
    private BigDecimal totalAmount;
    private String whatsappUrl;
    private Long orderId;
    private String orderNumber;

    @Builder.Default
    private List<InquiryItemResponse> items = new ArrayList<>();
}

