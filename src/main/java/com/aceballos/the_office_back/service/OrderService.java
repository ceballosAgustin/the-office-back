package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.OrderItemRequest;
import com.aceballos.the_office_back.dto.OrderItemResponse;
import com.aceballos.the_office_back.dto.OrderRequest;
import com.aceballos.the_office_back.dto.OrderResponse;
import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.OrderItem;
import com.aceballos.the_office_back.entity.OrderStatus;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.repository.OrderRepository;
import com.aceballos.the_office_back.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    /**
     * Crea una orden asegurando:
     * 1. Cálculo del total basado en precios reales en base de datos.
     * 2. Validación de stock disponible y descuento automático.
     * 3. Generación de un orderNumber único (ej. ORD-20261008-A1B2C3).
     * 4. Estado inicial PENDING.
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La orden debe contener al menos un producto");
        }

        String orderNumber = generateUniqueOrderNumber();

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setStatus(OrderStatus.PENDING);
        order.setContactName(request.getContactName());
        order.setContactPhone(request.getContactPhone());
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada con ID: " + itemReq.getVariantId()));

            // Validar que el producto padre esté activo
            if (!Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
                throw new IllegalStateException("El producto '" + variant.getProduct().getTitle() + "' no se encuentra activo");
            }

            // Validar stock disponible
            if (variant.getStock() < itemReq.getQuantity()) {
                throw new IllegalStateException("Stock insuficiente para el producto '" +
                        variant.getProduct().getTitle() + "' en talle '" + variant.getSize() +
                        "'. Stock actual: " + variant.getStock() + ", solicitado: " + itemReq.getQuantity());
            }

            // Restar stock
            variant.setStock(variant.getStock() - itemReq.getQuantity());
            productVariantRepository.save(variant);

            // Precio real desde la base de datos
            BigDecimal unitPrice = variant.getProduct().getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductVariant(variant);
            orderItem.setQuantity(itemReq.getQuantity());
            orderItem.setPriceAtPurchase(unitPrice);

            orderItems.add(orderItem);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);
        log.info("Orden creada con éxito: {} por un total de {}", savedOrder.getOrderNumber(), savedOrder.getTotalAmount());

        return mapToResponse(savedOrder);
    }

    /**
     * Actualiza el estado de la orden por parte del administrador.
     * Si pasa a CANCELLED y previamente estaba PENDING o COMPLETED,
     * se restaura el stock sumándolo a las variantes.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return mapToResponse(order);
        }

        // Si se cancela una orden que estaba activa, se restaura el stock de cada variante
        if (newStatus == OrderStatus.CANCELLED && currentStatus != OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                ProductVariant variant = item.getProductVariant();
                variant.setStock(variant.getStock() + item.getQuantity());
                productVariantRepository.save(variant);
            }
            log.info("Stock restaurado para la orden cancelada: {}", order.getOrderNumber());
        }

        // Si una orden cancelada se vuelve a activar/completar, se valida y descuenta nuevamente
        if (currentStatus == OrderStatus.CANCELLED && (newStatus == OrderStatus.PENDING || newStatus == OrderStatus.COMPLETED)) {
            for (OrderItem item : order.getItems()) {
                ProductVariant variant = item.getProductVariant();
                if (variant.getStock() < item.getQuantity()) {
                    throw new IllegalStateException("No es posible reactivar la orden: Stock insuficiente en " +
                            variant.getProduct().getTitle() + " talle " + variant.getSize());
                }
                variant.setStock(variant.getStock() - item.getQuantity());
                productVariantRepository.save(variant);
            }
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        log.info("Estado de orden {} actualizado a {}", updated.getOrderNumber(), newStatus);

        return mapToResponse(updated);
    }

    /**
     * Lista pedidos filtrando por estado.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista pedidos paginados con filtro opcional de estado.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByStatus(status, pageable).map(this::mapToResponse);
        }
        return orderRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Obtiene una orden por su ID.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con ID: " + id));
        return mapToResponse(order);
    }

    /**
     * Obtiene una orden por su orderNumber único.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con número: " + orderNumber));
        return mapToResponse(order);
    }

    private String generateUniqueOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "ORD-" + datePart + "-" + randomPart;
    }

    public OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            itemResponses = order.getItems().stream()
                    .map(item -> OrderItemResponse.builder()
                            .id(item.getId())
                            .variantId(item.getProductVariant().getId())
                            .productTitle(item.getProductVariant().getProduct().getTitle())
                            .size(item.getProductVariant().getSize())
                            .quantity(item.getQuantity())
                            .priceAtPurchase(item.getPriceAtPurchase())
                            .subtotal(item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .build())
                    .collect(Collectors.toList());
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .contactName(order.getContactName())
                .contactPhone(order.getContactPhone())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
