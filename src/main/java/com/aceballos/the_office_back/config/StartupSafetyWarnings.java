package com.aceballos.the_office_back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Emite advertencias al iniciar cuando la configuración efectiva parece insegura
 * para producción (por ejemplo, si el host olvidó activar el perfil "prod").
 * Solo registra logs; no modifica el comportamiento de la aplicación.
 */
@Slf4j
@Component
public class StartupSafetyWarnings implements ApplicationRunner {

    private final Environment environment;

    public StartupSafetyWarnings(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        evaluate(environment).forEach(log::warn);
    }

    static List<String> evaluate(Environment env) {
        List<String> warnings = new ArrayList<>();
        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto", "");
        String dbUrl = env.getProperty("spring.datasource.url", "");
        boolean remoteDb = !dbUrl.isBlank() && !isLocalUrl(dbUrl);

        if (env.getActiveProfiles().length == 0) {
            warnings.add("No hay perfil de Spring activo (SPRING_PROFILES_ACTIVE). Si este es un entorno "
                    + "productivo, active el perfil 'prod': de lo contrario rigen los valores por defecto de "
                    + "desarrollo (ddl-auto=update, registro público habilitado).");
        }
        if ("update".equalsIgnoreCase(ddlAuto) && remoteDb) {
            warnings.add("spring.jpa.hibernate.ddl-auto=update con una base de datos que no es local. "
                    + "Hibernate podría modificar el esquema automáticamente; en producción use 'validate'.");
        }
        return warnings;
    }

    private static boolean isLocalUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("//localhost") || lower.contains("//127.0.0.1") || lower.contains("//[::1]");
    }
}
