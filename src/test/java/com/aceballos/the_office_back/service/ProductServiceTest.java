package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ProductRequest;
import com.aceballos.the_office_back.dto.ProductResponse;
import com.aceballos.the_office_back.dto.VariantDTO;
import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.entity.Product;
import com.aceballos.the_office_back.entity.ProductImage;
import com.aceballos.the_office_back.entity.ProductVariant;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.InquiryRepository;
import com.aceballos.the_office_back.repository.OrderItemRepository;
import com.aceballos.the_office_back.repository.ProductRepository;
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
    private CategoryRepository categoryRepository;

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
                categoryRepository,
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
}

