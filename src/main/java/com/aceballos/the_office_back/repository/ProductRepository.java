package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndIsActiveTrue(Long id);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByIsActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    /**
     * Búsqueda por coincidencia de título para la barra de búsqueda del e-commerce
     * ignorando mayúsculas/minúsculas y filtrando solo productos activos.
     */
    Page<Product> findByTitleContainingIgnoreCaseAndIsActiveTrue(String title, Pageable pageable);

    Page<Product> findByTitleContainingIgnoreCaseAndIsActiveTrueAndCategoryId(String title, Long categoryId, Pageable pageable);

    /**
     * Consulta administrativa paginada que permite filtrar opcionalmente por estado activo/inactivo,
     * categoría y coincidencia parcial en el título.
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(:isActive IS NULL OR p.isActive = :isActive) AND " +
            "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
            "(:title IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%')))")
    Page<Product> findAdminProducts(
            @Param("isActive") Boolean isActive,
            @Param("categoryId") Long categoryId,
            @Param("title") String title,
            Pageable pageable);
}
