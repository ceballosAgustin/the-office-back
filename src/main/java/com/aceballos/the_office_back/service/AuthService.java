package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.JwtResponse;
import com.aceballos.the_office_back.dto.LoginRequest;
import com.aceballos.the_office_back.dto.RegisterRequest;
import com.aceballos.the_office_back.dto.RegisterResponse;
import com.aceballos.the_office_back.entity.Role;
import com.aceballos.the_office_back.entity.User;
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
     * Registra un nuevo usuario verificando unicidad y encriptando contraseña.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("El nombre de usuario '" + registerRequest.getUsername() + "' ya está en uso");
        }

        Role role = Role.USER;
        if (Boolean.TRUE.equals(registerRequest.getAdmin()) || registerRequest.getUsername().toLowerCase().contains("admin")) {
            role = Role.ADMIN;
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole(role);

        userRepository.save(user);
        log.info("Nuevo usuario registrado: {} con rol: {}", user.getUsername(), user.getRole());

        return RegisterResponse.builder()
                .message("Usuario registrado exitosamente")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}
