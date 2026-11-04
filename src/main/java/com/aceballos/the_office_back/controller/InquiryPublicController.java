package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.InquiryRequest;
import com.aceballos.the_office_back.dto.InquiryResponse;
import com.aceballos.the_office_back.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryPublicController {

    private final InquiryService inquiryService;

    /**
     * Endpoint público para registrar una consulta comercial desde el carrito.
     * No crea un pedido ni descuenta stock. Retorna 201 Created con el detalle y el enlace de WhatsApp.
     */
    @PostMapping
    public ResponseEntity<InquiryResponse> createInquiry(@Valid @RequestBody InquiryRequest request) {
        InquiryResponse response = inquiryService.createInquiry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

