package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloudinaryServiceTest {

    @Test
    void upload_WithNonImageContentType_ShouldRejectBeforeCallingCloudinary() {
        Cloudinary cloudinary = mock(Cloudinary.class);
        CloudinaryService service = new CloudinaryService(cloudinary);

        for (String type : new String[] { "application/pdf", "text/html", "image/svg+xml", null }) {
            MockMultipartFile file = new MockMultipartFile("files", "x.bin", type, new byte[] { 1, 2, 3 });
            assertThrows(BusinessRuleException.class, () -> service.uploadImageWithDetails(file));
        }
        verifyNoInteractions(cloudinary);
    }

    @Test
    @SuppressWarnings("unchecked")
    void upload_WithAllowedContentType_ShouldUseImageResourceType() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any(Map.class))).thenReturn(
                Map.of("secure_url", "https://res.cloudinary.com/demo/a.jpg", "public_id", "the-office/products/a"));
        CloudinaryService service = new CloudinaryService(cloudinary);

        MockMultipartFile file = new MockMultipartFile("files", "a.jpg", "IMAGE/JPEG; charset=binary", new byte[] { 1 });
        CloudinaryService.CloudinaryUploadResult result = service.uploadImageWithDetails(file);

        assertEquals("the-office/products/a", result.publicId());
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(), options.capture());
        assertEquals("image", options.getValue().get("resource_type"));
    }
}
