package com.aceballos.the_office_back.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartupSafetyWarningsTest {

    @Test
    void shouldWarnWhenNoProfileAndRemoteDbWithUpdate() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.jpa.hibernate.ddl-auto", "update")
                .withProperty("spring.datasource.url", "jdbc:mysql://db.example.com:3306/office");

        List<String> warnings = StartupSafetyWarnings.evaluate(env);

        assertEquals(2, warnings.size());
    }

    @Test
    void shouldNotWarnAboutDdlForLocalhostDb() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.jpa.hibernate.ddl-auto", "update")
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/office");

        List<String> warnings = StartupSafetyWarnings.evaluate(env);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("perfil"));
    }

    @Test
    void shouldNotWarnForProdProfileWithValidate() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.datasource.url", "jdbc:mysql://db.example.com:3306/office");
        env.setActiveProfiles("prod");

        assertTrue(StartupSafetyWarnings.evaluate(env).isEmpty());
    }
}
