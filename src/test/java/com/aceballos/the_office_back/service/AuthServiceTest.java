package com.aceballos.the_office_back.service;

import com.aceballos.the_office_back.dto.RegisterRequest;
import com.aceballos.the_office_back.entity.Role;
import com.aceballos.the_office_back.entity.User;
import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.repository.UserRepository;
import com.aceballos.the_office_back.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Test
    void setupFirstAdmin_UnderConcurrency_ShouldCreateExactlyOneAdmin() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AtomicInteger admins = new AtomicInteger();
        List<User> saved = Collections.synchronizedList(new ArrayList<>());

        when(userRepository.countByRole(Role.ADMIN)).thenAnswer(inv -> (long) admins.get());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenAnswer(inv -> {
            Thread.sleep(50); // ensancha la ventana de carrera entre el chequeo y el guardado
            return "hash";
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            admins.incrementAndGet();
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        AuthService service = new AuthService(mock(AuthenticationManager.class), userRepository, passwordEncoder,
                mock(JwtUtils.class));
        ReflectionTestUtils.setField(service, "adminSetupToken", "secret-token");

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.setupFirstAdmin(new RegisterRequest("admin" + n, "password1"), "secret-token");
                    return true;
                } catch (BusinessRuleException ex) {
                    return false;
                }
            }));
        }
        start.countDown();

        int successes = 0;
        for (Future<Boolean> f : results) {
            if (f.get(10, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdownNow();

        assertEquals(1, successes);
        assertEquals(1, saved.size());
        assertEquals(Role.ADMIN, saved.get(0).getRole());
    }
}
