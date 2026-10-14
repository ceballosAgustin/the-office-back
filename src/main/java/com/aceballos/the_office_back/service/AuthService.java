package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.ChangePasswordRequest;
import com.aceballos.the_office_back.dto.JwtResponse;
import com.aceballos.the_office_back.dto.LoginRequest;
import com.aceballos.the_office_back.dto.RegisterRequest;
import com.aceballos.the_office_back.dto.RegisterResponse;
import com.aceballos.the_office_back.entity.Role;
import com.aceballos.the_office_back.entity.User;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.exception.ResourceNotFoundException;
import com.aceballos.the_office_back.repository.UserRepository;
import com.aceballos.the_office_back.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * Autentica credenciales, establece el contexto de seguridad y genera el token JWT.
     */
    public JwtResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .orElse("USER");

        log.info("Usuario autenticado exitosamente: {}", loginRequest.getUsername());
        return new JwtResponse(jwt, loginRequest.getUsername(), role);
    }

    /**
     * Registro público: siempre asigna ROLE_USER, ignorando cualquier payload
     * que intente elevar privilegios.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException(
                    "El nombre de usuario '" + registerRequest.getUsername() + "' ya está en uso");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole(Role.USER); // Forzado: siempre USER en registro público

        userRepository.save(user);
        log.info("Nuevo usuario registrado: {} con rol: {}", user.getUsername(), user.getRole());

        return RegisterResponse.builder()
                .message("Usuario registrado exitosamente")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    /**
     * One-Time Setup: crea el primer administrador solo si no existe ninguno aún.
     * Una vez creado, este endpoint se vuelve inoperante.
     */
    @Transactional
    public RegisterResponse setupFirstAdmin(RegisterRequest registerRequest) {
        long adminCount = userRepository.countByRole(Role.ADMIN);
        if (adminCount > 0) {
            throw new BusinessRuleException(
                    "Ya existe un administrador configurado. Este endpoint está deshabilitado.");
        }

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException(
                    "El nombre de usuario '" + registerRequest.getUsername() + "' ya está en uso");
        }

        User admin = new User();
        admin.setUsername(registerRequest.getUsername());
        admin.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        admin.setRole(Role.ADMIN);

        userRepository.save(admin);
        log.info("Primer administrador creado exitosamente: {}", admin.getUsername());

        return RegisterResponse.builder()
                .message("Administrador creado exitosamente")
                .username(admin.getUsername())
                .role(admin.getRole().name())
                .build();
    }

    /**
     * Cambio de contraseña autenticado: verifica la contraseña actual contra
     * el hash en base de datos antes de actualizar.
     *
     * @param username        nombre de usuario del contexto de seguridad (del JWT).
     * @param passwordRequest DTO con currentPassword y newPassword.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest passwordRequest) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + username));

        if (!passwordEncoder.matches(passwordRequest.getCurrentPassword(), user.getPassword())) {
            throw new BusinessRuleException("La contraseña actual no es correcta");
        }

        user.setPassword(passwordEncoder.encode(passwordRequest.getNewPassword()));
        userRepository.save(user);
        log.info("Contraseña actualizada exitosamente para el usuario: {}", username);
    }
}

