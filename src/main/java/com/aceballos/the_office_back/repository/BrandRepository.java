package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findBySlug(String slug);

    Optional<Brand> findBySlugAndIsActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Brand> findAllByOrderByNameAsc();

    /**
     * Devuelve las marcas activas que tienen al menos un producto activo asociado en el catálogo público,
     * ordenadas alfabéticamente por nombre.
     */
    @Query("SELECT b FROM Brand b WHERE b.isActive = true AND " +
           "EXISTS (SELECT 1 FROM Product p WHERE p.brand = b AND p.isActive = true) " +
           "ORDER BY b.name ASC")
    List<Brand> findActiveBrandsWithActiveProducts();
}

