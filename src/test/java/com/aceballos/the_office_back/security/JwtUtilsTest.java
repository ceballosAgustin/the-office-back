package com.aceballos.the_office_back.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils newUtils(String secret) {
        JwtUtils utils = new JwtUtils();
        ReflectionTestUtils.setField(utils, "jwtSecret", secret);
        ReflectionTestUtils.setField(utils, "jwtExpirationMs", 60_000L);
        ReflectionTestUtils.invokeMethod(utils, "initSigningKey");
        return utils;
    }

    private static String base64Secret() {
        return Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    }

    @Test
    void shouldValidateTokenSignedWithBase64DecodedKey() {
        String secret = base64Secret();
        JwtUtils utils = newUtils(secret);

        // Token emitido "a mano" con la misma decodificación Base64 que usaba la versión anterior
        String legacyToken = Jwts.builder()
                .subject("admin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();

        assertTrue(utils.validateJwtToken(legacyToken));
        assertEquals("admin", utils.getUserNameFromJwtToken(legacyToken));
    }

    @Test
    void shouldGenerateAndValidateOwnToken() {
        JwtUtils utils = newUtils(base64Secret());

        String token = utils.generateTokenFromUsername("admin", List.of("ROLE_ADMIN"));

        assertTrue(utils.validateJwtToken(token));
        assertEquals("admin", utils.getUserNameFromJwtToken(token));
        assertFalse(utils.validateJwtToken("not-a-jwt"));
    }

    @Test
    void shouldFailFastWhenSecretIsShorterThan32Bytes() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> newUtils(
                Base64.getEncoder().encodeToString("short-secret".getBytes())));
        assertTrue(ex.getMessage().contains("32 bytes"));
    }
}
