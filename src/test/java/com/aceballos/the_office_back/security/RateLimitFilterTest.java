package com.aceballos.the_office_back.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final AtomicLong now = new AtomicLong(1_000_000L);

    private RateLimitFilter filter(boolean enabled, String ipHeader, int maxKeys) {
        return new RateLimitFilter(objectMapper, enabled, ipHeader, maxKeys, now::get, List.of(
                new RateLimitFilter.Rule("login", "/api/auth/login", 2, 60_000),
                new RateLimitFilter.Rule("inquiries", "/api/inquiries", 3, 600_000)));
    }

    private MockHttpServletResponse call(RateLimitFilter f, String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        f.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void shouldReturn429WithErrorResponseShapeAfterLimit() throws Exception {
        RateLimitFilter f = filter(true, "", 100);

        assertEquals(200, call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus());
        assertEquals(200, call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus());
        MockHttpServletResponse blocked = call(f, "POST", "/api/auth/login", "1.1.1.1");

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentType().startsWith("application/json"));
        assertNotNull(blocked.getHeader("Retry-After"));
        var json = objectMapper.readTree(blocked.getContentAsString());
        assertEquals(429, json.get("status").asInt());
        assertEquals("Too Many Requests", json.get("error").asText());
        assertTrue(json.get("message").asText().startsWith("Demasiadas solicitudes"));
        assertNotNull(json.get("timestamp"));
    }

    @Test
    void shouldTrackIpsAndRulesIndependently() throws Exception {
        RateLimitFilter f = filter(true, "", 100);

        call(f, "POST", "/api/auth/login", "1.1.1.1");
        call(f, "POST", "/api/auth/login", "1.1.1.1");
        assertEquals(429, call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus());
        assertEquals(200, call(f, "POST", "/api/auth/login", "2.2.2.2").getStatus());
        assertEquals(200, call(f, "POST", "/api/inquiries", "1.1.1.1").getStatus());
    }

    @Test
    void shouldAllowAgainAfterWindowExpires() throws Exception {
        RateLimitFilter f = filter(true, "", 100);

        call(f, "POST", "/api/auth/login", "1.1.1.1");
        call(f, "POST", "/api/auth/login", "1.1.1.1");
        assertEquals(429, call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus());

        now.addAndGet(60_000);
        assertEquals(200, call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus());
    }

    @Test
    void shouldIgnoreNonMatchingRequestsAndWhenDisabled() throws Exception {
        RateLimitFilter f = filter(true, "", 100);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, call(f, "GET", "/api/inquiries", "1.1.1.1").getStatus());
            assertEquals(200, call(f, "POST", "/api/other", "1.1.1.1").getStatus());
        }

        RateLimitFilter disabled = filter(false, "", 100);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, call(disabled, "POST", "/api/auth/login", "1.1.1.1").getStatus());
        }
    }

    @Test
    void shouldUseLastEntryOfConfiguredForwardedHeader() throws Exception {
        RateLimitFilter f = filter(true, "X-Forwarded-For", 100);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "spoofed-" + i + ", 9.9.9.9");
            MockHttpServletResponse response = new MockHttpServletResponse();
            f.doFilter(request, response, new MockFilterChain());
            assertEquals(i < 2 ? 200 : 429, response.getStatus());
        }
    }

    @Test
    void shouldBoundTrackedKeys() throws Exception {
        RateLimitFilter f = filter(true, "", 5);

        for (int i = 0; i < 50; i++) {
            call(f, "POST", "/api/auth/login", "10.0.0." + i);
            assertTrue(f.trackedKeys() <= 5, "El mapa no debe superar el máximo de claves");
        }
    }
}
