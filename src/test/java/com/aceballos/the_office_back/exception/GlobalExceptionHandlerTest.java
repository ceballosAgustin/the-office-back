package com.aceballos.the_office_back.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    static class Body {
        @NotBlank(message = "El nombre es obligatorio")
        public String name;
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/param")
        public String param(@RequestParam("q") String q) {
            return q;
        }

        @PostMapping("/probe/part")
        public String part(@RequestPart("file") MultipartFile file) {
            return "ok";
        }

        @PostMapping(value = "/probe/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        public String json(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/probe/missing")
        public String missing() throws Exception {
            throw new NoResourceFoundException(HttpMethod.GET, "probe/missing");
        }

        @GetMapping("/probe/upload")
        public String upload() {
            throw new MaxUploadSizeExceededException(10);
        }

        @GetMapping("/probe/sort")
        public String sort() {
            throw new PropertyReferenceException("foo", TypeInformation.of(String.class), List.of());
        }

        @GetMapping("/probe/integrity")
        public String integrity() {
            throw new DataIntegrityViolationException("Duplicate entry 'x' for key 'uk_secret_index'");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void missingRequestParameter_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/probe/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void missingRequestPart_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/probe/part").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedMethod_ShouldReturn405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/probe/param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unsupportedMediaType_ShouldReturn415() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void noResourceFound_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("El recurso solicitado no existe"));
    }

    @Test
    void maxUploadSizeExceeded_ShouldReturn413() throws Exception {
        mockMvc.perform(get("/probe/upload"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void invalidSortProperty_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/probe/sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void dataIntegrityViolation_ShouldReturn409WithoutLeakingSql() throws Exception {
        mockMvc.perform(get("/probe/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("uk_secret_index"))));
    }

    @Test
    void validationErrors_ShouldKeepFieldErrorsShape() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error de validación en los campos enviados"))
                .andExpect(jsonPath("$.fieldErrors.name").value("El nombre es obligatorio"));
    }
}
