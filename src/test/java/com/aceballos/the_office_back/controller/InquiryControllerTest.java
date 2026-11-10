package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.InquiryConfirmationResponse;
import com.aceballos.the_office_back.dto.InquiryItemResponse;
import com.aceballos.the_office_back.dto.InquiryResponse;
import com.aceballos.the_office_back.dto.OrderItemResponse;
import com.aceballos.the_office_back.dto.OrderResponse;
import com.aceballos.the_office_back.entity.InquiryStatus;
import com.aceballos.the_office_back.entity.OrderStatus;
import com.aceballos.the_office_back.exception.GlobalExceptionHandler;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.security.JwtAuthFilter;
import com.aceballos.the_office_back.security.JwtUtils;
import com.aceballos.the_office_back.security.SecurityConfig;
import com.aceballos.the_office_back.security.UserDetailsServiceImpl;
import com.aceballos.the_office_back.service.InquiryService;
import com.aceballos.the_office_back.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        InquiryPublicController.class,
        InquiryAdminController.class,
        OrderPublicController.class
})
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
class InquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InquiryService inquiryService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void createInquiry_ShouldBePublicAndReturn201Created() throws Exception {
        InquiryResponse mockResponse = InquiryResponse.builder()
                .id(23L)
                .inquiryNumber("INQ-261019-ABC123")
                .status(InquiryStatus.NEW)
                .contactName("Juan")
                .createdAt(Instant.parse("2026-11-07T00:55:56.158816Z"))
                .totalAmount(new BigDecimal("57000.00"))
                .whatsappUrl("https://wa.me/541136823069?text=Hola")
                .items(List.of(
                        InquiryItemResponse.builder()
                                .id(1L)
                                .variantId(101L)
                                .productTitle("Remera Oversize Dunder Mifflin")
                                .size("M")
                                .quantity(2)
                                .unitPrice(new BigDecimal("28500.00"))
                                .subtotal(new BigDecimal("57000.00"))
                                .imageUrl("https://example.com/product.jpg")
                                .build()
                ))
                .build();

        when(inquiryService.createInquiry(any())).thenReturn(mockResponse);

        String payload = """
                {
                  "contactName": "Juan",
                  "items": [
                    {
                      "variantId": 101,
                      "quantity": 2
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(23))
                .andExpect(jsonPath("$.inquiryNumber").value("INQ-261019-ABC123"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-07T00:55:56.158816Z"))
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.totalAmount").value(57000.00))
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/541136823069?text=Hola"))
                .andExpect(jsonPath("$.items[0].variantId").value(101))
                .andExpect(jsonPath("$.items[0].unitPrice").isNumber())
                .andExpect(jsonPath("$.items[0].unitPrice").value(28500.00))
                .andExpect(jsonPath("$.items[0].subtotal").isNumber())
                .andExpect(jsonPath("$.items[0].subtotal").value(57000.00));
    }

    @Test
    void createInquiry_WhenInsufficientStock_ShouldReturn409ConflictWithClearMessage() throws Exception {
        when(inquiryService.createInquiry(any())).thenThrow(
                new ResourceConflictException(
                        "Stock insuficiente para el producto 'Remera Oversize Dunder Mifflin' en talle 'M'. Stock disponible: 3, cantidad solicitada: 5"));

        String payload = """
                {
                  "contactName": "Juan",
                  "items": [
                    {
                      "variantId": 101,
                      "quantity": 5
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Stock insuficiente para el producto 'Remera Oversize Dunder Mifflin' en talle 'M'. Stock disponible: 3, cantidad solicitada: 5"));
    }

    @Test
    void createInquiry_WhenInvalidItemsOrQuantityZeroOrNegative_ShouldReturn400BadRequest() throws Exception {
        String emptyItemsPayload = """
                {
                  "contactName": "Juan",
                  "items": []
                }
                """;

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyItemsPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.items").exists());

        String zeroQuantityPayload = """
                {
                  "contactName": "Juan",
                  "items": [
                    {
                      "variantId": 101,
                      "quantity": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroQuantityPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").exists());
    }

    @Test
    void createOrderDirectly_ShouldBlockAnonymousVisitorsWith401() throws Exception {
        String payload = """
                {
                  "contactName": "Juan",
                  "contactPhone": "1122334455",
                  "items": [
                    { "variantId": 101, "quantity": 1 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrderByOrderNumber_ShouldRemainPublic() throws Exception {
        OrderResponse orderResponse = OrderResponse.builder()
                .id(45L)
                .orderNumber("ORD-261019-999999")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("57000.00"))
                .build();

        when(orderService.getOrderByOrderNumber("ORD-261019-999999")).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/ORD-261019-999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-261019-999999"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminInquiriesEndpoints_ShouldRejectNonAdminUserWith403() throws Exception {
        mockMvc.perform(get("/api/admin/inquiries"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/inquiries/23/confirm"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmInquiry_WithAdminRole_ShouldReturn200WithConfirmationDetails() throws Exception {
        OrderResponse orderResponse = OrderResponse.builder()
                .id(45L)
                .orderNumber("ORD-261019-7F3B2A")
                .status(OrderStatus.PENDING)
                .contactName("Juan")
                .contactPhone("WhatsApp")
                .totalAmount(new BigDecimal("300.00"))
                .items(List.of(
                        OrderItemResponse.builder()
                                .id(10L)
                                .variantId(101L)
                                .productTitle("Test Oversize T-Shirt")
                                .size("M")
                                .quantity(2)
                                .priceAtPurchase(new BigDecimal("150.00"))
                                .subtotal(new BigDecimal("300.00"))
                                .build()
                ))
                .build();

        InquiryConfirmationResponse confirmationResponse = InquiryConfirmationResponse.builder()
                .inquiryId(23L)
                .inquiryNumber("INQ-261019-ABC123")
                .status(InquiryStatus.CONVERTED)
                .orderId(45L)
                .orderNumber("ORD-261019-7F3B2A")
                .order(orderResponse)
                .build();

        when(inquiryService.confirmInquiry(eq(23L), any())).thenReturn(confirmationResponse);

        mockMvc.perform(post("/api/admin/inquiries/23/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiryId").value(23))
                .andExpect(jsonPath("$.inquiryNumber").value("INQ-261019-ABC123"))
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.orderId").value(45))
                .andExpect(jsonPath("$.orderNumber").value("ORD-261019-7F3B2A"))
                .andExpect(jsonPath("$.order.status").value("PENDING"))
                .andExpect(jsonPath("$.order.totalAmount").isNumber())
                .andExpect(jsonPath("$.order.totalAmount").value(300.00))
                .andExpect(jsonPath("$.order.items[0].priceAtPurchase").isNumber())
                .andExpect(jsonPath("$.order.items[0].priceAtPurchase").value(150.00))
                .andExpect(jsonPath("$.order.items[0].subtotal").isNumber())
                .andExpect(jsonPath("$.order.items[0].subtotal").value(300.00));
    }
}
