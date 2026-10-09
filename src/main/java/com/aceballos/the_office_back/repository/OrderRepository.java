package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByStatus(OrderStatus status);

    /**
     * Versión paginada para que el panel de administración no colapse
     * al listar cientos de órdenes por estado.
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Permite consultar el historial de órdenes de un usuario registrado.
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
