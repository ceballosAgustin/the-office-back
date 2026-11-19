package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Order;
import com.aceballos.the_office_back.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Obtiene la orden con bloqueo pesimista de escritura para serializar
     * cambios de estado concurrentes (evita restaurar/descontar stock dos veces).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    List<Order> findByStatus(OrderStatus status);

    /**
     * Versión paginada para que el panel de administración no colapse
     * al listar cientos de órdenes por estado.
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

}
