package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * Permite consultar todas las variantes asociadas a un producto determinado.
     */
    List<ProductVariant> findByProductId(Long productId);
}
