package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.config.NavigationDataInitializer;
import com.aceballos.the_office_back.dto.*;
import com.aceballos.the_office_back.entity.*;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceConflictException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.BrandRepository;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.NavItemRepository;
import com.aceballos.the_office_back.repository.NavSectionRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NavigationServiceTest {

    @Mock
    private NavSectionRepository navSectionRepository;

    @Mock
    private NavItemRepository navItemRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private NavigationService navigationService;

    @BeforeEach
    void setUp() {
        navigationService = new NavigationService(
                navSectionRepository,
                navItemRepository,
                brandRepository,
                categoryRepository,
                productRepository
        );
    }

    // =========================================================================
    // MODELO Y RELACIONES (Pruebas 1 a 6)
    // =========================================================================

    @Test
    @DisplayName("1. Crear una sección exitosamente")
    void test01_createSection_Success() {
        when(navSectionRepository.existsByCode("brands")).thenReturn(false);
        when(navSectionRepository.countByIsVisibleTrue()).thenReturn(2L);
        when(navSectionRepository.count()).thenReturn(2L);
        when(navSectionRepository.save(any(NavSection.class))).thenAnswer(inv -> {
            NavSection s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        NavSectionRequest request = NavSectionRequest.builder()
                .code("brands")
                .label("Marcas Destacadas")
                .isVisible(true)
                .build();

        NavSectionResponse response = navigationService.createSection(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("brands", response.getCode());
        assertEquals("Marcas Destacadas", response.getLabel());
        assertTrue(response.getIsVisible());
    }

    @Test
    @DisplayName("2. Crear un elemento BRAND")
    void test02_createItem_Brand_Success() {
        NavSection section = NavSection.builder().id(1L).code("brands").label("Marcas").build();
        Brand brand = Brand.builder().id(100L).name("Nike").slug("nike").isActive(true).build();

        when(navSectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(brandRepository.findById(100L)).thenReturn(Optional.of(brand));
        when(navItemRepository.existsBySectionIdAndBrandId(1L, 100L)).thenReturn(false);
        when(navItemRepository.countVisibleBrandsBySectionId(1L)).thenReturn(0L);
        when(navItemRepository.countVisibleBrandsBySectionIdAndColumnIndex(1L, 0)).thenReturn(0L);
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> {
            NavItem it = inv.getArgument(0);
            it.setId(50L);
            return it;
        });

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.BRAND)
                .brandId(100L)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse response = navigationService.addItem(1L, request);

        assertEquals(50L, response.getId());
        assertEquals(NavTargetType.BRAND, response.getTargetType());
        assertEquals(100L, response.getBrandId());
        assertEquals("Nike", response.getBrandName());
        assertEquals("nike", response.getBrandSlug());
        assertEquals("Nike", response.getLabel());
    }

    @Test
    @DisplayName("3. Crear un elemento CATEGORY")
    void test03_createItem_Category_Success() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        Category cat = Category.builder().id(200L).name("Zapatillas").slug("zapatillas").build();

        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(categoryRepository.findById(200L)).thenReturn(Optional.of(cat));
        when(navItemRepository.existsBySectionIdAndCategoryId(2L, 200L)).thenReturn(false);
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> {
            NavItem it = inv.getArgument(0);
            it.setId(51L);
            return it;
        });

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.CATEGORY)
                .categoryId(200L)
                .label("Sneakers Exclusivos")
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse response = navigationService.addItem(2L, request);

        assertEquals(51L, response.getId());
        assertEquals(NavTargetType.CATEGORY, response.getTargetType());
        assertEquals(200L, response.getCategoryId());
        assertEquals("Sneakers Exclusivos", response.getLabel());
        assertEquals("zapatillas", response.getCategorySlug());
    }

    @Test
    @DisplayName("4. Crear un CUSTOM_LINK")
    void test04_createItem_CustomLink_Success() {
        NavSection section = NavSection.builder().id(3L).code("extra").label("Extra").build();

        when(navSectionRepository.findById(3L)).thenReturn(Optional.of(section));
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> {
            NavItem it = inv.getArgument(0);
            it.setId(52L);
            return it;
        });

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.CUSTOM_LINK)
                .label("Lanzamientos")
                .customUrl("/drops")
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse response = navigationService.addItem(3L, request);

        assertEquals(52L, response.getId());
        assertEquals(NavTargetType.CUSTOM_LINK, response.getTargetType());
        assertEquals("Lanzamientos", response.getLabel());
        assertEquals("/drops", response.getCustomUrl());
    }

    @Test
    @DisplayName("5. Rechazar destinos incompatibles con targetType")
    void test05_rejectIncompatibleDestinationsForTargetType() {
        NavSection section = NavSection.builder().id(1L).code("general").label("General").build();
        when(navSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        // BRAND con categoryId o customUrl
        NavItemRequest brandWithCat = NavItemRequest.builder()
                .targetType(NavTargetType.BRAND)
                .brandId(10L)
                .categoryId(20L)
                .columnIndex(0)
                .build();
        assertThrows(BusinessRuleException.class, () -> navigationService.addItem(1L, brandWithCat));

        // CATEGORY con brandId o customUrl
        NavItemRequest catWithBrand = NavItemRequest.builder()
                .targetType(NavTargetType.CATEGORY)
                .categoryId(20L)
                .brandId(10L)
                .columnIndex(0)
                .build();
        assertThrows(BusinessRuleException.class, () -> navigationService.addItem(1L, catWithBrand));

        // CUSTOM_LINK con brandId
        NavItemRequest linkWithBrand = NavItemRequest.builder()
                .targetType(NavTargetType.CUSTOM_LINK)
                .brandId(10L)
                .label("Test")
                .customUrl("/test")
                .columnIndex(0)
                .build();
        assertThrows(BusinessRuleException.class, () -> navigationService.addItem(1L, linkWithBrand));
    }

    @Test
    @DisplayName("6. Evitar referencias duplicadas dentro de una misma sección")
    void test06_preventDuplicateReferencesWithinSection() {
        NavSection section = NavSection.builder().id(1L).code("brands").label("Marcas").build();
        Brand brand = Brand.builder().id(10L).name("Nike").slug("nike").isActive(true).build();

        when(navSectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand));
        when(navItemRepository.existsBySectionIdAndBrandId(1L, 10L)).thenReturn(true);

        NavItemRequest duplicateBrand = NavItemRequest.builder()
                .targetType(NavTargetType.BRAND)
                .brandId(10L)
                .columnIndex(0)
                .build();

        assertThrows(ResourceConflictException.class, () -> navigationService.addItem(1L, duplicateBrand));
    }

    // =========================================================================
    // LÍMITES DE MENÚ (Pruebas 7 a 13)
    // =========================================================================

    @Test
    @DisplayName("7 y 8. Permitir hasta 8 marcas visibles y rechazar la novena")
    void test07_test08_allowUpToEightVisibleBrands_and_rejectNinthVisibleBrand() {
        NavSection brandsSection = NavSection.builder().id(1L).code("brands").label("Marcas").build();
        Brand brand = Brand.builder().id(99L).name("Puma").slug("puma").isActive(true).build();

        when(navSectionRepository.findById(1L)).thenReturn(Optional.of(brandsSection));
        when(navItemRepository.countVisibleBrandsBySectionId(1L)).thenReturn(8L);

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.BRAND)
                .brandId(99L)
                .columnIndex(1)
                .isVisible(true)
                .build();

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> navigationService.addItem(1L, request));
        assertTrue(ex.getMessage().contains("no puede tener más de 8 marcas visibles"));
    }

    @Test
    @DisplayName("9 y 10. Permitir 4 marcas por columna y rechazar una quinta")
    void test09_test10_allowFourBrandsPerColumn_and_rejectFifthInSameColumn() {
        NavSection brandsSection = NavSection.builder().id(1L).code("brands").label("Marcas").build();

        when(navSectionRepository.findById(1L)).thenReturn(Optional.of(brandsSection));
        when(navItemRepository.countVisibleBrandsBySectionId(1L)).thenReturn(4L);
        when(navItemRepository.countVisibleBrandsBySectionIdAndColumnIndex(1L, 0)).thenReturn(4L);

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.BRAND)
                .brandId(105L)
                .columnIndex(0)
                .isVisible(true)
                .build();

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> navigationService.addItem(1L, request));
        assertTrue(ex.getMessage().contains("No se pueden tener más de 4 marcas visibles en la columna 0"));
    }

    @Test
    @DisplayName("11. Permitir ocultar una marca y recuperar su cupo")
    void test11_hidingBrandReclaimsVisibleQuota() {
        NavSection brandsSection = NavSection.builder().id(1L).code("brands").label("Marcas").build();
        NavItem item = NavItem.builder()
                .id(10L)
                .section(brandsSection)
                .targetType(NavTargetType.BRAND)
                .columnIndex(0)
                .isVisible(true)
                .build();

        when(navItemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> inv.getArgument(0));

        NavItemResponse response = navigationService.updateItemStatus(10L, false);

        assertFalse(response.getIsVisible());
        assertFalse(item.getIsVisible());
    }

    @Test
    @DisplayName("12 y 13. Permitir hasta 5 secciones visibles y rechazar una sexta")
    void test12_test13_allowUpToFiveVisibleSections_and_rejectSixthVisibleSection() {
        when(navSectionRepository.countByIsVisibleTrue()).thenReturn(5L);

        NavSectionRequest request = NavSectionRequest.builder()
                .code("section-6")
                .label("Sección Extra")
                .isVisible(true)
                .build();

        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> navigationService.createSection(request));
        assertTrue(ex.getMessage().contains("No se pueden tener más de 5 secciones visibles"));
    }

    // =========================================================================
    // INTEGRIDAD Y REGLAS DE NEGOCIO (Pruebas 14 a 21)
    // =========================================================================

    @Test
    @DisplayName("14 y 15. Renombrar Nike a Off White: NavItem refleja Off White y slug off-white")
    void test14_test15_renameBrand_updatesNavItemNameAndSlug_andKeepsProductAssociation() {
        Brand brand = Brand.builder().id(1L).name("Nike").slug("nike").isActive(true).build();
        NavItem item = NavItem.builder()
                .id(10L)
                .targetType(NavTargetType.BRAND)
                .brand(brand)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse beforeRename = navigationService.mapToItemResponse(item);
        assertEquals("Nike", beforeRename.getLabel());
        assertEquals("nike", beforeRename.getBrandSlug());

        // Modificamos el nombre y slug de la marca (simulando BrandService.updateBrand)
        brand.setName("Off White");
        brand.setSlug("off-white");

        NavItemResponse afterRename = navigationService.mapToItemResponse(item);
        assertEquals("Off White", afterRename.getLabel());
        assertEquals("off-white", afterRename.getBrandSlug());
        assertEquals(1L, afterRename.getBrandId());
    }

    @Test
    @DisplayName("16. Ocultar una marca del menú sin desactivar Brand")
    void test16_hideBrandInNav_withoutDeactivatingBrand() {
        NavSection section = NavSection.builder().id(1L).code("brands").build();
        Brand brand = Brand.builder().id(5L).name("Vans").slug("vans").isActive(true).build();
        NavItem item = NavItem.builder()
                .id(20L)
                .section(section)
                .targetType(NavTargetType.BRAND)
                .brand(brand)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        when(navItemRepository.findById(20L)).thenReturn(Optional.of(item));
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> inv.getArgument(0));

        NavItemResponse res = navigationService.updateItemStatus(20L, false);

        assertFalse(res.getIsVisible());
        assertTrue(brand.getIsActive(), "Brand.isActive no debe verse afectado por ocultar el ítem en el navbar");
    }

    @Test
    @DisplayName("17 y 18. Desactivar marca: desaparece del endpoint público; reactivar: reaparece")
    void test17_test18_deactivateBrand_disappearsFromPublicNav_andReappearsOnReactivation() {
        Brand nike = Brand.builder().id(1L).name("Nike").slug("nike").isActive(true).build();
        NavSection section = NavSection.builder().id(1L).code("brands").label("Marcas").orderIndex(0).isVisible(true).build();
        NavItem nikeItem = NavItem.builder()
                .id(100L)
                .section(section)
                .targetType(NavTargetType.BRAND)
                .brand(nike)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();
        section.setItems(List.of(nikeItem));

        when(navSectionRepository.findVisibleSectionsWithItems()).thenReturn(List.of(section));

        // Caso 1: Nike está inactiva o no tiene productos activos en catálogo
        when(brandRepository.findActiveBrandsWithActiveProducts()).thenReturn(Collections.emptyList());
        List<PublicNavigationSectionResponse> publicNav1 = navigationService.getPublicNavigation();
        assertEquals(1, publicNav1.size());
        assertTrue(publicNav1.get(0).getColumns().isEmpty(), "Nike no debe aparecer si no está en la lista de marcas activas con productos");

        // Caso 2: Reactivamos la marca con productos activos
        when(brandRepository.findActiveBrandsWithActiveProducts()).thenReturn(List.of(nike));
        List<PublicNavigationSectionResponse> publicNav2 = navigationService.getPublicNavigation();
        assertEquals(1, publicNav2.size());
        assertEquals(1, publicNav2.get(0).getColumns().size());
        assertEquals(1, publicNav2.get(0).getColumns().get(0).getItems().size());
        assertEquals("Nike", publicNav2.get(0).getColumns().get(0).getItems().get(0).getBrandName());
        assertEquals("nike", publicNav2.get(0).getColumns().get(0).getItems().get(0).getBrandSlug());
    }

    @Test
    @DisplayName("19. Eliminar un NavItem sin eliminar Brand")
    void test19_deleteNavItem_doesNotDeleteBrand() {
        NavSection section = NavSection.builder().id(1L).build();
        Brand brand = Brand.builder().id(10L).name("Jordan").build();
        NavItem item = NavItem.builder().id(50L).section(section).brand(brand).build();

        when(navItemRepository.findById(50L)).thenReturn(Optional.of(item));

        navigationService.deleteItem(50L);

        verify(navItemRepository, times(1)).delete(item);
        verify(brandRepository, never()).delete(any());
    }

    @Test
    @DisplayName("20. Eliminar una sección sin eliminar Brand ni Category")
    void test20_deleteSection_doesNotDeleteBrandOrCategory() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").build();
        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));

        navigationService.deleteSection(2L);

        verify(navSectionRepository, times(1)).delete(section);
        verify(brandRepository, never()).delete(any());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("21. Eliminar marca o categoría limpia sus NavItems para evitar huérfanos")
    void test21_deleteBrandOrCategory_cleansUpNavItems() {
        // Probamos que NavItemRepository dispone de deleteByBrandId y deleteByCategoryId
        navItemRepository.deleteByBrandId(1L);
        verify(navItemRepository, times(1)).deleteByBrandId(1L);

        navItemRepository.deleteByCategoryId(2L);
        verify(navItemRepository, times(1)).deleteByCategoryId(2L);
    }

    // =========================================================================
    // SEGURIDAD Y VALIDACIÓN DE URLS (Prueba 25)
    // =========================================================================

    @Test
    @DisplayName("25. CUSTOM_LINK rechaza URLs peligrosas o mal formadas")
    void test25_customLinkRejectsDangerousUrls() {
        assertThrows(BusinessRuleException.class, () -> NavigationService.validateCustomUrl("javascript:alert(1)"));
        assertThrows(BusinessRuleException.class, () -> NavigationService.validateCustomUrl("data:text/html;base64,..."));
        assertThrows(BusinessRuleException.class, () -> NavigationService.validateCustomUrl("vbscript:test"));
        assertThrows(BusinessRuleException.class, () -> NavigationService.validateCustomUrl("file:///etc/passwd"));
        assertThrows(BusinessRuleException.class, () -> NavigationService.validateCustomUrl("//sitio-malicioso.com"));
        assertThrows(BusinessRuleException.class, () -> NavigationService.validateCustomUrl("http://inseguro.com"));

        // Válidas
        assertDoesNotThrow(() -> NavigationService.validateCustomUrl("/drops"));
        assertDoesNotThrow(() -> NavigationService.validateCustomUrl("/catalog?filter=news"));
        assertDoesNotThrow(() -> NavigationService.validateCustomUrl("https://theofficeba.com/terminos"));
    }

    // =========================================================================
    // INICIALIZACIÓN POR DEFECTO (Pruebas 27 a 30)
    // =========================================================================

    @Test
    @DisplayName("27, 28, 29. Se crean 4 secciones por defecto, sin marcas precargadas, asociando categorías reales")
    void test27_test28_test29_initializer_createsDefaultSections_withoutBrands_associatesRealCategories() {
        when(navSectionRepository.count()).thenReturn(0L);

        Category sneakersCat = Category.builder().id(1L).name("Sneakers").slug("sneakers").build();
        when(categoryRepository.findBySlug("sneakers")).thenReturn(Optional.of(sneakersCat));
        when(categoryRepository.findBySlug("tops")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("remeras")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("bottoms")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("pantalones")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("streetwear")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("indumentaria")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("accessories")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("accesorios")).thenReturn(Optional.empty());

        NavigationDataInitializer initializer = new NavigationDataInitializer(navSectionRepository, categoryRepository, productRepository);
        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<NavSection> captor = ArgumentCaptor.forClass(NavSection.class);
        verify(navSectionRepository, times(4)).save(captor.capture());

        List<NavSection> savedSections = captor.getAllValues();
        assertEquals(4, savedSections.size());

        // 1. Brands
        assertEquals("brands", savedSections.get(0).getCode());
        assertTrue(savedSections.get(0).getItems().isEmpty(), "Brands no debe tener marcas precargadas automáticamente");

        // 2. Sneakers
        assertEquals("sneakers", savedSections.get(1).getCode());
        assertEquals(1, savedSections.get(1).getItems().size());
        assertEquals(sneakersCat, savedSections.get(1).getItems().get(0).getCategory());

        // 3. Streetwear
        assertEquals("streetwear", savedSections.get(2).getCode());

        // 4. Accessories
        assertEquals("accessories", savedSections.get(3).getCode());
    }

    @Test
    @DisplayName("30. Reinicializar no sobrescribe ni duplica configuraciones existentes")
    void test30_initializer_doesNotOverwriteExistingConfiguration() {
        when(navSectionRepository.count()).thenReturn(4L);

        NavigationDataInitializer initializer = new NavigationDataInitializer(navSectionRepository, categoryRepository, productRepository);
        initializer.run(new DefaultApplicationArguments());

        verify(navSectionRepository, never()).save(any());
    }

    // =========================================================================
    // PRUEBAS DE PRODUCT_FILTER (Escenarios 30 a 42)
    // =========================================================================

    @Test
    @DisplayName("30. Crear PRODUCT_FILTER con categoría y género")
    void test30_createProductFilter_CategoryAndGender_Success() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        Category cat = Category.builder().id(20L).name("Sneakers").slug("sneakers").build();

        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(cat));
        when(navItemRepository.existsDuplicateProductFilter(2L, 20L, null, ProductGender.MEN)).thenReturn(false);
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> {
            NavItem it = inv.getArgument(0);
            it.setId(60L);
            return it;
        });

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Sneakers Men")
                .categoryId(20L)
                .gender(ProductGender.MEN)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse response = navigationService.addItem(2L, request);
        assertNotNull(response);
        assertEquals(NavTargetType.PRODUCT_FILTER, response.getTargetType());
        assertEquals("Sneakers Men", response.getLabel());
        assertEquals(20L, response.getCategoryId());
        assertEquals(ProductGender.MEN, response.getGender());
        assertNull(response.getBrandId());
    }

    @Test
    @DisplayName("31. Crear PRODUCT_FILTER con marca")
    void test31_createProductFilter_Brand_Success() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        Brand brand = Brand.builder().id(100L).name("Nike").slug("nike").isActive(true).build();

        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(brandRepository.findById(100L)).thenReturn(Optional.of(brand));
        when(navItemRepository.existsDuplicateProductFilter(2L, null, 100L, null)).thenReturn(false);
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> {
            NavItem it = inv.getArgument(0);
            it.setId(61L);
            return it;
        });

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Nike en Sneakers")
                .brandId(100L)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse response = navigationService.addItem(2L, request);
        assertNotNull(response);
        assertEquals(NavTargetType.PRODUCT_FILTER, response.getTargetType());
        assertEquals(100L, response.getBrandId());
    }

    @Test
    @DisplayName("32. Crear PRODUCT_FILTER con los tres criterios")
    void test32_createProductFilter_AllCriteria_Success() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        Category cat = Category.builder().id(20L).name("Sneakers").slug("sneakers").build();
        Brand brand = Brand.builder().id(100L).name("Nike").slug("nike").isActive(true).build();

        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(cat));
        when(brandRepository.findById(100L)).thenReturn(Optional.of(brand));
        when(navItemRepository.existsDuplicateProductFilter(2L, 20L, 100L, ProductGender.WOMEN)).thenReturn(false);
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> {
            NavItem it = inv.getArgument(0);
            it.setId(62L);
            return it;
        });

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Nike Sneakers Women")
                .categoryId(20L)
                .brandId(100L)
                .gender(ProductGender.WOMEN)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse response = navigationService.addItem(2L, request);
        assertNotNull(response);
        assertEquals(NavTargetType.PRODUCT_FILTER, response.getTargetType());
        assertEquals(20L, response.getCategoryId());
        assertEquals(100L, response.getBrandId());
        assertEquals(ProductGender.WOMEN, response.getGender());
    }

    @Test
    @DisplayName("33. Permitir PRODUCT_FILTER solamente con género")
    void test33_createProductFilter_GenderOnly_Success() {
        NavSection section = NavSection.builder().id(3L).code("streetwear").label("Streetwear").build();

        when(navSectionRepository.findById(3L)).thenReturn(Optional.of(section));
        when(navItemRepository.existsDuplicateProductFilter(3L, null, null, ProductGender.MEN)).thenReturn(false);
        when(navItemRepository.save(any(NavItem.class))).thenAnswer(inv -> {
            NavItem it = inv.getArgument(0);
            it.setId(63L);
            return it;
        });

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Colección Hombre")
                .gender(ProductGender.MEN)
                .columnIndex(0)
                .orderIndex(0)
                .isVisible(true)
                .build();

        NavItemResponse response = navigationService.addItem(3L, request);
        assertNotNull(response);
        assertEquals(NavTargetType.PRODUCT_FILTER, response.getTargetType());
        assertEquals(ProductGender.MEN, response.getGender());
        assertNull(response.getCategoryId());
        assertNull(response.getBrandId());
    }

    @Test
    @DisplayName("34. Rechazar PRODUCT_FILTER sin criterios")
    void test34_rejectProductFilter_WithoutCriteria() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Filtro Vacío")
                .columnIndex(0)
                .build();

        assertThrows(BusinessRuleException.class, () -> navigationService.addItem(2L, request));
    }

    @Test
    @DisplayName("35. Rechazar PRODUCT_FILTER con customUrl")
    void test35_rejectProductFilter_WithCustomUrl() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Filtro con URL")
                .gender(ProductGender.MEN)
                .columnIndex(0)
                .customUrl("https://theofficeba.com/filter")
                .build();

        assertThrows(BusinessRuleException.class, () -> navigationService.addItem(2L, request));
    }

    @Test
    @DisplayName("36. Rechazar PRODUCT_FILTER con referencias inexistentes")
    void test36_rejectProductFilter_NonExistentReferences() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Inexistente")
                .categoryId(999L)
                .columnIndex(0)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> navigationService.addItem(2L, request));
    }

    @Test
    @DisplayName("37. Rechazar duplicados de la misma combinación")
    void test37_rejectDuplicateProductFilter() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").build();
        when(navSectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(navItemRepository.existsDuplicateProductFilter(2L, null, null, ProductGender.MEN)).thenReturn(true);

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Duplicado")
                .gender(ProductGender.MEN)
                .columnIndex(0)
                .build();

        assertThrows(BusinessRuleException.class, () -> navigationService.addItem(2L, request));
    }

    @Test
    @DisplayName("38. Conservar las restricciones de Brands: rechazar PRODUCT_FILTER en sección 'brands'")
    void test38_rejectProductFilter_InBrandsSection() {
        NavSection brandsSection = NavSection.builder().id(1L).code("brands").label("Marcas").build();
        when(navSectionRepository.findById(1L)).thenReturn(Optional.of(brandsSection));

        NavItemRequest request = NavItemRequest.builder()
                .targetType(NavTargetType.PRODUCT_FILTER)
                .label("Filtro en Brands")
                .gender(ProductGender.MEN)
                .columnIndex(0)
                .build();

        assertThrows(BusinessRuleException.class, () -> navigationService.addItem(1L, request));
    }

    @Test
    @DisplayName("39 y 40. Navegación pública incluye PRODUCT_FILTER cuando hay productos y lo oculta cuando no hay")
    void test39_test40_publicNavigation_FilterByActiveProducts() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").isVisible(true).items(new ArrayList<>()).build();

        Category cat = Category.builder().id(20L).name("Sneakers").slug("sneakers").build();
        NavItem itemWithProducts = NavItem.builder()
                .id(1L)
                .section(section)
                .label("Con Productos")
                .targetType(NavTargetType.PRODUCT_FILTER)
                .category(cat)
                .gender(ProductGender.MEN)
                .isVisible(true)
                .columnIndex(0)
                .orderIndex(0)
                .build();

        NavItem itemWithoutProducts = NavItem.builder()
                .id(2L)
                .section(section)
                .label("Sin Productos")
                .targetType(NavTargetType.PRODUCT_FILTER)
                .category(cat)
                .gender(ProductGender.WOMEN)
                .isVisible(true)
                .columnIndex(0)
                .orderIndex(1)
                .build();

        section.getItems().add(itemWithProducts);
        section.getItems().add(itemWithoutProducts);

        when(navSectionRepository.findVisibleSectionsWithItems()).thenReturn(List.of(section));

        // Item 1 tiene productos activos coincidentes (MEN incluye MEN y UNISEX)
        when(productRepository.existsActiveProductsByCriteria(eq(20L), isNull(), eq(true), eq(List.of(ProductGender.MEN, ProductGender.UNISEX))))
                .thenReturn(true);
        // Item 2 NO tiene productos activos coincidentes
        when(productRepository.existsActiveProductsByCriteria(eq(20L), isNull(), eq(true), eq(List.of(ProductGender.WOMEN, ProductGender.UNISEX))))
                .thenReturn(false);

        List<PublicNavigationSectionResponse> publicNav = navigationService.getPublicNavigation();
        assertNotNull(publicNav);
        assertEquals(1, publicNav.size());
        assertEquals(1, publicNav.get(0).getColumns().size());
        assertEquals(1, publicNav.get(0).getColumns().get(0).getItems().size());
        assertEquals("Con Productos", publicNav.get(0).getColumns().get(0).getItems().get(0).getLabel());
    }

    @Test
    @DisplayName("41. GET /api/admin/navigation conserva toda la configuración, incluyendo elementos ocultos y sin productos")
    void test41_adminNavigation_PreservesAllItems() {
        NavSection section = NavSection.builder().id(2L).code("sneakers").label("Sneakers").isVisible(true).items(new ArrayList<>()).build();
        NavItem hiddenItem = NavItem.builder()
                .id(3L)
                .section(section)
                .label("Item Oculto")
                .targetType(NavTargetType.PRODUCT_FILTER)
                .gender(ProductGender.UNISEX)
                .isVisible(false)
                .columnIndex(0)
                .orderIndex(0)
                .build();
        section.getItems().add(hiddenItem);

        when(navSectionRepository.findAllSectionsWithItems()).thenReturn(List.of(section));

        List<NavSectionResponse> adminNav = navigationService.getAllAdminSections();
        assertEquals(1, adminNav.size());
        assertEquals(1, adminNav.get(0).getItems().size());
        assertEquals("Item Oculto", adminNav.get(0).getItems().get(0).getLabel());
        assertFalse(adminNav.get(0).getItems().get(0).getIsVisible());
    }
}

