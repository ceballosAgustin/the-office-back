package com.aceballos.the_office_back.controller;

import com.aceballos.the_office_back.dto.ChangePasswordRequest;
import com.aceballos.the_office_back.dto.JwtResponse;
import com.aceballos.the_office_back.dto.LoginRequest;
import com.aceballos.the_office_back.dto.RegisterRequest;
import com.aceballos.the_office_back.dto.RegisterResponse;
import com.aceballos.the_office_back.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        JwtResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Registro público. Siempre crea usuarios con ROLE_USER.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        RegisterResponse response = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * One-Time Setup: crea el primer administrador del sistema.
     * Una vez que ya existe al menos un ADMIN, este endpoint devuelve 400.
     */
    @PostMapping("/setup-admin")
    public ResponseEntity<RegisterResponse> setupFirstAdmin(@Valid @RequestBody RegisterRequest registerRequest) {
        RegisterResponse response = authService.setupFirstAdmin(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cambio de contraseña para el usuario autenticado (requiere JWT válido).
     * Extrae el username del contexto de seguridad para evitar suplantación.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        authService.changePassword(username, changePasswordRequest);
        return ResponseEntity.noContent().build();
    }
}


