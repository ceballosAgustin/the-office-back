package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.NavItem;
import com.aceballos.the_office_back.entity.ProductGender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NavItemRepository extends JpaRepository<NavItem, Long> {

    List<NavItem> findBySectionIdOrderByColumnIndexAscOrderIndexAsc(Long sectionId);

    boolean existsBySectionIdAndBrandId(Long sectionId, Long brandId);

    boolean existsBySectionIdAndCategoryId(Long sectionId, Long categoryId);

    boolean existsBySectionIdAndBrandIdAndIdNot(Long sectionId, Long brandId, Long id);

    boolean existsBySectionIdAndCategoryIdAndIdNot(Long sectionId, Long categoryId, Long id);

    long countBySectionIdAndIsVisibleTrue(Long sectionId);

    long countBySectionIdAndColumnIndexAndIsVisibleTrue(Long sectionId, Integer columnIndex);

    @Query("SELECT COUNT(i) FROM NavItem i WHERE i.section.id = :sectionId AND i.targetType = com.aceballos.the_office_back.entity.NavTargetType.BRAND AND i.isVisible = true")
    long countVisibleBrandsBySectionId(@Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(i) FROM NavItem i WHERE i.section.id = :sectionId AND i.targetType = com.aceballos.the_office_back.entity.NavTargetType.BRAND AND i.columnIndex = :columnIndex AND i.isVisible = true")
    long countVisibleBrandsBySectionIdAndColumnIndex(@Param("sectionId") Long sectionId, @Param("columnIndex") Integer columnIndex);

    @Query("SELECT COUNT(i) FROM NavItem i WHERE i.section.id = :sectionId AND i.targetType = com.aceballos.the_office_back.entity.NavTargetType.BRAND AND i.isVisible = true AND i.id != :excludeItemId")
    long countVisibleBrandsBySectionIdExcludingItem(@Param("sectionId") Long sectionId, @Param("excludeItemId") Long excludeItemId);

    @Query("SELECT COUNT(i) FROM NavItem i WHERE i.section.id = :sectionId AND i.targetType = com.aceballos.the_office_back.entity.NavTargetType.BRAND AND i.columnIndex = :columnIndex AND i.isVisible = true AND i.id != :excludeItemId")
    long countVisibleBrandsBySectionIdAndColumnIndexExcludingItem(@Param("sectionId") Long sectionId, @Param("columnIndex") Integer columnIndex, @Param("excludeItemId") Long excludeItemId);

    @Query("SELECT COUNT(i) > 0 FROM NavItem i WHERE i.section.id = :sectionId " +
            "AND i.targetType = com.aceballos.the_office_back.entity.NavTargetType.PRODUCT_FILTER " +
            "AND ((:categoryId IS NULL AND i.category IS NULL) OR i.category.id = :categoryId) " +
            "AND ((:brandId IS NULL AND i.brand IS NULL) OR i.brand.id = :brandId) " +
            "AND ((:gender IS NULL AND i.gender IS NULL) OR i.gender = :gender)")
    boolean existsDuplicateProductFilter(
            @Param("sectionId") Long sectionId,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            @Param("gender") ProductGender gender);

    @Query("SELECT COUNT(i) > 0 FROM NavItem i WHERE i.section.id = :sectionId " +
            "AND i.id != :excludeId " +
            "AND i.targetType = com.aceballos.the_office_back.entity.NavTargetType.PRODUCT_FILTER " +
            "AND ((:categoryId IS NULL AND i.category IS NULL) OR i.category.id = :categoryId) " +
            "AND ((:brandId IS NULL AND i.brand IS NULL) OR i.brand.id = :brandId) " +
            "AND ((:gender IS NULL AND i.gender IS NULL) OR i.gender = :gender)")
    boolean existsDuplicateProductFilterExcludingId(
            @Param("sectionId") Long sectionId,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            @Param("gender") ProductGender gender,
            @Param("excludeId") Long excludeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM NavItem i WHERE i.brand.id = :brandId")
    void deleteByBrandId(@Param("brandId") Long brandId);

    @Modifying
    @Transactional
    @Query("DELETE FROM NavItem i WHERE i.category.id = :categoryId")
    void deleteByCategoryId(@Param("categoryId") Long categoryId);
}

