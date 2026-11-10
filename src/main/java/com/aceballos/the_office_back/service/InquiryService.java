package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ConfirmInquiryRequest;
import com.aceballos.the_office_back.dto.InquiryConfirmationResponse;
import com.aceballos.the_office_back.dto.InquiryItemRequest;
import com.aceballos.the_office_back.dto.InquiryItemResponse;
import com.aceballos.the_office_back.dto.InquiryRequest;
import com.aceballos.the_office_back.dto.InquiryResponse;
import com.aceballos.the_office_back.entity.Inquiry;
import com.aceballos.the_office_back.entity.InquiryItem;
import com.aceballos.the_office_back.entity.InquiryStatus;
import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.InquiryRepository;
import com.aceballos.the_office_back.repository.ProductVariantRepository;
import com.aceballos.the_office_back.util.CurrencyFormatter;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryService {

    public static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter ARGENTINA_DATE_PREFIX_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    private final InquiryRepository inquiryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderService orderService;

    @Value("${app.whatsapp.phone-number:}")
    private String whatsappPhoneNumber;

    /**
     * Registra una consulta comercial pública sin crear pedido ni descontar stock.
     * Valida la existencia y disponibilidad actual de los productos/variantes,
     * consolida variantes repetidas dentro de la misma solicitud,
     * toma una fotografía histórica de precios, talles e imágenes desde la base de datos,
     * y genera el enlace de WhatsApp con el detalle formateado.
     */
    @Transactional
    public InquiryResponse createInquiry(InquiryRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La consulta debe contener al menos un producto");
        }

        // Validar cada línea y consolidar cantidades por variante preservando el orden de aparición
        Map<Long, Integer> consolidatedQuantities = new LinkedHashMap<>();
        for (InquiryItemRequest itemReq : request.getItems()) {
            if (itemReq == null || itemReq.getVariantId() == null) {
                throw new IllegalArgumentException("El ID de la variante es obligatorio");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() < 1) {
                throw new IllegalArgumentException("La cantidad de cada artículo debe ser mayor o igual a 1");
            }
            consolidatedQuantities.merge(itemReq.getVariantId(), itemReq.getQuantity(), Integer::sum);
        }

        String normalizedPhone = validateAndNormalizeWhatsappPhone();

        Inquiry inquiry = new Inquiry();
        inquiry.setInquiryNumber(generateUniqueInquiryNumber(inquiry.getCreatedAt()));
        inquiry.setStatus(InquiryStatus.NEW);
        inquiry.setContactName(StringUtils.hasText(request.getContactName()) ? request.getContactName().trim() : null);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<InquiryItem> inquiryItems = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : consolidatedQuantities.entrySet()) {
            Long variantId = entry.getKey();
            int requestedQuantity = entry.getValue();

            ProductVariant variant = productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variante no encontrada con ID: " + variantId));

            Product product = variant.getProduct();
            if (product == null || !Boolean.TRUE.equals(product.getIsActive())) {
                String title = product != null ? product.getTitle() : "Desconocido";
                throw new BusinessRuleException(
                        "El producto '" + title + "' no se encuentra activo");
            }

            if (Boolean.FALSE.equals(variant.getIsActive())) {
                throw new BusinessRuleException(
                        "El talle '" + variant.getSize() + "' del producto '" + product.getTitle() + "' no se encuentra disponible");
            }

            int availableStock = variant.getStock() != null ? variant.getStock() : 0;

            // Validación de disponibilidad actual (no reserva ni descuenta stock)
            if (availableStock < requestedQuantity) {
                throw new ResourceConflictException("Stock insuficiente para el producto '" +
                        product.getTitle() + "' en talle '" + variant.getSize() +
                        "'. Stock disponible: " + availableStock + ", cantidad solicitada: " + requestedQuantity);
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            totalAmount = totalAmount.add(subtotal);

            String primaryImageUrl = resolveProductImageUrl(product);

            InquiryItem inquiryItem = InquiryItem.builder()
                    .inquiry(inquiry)
                    .variantId(variant.getId())
                    .productTitle(product.getTitle())
                    .size(variant.getSize())
                    .quantity(requestedQuantity)
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .imageUrl(primaryImageUrl)
                    .build();

            inquiryItems.add(inquiryItem);
        }

        inquiry.setTotalAmount(totalAmount);
        inquiry.setItems(inquiryItems);

        Inquiry savedInquiry = inquiryRepository.save(inquiry);
        log.info("Consulta comercial registrada exitosamente: {} por un total estimado de {}",
                savedInquiry.getInquiryNumber(), savedInquiry.getTotalAmount());

        InquiryResponse response = mapToResponse(savedInquiry);
        response.setWhatsappUrl(buildWhatsappUrl(savedInquiry, normalizedPhone));
        return response;
    }

    /**
     * Listado paginado de consultas para el panel de administración con filtro opcional por estado.
     */
    @Transactional(readOnly = true)
    public Page<InquiryResponse> getInquiries(InquiryStatus status, Pageable pageable) {
        if (status != null) {
            return inquiryRepository.findByStatus(status, pageable).map(this::mapToResponse);
        }
        return inquiryRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Obtiene el detalle completo de una consulta por su ID interno.
     */
    @Transactional(readOnly = true)
    public InquiryResponse getInquiryById(Long id) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consulta no encontrada con ID: " + id));
        return mapToResponse(inquiry);
    }

    /**
     * Actualiza el estado de una consulta a IN_PROGRESS o NOT_CONVERTED.
     * Impide asignar CONVERTED manualmente o alterar una consulta ya convertida o cerrada.
     */
    @Transactional
    public InquiryResponse updateInquiryStatus(Long id, InquiryStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("El nuevo estado es obligatorio");
        }

        if (newStatus == InquiryStatus.CONVERTED) {
            throw new BusinessRuleException(
                    "No se puede asignar el estado CONVERTED manualmente. Utilice el endpoint de confirmación de venta.");
        }

        if (newStatus != InquiryStatus.IN_PROGRESS && newStatus != InquiryStatus.NOT_CONVERTED) {
            throw new BusinessRuleException(
                    "Solo se permite actualizar el estado de una consulta a IN_PROGRESS o NOT_CONVERTED");
        }

        Inquiry inquiry = inquiryRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consulta no encontrada con ID: " + id));

        InquiryStatus currentStatus = inquiry.getStatus();

        if (currentStatus == InquiryStatus.CONVERTED) {
            throw new BusinessRuleException(
                    "No se puede modificar el estado de una consulta que ya fue convertida en pedido");
        }

        if (currentStatus == InquiryStatus.NOT_CONVERTED && newStatus != InquiryStatus.NOT_CONVERTED) {
            throw new BusinessRuleException(
                    "No se puede reabrir una consulta que ya fue cerrada como NOT_CONVERTED");
        }

        if (currentStatus == newStatus) {
            return mapToResponse(inquiry);
        }

        inquiry.setStatus(newStatus);
        Inquiry updated = inquiryRepository.save(inquiry);
        log.info("Estado de la consulta {} actualizado de {} a {}",
                updated.getInquiryNumber(), currentStatus, newStatus);

        return mapToResponse(updated);
    }

    /**
     * Convierte de forma transaccional y atómica una consulta en un pedido real (Order),
     * validando nuevamente existencias y descontando el stock con bloqueo pesimista.
     * Operación idempotente: si ya estaba CONVERTED, retorna la orden existente sin descontar stock.
     */
    @Transactional
    public InquiryConfirmationResponse confirmInquiry(Long id, ConfirmInquiryRequest confirmRequest) {
        Inquiry inquiry = inquiryRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consulta no encontrada con ID: " + id));

        // Idempotencia: si ya fue convertida, devolver la referencia al pedido existente sin tocar inventario
        if (inquiry.getStatus() == InquiryStatus.CONVERTED && inquiry.getOrder() != null) {
            Order existingOrder = inquiry.getOrder();
            log.info("La consulta {} ya se encontraba convertida en el pedido {}. Retornando respuesta idempotente.",
                    inquiry.getInquiryNumber(), existingOrder.getOrderNumber());
            return InquiryConfirmationResponse.builder()
                    .inquiryId(inquiry.getId())
                    .inquiryNumber(inquiry.getInquiryNumber())
                    .status(inquiry.getStatus())
                    .orderId(existingOrder.getId())
                    .orderNumber(existingOrder.getOrderNumber())
                    .order(orderService.mapToResponse(existingOrder))
                    .build();
        }

        if (inquiry.getStatus() == InquiryStatus.NOT_CONVERTED) {
            throw new BusinessRuleException(
                    "No se puede confirmar la consulta " + inquiry.getInquiryNumber() +
                            " porque ya fue cerrada como NOT_CONVERTED");
        }

        // Crear la orden real y descontar stock de las variantes usando precios de referencia de la consulta
        Order createdOrder = orderService.createOrderFromInquiry(inquiry, confirmRequest);

        inquiry.setStatus(InquiryStatus.CONVERTED);
        inquiry.setOrder(createdOrder);
        Inquiry savedInquiry = inquiryRepository.save(inquiry);

        log.info("Consulta {} convertida exitosamente en el pedido {}",
                savedInquiry.getInquiryNumber(), createdOrder.getOrderNumber());

        return InquiryConfirmationResponse.builder()
                .inquiryId(savedInquiry.getId())
                .inquiryNumber(savedInquiry.getInquiryNumber())
                .status(savedInquiry.getStatus())
                .orderId(createdOrder.getId())
                .orderNumber(createdOrder.getOrderNumber())
                .order(orderService.mapToResponse(createdOrder))
                .build();
    }

    private String resolveProductImageUrl(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        return product.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .orElseGet(() -> product.getImages().stream()
                        .min(Comparator.comparing(img -> img.getOrderIndex() != null ? img.getOrderIndex() : Integer.MAX_VALUE))
                        .orElse(product.getImages().get(0)))
                .getImageUrl();
    }

    static String formatArgentinaDatePrefix(Instant instant) {
        Instant target = instant != null ? instant : Instant.now();
        return ZonedDateTime.ofInstant(target, ARGENTINA_ZONE).format(ARGENTINA_DATE_PREFIX_FORMATTER);
    }

    private String generateUniqueInquiryNumber(Instant referenceInstant) {
        String datePart = formatArgentinaDatePrefix(referenceInstant);
        String candidate;
        do {
            String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = "INQ-" + datePart + "-" + randomPart;
        } while (inquiryRepository.existsByInquiryNumber(candidate));
        return candidate;
    }

    private String validateAndNormalizeWhatsappPhone() {
        if (!StringUtils.hasText(whatsappPhoneNumber)) {
            throw new BusinessRuleException("El número de WhatsApp de la tienda no se encuentra configurado");
        }
        String digitsOnly = whatsappPhoneNumber.replaceAll("[^0-9]", "");
        if (digitsOnly.isEmpty()) {
            throw new BusinessRuleException("El número de WhatsApp configurado no tiene un formato válido");
        }
        return digitsOnly;
    }

    private String buildWhatsappUrl(Inquiry inquiry, String normalizedPhone) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(inquiry.getContactName())) {
            sb.append("Hola, soy ").append(inquiry.getContactName().trim()).append(". Te consulto por estos items:\n\n");
        } else {
            sb.append("Hola, te consulto por estos items:\n\n");
        }

        sb.append("Consulta: ").append(inquiry.getInquiryNumber()).append("\n\n");

        if (inquiry.getItems() != null) {
            for (InquiryItem item : inquiry.getItems()) {
                BigDecimal itemSubtotal = item.getSubtotal() != null
                        ? item.getSubtotal()
                        : (item.getUnitPrice() != null && item.getQuantity() != null
                                ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                                : BigDecimal.ZERO);
                sb.append("- ")
                        .append(item.getProductTitle())
                        .append(" — Talle ")
                        .append(item.getSize())
                        .append(" — Cantidad: ")
                        .append(item.getQuantity())
                        .append(" — Precio unitario: ")
                        .append(CurrencyFormatter.formatUsd(item.getUnitPrice()))
                        .append(" — Subtotal: ")
                        .append(CurrencyFormatter.formatUsd(itemSubtotal))
                        .append("\n");
            }
        }

        sb.append("\nTotal estimado: ").append(CurrencyFormatter.formatUsd(inquiry.getTotalAmount())).append("\n\n");
        sb.append("¿Me confirman la disponibilidad y cómo continuar con la compra?");

        String encodedText = URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8);
        return "https://wa.me/" + normalizedPhone + "?text=" + encodedText;
    }

    public InquiryResponse mapToResponse(Inquiry inquiry) {
        List<InquiryItemResponse> itemResponses = new ArrayList<>();
        if (inquiry.getItems() != null) {
            itemResponses = inquiry.getItems().stream()
                    .map(item -> InquiryItemResponse.builder()
                            .id(item.getId())
                            .variantId(item.getVariantId())
                            .productTitle(item.getProductTitle())
                            .size(item.getSize())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .subtotal(item.getSubtotal())
                            .imageUrl(item.getImageUrl())
                            .build())
                    .collect(Collectors.toList());
        }

        Order relatedOrder = inquiry.getOrder();
        String whatsappUrl = null;
        if (StringUtils.hasText(whatsappPhoneNumber)) {
            String digitsOnly = whatsappPhoneNumber.replaceAll("[^0-9]", "");
            if (!digitsOnly.isEmpty()) {
                whatsappUrl = buildWhatsappUrl(inquiry, digitsOnly);
            }
        }

        return InquiryResponse.builder()
                .id(inquiry.getId())
                .inquiryNumber(inquiry.getInquiryNumber())
                .status(inquiry.getStatus())
                .contactName(inquiry.getContactName())
                .createdAt(inquiry.getCreatedAt())
                .totalAmount(inquiry.getTotalAmount())
                .whatsappUrl(whatsappUrl)
                .orderId(relatedOrder != null ? relatedOrder.getId() : null)
                .orderNumber(relatedOrder != null ? relatedOrder.getOrderNumber() : null)
                .items(itemResponses)
                .build();
    }
}

