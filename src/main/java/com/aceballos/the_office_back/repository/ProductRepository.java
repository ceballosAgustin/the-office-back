package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductGender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndIsActiveTrue(Long id);

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    boolean existsByBrandId(Long brandId);

    long countByBrandId(Long brandId);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByIsActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    /**
     * Búsqueda por coincidencia de título para la barra de búsqueda del e-commerce
     * ignorando mayúsculas/minúsculas y filtrando solo productos activos.
     */
    Page<Product> findByTitleContainingIgnoreCaseAndIsActiveTrue(String title, Pageable pageable);

    Page<Product> findByTitleContainingIgnoreCaseAndIsActiveTrueAndCategoryId(String title, Long categoryId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.gender = com.aceballos.the_office_back.entity.ProductGender.UNISEX WHERE p.gender IS NULL")
    int updateNullGenderToUnisex();

    /**
     * Consulta pública paginada con EntityGraph para evitar consultas N+1 en category y brand.
     * Soporta la combinación flexible de categoría, brand slug, búsqueda por título, género
     * y talle de variante con stock > 0, garantizando que únicamente se devuelvan productos activos.
     */
    @EntityGraph(attributePaths = {"category", "brand"})
    @Query("SELECT p FROM Product p " +
            "LEFT JOIN p.category c " +
            "LEFT JOIN p.brand b " +
            "WHERE p.isActive = true AND " +
            "(:categoryId IS NULL OR c.id = :categoryId) AND " +
            "(:brandSlug IS NULL OR (b.slug = :brandSlug AND b.isActive = true)) AND " +
            "(:title IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
            "(:hasGenderFilter = false OR p.gender IN :genders) AND " +
            "(:variantSize IS NULL OR EXISTS (" +
            "    SELECT 1 FROM ProductVariant pv " +
            "    WHERE pv.product = p " +
            "      AND pv.isActive = true " +
            "      AND pv.stock > 0 " +
            "      AND (" +
            "          UPPER(TRIM(pv.size)) = UPPER(TRIM(:variantSize))" +
            "          OR REPLACE(UPPER(TRIM(pv.size)), ',', '.') = UPPER(TRIM(:variantSize))" +
            "          OR UPPER(TRIM(pv.size)) = CONCAT(UPPER(TRIM(:variantSize)), '.0')" +
            "          OR UPPER(TRIM(pv.size)) = CONCAT(UPPER(TRIM(:variantSize)), '.00')" +
            "          OR CONCAT(UPPER(TRIM(pv.size)), '.0') = UPPER(TRIM(:variantSize))" +
            "          OR CONCAT(UPPER(TRIM(pv.size)), '.00') = UPPER(TRIM(:variantSize))" +
            "      )" +
            "))")
    Page<Product> findActiveProducts(
            @Param("categoryId") Long categoryId,
            @Param("brandSlug") String brandSlug,
            @Param("title") String title,
            @Param("hasGenderFilter") boolean hasGenderFilter,
            @Param("genders") List<ProductGender> genders,
            @Param("variantSize") String variantSize,
            Pageable pageable);

    /**
     * Verifica si existen productos activos que satisfagan los criterios de un PRODUCT_FILTER en el menú.
     */
    @Query("SELECT COUNT(p) > 0 FROM Product p " +
            "LEFT JOIN p.category c " +
            "LEFT JOIN p.brand b " +
            "WHERE p.isActive = true AND " +
            "(:categoryId IS NULL OR c.id = :categoryId) AND " +
            "(:brandId IS NULL OR (b.id = :brandId AND b.isActive = true)) AND " +
            "(:hasGenderFilter = false OR p.gender IN :genders)")
    boolean existsActiveProductsByCriteria(
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            @Param("hasGenderFilter") boolean hasGenderFilter,
            @Param("genders") List<ProductGender> genders);

    /**
     * Consulta administrativa paginada que permite filtrar opcionalmente por estado activo/inactivo,
     * categoría y coincidencia parcial en el título.
     */
    @EntityGraph(attributePaths = {"category", "brand"})
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
