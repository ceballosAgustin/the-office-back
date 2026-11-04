package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ConfirmInquiryRequest;
import com.aceballos.the_office_back.dto.InquiryConfirmationResponse;
import com.aceballos.the_office_back.dto.InquiryItemRequest;
import com.aceballos.the_office_back.dto.InquiryRequest;
import com.aceballos.the_office_back.dto.InquiryResponse;
import com.aceballos.the_office_back.entity.Inquiry;
import com.aceballos.the_office_back.entity.InquiryItem;
import com.aceballos.the_office_back.entity.InquiryStatus;
import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.OrderStatus;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
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
import java.util.List;
import java.util.Optional;

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
        assertTrue(decodedText.contains("Remera Oversize Dunder Mifflin — Talle M — Cantidad: 2 — Precio unitario: $28.500"));
        assertTrue(decodedText.contains("Total estimado: $57.000"));

        // Verificar que NO se modificó el stock ni se creó ninguna orden
        assertEquals(5, variantM.getStock());
        assertEquals(15, activeProduct.getBaseStock());
        verify(productVariantRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createInquiry_ShouldRejectWhenRequestedQuantityExceedsCurrentStock() {
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variantM));
        when(inquiryRepository.existsByInquiryNumber(anyString())).thenReturn(false);

        InquiryRequest request = InquiryRequest.builder()
                .contactName("Juan")
                .items(List.of(new InquiryItemRequest(101L, 6)))
                .build();

        assertThrows(BusinessRuleException.class, () -> inquiryService.createInquiry(request));

        assertEquals(5, variantM.getStock());
        verify(inquiryRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
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

        assertThrows(BusinessRuleException.class, () -> inquiryService.confirmInquiry(23L, null));

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
        when(inquiryRepository.existsByInquiryNumber(anyString())).thenReturn(false);

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
}

