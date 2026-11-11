package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.BrandRequest;
import com.aceballos.the_office_back.dto.BrandResponse;
import com.aceballos.the_office_back.entity.Brand;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.BrandRepository;
import com.aceballos.the_office_back.repository.NavItemRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private NavItemRepository navItemRepository;

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandRepository, productRepository, navItemRepository);
    }

    @Test
    void createBrand_WhenSlugOmitted_ShouldGenerateNormalizedSlugAndSave() {
        when(brandRepository.existsByNameIgnoreCase("Nike Air & Sport")).thenReturn(false);
        when(brandRepository.existsBySlug("nike-air-sport")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> {
            Brand b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BrandRequest request = BrandRequest.builder()
                .name("  Nike Air & Sport  ")
                .build();

        BrandResponse response = brandService.createBrand(request);

        assertEquals(1L, response.getId());
        assertEquals("Nike Air & Sport", response.getName());
        assertEquals("nike-air-sport", response.getSlug());
        assertTrue(response.getIsActive());
    }

    @Test
    void createBrand_WhenSlugProvided_ShouldNormalizeProvidedSlug() {
        when(brandRepository.existsByNameIgnoreCase("Jordan")).thenReturn(false);
        when(brandRepository.existsBySlug("jordan-retro")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> {
            Brand b = inv.getArgument(0);
            b.setId(2L);
            return b;
        });

        BrandRequest request = BrandRequest.builder()
                .name("Jordan")
                .slug("  Jordan Retro!  ")
                .isActive(true)
                .build();

        BrandResponse response = brandService.createBrand(request);

        assertEquals(2L, response.getId());
        assertEquals("Jordan", response.getName());
        assertEquals("jordan-retro", response.getSlug());
        assertTrue(response.getIsActive());
    }

    @Test
    void createBrand_WhenDuplicateNameOrSlug_ShouldThrowResourceConflictException() {
        when(brandRepository.existsByNameIgnoreCase("Adidas")).thenReturn(true);
        BrandRequest dupName = BrandRequest.builder().name("Adidas").build();
        assertThrows(ResourceConflictException.class, () -> brandService.createBrand(dupName));

        when(brandRepository.existsByNameIgnoreCase("Puma")).thenReturn(false);
        when(brandRepository.existsBySlug("puma")).thenReturn(true);
        BrandRequest dupSlug = BrandRequest.builder().name("Puma").slug("puma").build();
        assertThrows(ResourceConflictException.class, () -> brandService.createBrand(dupSlug));
    }

    @Test
    void createBrand_WhenNameIsBlankOrTooLong_ShouldThrowBusinessRuleException() {
        assertThrows(BusinessRuleException.class, () -> brandService.createBrand(
                BrandRequest.builder().name("   ").build()));

        String longName = "A".repeat(101);
        assertThrows(BusinessRuleException.class, () -> brandService.createBrand(
                BrandRequest.builder().name(longName).build()));
    }

    @Test
    void getBrandByIdAndSlug_WhenFoundAndNotFound_ShouldReturnOrThrow() {
        Brand brand = new Brand(10L, "New Balance", "new-balance", true);
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand));
        when(brandRepository.findBySlug("new-balance")).thenReturn(Optional.of(brand));
        when(brandRepository.findById(999L)).thenReturn(Optional.empty());
        when(brandRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

        BrandResponse byId = brandService.getBrandById(10L);
        assertEquals(10L, byId.getId());
        assertEquals("New Balance", byId.getName());

        BrandResponse bySlug = brandService.getBrandBySlug("New-Balance");
        assertEquals("new-balance", bySlug.getSlug());

        assertThrows(ResourceNotFoundException.class, () -> brandService.getBrandById(999L));
        assertThrows(ResourceNotFoundException.class, () -> brandService.getBrandBySlug("inexistente"));
    }

    @Test
    void updateBrand_ShouldUpdateNameAndSlugAndValidateDuplicates() {
        Brand existing = new Brand(5L, "Vans", "vans", true);
        when(brandRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("Vans Off The Wall", 5L)).thenReturn(false);
        when(brandRepository.existsBySlugAndIdNot("vans-off-the-wall", 5L)).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        BrandRequest request = BrandRequest.builder()
                .name("Vans Off The Wall")
                .slug("vans-off-the-wall")
                .isActive(false)
                .build();

        BrandResponse updated = brandService.updateBrand(5L, request);
        assertEquals("Vans Off The Wall", updated.getName());
        assertEquals("vans-off-the-wall", updated.getSlug());
        assertFalse(updated.getIsActive());
    }

    @Test
    void updateBrandStatus_ActivateAndDeactivate_ShouldUpdateIsActive() {
        Brand brand = new Brand(8L, "Converse", "converse", true);
        when(brandRepository.findById(8L)).thenReturn(Optional.of(brand));
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        BrandResponse deactivated = brandService.updateBrandStatus(8L, false);
        assertFalse(deactivated.getIsActive());

        BrandResponse reactivated = brandService.updateBrandStatus(8L, true);
        assertTrue(reactivated.getIsActive());

        assertThrows(IllegalArgumentException.class, () -> brandService.updateBrandStatus(8L, null));
    }

    @Test
    void deleteBrand_WhenAssociatedProductsExist_ShouldThrowConflict() {
        Brand brand = new Brand(1L, "Nike", "nike", true);
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(productRepository.countByBrandId(1L)).thenReturn(3L);

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> brandService.deleteBrand(1L));
        assertTrue(ex.getMessage().contains("tiene productos asociados"));
        verify(brandRepository, never()).delete(any());
    }

    @Test
    void deleteBrand_WhenNoProductsAssociated_ShouldDelete() {
        Brand brand = new Brand(1L, "Nike", "nike", true);
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(productRepository.countByBrandId(1L)).thenReturn(0L);
        when(productRepository.existsByBrandId(1L)).thenReturn(false);

        brandService.deleteBrand(1L);
        verify(navItemRepository, times(1)).deleteByBrandId(1L);
        verify(brandRepository, times(1)).delete(brand);
    }

    @Test
    void getActiveBrands_ShouldReturnOnlyActiveBrandsWithActiveProducts() {
        Brand nike = new Brand(1L, "Nike", "nike", true);
        Brand jordan = new Brand(2L, "Jordan", "jordan", true);
        when(brandRepository.findActiveBrandsWithActiveProducts()).thenReturn(List.of(jordan, nike));

        List<BrandResponse> active = brandService.getActiveBrands();
        assertEquals(2, active.size());
        assertEquals("Jordan", active.get(0).getName());
        assertEquals("Nike", active.get(1).getName());
    }

    @Test
    void getAllBrandsAdmin_ShouldReturnAllBrandsOrderedAlphabetically() {
        Brand adidas = new Brand(3L, "Adidas", "adidas", false);
        Brand nike = new Brand(1L, "Nike", "nike", true);
        when(brandRepository.findAllByOrderByNameAsc()).thenReturn(List.of(adidas, nike));

        List<BrandResponse> all = brandService.getAllBrandsAdmin();
        assertEquals(2, all.size());
        assertEquals("Adidas", all.get(0).getName());
        assertFalse(all.get(0).getIsActive());
        assertEquals("Nike", all.get(1).getName());
        assertTrue(all.get(1).getIsActive());
    }
}

