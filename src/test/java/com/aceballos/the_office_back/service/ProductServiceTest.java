package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.FilterOptionsResponse;
import com.aceballos.the_office_back.dto.ProductRequest;
import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.VariantDTO;
import com.aceballos.the_office_back.entity.Brand;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductGender;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.BrandRepository;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.InquiryRepository;
import com.aceballos.the_office_back.repository.OrderItemRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
import com.aceballos.the_office_back.repository.ProductVariantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private InquiryRepository inquiryRepository;

    private ProductService productService;
    private Product existingProduct;
    private ProductVariant variantM;
    private ProductVariant variantL;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ObjectMapper objectMapper = new ObjectMapper();

        productService = new ProductService(
                productRepository,
                productVariantRepository,
                categoryRepository,
                brandRepository,
                cloudinaryService,
                objectMapper,
                validator,
                orderItemRepository,
                inquiryRepository
        );

        existingProduct = new Product();
        existingProduct.setId(10L);
        existingProduct.setTitle("Remera Oversize Dunder Mifflin");
        existingProduct.setDescription("Algodón 24/1");
        existingProduct.setPrice(new BigDecimal("28500.00"));
        existingProduct.setIsActive(true);

        variantM = new ProductVariant(101L, "M", 5, true, existingProduct);
        variantL = new ProductVariant(102L, "L", 10, true, existingProduct);
        existingProduct.setVariants(new ArrayList<>(List.of(variantM, variantL)));
        existingProduct.setBaseStock(15);

        ProductImage img1 = ProductImage.builder()
                .id(1L)
                .imageUrl("https://res.cloudinary.com/demo/image/upload/img1.jpg")
                .publicId("the-office/products/img1")
                .isPrimary(true)
                .orderIndex(0)
                .product(existingProduct)
                .build();
        ProductImage img2 = ProductImage.builder()
                .id(2L)
                .imageUrl("https://res.cloudinary.com/demo/image/upload/img2.jpg")
                .publicId("the-office/products/img2")
                .isPrimary(false)
                .orderIndex(1)
                .product(existingProduct)
                .build();
        existingProduct.setImages(new ArrayList<>(List.of(img1, img2)));
    }

    @Test
    void case1_createProduct_WithMultipleVariants_ShouldCalculateBaseStockFromActiveVariants() {
        Category cat = new Category(1L, "Remeras", "remeras");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(20L);
            long vId = 201L;
            for (ProductVariant v : p.getVariants()) {
                v.setId(vId++);
            }
            return p;
        });

        ProductRequest request = ProductRequest.builder()
                .title("Buzo The Office")
                .description("Frisa invisible")
                .price(new BigDecimal("45000.00"))
                .baseStock(999) // No autoritativo: debe ignorarse y calcularse como 4 + 6 = 10
                .categoryId(1L)
                .variants(List.of(
                        new VariantDTO(null, "M", 4, true),
                        new VariantDTO(null, "L", 6, true)
                ))
                .build();

        ProductResponse response = productService.createProduct(request, null);

        assertEquals(20L, response.getId());
        assertEquals(10, response.getBaseStock());
        assertEquals(2, response.getVariants().size());
        assertEquals(201L, response.getVariants().get(0).getId());
        assertEquals(202L, response.getVariants().get(1).getId());
    }

    @Test
    void case2_updateProduct_EditOnlyTitle_ShouldPreserveVariantIds() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = ProductRequest.builder()
                .title("Remera Oversize Dunder Mifflin Edición Limitada")
                .description("Algodón 24/1")
                .price(new BigDecimal("28500.00"))
                .variants(List.of(
                        new VariantDTO(101L, "M", 5, true),
                        new VariantDTO(102L, "L", 10, true)
                ))
                .build();

        ProductResponse response = productService.updateProduct(10L, request, null);

        assertEquals("Remera Oversize Dunder Mifflin Edición Limitada", response.getTitle());
        assertEquals(2, response.getVariants().size());
        assertEquals(101L, response.getVariants().get(0).getId());
        assertEquals(102L, response.getVariants().get(1).getId());
        assertEquals(15, response.getBaseStock());
    }

    @Test
    void case3_updateProduct_ModifyStock_ShouldPreserveOriginalIdsAndRecalculateBaseStock() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = ProductRequest.builder()
                .title(existingProduct.getTitle())
                .price(existingProduct.getPrice())
                .variants(List.of(
                        new VariantDTO(101L, "M", 12, true),
                        new VariantDTO(102L, "L", 8, true)
                ))
                .build();

        ProductResponse response = productService.updateProduct(10L, request, null);

        assertEquals(101L, response.getVariants().get(0).getId());
        assertEquals(12, response.getVariants().get(0).getStock());
        assertEquals(102L, response.getVariants().get(1).getId());
        assertEquals(8, response.getVariants().get(1).getStock());
        assertEquals(20, response.getBaseStock());
    }

    @Test
    void case4_updateProduct_AddNewVariant_ShouldKeepExistingIdsAndAddNewVariant() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            for (ProductVariant v : p.getVariants()) {
                if (v.getId() == null) {
                    v.setId(103L);
                }
            }
            return p;
        });

        ProductRequest request = ProductRequest.builder()
                .title(existingProduct.getTitle())
                .price(existingProduct.getPrice())
                .variants(List.of(
                        new VariantDTO(101L, "M", 5, true),
                        new VariantDTO(102L, "L", 10, true),
                        new VariantDTO(null, "XL", 7, true)
                ))
                .build();

        ProductResponse response = productService.updateProduct(10L, request, null);

        assertEquals(3, response.getVariants().size());
        assertEquals(101L, response.getVariants().get(0).getId());
        assertEquals(102L, response.getVariants().get(1).getId());
        assertEquals(103L, response.getVariants().get(2).getId());
        assertEquals("XL", response.getVariants().get(2).getSize());
        assertEquals(22, response.getBaseStock());
    }

    @Test
    void case5_updateProduct_OmitExistingVariant_ShouldDeactivateWithoutDeletingAndExcludeFromStock() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Se envía únicamente la variante 101 (M); la 102 (L) se omite
        ProductRequest request = ProductRequest.builder()
                .title(existingProduct.getTitle())
                .price(existingProduct.getPrice())
                .variants(List.of(
                        new VariantDTO(101L, "M", 5, true)
                ))
                .build();

        ProductResponse adminResponse = productService.updateProduct(10L, request, null);

        // El registro 102 sigue existiendo con su ID original pero inactivo, y no suma a baseStock
        assertEquals(2, existingProduct.getVariants().size());
        assertTrue(variantM.getIsActive());
        assertFalse(variantL.getIsActive());
        assertEquals(102L, variantL.getId());
        assertEquals(5, adminResponse.getBaseStock());

        // En la vista pública solo se expone la variante activa (101)
        ProductResponse publicResponse = productService.mapToPublicResponse(existingProduct);
        assertEquals(1, publicResponse.getVariants().size());
        assertEquals(101L, publicResponse.getVariants().get(0).getId());
    }

    @Test
    void case6_updateProduct_ChangeSizeOfVariantWithHistoricalReferences_ShouldThrowBusinessRuleException() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(orderItemRepository.existsByProductVariantId(101L)).thenReturn(true);

        ProductRequest request = ProductRequest.builder()
                .title(existingProduct.getTitle())
                .price(existingProduct.getPrice())
                .variants(List.of(
                        new VariantDTO(101L, "S", 5, true), // Intenta renombrar M -> S teniendo órdenes históricas
                        new VariantDTO(102L, "L", 10, true)
                ))
                .build();

        assertThrows(BusinessRuleException.class, () -> productService.updateProduct(10L, request, null));
        assertEquals("M", variantM.getSize());
    }

    @Test
    void case7_updateProduct_WithHistoricalSales_ShouldSucceedWhenUpdatingPriceTitleAndStock() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Variante 101 fue vendida en órdenes anteriores; se actualiza su stock y el precio del producto, y se desactiva 102
        ProductRequest request = ProductRequest.builder()
                .title("Remera Oversize Actualizada")
                .price(new BigDecimal("32000.00"))
                .variants(List.of(
                        new VariantDTO(101L, "M", 20, true)
                ))
                .build();

        ProductResponse response = productService.updateProduct(10L, request, null);

        assertEquals("Remera Oversize Actualizada", response.getTitle());
        assertEquals(new BigDecimal("32000.00"), response.getPrice());
        assertEquals(20, response.getBaseStock());
        assertEquals(101L, variantM.getId());
        assertEquals(102L, variantL.getId());
        assertFalse(variantL.getIsActive());
    }

    @Test
    void case8_updateProduct_WithNewPhotos_ShouldAppendToExistingGalleryAndPreservePrimary() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(cloudinaryService.uploadImageWithDetails(any())).thenReturn(
                new CloudinaryService.CloudinaryUploadResult(
                        "https://res.cloudinary.com/demo/image/upload/img3.jpg",
                        "the-office/products/img3"
                )
        );
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            for (ProductImage img : p.getImages()) {
                if (img.getId() == null) {
                    img.setId(3L);
                }
            }
            return p;
        });

        MockMultipartFile newFile = new MockMultipartFile("files", "img3.jpg", "image/jpeg", new byte[]{1, 2, 3});
        ProductRequest request = ProductRequest.builder()
                .title(existingProduct.getTitle())
                .price(existingProduct.getPrice())
                .build();

        ProductResponse response = productService.updateProduct(10L, request, List.of(newFile));

        assertEquals(3, response.getImages().size());
        assertTrue(response.getImages().get(0).getIsPrimary()); // La imagen 1 sigue siendo la portada
        assertFalse(response.getImages().get(2).getIsPrimary());
        assertEquals("the-office/products/img3", response.getImages().get(2).getPublicId());
        assertEquals(2, response.getImages().get(2).getOrderIndex());
    }

    @Test
    void case9_deleteAndChangePrimaryImage_ShouldUpdateGalleryAndDeleteFromCloudinary() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Cambiar portada a la imagen 2
        ProductResponse afterPrimaryChange = productService.setPrimaryProductImage(10L, 2L);
        assertFalse(afterPrimaryChange.getImages().get(0).getIsPrimary());
        assertTrue(afterPrimaryChange.getImages().get(1).getIsPrimary());

        // Eliminar la imagen 2 (que ahora es la principal); la imagen 1 debe quedar como nueva principal
        ProductResponse afterDelete = productService.deleteProductImage(10L, 2L);
        assertEquals(1, afterDelete.getImages().size());
        assertEquals(1L, afterDelete.getImages().get(0).getId());
        assertTrue(afterDelete.getImages().get(0).getIsPrimary());
        verify(cloudinaryService, times(1)).deleteImage("the-office/products/img2");
    }

    @Test
    void case10_createProduct_WithInvalidMultipartJsonOrInvalidFields_ShouldThrowValidationException() {
        // JSON malformado
        assertThrows(IllegalArgumentException.class,
                () -> productService.createProduct("{ invalid json ", null));

        // JSON válido pero con campos inválidos (título vacío y precio negativo)
        String invalidFieldsJson = """
                {
                  "title": "",
                  "price": -100,
                  "variants": [
                    { "size": "", "stock": -2 }
                  ]
                }
                """;
        assertThrows(ConstraintViolationException.class,
                () -> productService.createProduct(invalidFieldsJson, null));
    }

    @Test
    void case11And12_adminListingAndReactivation_ShouldListInactiveAndAllowReactivating() {
        existingProduct.setIsActive(false);
        when(productRepository.findAdminProducts(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // El detalle público rechaza el producto inactivo
        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(10L));

        // El listado admin y detalle admin sí lo devuelven
        Page<ProductResponse> adminPage = productService.getAdminProducts(false, null, null, PageRequest.of(0, 12));
        assertEquals(1, adminPage.getTotalElements());
        assertFalse(adminPage.getContent().get(0).getIsActive());

        // Reactivar el producto
        ProductResponse reactivated = productService.updateProductStatus(10L, true);
        assertTrue(reactivated.getIsActive());
    }

    @Test
    void productBrand_CreateWithBrand_ShouldAssignBrandWhenActive() {
        Brand nike = new Brand(1L, "Nike", "nike", true);
        when(brandRepository.findById(1L)).thenReturn(Optional.of(nike));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(30L);
            return p;
        });

        ProductRequest request = ProductRequest.builder()
                .title("Remera Nike Air")
                .price(new BigDecimal("35000.00"))
                .brandId(1L)
                .variants(List.of(new VariantDTO(null, "M", 5, true)))
                .build();

        ProductResponse response = productService.createProduct(request, null);

        assertNotNull(response);
        assertEquals(30L, response.getId());
        assertEquals(1L, response.getBrandId());
        assertEquals("Nike", response.getBrandName());
        assertEquals("nike", response.getBrandSlug());
    }

    @Test
    void productBrand_CreateWithoutBrand_ShouldLeaveBrandAsNull() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(31L);
            return p;
        });

        ProductRequest request = ProductRequest.builder()
                .title("Remera Genérica Sin Marca")
                .price(new BigDecimal("20000.00"))
                .brandId(null)
                .variants(List.of(new VariantDTO(null, "M", 5, true)))
                .build();

        ProductResponse response = productService.createProduct(request, null);

        assertNotNull(response);
        assertNull(response.getBrandId());
        assertNull(response.getBrandName());
        assertNull(response.getBrandSlug());
        verifyNoInteractions(brandRepository);
    }

    @Test
    void productBrand_WhenBrandNotFoundOrInactive_ShouldReject() {
        // 1. Marca inexistente -> 404
        when(brandRepository.findById(999L)).thenReturn(Optional.empty());
        ProductRequest notFoundReq = ProductRequest.builder()
                .title("Remera Test")
                .price(new BigDecimal("25000.00"))
                .brandId(999L)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> productService.createProduct(notFoundReq, null));

        // 2. Marca inactiva -> BusinessRuleException
        Brand inactiveBrand = new Brand(2L, "Reebok", "reebok", false);
        when(brandRepository.findById(2L)).thenReturn(Optional.of(inactiveBrand));
        ProductRequest inactiveReq = ProductRequest.builder()
                .title("Remera Reebok Inactiva")
                .price(new BigDecimal("25000.00"))
                .brandId(2L)
                .build();

        assertThrows(BusinessRuleException.class, () -> productService.createProduct(inactiveReq, null));
        verify(productRepository, never()).save(any());
    }

    @Test
    void productBrand_UpdateBrandAndRemoveBrand_ShouldPreserveOrClearBrand() {
        Brand adidas = new Brand(3L, "Adidas", "adidas", true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(brandRepository.findById(3L)).thenReturn(Optional.of(adidas));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Asignar marca Adidas a existingProduct
        ProductRequest assignRequest = ProductRequest.builder()
                .title(existingProduct.getTitle())
                .price(existingProduct.getPrice())
                .brandId(3L)
                .variants(List.of(new VariantDTO(101L, "M", 5, true), new VariantDTO(102L, "L", 10, true)))
                .build();

        ProductResponse assigned = productService.updateProduct(10L, assignRequest, null);
        assertEquals(3L, assigned.getBrandId());
        assertEquals("Adidas", assigned.getBrandName());
        assertEquals("adidas", assigned.getBrandSlug());
        assertNotNull(existingProduct.getBrand());

        // 2. Quitar marca (enviar brandId = null) -> debe quedar sin marca
        ProductRequest removeRequest = ProductRequest.builder()
                .title(existingProduct.getTitle())
                .price(existingProduct.getPrice())
                .brandId(null)
                .variants(List.of(new VariantDTO(101L, "M", 5, true), new VariantDTO(102L, "L", 10, true)))
                .build();

        ProductResponse removed = productService.updateProduct(10L, removeRequest, null);
        assertNull(removed.getBrandId());
        assertNull(removed.getBrandName());
        assertNull(removed.getBrandSlug());
        assertNull(existingProduct.getBrand());
    }

    @Test
    void publicFiltering_ByBrandSlugAndCombiningWithCategoryAndSearch_ShouldDelegateToRepository() {
        existingProduct.setBrand(new Brand(1L, "Nike", "nike", true));
        when(productRepository.findActiveProducts(eq(1L), eq("nike"), eq("remera"), eq(false), anyList(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> result = productService.searchActiveProducts(
                "remera", 1L, "nike", PageRequest.of(0, 12));

        assertEquals(1, result.getTotalElements());
        assertEquals(10L, result.getContent().get(0).getId());
        assertEquals("nike", result.getContent().get(0).getBrandSlug());

        // Verificación con mayúsculas/espacios que se normalizan
        when(productRepository.findActiveProducts(isNull(), eq("nike"), isNull(), eq(false), anyList(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> brandOnly = productService.getActiveProducts(null, " NIKE ", PageRequest.of(0, 12));
        assertEquals(1, brandOnly.getTotalElements());
    }

    // =========================================================================
    // PRUEBAS DE GÉNERO, FILTROS Y OPCIONES DINÁMICAS (Escenarios 1 a 29)
    // =========================================================================

    @Test
    void test01_createProduct_WithoutGender_ShouldDefaultToUnisex() {
        ProductRequest request = ProductRequest.builder()
                .title("Remera Básica")
                .description("Descripción")
                .price(new BigDecimal("100.00"))
                .categoryId(1L)
                .variants(List.of(VariantDTO.builder().size("M").stock(5).build()))
                .build();

        Category cat = new Category(1L, "Remeras", "remeras");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(20L);
            return p;
        });

        ProductResponse response = productService.createProduct(request, null);
        assertNotNull(response);
        assertEquals(ProductGender.UNISEX, response.getGender());
    }

    @Test
    void test02_createProduct_WithMenGender_ShouldSaveAsMen() {
        ProductRequest request = ProductRequest.builder()
                .title("Remera Men")
                .description("Descripción")
                .price(new BigDecimal("100.00"))
                .categoryId(1L)
                .gender(ProductGender.MEN)
                .variants(List.of(VariantDTO.builder().size("M").stock(5).build()))
                .build();

        Category cat = new Category(1L, "Remeras", "remeras");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(21L);
            return p;
        });

        ProductResponse response = productService.createProduct(request, null);
        assertNotNull(response);
        assertEquals(ProductGender.MEN, response.getGender());
    }

    @Test
    void test03_updateProduct_WithoutGender_ShouldPreserveExistingGender() {
        existingProduct.setGender(ProductGender.MEN);
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest updateReq = ProductRequest.builder()
                .title("Remera Actualizada")
                .price(new BigDecimal("200.00"))
                .variants(List.of(VariantDTO.builder().size("M").stock(5).build()))
                .build(); // gender es null

        ProductResponse response = productService.updateProduct(10L, updateReq, null);
        assertEquals(ProductGender.MEN, response.getGender());
        assertEquals(ProductGender.MEN, existingProduct.getGender());
    }

    @Test
    void test04_updateProduct_WithWomenGender_ShouldUpdateToWomen() {
        existingProduct.setGender(ProductGender.MEN);
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest updateReq = ProductRequest.builder()
                .title("Remera Actualizada")
                .price(new BigDecimal("200.00"))
                .gender(ProductGender.WOMEN)
                .variants(List.of(VariantDTO.builder().size("M").stock(5).build()))
                .build();

        ProductResponse response = productService.updateProduct(10L, updateReq, null);
        assertEquals(ProductGender.WOMEN, response.getGender());
        assertEquals(ProductGender.WOMEN, existingProduct.getGender());
    }

    @Test
    void test07_filterMen_ShouldIncludeMenAndUnisex() {
        when(productRepository.findActiveProducts(isNull(), isNull(), isNull(), eq(true), eq(List.of(ProductGender.MEN, ProductGender.UNISEX)), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> page = productService.getActiveProducts(null, null, null, "MEN", null, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());

        // Verificamos case-insensitivity: "men" minúscula
        Page<ProductResponse> pageLower = productService.getActiveProducts(null, null, null, "men", null, PageRequest.of(0, 10));
        assertEquals(1, pageLower.getTotalElements());
    }

    @Test
    void test08_filterWomen_ShouldIncludeWomenAndUnisex() {
        when(productRepository.findActiveProducts(isNull(), isNull(), isNull(), eq(true), eq(List.of(ProductGender.WOMEN, ProductGender.UNISEX)), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> page = productService.getActiveProducts(null, null, null, "WOMEN", null, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void test09_filterUnisex_ShouldIncludeOnlyUnisex() {
        when(productRepository.findActiveProducts(isNull(), isNull(), isNull(), eq(true), eq(List.of(ProductGender.UNISEX)), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> page = productService.getActiveProducts(null, null, null, "UNISEX", null, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void testFilterInvalidGender_ShouldThrowBusinessRuleException() {
        assertThrows(BusinessRuleException.class, () ->
                productService.getActiveProducts(null, null, null, "KIDS", null, PageRequest.of(0, 10))
        );
    }

    @Test
    void test10_test11_test12_combineCategoryBrandGenderAndSearch() {
        Category cat = new Category(1L, "Sneakers", "sneakers");
        when(categoryRepository.findBySlug("sneakers")).thenReturn(Optional.of(cat));
        when(productRepository.findActiveProducts(eq(1L), eq("nike"), eq("dunk"), eq(true), eq(List.of(ProductGender.MEN, ProductGender.UNISEX)), eq("42"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> result = productService.searchActiveProducts(
                "dunk", null, "sneakers", "nike", "MEN", "42", PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void test13_filterByCategorySlug_ShouldResolveCategoryId() {
        Category cat = new Category(2L, "Remeras", "remeras");
        when(categoryRepository.findBySlug("remeras")).thenReturn(Optional.of(cat));
        when(productRepository.findActiveProducts(eq(2L), isNull(), isNull(), eq(false), anyList(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> result = productService.getActiveProducts(null, "remeras", null, null, null, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void test14_maintainCompatibilityWithCategoryId() {
        when(productRepository.findActiveProducts(eq(2L), isNull(), isNull(), eq(false), anyList(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct)));

        Page<ProductResponse> result = productService.getActiveProducts(2L, null, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void test15_categoryAndCategoryIdMismatch_ShouldThrowBusinessRuleException() {
        Category cat = new Category(1L, "Calzado", "calzado");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        // categoryId=1 (slug=calzado) vs category=remeras (mismatch)
        assertThrows(BusinessRuleException.class, () ->
                productService.getActiveProducts(1L, "remeras", null, null, null, PageRequest.of(0, 10))
        );
    }

    @Test
    void testCategorySlugNotFound_ShouldReturnEmptyPageWithoutError() {
        when(categoryRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

        Page<ProductResponse> result = productService.getActiveProducts(null, "inexistente", null, null, null, PageRequest.of(0, 10));
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(productRepository, never()).findActiveProducts(any(), any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void test16_to_23_variantSizeFilter_NormalizedAndPassedToRepository() {
        when(productRepository.findActiveProducts(isNull(), isNull(), isNull(), eq(false), anyList(), eq("42"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct), PageRequest.of(0, 10), 1));

        Page<ProductResponse> result = productService.getActiveProducts(null, null, null, null, " 42 ", PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void test24_to_29_getFilterOptions_ReturnsSizesSortedDeterministically() {
        Category cat = new Category(1L, "Sneakers", "sneakers");
        when(categoryRepository.findBySlug("sneakers")).thenReturn(Optional.of(cat));

        when(productVariantRepository.findAvailableSizesByFilters(
                eq(1L), eq("nike"), eq("dunk"), eq(true), eq(List.of(ProductGender.MEN, ProductGender.UNISEX))
        )).thenReturn(List.of("42", "40", "XL", "S", "M", "39.5", "41", "XS", "38"));

        FilterOptionsResponse response = productService.getFilterOptions(null, "sneakers", "nike", "MEN", "dunk");
        assertNotNull(response);
        // Debe ordenar determinísticamente: numéricos 38, 39.5, 40, 41, 42 luego ropa XS, S, M, XL
        List<String> expected = List.of("38", "39.5", "40", "41", "42", "XS", "S", "M", "XL");
        assertEquals(expected, response.getAvailableSizes());
    }
}

