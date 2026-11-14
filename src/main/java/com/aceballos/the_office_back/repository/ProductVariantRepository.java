package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.ProductGender;
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

    /**
     * Obtiene los talles únicos disponibles (stock > 0 y variante activa) para productos activos
     * que coinciden con los filtros especificados.
     */
    @Query("SELECT DISTINCT pv.size FROM ProductVariant pv " +
            "JOIN pv.product p " +
            "LEFT JOIN p.category c " +
            "LEFT JOIN p.brand b " +
            "WHERE p.isActive = true " +
            "  AND pv.isActive = true " +
            "  AND pv.stock > 0 " +
            "  AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "  AND (:brandSlug IS NULL OR (b.slug = :brandSlug AND b.isActive = true)) " +
            "  AND (:title IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))) " +
            "  AND (:hasGenderFilter = false OR p.gender IN :genders)")
    List<String> findAvailableSizesByFilters(
            @Param("categoryId") Long categoryId,
            @Param("brandSlug") String brandSlug,
            @Param("title") String title,
            @Param("hasGenderFilter") boolean hasGenderFilter,
            @Param("genders") List<ProductGender> genders);
}
