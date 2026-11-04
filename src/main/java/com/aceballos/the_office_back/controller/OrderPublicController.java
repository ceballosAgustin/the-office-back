package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.OrderRequest;
import com.aceballos.the_office_back.dto.OrderResponse;
import com.aceballos.the_office_back.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderPublicController {

    private final OrderService orderService;

    /**
     * Creación directa de un pedido restringida exclusivamente a administradores.
     * El flujo público de clientes debe iniciar una consulta vía POST
     * /api/inquiries.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Permite consultar públicamente el estado de una orden por su número único de
     * pedido (ej. ORD-261008-AB12CD).
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByOrderNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByOrderNumber(orderNumber));
    }
}
