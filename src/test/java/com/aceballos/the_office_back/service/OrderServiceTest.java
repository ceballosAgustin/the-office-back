package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.OrderItemRequest;
import com.aceballos.the_office_back.dto.OrderRequest;
import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.OrderItem;
import com.aceballos.the_office_back.entity.OrderStatus;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.OrderRepository;
import com.aceballos.the_office_back.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private ProductVariantRepository productVariantRepository;

        private OrderService orderService;

        private Product product;
        private ProductVariant variant;

        @BeforeEach
        void setUp() {
                orderService = new OrderService(orderRepository, productVariantRepository);
                ReflectionTestUtils.setField(orderService, "whatsappPhoneNumber", "541127081425");

                product = new Product();
                product.setId(10L);
                product.setTitle("Remera Oversize Dunder Mifflin");
                product.setPrice(new BigDecimal("100.00"));
                product.setIsActive(true);

                variant = new ProductVariant();
                variant.setId(101L);
                variant.setSize("M");
                variant.setStock(5);
                variant.setIsActive(true);
                variant.setProduct(product);
                product.setVariants(List.of(variant));
        }

        private Order buildOrder(OrderStatus status, int quantity) {
                Order order = new Order();
                order.setId(7L);
                order.setOrderNumber("ORD-261019-ABC123");
                order.setStatus(status);
                order.setContactName("Juan");
                order.setContactPhone("1155550000");
                order.setTotalAmount(new BigDecimal("200.00"));

                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProductVariant(variant);
                item.setQuantity(quantity);
                item.setPriceAtPurchase(new BigDecimal("100.00"));
                order.setItems(new ArrayList<>(List.of(item)));
                return order;
        }

        @Test
        void updateOrderStatus_PendingToCancelled_ShouldRestoreStockOnceUsingLockedOrder() {
                Order order = buildOrder(OrderStatus.PENDING, 2);
                when(orderRepository.findByIdWithLock(7L)).thenReturn(Optional.of(order));
                when(productVariantRepository.findByIdWithLock(101L)).thenReturn(Optional.of(variant));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                orderService.updateOrderStatus(7L, OrderStatus.CANCELLED);

                assertEquals(OrderStatus.CANCELLED, order.getStatus());
                assertEquals(7, variant.getStock());
                verify(orderRepository).findByIdWithLock(7L);
                verify(orderRepository, never()).findById(any());
                verify(productVariantRepository, times(1)).findByIdWithLock(101L);

                // Segunda cancelación sobre el mismo estado ya persistido: no vuelve a restaurar stock
                orderService.updateOrderStatus(7L, OrderStatus.CANCELLED);
                assertEquals(7, variant.getStock());
                verify(productVariantRepository, times(1)).findByIdWithLock(101L);
        }

        @Test
        void updateOrderStatus_CancelledToCancelled_ShouldBeIdempotentNoOpWithoutTouchingStock() {
                Order order = buildOrder(OrderStatus.CANCELLED, 2);
                when(orderRepository.findByIdWithLock(7L)).thenReturn(Optional.of(order));

                orderService.updateOrderStatus(7L, OrderStatus.CANCELLED);

                assertEquals(OrderStatus.CANCELLED, order.getStatus());
                assertEquals(5, variant.getStock());
                verify(orderRepository).findByIdWithLock(7L);
                verify(productVariantRepository, never()).findByIdWithLock(any());
                verify(productVariantRepository, never()).save(any());
                verify(orderRepository, never()).save(any());
        }

        @Test
        void updateOrderStatus_CancelledToPending_ShouldDeductStockUnderOrderLock() {
                Order order = buildOrder(OrderStatus.CANCELLED, 2);
                when(orderRepository.findByIdWithLock(7L)).thenReturn(Optional.of(order));
                when(productVariantRepository.findByIdWithLock(101L)).thenReturn(Optional.of(variant));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                orderService.updateOrderStatus(7L, OrderStatus.PENDING);

                assertEquals(OrderStatus.PENDING, order.getStatus());
                assertEquals(3, variant.getStock());
                verify(orderRepository).findByIdWithLock(7L);
        }

        private Order buildTwoItemOrder(OrderStatus status, ProductVariant high, ProductVariant low) {
                Order order = new Order();
                order.setId(8L);
                order.setOrderNumber("ORD-261019-XYZ789");
                order.setStatus(status);
                order.setContactName("Juan");
                order.setContactPhone("1155550000");
                order.setTotalAmount(new BigDecimal("200.00"));
                List<OrderItem> items = new ArrayList<>();
                for (ProductVariant v : List.of(high, low)) { // orden de inserción: id alto primero
                        OrderItem item = new OrderItem();
                        item.setOrder(order);
                        item.setProductVariant(v);
                        item.setQuantity(1);
                        item.setPriceAtPurchase(new BigDecimal("100.00"));
                        items.add(item);
                }
                order.setItems(items);
                return order;
        }

        private ProductVariant newVariant(long id) {
                ProductVariant v = new ProductVariant();
                v.setId(id);
                v.setSize("S" + id);
                v.setStock(5);
                v.setIsActive(true);
                v.setProduct(product);
                return v;
        }

        @Test
        void updateOrderStatus_CancelAndReactivate_ShouldLockVariantsInAscendingIdOrder() {
                ProductVariant high = newVariant(300L);
                ProductVariant low = newVariant(200L);
                product.setVariants(List.of(high, low));
                when(productVariantRepository.findByIdWithLock(300L)).thenReturn(Optional.of(high));
                when(productVariantRepository.findByIdWithLock(200L)).thenReturn(Optional.of(low));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                Order toCancel = buildTwoItemOrder(OrderStatus.PENDING, high, low);
                when(orderRepository.findByIdWithLock(8L)).thenReturn(Optional.of(toCancel));
                orderService.updateOrderStatus(8L, OrderStatus.CANCELLED);

                org.mockito.InOrder cancelOrder = inOrder(productVariantRepository);
                cancelOrder.verify(productVariantRepository).findByIdWithLock(200L);
                cancelOrder.verify(productVariantRepository).findByIdWithLock(300L);

                clearInvocations(productVariantRepository);
                orderService.updateOrderStatus(8L, OrderStatus.PENDING);

                org.mockito.InOrder reactivateOrder = inOrder(productVariantRepository);
                reactivateOrder.verify(productVariantRepository).findByIdWithLock(200L);
                reactivateOrder.verify(productVariantRepository).findByIdWithLock(300L);
        }

        @Test
        void updateOrderStatus_WhenOrderMissing_ShouldThrowResourceNotFound() {
                when(orderRepository.findByIdWithLock(99L)).thenReturn(Optional.empty());

                assertThrows(ResourceNotFoundException.class,
                                () -> orderService.updateOrderStatus(99L, OrderStatus.CANCELLED));
        }

        @Test
        void createOrder_WhenDuplicateLinesExceedCapOrOverflow_ShouldRejectWithBusinessRuleException() {
                OrderRequest overCap = OrderRequest.builder()
                                .contactName("Juan")
                                .contactPhone("1155550000")
                                .items(List.of(new OrderItemRequest(101L, 60), new OrderItemRequest(101L, 40)))
                                .build();
                assertThrows(BusinessRuleException.class, () -> orderService.createOrder(overCap));

                OrderRequest overflow = OrderRequest.builder()
                                .contactName("Juan")
                                .contactPhone("1155550000")
                                .items(List.of(new OrderItemRequest(101L, Integer.MAX_VALUE),
                                                new OrderItemRequest(101L, 2)))
                                .build();
                assertThrows(BusinessRuleException.class, () -> orderService.createOrder(overflow));

                verify(productVariantRepository, never()).findByIdWithLock(any());
                verify(orderRepository, never()).save(any());
                assertEquals(5, variant.getStock());
        }

        @Test
        void createOrder_WhenQuantityIsZeroOrNegative_ShouldRejectBeforeTouchingStock() {
                OrderRequest request = OrderRequest.builder()
                                .contactName("Juan")
                                .contactPhone("1155550000")
                                .items(List.of(new OrderItemRequest(101L, -3)))
                                .build();

                assertThrows(BusinessRuleException.class, () -> orderService.createOrder(request));
                verify(productVariantRepository, never()).findByIdWithLock(any());
                assertEquals(5, variant.getStock());
        }
}
