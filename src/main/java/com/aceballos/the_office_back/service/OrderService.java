package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ConfirmInquiryRequest;
import com.aceballos.the_office_back.dto.OrderItemRequest;
import com.aceballos.the_office_back.dto.OrderItemResponse;
import com.aceballos.the_office_back.dto.OrderRequest;
import com.aceballos.the_office_back.dto.OrderResponse;
import com.aceballos.the_office_back.entity.Inquiry;
import com.aceballos.the_office_back.entity.InquiryItem;
import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.OrderItem;
import com.aceballos.the_office_back.entity.OrderStatus;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.OrderRepository;
import com.aceballos.the_office_back.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    @Value("${app.whatsapp.phone-number}")
    private String whatsappPhoneNumber;

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

        // Adquirir bloqueo pesimista en orden ascendente de variantId para prevenir
        // interbloqueos (deadlocks)
        List<Long> sortedVariantIds = request.getItems().stream()
                .map(OrderItemRequest::getVariantId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<Long, ProductVariant> lockedVariants = new HashMap<>();
        for (Long variantId : sortedVariantIds) {
            ProductVariant variant = productVariantRepository.findByIdWithLock(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variante no encontrada con ID: " + variantId));
            lockedVariants.put(variantId, variant);
        }

        for (OrderItemRequest itemReq : request.getItems()) {
            ProductVariant variant = lockedVariants.get(itemReq.getVariantId());

            // Validar que el producto padre esté activo
            if (variant.getProduct() == null || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
                String title = variant.getProduct() != null ? variant.getProduct().getTitle() : "Desconocido";
                throw new BusinessRuleException(
                        "El producto '" + title + "' no se encuentra activo");
            }

            // Validar stock disponible
            if (variant.getStock() < itemReq.getQuantity()) {
                throw new BusinessRuleException("Stock insuficiente para el producto '" +
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
        log.info("Orden creada con éxito: {} por un total de {}", savedOrder.getOrderNumber(),
                savedOrder.getTotalAmount());

        OrderResponse response = mapToResponse(savedOrder);
        response.setWhatsappUrl(buildWhatsappUrl(savedOrder));
        return response;
    }

    /**
     * Crea una orden real a partir de una consulta comercial confirmada por el administrador.
     * Utiliza bloqueo pesimista sobre las variantes, valida existencias y estado de los productos,
     * descuenta el inventario y utiliza los precios históricos de referencia guardados en la consulta.
     */
    @Transactional
    public Order createOrderFromInquiry(Inquiry inquiry, ConfirmInquiryRequest confirmRequest) {
        if (inquiry.getItems() == null || inquiry.getItems().isEmpty()) {
            throw new BusinessRuleException("La consulta no contiene artículos para convertir en pedido");
        }

        // Bloquear variantes en orden ascendente de ID para evitar deadlocks en confirmaciones concurrentes
        List<Long> sortedVariantIds = inquiry.getItems().stream()
                .map(InquiryItem::getVariantId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<Long, ProductVariant> lockedVariants = new HashMap<>();
        for (Long variantId : sortedVariantIds) {
            ProductVariant variant = productVariantRepository.findByIdWithLock(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variante no encontrada con ID: " + variantId));
            lockedVariants.put(variantId, variant);
        }

        String contactName = "Cliente WhatsApp";
        if (confirmRequest != null && StringUtils.hasText(confirmRequest.getContactName())) {
            contactName = confirmRequest.getContactName().trim();
        } else if (StringUtils.hasText(inquiry.getContactName())) {
            contactName = inquiry.getContactName().trim();
        }

        String contactPhone = "WhatsApp";
        if (confirmRequest != null && StringUtils.hasText(confirmRequest.getContactPhone())) {
            contactPhone = confirmRequest.getContactPhone().trim();
        }

        Order order = new Order();
        order.setOrderNumber(generateUniqueOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        order.setContactName(contactName);
        order.setContactPhone(contactPhone);
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (InquiryItem inquiryItem : inquiry.getItems()) {
            ProductVariant variant = lockedVariants.get(inquiryItem.getVariantId());

            if (variant.getProduct() == null || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
                String title = variant.getProduct() != null ? variant.getProduct().getTitle() : inquiryItem.getProductTitle();
                throw new BusinessRuleException(
                        "El producto '" + title + "' no se encuentra activo");
            }

            if (variant.getStock() < inquiryItem.getQuantity()) {
                throw new BusinessRuleException("Stock insuficiente para el producto '" +
                        variant.getProduct().getTitle() + "' en talle '" + variant.getSize() +
                        "'. Stock actual: " + variant.getStock() + ", solicitado: " + inquiryItem.getQuantity());
            }

            variant.setStock(variant.getStock() - inquiryItem.getQuantity());
            productVariantRepository.save(variant);

            // Usar el precio de referencia capturado por el backend en la consulta
            BigDecimal unitPrice = inquiryItem.getUnitPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(inquiryItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductVariant(variant);
            orderItem.setQuantity(inquiryItem.getQuantity());
            orderItem.setPriceAtPurchase(unitPrice);

            orderItems.add(orderItem);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);
        log.info("Orden {} creada a partir de la consulta {} por un total de {}",
                savedOrder.getOrderNumber(), inquiry.getInquiryNumber(), savedOrder.getTotalAmount());
        return savedOrder;
    }

    /**
     * Actualiza el estado de la orden por parte del administrador.
     * Si pasa a CANCELLED y previamente estaba PENDING o COMPLETED,
     * se restaura el stock sumándolo a las variantes.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return mapToResponse(order);
        }

        // Si se cancela una orden que estaba activa, se restaura el stock de cada
        // variante con bloqueo pesimista
        if (newStatus == OrderStatus.CANCELLED && currentStatus != OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                ProductVariant variant = productVariantRepository.findByIdWithLock(item.getProductVariant().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Variante no encontrada con ID: " + item.getProductVariant().getId()));
                variant.setStock(variant.getStock() + item.getQuantity());
                productVariantRepository.save(variant);
            }
            log.info("Stock restaurado para la orden cancelada: {}", order.getOrderNumber());
        }

        // Si una orden cancelada se vuelve a activar/completar, se valida y descuenta
        // nuevamente con bloqueo pesimista
        if (currentStatus == OrderStatus.CANCELLED
                && (newStatus == OrderStatus.PENDING || newStatus == OrderStatus.COMPLETED)) {
            for (OrderItem item : order.getItems()) {
                ProductVariant variant = productVariantRepository.findByIdWithLock(item.getProductVariant().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Variante no encontrada con ID: " + item.getProductVariant().getId()));
                if (variant.getStock() < item.getQuantity()) {
                    throw new BusinessRuleException(
                            "No es posible reactivar la orden: Stock insuficiente en " +
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada con ID: " + id));
        return mapToResponse(order);
    }

    /**
     * Obtiene una orden por su orderNumber único.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada con número: " + orderNumber));
        return mapToResponse(order);
    }

    private String generateUniqueOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String candidate;
        do {
            String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = "ORD-" + datePart + "-" + randomPart;
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }

    /**
     * Genera el enlace de WhatsApp con un mensaje codificado incluyendo
     * el número de pedido, total y nombre del cliente.
     */
    private String buildWhatsappUrl(Order order) {
        String text = String.format(
                // Mensaje provisorio
                "Hola, acabo de hacer el pedido %s por un total de $%s. Mi nombre es %s.",
                order.getOrderNumber(),
                order.getTotalAmount().toPlainString(),
                order.getContactName());
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return "https://wa.me/" + whatsappPhoneNumber + "?text=" + encodedText;
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
