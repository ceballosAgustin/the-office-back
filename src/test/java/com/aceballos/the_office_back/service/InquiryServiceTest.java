package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ConfirmInquiryRequest;
import com.aceballos.the_office_back.dto.InquiryConfirmationResponse;
import com.aceballos.the_office_back.dto.InquiryItemRequest;
import com.aceballos.the_office_back.dto.InquiryRequest;
import com.aceballos.the_office_back.dto.InquiryResponse;
import com.aceballos.the_office_back.dto.OrderItemRequest;
import com.aceballos.the_office_back.dto.OrderRequest;
import com.aceballos.the_office_back.dto.OrderResponse;
import com.aceballos.the_office_back.util.CurrencyFormatter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.aceballos.the_office_back.entity.Inquiry;
import com.aceballos.the_office_back.entity.InquiryItem;
import com.aceballos.the_office_back.entity.InquiryStatus;
import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.OrderStatus;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.InquiryRepository;
import com.aceballos.the_office_back.repository.OrderRepository;
import com.aceballos.the_office_back.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;
    private InquiryService inquiryService;

    private Product activeProduct;
    private ProductVariant variantM;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productVariantRepository);
        ReflectionTestUtils.setField(orderService, "whatsappPhoneNumber", "541136823069");

        inquiryService = new InquiryService(inquiryRepository, productVariantRepository, orderService);
        ReflectionTestUtils.setField(inquiryService, "whatsappPhoneNumber", "541136823069");

        activeProduct = new Product();
        activeProduct.setId(10L);
        activeProduct.setTitle("Remera Oversize Dunder Mifflin");
        activeProduct.setPrice(new BigDecimal("28500.00"));
        activeProduct.setBaseStock(15);
        activeProduct.setIsActive(true);
        activeProduct.setImages(List.of(
                ProductImage.builder()
                        .id(1L)
                        .imageUrl("https://res.cloudinary.com/demo/image/upload/remera.jpg")
                        .isPrimary(true)
                        .orderIndex(0)
                        .product(activeProduct)
                        .build()
        ));

        variantM = new ProductVariant();
        variantM.setId(101L);
        variantM.setSize("M");
        variantM.setStock(5);
        variantM.setIsActive(true);
        variantM.setProduct(activeProduct);
        activeProduct.setVariants(List.of(variantM));
    }

    @Test
    void createInquiry_ShouldRegisterInquiryWithoutCreatingOrderOrReducingStock() {
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));
        when(inquiryRepository.existsByInquiryNumber(anyString())).thenReturn(false);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(invocation -> {
            Inquiry saved = invocation.getArgument(0);
            saved.setId(23L);
            return saved;
        });

        InquiryRequest request = InquiryRequest.builder()
                .contactName("Juan")
                .items(List.of(new InquiryItemRequest(101L, 2)))
                .build();

        InquiryResponse response = inquiryService.createInquiry(request);

        assertNotNull(response);
        assertEquals(23L, response.getId());
        assertTrue(response.getInquiryNumber().startsWith("INQ-"));
        assertEquals(InquiryStatus.NEW, response.getStatus());
        assertEquals("Juan", response.getContactName());
        assertEquals(new BigDecimal("57000.00"), response.getTotalAmount());
        assertNull(response.getOrderId());
        assertNull(response.getOrderNumber());

        assertEquals(1, response.getItems().size());
        assertEquals(101L, response.getItems().get(0).getVariantId());
        assertEquals("Remera Oversize Dunder Mifflin", response.getItems().get(0).getProductTitle());
        assertEquals("M", response.getItems().get(0).getSize());
        assertEquals(2, response.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("28500.00"), response.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("57000.00"), response.getItems().get(0).getSubtotal());
        assertEquals("https://res.cloudinary.com/demo/image/upload/remera.jpg", response.getItems().get(0).getImageUrl());

        assertNotNull(response.getWhatsappUrl());
        assertTrue(response.getWhatsappUrl().startsWith("https://wa.me/541136823069?text="));
        String decodedText = URLDecoder.decode(
                response.getWhatsappUrl().substring("https://wa.me/541136823069?text=".length()),
                StandardCharsets.UTF_8);
        assertTrue(decodedText.contains(response.getInquiryNumber()));
        assertTrue(decodedText.contains(
                "Remera Oversize Dunder Mifflin — Talle M — Cantidad: 2 — Precio unitario: 28.500 USD — Subtotal: 57.000 USD"));
        assertTrue(decodedText.contains("Total estimado: 57.000 USD"));
        assertFalse(decodedText.contains("$"));
        assertFalse(decodedText.contains("ARS"));

        // Verificar que NO se modificó el stock ni se creó ninguna orden
        assertEquals(5, variantM.getStock());
        assertEquals(15, activeProduct.getBaseStock());
        verify(productVariantRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createInquiry_WhenQuantityEqualsOneOrEqualsAvailableStock_ShouldSucceedWithoutModifyingStock() {
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));
        when(inquiryRepository.existsByInquiryNumber(anyString())).thenReturn(false);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> {
            Inquiry saved = inv.getArgument(0);
            saved.setId(24L);
            return saved;
        });

        // 1. Cantidad igual a 1
        InquiryResponse responseOne = inquiryService.createInquiry(InquiryRequest.builder()
                .contactName("Ana")
                .items(List.of(new InquiryItemRequest(101L, 1)))
                .build());
        assertEquals(1, responseOne.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("28500.00"), responseOne.getTotalAmount());
        assertEquals(5, variantM.getStock());

        // 2. Cantidad exactamente igual al stock disponible (5)
        InquiryResponse responseFullStock = inquiryService.createInquiry(InquiryRequest.builder()
                .contactName("Ana")
                .items(List.of(new InquiryItemRequest(101L, 5)))
                .build());
        assertEquals(5, responseFullStock.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("142500.00"), responseFullStock.getTotalAmount());
        assertEquals(5, variantM.getStock());
    }

    @Test
    void createInquiry_ShouldRejectWhenRequestedQuantityExceedsCurrentStock() {
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));

        InquiryRequest request = InquiryRequest.builder()
                .contactName("Juan")
                .items(List.of(new InquiryItemRequest(101L, 6)))
                .build();

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> inquiryService.createInquiry(request));

        assertTrue(ex.getMessage().contains("Remera Oversize Dunder Mifflin"));
        assertTrue(ex.getMessage().contains("M"));
        assertTrue(ex.getMessage().contains("5"));
        assertTrue(ex.getMessage().contains("6"));

        assertEquals(5, variantM.getStock());
        verify(inquiryRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createInquiry_WhenRepeatedVariantInMultipleItems_ShouldConsolidateAndEnforceStockLimit() {
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));

        // 1. Suma de líneas repetidas (3 + 3 = 6) supera el stock disponible (5) -> rechazar con 409
        InquiryRequest exceedingRequest = InquiryRequest.builder()
                .contactName("Pedro")
                .items(List.of(
                        new InquiryItemRequest(101L, 3),
                        new InquiryItemRequest(101L, 3)
                ))
                .build();

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> inquiryService.createInquiry(exceedingRequest));
        assertTrue(ex.getMessage().contains("Stock disponible: 5, cantidad solicitada: 6"));
        verify(inquiryRepository, never()).save(any());

        // 2. Suma de líneas repetidas (2 + 3 = 5) entra dentro del stock (5) -> consolida en un único InquiryItem
        when(inquiryRepository.existsByInquiryNumber(anyString())).thenReturn(false);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> {
            Inquiry saved = inv.getArgument(0);
            saved.setId(25L);
            return saved;
        });

        InquiryRequest validRepeatedRequest = InquiryRequest.builder()
                .contactName("Pedro")
                .items(List.of(
                        new InquiryItemRequest(101L, 2),
                        new InquiryItemRequest(101L, 3)
                ))
                .build();

        InquiryResponse consolidatedResponse = inquiryService.createInquiry(validRepeatedRequest);
        assertEquals(1, consolidatedResponse.getItems().size());
        assertEquals(5, consolidatedResponse.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("142500.00"), consolidatedResponse.getTotalAmount());
        assertEquals(5, variantM.getStock());
    }

    @Test
    void createInquiry_WhenQuantityIsZeroOrNegativeOrItemsEmpty_ShouldRejectWithIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> inquiryService.createInquiry(
                InquiryRequest.builder().contactName("Juan").items(Collections.emptyList()).build()));

        assertThrows(IllegalArgumentException.class, () -> inquiryService.createInquiry(
                InquiryRequest.builder().contactName("Juan").items(List.of(new InquiryItemRequest(101L, 0))).build()));

        assertThrows(IllegalArgumentException.class, () -> inquiryService.createInquiry(
                InquiryRequest.builder().contactName("Juan").items(List.of(new InquiryItemRequest(101L, -3))).build()));

        verifyNoInteractions(inquiryRepository, orderRepository);
    }

    @Test
    void createInquiry_WhenVariantDoesNotExistOrProductIsInactive_ShouldReject() {
        // Variante inexistente -> 404 ResourceNotFoundException
        when(productVariantRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> inquiryService.createInquiry(
                InquiryRequest.builder().items(List.of(new InquiryItemRequest(999L, 1))).build()));

        // Producto inactivo -> BusinessRuleException
        activeProduct.setIsActive(false);
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));
        assertThrows(BusinessRuleException.class, () -> inquiryService.createInquiry(
                InquiryRequest.builder().items(List.of(new InquiryItemRequest(101L, 1))).build()));

        verify(inquiryRepository, never()).save(any());
    }

    @Test
    void updateInquiryStatus_ShouldDisallowManualConversionAndAllowInProgressOrNotConverted() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(23L);
        inquiry.setInquiryNumber("INQ-261019-ABC123");
        inquiry.setStatus(InquiryStatus.NEW);
        inquiry.setTotalAmount(new BigDecimal("57000.00"));

        // No debe permitir pasar a CONVERTED manualmente
        assertThrows(BusinessRuleException.class,
                () -> inquiryService.updateInquiryStatus(23L, InquiryStatus.CONVERTED));

        when(inquiryRepository.findByIdWithLock(23L)).thenReturn(Optional.of(inquiry));
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> inv.getArgument(0));

        InquiryResponse inProgress = inquiryService.updateInquiryStatus(23L, InquiryStatus.IN_PROGRESS);
        assertEquals(InquiryStatus.IN_PROGRESS, inProgress.getStatus());

        InquiryResponse notConverted = inquiryService.updateInquiryStatus(23L, InquiryStatus.NOT_CONVERTED);
        assertEquals(InquiryStatus.NOT_CONVERTED, notConverted.getStatus());

        // Una vez cerrada como NOT_CONVERTED no debe permitir reabrirse a IN_PROGRESS
        assertThrows(BusinessRuleException.class,
                () -> inquiryService.updateInquiryStatus(23L, InquiryStatus.IN_PROGRESS));
    }

    @Test
    void confirmInquiry_ShouldCreateOrderDeductStockWithReferencePriceAndBeIdempotent() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(23L);
        inquiry.setInquiryNumber("INQ-261019-ABC123");
        inquiry.setStatus(InquiryStatus.IN_PROGRESS);
        inquiry.setContactName("Juan");
        inquiry.setTotalAmount(new BigDecimal("57000.00"));

        InquiryItem item = InquiryItem.builder()
                .id(1L)
                .inquiry(inquiry)
                .variantId(101L)
                .productTitle("Remera Oversize Dunder Mifflin")
                .size("M")
                .quantity(2)
                .unitPrice(new BigDecimal("28500.00"))
                .subtotal(new BigDecimal("57000.00"))
                .build();
        inquiry.setItems(List.of(item));

        // Simular que el precio del catálogo subió después de la consulta; la orden debe respetar el precio de referencia (28500.00)
        activeProduct.setPrice(new BigDecimal("35000.00"));

        when(inquiryRepository.findByIdWithLock(23L)).thenReturn(Optional.of(inquiry));
        when(productVariantRepository.findByIdWithLock(101L)).thenReturn(Optional.of(variantM));
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(45L);
            return o;
        });
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1ra confirmación: crea pedido y descuenta stock
        InquiryConfirmationResponse firstConfirmation = inquiryService.confirmInquiry(
                23L, new ConfirmInquiryRequest("Juan Pérez", "1122334455"));

        assertEquals(23L, firstConfirmation.getInquiryId());
        assertEquals(InquiryStatus.CONVERTED, firstConfirmation.getStatus());
        assertEquals(45L, firstConfirmation.getOrderId());
        assertNotNull(firstConfirmation.getOrderNumber());
        assertEquals(OrderStatus.PENDING, firstConfirmation.getOrder().getStatus());
        assertEquals(new BigDecimal("57000.00"), firstConfirmation.getOrder().getTotalAmount());
        assertEquals(3, variantM.getStock()); // 5 - 2 = 3

        // 2da confirmación sobre la misma consulta: debe ser idempotente (no descuenta stock de nuevo ni crea otra orden)
        InquiryConfirmationResponse secondConfirmation = inquiryService.confirmInquiry(23L, null);

        assertEquals(45L, secondConfirmation.getOrderId());
        assertEquals(firstConfirmation.getOrderNumber(), secondConfirmation.getOrderNumber());
        assertEquals(3, variantM.getStock()); // El stock permanece en 3
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void confirmInquiry_WhenInsufficientStock_ShouldFailAndNotChangeInquiryStatus() {
        variantM.setStock(1); // Solo queda 1 en stock, pero la consulta pide 2

        Inquiry inquiry = new Inquiry();
        inquiry.setId(23L);
        inquiry.setInquiryNumber("INQ-261019-ABC123");
        inquiry.setStatus(InquiryStatus.NEW);
        inquiry.setTotalAmount(new BigDecimal("57000.00"));
        inquiry.setItems(List.of(
                InquiryItem.builder()
                        .id(1L)
                        .inquiry(inquiry)
                        .variantId(101L)
                        .productTitle("Remera Oversize Dunder Mifflin")
                        .size("M")
                        .quantity(2)
                        .unitPrice(new BigDecimal("28500.00"))
                        .subtotal(new BigDecimal("57000.00"))
                        .build()
        ));

        when(inquiryRepository.findByIdWithLock(23L)).thenReturn(Optional.of(inquiry));
        when(productVariantRepository.findByIdWithLock(101L)).thenReturn(Optional.of(variantM));

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> inquiryService.confirmInquiry(23L, null));

        assertTrue(ex.getMessage().contains("Stock disponible: 1, cantidad solicitada: 2"));

        // La consulta no debe pasar a CONVERTED ni asociar ninguna orden, y el stock no debe alterarse
        assertEquals(InquiryStatus.NEW, inquiry.getStatus());
        assertNull(inquiry.getOrder());
        assertEquals(1, variantM.getStock());
        verify(orderRepository, never()).save(any());
        verify(inquiryRepository, never()).save(any());
    }

    @Test
    void createAndConfirmInquiry_WhenVariantIsInactive_ShouldRejectWithBusinessRuleException() {
        variantM.setIsActive(false);

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));

        InquiryRequest request = InquiryRequest.builder()
                .contactName("Juan")
                .items(List.of(new InquiryItemRequest(101L, 1)))
                .build();

        assertThrows(BusinessRuleException.class, () -> inquiryService.createInquiry(request));

        Inquiry inquiry = new Inquiry();
        inquiry.setId(23L);
        inquiry.setInquiryNumber("INQ-261019-ABC123");
        inquiry.setStatus(InquiryStatus.NEW);
        inquiry.setTotalAmount(new BigDecimal("28500.00"));
        inquiry.setItems(List.of(
                InquiryItem.builder()
                        .id(1L)
                        .inquiry(inquiry)
                        .variantId(101L)
                        .productTitle("Remera Oversize Dunder Mifflin")
                        .size("M")
                        .quantity(1)
                        .unitPrice(new BigDecimal("28500.00"))
                        .subtotal(new BigDecimal("28500.00"))
                        .build()
        ));

        when(inquiryRepository.findByIdWithLock(23L)).thenReturn(Optional.of(inquiry));
        when(productVariantRepository.findByIdWithLock(101L)).thenReturn(Optional.of(variantM));

        assertThrows(BusinessRuleException.class, () -> inquiryService.confirmInquiry(23L, null));
        assertEquals(InquiryStatus.NEW, inquiry.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void argentinaDatePrefix_ShouldUseBuenosAiresCalendarDateRegardlessOfJvmDefaultTimeZone() {
        TimeZone originalTz = TimeZone.getDefault();
        try {
            // Simular que el servidor corre en UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            // 2026-11-07T00:55:56.158816Z equivale a 2026-11-06 21:55:56.158816 en Argentina (-03:00)
            Instant historicalInstant = Instant.parse("2026-11-07T00:55:56.158816Z");
            assertEquals("261106", InquiryService.formatArgentinaDatePrefix(historicalInstant));
            assertEquals("261106", OrderService.formatArgentinaDatePrefix(historicalInstant));

            // Límite exacto antes y después de la medianoche de Argentina (03:00 UTC)
            Instant beforeMidnightAr = Instant.parse("2026-11-07T02:59:59Z");
            Instant atMidnightAr = Instant.parse("2026-11-07T03:00:00Z");

            assertEquals("261106", InquiryService.formatArgentinaDatePrefix(beforeMidnightAr));
            assertEquals("261107", InquiryService.formatArgentinaDatePrefix(atMidnightAr));
            assertEquals("261106", OrderService.formatArgentinaDatePrefix(beforeMidnightAr));
            assertEquals("261107", OrderService.formatArgentinaDatePrefix(atMidnightAr));
        } finally {
            TimeZone.setDefault(originalTz);
        }
    }

    @Test
    void createdAtPrecision_InInquiryAndOrder_ShouldTruncateNanosecondsToMicrosecondsBeforePersistenceAndMatchOnRead() throws Exception {
        Instant nanoInstant = Instant.parse("2026-11-10T02:50:00.577511900Z");
        Instant expectedMicrosInstant = Instant.parse("2026-11-10T02:50:00.577511Z");

        // 1. Verificar normalización en setter y en @PrePersist de Inquiry y Order
        Inquiry inquiry = new Inquiry();
        assertEquals(0, inquiry.getCreatedAt().getNano() % 1_000);
        inquiry.setCreatedAt(nanoInstant);
        assertEquals(expectedMicrosInstant, inquiry.getCreatedAt());

        ReflectionTestUtils.setField(inquiry, "createdAt", nanoInstant);
        inquiry.onCreate();
        assertEquals(expectedMicrosInstant, inquiry.getCreatedAt());

        Order order = new Order();
        assertEquals(0, order.getCreatedAt().getNano() % 1_000);
        order.setCreatedAt(nanoInstant);
        assertEquals(expectedMicrosInstant, order.getCreatedAt());

        ReflectionTestUtils.setField(order, "createdAt", nanoInstant);
        order.onCreate();
        assertEquals(expectedMicrosInstant, order.getCreatedAt());

        // 2. Verificar que la entidad conserva el valor normalizado antes de persistirse y que la lectura posterior coincide
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));
        when(inquiryRepository.existsByInquiryNumber(anyString())).thenReturn(false);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> {
            Inquiry toSave = inv.getArgument(0);
            assertEquals(0, toSave.getCreatedAt().getNano() % 1_000);
            toSave.setId(30L);
            return toSave;
        });

        InquiryResponse createdResponse = inquiryService.createInquiry(InquiryRequest.builder()
                .contactName("Juan")
                .items(List.of(new InquiryItemRequest(101L, 1)))
                .build());

        assertEquals(0, createdResponse.getCreatedAt().getNano() % 1_000);

        // 3. Verificar serialización JSON exacta a microsegundos (sin 9 dígitos de nanosegundos) y coincidencia en lectura posterior
        inquiry.setId(31L);
        inquiry.setInquiryNumber("INQ-261109-BA3F8D");
        inquiry.setTotalAmount(new BigDecimal("28500.00"));
        when(inquiryRepository.findById(31L)).thenReturn(Optional.of(inquiry));

        order.setId(41L);
        order.setOrderNumber("ORD-261109-A1D232");
        order.setContactName("Juan");
        order.setContactPhone("1122334455");
        order.setTotalAmount(new BigDecimal("28500.00"));
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));

        InquiryResponse readInquiry = inquiryService.getInquiryById(31L);
        OrderResponse readOrder = orderService.getOrderById(41L);

        assertEquals(expectedMicrosInstant, readInquiry.getCreatedAt());
        assertEquals(expectedMicrosInstant, readOrder.getCreatedAt());

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String inquiryJson = mapper.writeValueAsString(readInquiry);
        String orderJson = mapper.writeValueAsString(readOrder);

        assertTrue(inquiryJson.contains("\"createdAt\":\"2026-11-10T02:50:00.577511Z\""));
        assertTrue(orderJson.contains("\"createdAt\":\"2026-11-10T02:50:00.577511Z\""));
    }

    @Test
    void currencyFormatter_ShouldFormatUsdAccordingToStoreRules() {
        // 1. 150 -> 150 USD (incluso con escala 2 como 150.00)
        assertEquals("150 USD", CurrencyFormatter.formatUsd(new BigDecimal("150")));
        assertEquals("150 USD", CurrencyFormatter.formatUsd(new BigDecimal("150.0")));
        assertEquals("150 USD", CurrencyFormatter.formatUsd(new BigDecimal("150.00")));

        // 2. 1500 -> 1.500 USD
        assertEquals("1.500 USD", CurrencyFormatter.formatUsd(new BigDecimal("1500")));
        assertEquals("1.500 USD", CurrencyFormatter.formatUsd(new BigDecimal("1500.00")));

        // 3. 150.50 -> 150,50 USD
        assertEquals("150,50 USD", CurrencyFormatter.formatUsd(new BigDecimal("150.50")));
        assertEquals("150,50 USD", CurrencyFormatter.formatUsd(new BigDecimal("150.5")));

        // 4. 150000.75 -> 150.000,75 USD
        assertEquals("150.000,75 USD", CurrencyFormatter.formatUsd(new BigDecimal("150000.75")));

        // Cero y null
        assertEquals("0 USD", CurrencyFormatter.formatUsd(BigDecimal.ZERO));
        assertEquals("0 USD", CurrencyFormatter.formatUsd(new BigDecimal("0.00")));
        assertEquals("0 USD", CurrencyFormatter.formatUsd(null));
    }

    @Test
    void usdCalculationsAndConsistency_BetweenInquiryAndOrder_ShouldMaintainExactBigDecimalPrecisionAndWhatsappMessages() throws Exception {
        // Configurar Producto 1: Test Oversize T-Shirt a 150.00 USD
        Product tshirt = new Product();
        tshirt.setId(50L);
        tshirt.setTitle("Test Oversize T-Shirt");
        tshirt.setPrice(new BigDecimal("150.00"));
        tshirt.setBaseStock(10);
        tshirt.setIsActive(true);

        ProductVariant tshirtM = new ProductVariant(501L, "M", 10, true, tshirt);
        tshirt.setVariants(List.of(tshirtM));

        // Configurar Producto 2 con decimales: Hoodie Dunder Mifflin a 150.50 USD
        Product hoodie = new Product();
        hoodie.setId(51L);
        hoodie.setTitle("Hoodie Dunder Mifflin");
        hoodie.setPrice(new BigDecimal("150.50"));
        hoodie.setBaseStock(8);
        hoodie.setIsActive(true);

        ProductVariant hoodieL = new ProductVariant(502L, "L", 8, true, hoodie);
        hoodie.setVariants(List.of(hoodieL));

        when(productVariantRepository.findById(501L)).thenReturn(Optional.of(tshirtM));
        when(productVariantRepository.findById(502L)).thenReturn(Optional.of(hoodieL));
        when(inquiryRepository.existsByInquiryNumber(anyString())).thenReturn(false);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> {
            Inquiry saved = inv.getArgument(0);
            saved.setId(80L);
            return saved;
        });

        // Caso 5: Dos unidades de 150 USD producen subtotal 300 USD y total 300 USD
        InquiryResponse singleProductInquiry = inquiryService.createInquiry(InquiryRequest.builder()
                .contactName("Martín")
                .items(List.of(new InquiryItemRequest(501L, 2)))
                .build());

        assertEquals(0, singleProductInquiry.getItems().get(0).getUnitPrice().compareTo(new BigDecimal("150.00")));
        assertEquals(0, singleProductInquiry.getItems().get(0).getSubtotal().compareTo(new BigDecimal("300.00")));
        assertEquals(0, singleProductInquiry.getTotalAmount().compareTo(new BigDecimal("300.00")));

        String decodedSingleMsg = URLDecoder.decode(
                singleProductInquiry.getWhatsappUrl().substring("https://wa.me/541136823069?text=".length()),
                StandardCharsets.UTF_8);
        assertTrue(decodedSingleMsg.contains(
                "- Test Oversize T-Shirt — Talle M — Cantidad: 2 — Precio unitario: 150 USD — Subtotal: 300 USD"));
        assertTrue(decodedSingleMsg.contains("Total estimado: 300 USD"));
        assertFalse(decodedSingleMsg.contains("$"));
        assertFalse(decodedSingleMsg.contains("ARS"));

        // Casos 6, 7 y 9: Varios productos/variantes con decimales (2 x 150.00 = 300.00 + 3 x 150.50 = 451.50 -> Total = 751.50)
        final Inquiry[] capturedInquiry = new Inquiry[1];
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> {
            Inquiry saved = inv.getArgument(0);
            saved.setId(81L);
            capturedInquiry[0] = saved;
            return saved;
        });

        InquiryResponse multiProductInquiry = inquiryService.createInquiry(InquiryRequest.builder()
                .contactName("Lucía")
                .items(List.of(
                        new InquiryItemRequest(501L, 2),
                        new InquiryItemRequest(502L, 3)
                ))
                .build());

        assertEquals(2, multiProductInquiry.getItems().size());
        assertEquals(0, multiProductInquiry.getItems().get(0).getUnitPrice().compareTo(new BigDecimal("150.00")));
        assertEquals(0, multiProductInquiry.getItems().get(0).getSubtotal().compareTo(new BigDecimal("300.00")));
        assertEquals(0, multiProductInquiry.getItems().get(1).getUnitPrice().compareTo(new BigDecimal("150.50")));
        assertEquals(0, multiProductInquiry.getItems().get(1).getSubtotal().compareTo(new BigDecimal("451.50")));
        assertEquals(0, multiProductInquiry.getTotalAmount().compareTo(new BigDecimal("751.50")));

        String decodedMultiMsg = URLDecoder.decode(
                multiProductInquiry.getWhatsappUrl().substring("https://wa.me/541136823069?text=".length()),
                StandardCharsets.UTF_8);
        assertTrue(decodedMultiMsg.contains(
                "- Test Oversize T-Shirt — Talle M — Cantidad: 2 — Precio unitario: 150 USD — Subtotal: 300 USD"));
        assertTrue(decodedMultiMsg.contains(
                "- Hoodie Dunder Mifflin — Talle L — Cantidad: 3 — Precio unitario: 150,50 USD — Subtotal: 451,50 USD"));
        assertTrue(decodedMultiMsg.contains("Total estimado: 751,50 USD"));
        assertFalse(decodedMultiMsg.contains("$"));

        // Caso 8 y 11: Conversión de consulta a pedido conserva exactamente los importes y genera URL de WhatsApp del pedido en USD
        tshirt.setPrice(new BigDecimal("220.00")); // Cambio posterior en catálogo no debe alterar la consulta ya registrada
        when(inquiryRepository.findByIdWithLock(81L)).thenReturn(Optional.of(capturedInquiry[0]));
        when(productVariantRepository.findByIdWithLock(501L)).thenReturn(Optional.of(tshirtM));
        when(productVariantRepository.findByIdWithLock(502L)).thenReturn(Optional.of(hoodieL));
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(90L);
            return o;
        });

        InquiryConfirmationResponse confirmation = inquiryService.confirmInquiry(
                81L, new ConfirmInquiryRequest("Lucía Gómez", "5491144556677"));

        OrderResponse confirmedOrder = confirmation.getOrder();
        assertNotNull(confirmedOrder);
        assertEquals(0, confirmedOrder.getTotalAmount().compareTo(new BigDecimal("751.50")));
        assertEquals(2, confirmedOrder.getItems().size());
        assertEquals(0, confirmedOrder.getItems().get(0).getPriceAtPurchase().compareTo(new BigDecimal("150.00")));
        assertEquals(0, confirmedOrder.getItems().get(0).getSubtotal().compareTo(new BigDecimal("300.00")));
        assertEquals(0, confirmedOrder.getItems().get(1).getPriceAtPurchase().compareTo(new BigDecimal("150.50")));
        assertEquals(0, confirmedOrder.getItems().get(1).getSubtotal().compareTo(new BigDecimal("451.50")));

        assertNotNull(confirmedOrder.getWhatsappUrl());
        String decodedOrderMsg = URLDecoder.decode(
                confirmedOrder.getWhatsappUrl().substring("https://wa.me/541136823069?text=".length()),
                StandardCharsets.UTF_8);
        assertEquals(
                "Hola, acabo de hacer el pedido " + confirmedOrder.getOrderNumber() + " por un total de 751,50 USD. Mi nombre es Lucía Gómez.",
                decodedOrderMsg);
        assertFalse(decodedOrderMsg.contains("$"));

        // Verificar también creación directa de pedido vía OrderService.createOrder y serialización numérica en JSON (Casos 11, 13 y 14)
        tshirt.setPrice(new BigDecimal("150.00"));
        OrderResponse directOrder = orderService.createOrder(OrderRequest.builder()
                .contactName("Carlos")
                .contactPhone("1155667788")
                .items(List.of(new OrderItemRequest(501L, 2)))
                .build());

        assertEquals(0, directOrder.getTotalAmount().compareTo(new BigDecimal("300.00")));
        String decodedDirectOrderMsg = URLDecoder.decode(
                directOrder.getWhatsappUrl().substring("https://wa.me/541136823069?text=".length()),
                StandardCharsets.UTF_8);
        assertTrue(decodedDirectOrderMsg.contains("por un total de 300 USD."));
        assertFalse(decodedDirectOrderMsg.contains("$"));

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonNode inquiryNode = mapper.readTree(mapper.writeValueAsString(multiProductInquiry));
        JsonNode orderNode = mapper.readTree(mapper.writeValueAsString(confirmedOrder));

        assertTrue(inquiryNode.get("totalAmount").isNumber());
        assertTrue(inquiryNode.get("items").get(0).get("unitPrice").isNumber());
        assertTrue(inquiryNode.get("items").get(0).get("subtotal").isNumber());
        assertTrue(orderNode.get("totalAmount").isNumber());
        assertTrue(orderNode.get("items").get(0).get("priceAtPurchase").isNumber());
        assertTrue(orderNode.get("items").get(0).get("subtotal").isNumber());
    }
}
