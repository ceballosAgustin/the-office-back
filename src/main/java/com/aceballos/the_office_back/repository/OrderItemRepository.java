package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Permite consultar todos los ítems de un pedido específico.
     */
    List<OrderItem> findByOrderId(Long orderId);
}
