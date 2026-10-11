package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByIsActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    /**
     * Búsqueda por coincidencia de título para la barra de búsqueda del e-commerce
     * ignorando mayúsculas/minúsculas y filtrando solo productos activos.
     */
    Page<Product> findByTitleContainingIgnoreCaseAndIsActiveTrue(String title, Pageable pageable);

    Page<Product> findByTitleContainingIgnoreCaseAndIsActiveTrueAndCategoryId(String title, Long categoryId, Pageable pageable);
}
