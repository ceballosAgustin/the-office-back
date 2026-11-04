package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.CategoryRequest;
import com.aceballos.the_office_back.dto.CategoryResponse;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.CategoryRepository;
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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, productRepository);
    }

    @Test
    void createCategory_WhenSlugOmitted_ShouldGenerateNormalizedSlugAndSave() {
        when(categoryRepository.existsBySlug("tazas-accesorios-de-cafe")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CategoryRequest request = CategoryRequest.builder()
                .name("  Tazas & Accesorios de Café!  ")
                .build();

        CategoryResponse response = categoryService.createCategory(request);

        assertEquals(1L, response.getId());
        assertEquals("Tazas & Accesorios de Café!", response.getName());
        assertEquals("tazas-accesorios-de-cafe", response.getSlug());
    }

    @Test
    void createCategory_WhenSlugProvided_ShouldNormalizeProvidedSlug() {
        when(categoryRepository.existsBySlug("remeras-oversize")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        CategoryRequest request = CategoryRequest.builder()
                .name("Remeras")
                .slug("  Remeras Oversize  ")
                .build();

        CategoryResponse response = categoryService.createCategory(request);

        assertEquals(2L, response.getId());
        assertEquals("Remeras", response.getName());
        assertEquals("remeras-oversize", response.getSlug());
    }

    @Test
    void createCategory_WhenFieldsAreInvalid_ShouldRejectWithBusinessRuleException() {
        assertThrows(BusinessRuleException.class, () -> categoryService.createCategory(
                CategoryRequest.builder().name("   ").build()));

        assertThrows(BusinessRuleException.class, () -> categoryService.createCategory(
                CategoryRequest.builder().name("Remeras").slug("---???---").build()));
    }

    @Test
    void createCategory_WhenSlugAlreadyExists_ShouldRejectWithResourceConflictException() {
        when(categoryRepository.existsBySlug("remeras")).thenReturn(true);

        CategoryRequest request = CategoryRequest.builder()
                .name("Remeras")
                .build();

        assertThrows(ResourceConflictException.class, () -> categoryService.createCategory(request));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_ShouldUpdateNameAndSlugAndRejectDuplicateSlugOnOtherCategory() {
        Category existing = Category.builder()
                .id(1L)
                .name("Remeras")
                .slug("remeras")
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsBySlugAndIdNot("remeras-estampadas", 1L)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Si no se envía slug, se regenera automáticamente desde el nuevo nombre
        CategoryResponse updated = categoryService.updateCategory(1L, CategoryRequest.builder()
                .name("Remeras Estampadas")
                .build());

        assertEquals("Remeras Estampadas", updated.getName());
        assertEquals("remeras-estampadas", updated.getSlug());

        // 2. Si el slug colisiona con otra categoría, lanza ResourceConflictException (409)
        when(categoryRepository.existsBySlugAndIdNot("buzos", 1L)).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> categoryService.updateCategory(1L,
                CategoryRequest.builder().name("Buzos").build()));

        // 3. Si la categoría no existe, lanza ResourceNotFoundException (404)
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> categoryService.updateCategory(99L,
                CategoryRequest.builder().name("Gorras").build()));
    }

    @Test
    void deleteCategory_WhenNoAssociatedProducts_ShouldDeleteSuccessfully() {
        Category existing = Category.builder()
                .id(3L)
                .name("Pósters")
                .slug("posters")
                .build();

        when(categoryRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(productRepository.countByCategoryId(3L)).thenReturn(0L);
        when(productRepository.existsByCategoryId(3L)).thenReturn(false);

        categoryService.deleteCategory(3L);

        verify(categoryRepository).delete(existing);
    }

    @Test
    void deleteCategory_WhenProductsAreAssociated_ShouldRejectWithResourceConflictException() {
        Category existing = Category.builder()
                .id(1L)
                .name("Remeras")
                .slug("remeras")
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.countByCategoryId(1L)).thenReturn(4L);

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> categoryService.deleteCategory(1L));

        assertTrue(ex.getMessage().contains("4"));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void publicQueries_ShouldContinueWorkingAsExpected() {
        Category c1 = new Category(1L, "Remeras", "remeras");
        Category c2 = new Category(2L, "Buzos", "buzos");

        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));
        when(categoryRepository.findBySlug("remeras")).thenReturn(Optional.of(c1));

        List<CategoryResponse> all = categoryService.getAllCategories();
        assertEquals(2, all.size());
        assertEquals("remeras", all.get(0).getSlug());

        CategoryResponse bySlug = categoryService.getCategoryBySlug("remeras");
        assertEquals(1L, bySlug.getId());
        assertEquals("Remeras", bySlug.getName());
    }
}
