package com.aceballos.the_office_back.repository;

import com.aceballos.the_office_back.entity.NavSection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NavSectionRepository extends JpaRepository<NavSection, Long> {

    Optional<NavSection> findByCode(String code);

    boolean existsByCode(String code);

    long countByIsVisibleTrue();

    long countByIsVisibleTrueAndIdNot(Long id);

    List<NavSection> findAllByOrderByOrderIndexAsc();

    @EntityGraph(attributePaths = {"items", "items.brand", "items.category"})
    @Query("SELECT DISTINCT s FROM NavSection s WHERE s.isVisible = true ORDER BY s.orderIndex ASC")
    List<NavSection> findVisibleSectionsWithItems();

    @EntityGraph(attributePaths = {"items", "items.brand", "items.category"})
    @Query("SELECT DISTINCT s FROM NavSection s ORDER BY s.orderIndex ASC")
    List<NavSection> findAllSectionsWithItems();

    @EntityGraph(attributePaths = {"items", "items.brand", "items.category"})
    Optional<NavSection> findWithItemsById(Long id);
}

