package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.OrderResponse;
import com.aceballos.the_office_back.dto.UpdateOrderStatusRequest;
import com.aceballos.the_office_back.entity.OrderStatus;
import com.aceballos.the_office_back.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrderAdminController {

    private final OrderService orderService;

    /**
     * Lista pedidos pendientes o filtrados por estado para el panel de administración.
     * Ejemplo: GET /api/admin/orders?status=PENDING
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(
            @RequestParam(name = "status", defaultValue = "PENDING") OrderStatus status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status));
    }

    /**
     * Versión paginada para listar órdenes en el panel admin.
     * Ejemplo: GET /api/admin/orders/paged?status=PENDING&page=0&size=20
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<OrderResponse>> getOrdersPaged(
            @RequestParam(name = "status", required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrders(status, pageable));
    }

    /**
     * Detalle completo de una orden por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    /**
     * Botón del Admin: ¿Se realizó la compra?
     * Actualiza el estado a COMPLETED o CANCELLED.
     * Si pasa a CANCELLED, el servicio restaura el stock automáticamente.
     * Ejemplo: PATCH /api/admin/orders/15/status con body {"status": "COMPLETED"} o {"status": "CANCELLED"}
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request.getStatus()));
    }
}
