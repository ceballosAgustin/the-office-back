package com.aceballos.the_office_back.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /** Mínimo de bytes de la clave HMAC-SHA256 (256 bits). */
    private static final int MIN_KEY_BYTES = 32;

    private SecretKey signingKey;

    /**
     * Construye la clave de firma una sola vez al iniciar. Mantiene la decodificación
     * original (Base64 y, si falla, UTF-8) para que los tokens ya emitidos sigan siendo válidos.
     * Falla de inmediato si la clave es demasiado corta.
     */
    @PostConstruct
    void initSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (IllegalArgumentException e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) es demasiado corta: se requieren al menos "
                    + MIN_KEY_BYTES + " bytes (256 bits) para HS256 y se obtuvieron " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return generateTokenFromUsername(userPrincipal.getUsername(), userPrincipal.getAuthorities());
    }

    public String generateTokenFromUsername(String username, Object authorities) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("roles", authorities)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (SecurityException e) {
            log.warn("Firma JWT inválida: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.debug("Token JWT malformado: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.debug("Token JWT expirado: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.debug("Token JWT no soportado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("La cadena de claims de JWT está vacía: {}", e.getMessage());
        }
        return false;
    }
}
