package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * Permite consultar todas las variantes asociadas a un producto determinado.
     */
    List<ProductVariant> findByProductId(Long productId);

    /**
     * Obtiene una variante aplicando bloqueo pesimista de escritura (SELECT ... FOR UPDATE)
     * para evitar condiciones de carrera durante el descuento/restauración de stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")
    Optional<ProductVariant> findByIdWithLock(@Param("id") Long id);
}
