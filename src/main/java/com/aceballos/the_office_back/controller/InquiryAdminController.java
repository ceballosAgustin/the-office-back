package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.ConfirmInquiryRequest;
import com.aceballos.the_office_back.dto.InquiryConfirmationResponse;
import com.aceballos.the_office_back.dto.InquiryResponse;
import com.aceballos.the_office_back.dto.UpdateInquiryStatusRequest;
import com.aceballos.the_office_back.entity.InquiryStatus;
import com.aceballos.the_office_back.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InquiryAdminController {

    private final InquiryService inquiryService;

    /**
     * Listado paginado de consultas comerciales para el panel de administración.
     * Ejemplo: GET /api/admin/inquiries?status=NEW&page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<InquiryResponse>> getInquiries(
            @RequestParam(name = "status", required = false) InquiryStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inquiryService.getInquiries(status, pageable));
    }

    /**
     * Detalle completo de una consulta por su ID interno.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InquiryResponse> getInquiryById(@PathVariable Long id) {
        return ResponseEntity.ok(inquiryService.getInquiryById(id));
    }

    /**
     * Actualiza el estado de una consulta a IN_PROGRESS o NOT_CONVERTED.
     * No permite establecer CONVERTED manualmente.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<InquiryResponse> updateInquiryStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInquiryStatusRequest request) {
        return ResponseEntity.ok(inquiryService.updateInquiryStatus(id, request.getStatus()));
    }

    /**
     * Confirma que la venta fue acordada por WhatsApp, convirtiendo la consulta
     * en un pedido real (Order) y descontando el stock de manera atómica e idempotente.
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<InquiryConfirmationResponse> confirmInquiry(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ConfirmInquiryRequest request) {
        return ResponseEntity.ok(inquiryService.confirmInquiry(id, request));
    }
}

