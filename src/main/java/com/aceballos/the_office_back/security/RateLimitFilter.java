package com.aceballos.the_office_back.security;

import com.aceballos.the_office_back.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limitador de tasa en memoria por IP (ventana fija) para los endpoints públicos
 * sensibles: login, setup-admin y creación de consultas.
 * <p>
 * Es un contador por instancia (no distribuido). El mapa de contadores está acotado:
 * al alcanzar el máximo de claves se descartan primero las ventanas vencidas y, si
 * aun así sigue lleno, se reinicia por completo.
 * <p>
 * La IP del cliente se toma de {@code request.getRemoteAddr()}, salvo que se configure
 * {@code app.rate-limit.client-ip-header} (ej. X-Forwarded-For); en ese caso se usa la
 * ÚLTIMA entrada del encabezado (la agregada por el proxy de confianza más cercano) y,
 * si el encabezado no está presente, se vuelve a {@code getRemoteAddr()}.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Regla de limitación: máximo de peticiones por ventana de tiempo. */
    record Rule(String name, String path, int maxRequests, long windowMillis) {
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String clientIpHeader;
    private final int maxTrackedKeys;
    private final List<Rule> rules;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.client-ip-header:}") String clientIpHeader,
            @Value("${app.rate-limit.max-tracked-keys:10000}") int maxTrackedKeys,
            @Value("${app.rate-limit.login.max-requests:10}") int loginMax,
            @Value("${app.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${app.rate-limit.setup-admin.max-requests:5}") int setupMax,
            @Value("${app.rate-limit.setup-admin.window-seconds:60}") long setupWindowSeconds,
            @Value("${app.rate-limit.inquiries.max-requests:20}") int inquiriesMax,
            @Value("${app.rate-limit.inquiries.window-seconds:600}") long inquiriesWindowSeconds) {
        this(objectMapper, enabled, clientIpHeader, maxTrackedKeys, System::currentTimeMillis, List.of(
                new Rule("login", "/api/auth/login", loginMax, loginWindowSeconds * 1000),
                new Rule("setup-admin", "/api/auth/setup-admin", setupMax, setupWindowSeconds * 1000),
                new Rule("inquiries", "/api/inquiries", inquiriesMax, inquiriesWindowSeconds * 1000)));
    }

    RateLimitFilter(ObjectMapper objectMapper, boolean enabled, String clientIpHeader, int maxTrackedKeys,
            LongSupplier clock, List<Rule> rules) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.clientIpHeader = clientIpHeader;
        this.maxTrackedKeys = Math.max(1, maxTrackedKeys);
        this.clock = clock;
        this.rules = rules;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !enabled || !HttpMethod.POST.matches(request.getMethod()) || matchRule(request) == null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        Rule rule = matchRule(request);
        String ip = resolveClientIp(request);
        long retryAfterMillis = rule == null ? 0 : consume(rule, ip);

        if (retryAfterMillis > 0) {
            log.warn("Límite de peticiones excedido para '{}' desde {}", rule.name(), ip);
            writeTooManyRequests(response, retryAfterMillis);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Rule matchRule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = StringUtils.hasText(contextPath) && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
        for (Rule rule : rules) {
            if (rule.path().equals(path)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Registra una petición. Devuelve 0 si está permitida, o los milisegundos
     * restantes hasta el fin de la ventana si se excedió el límite.
     */
    private long consume(Rule rule, String ip) {
        long now = clock.getAsLong();
        if (windows.size() >= maxTrackedKeys) {
            evict(now);
        }
        String key = rule.name() + '|' + ip;
        long[] retryAfter = { 0 };
        windows.compute(key, (k, w) -> {
            Window current = w;
            if (current == null || now - current.start >= rule.windowMillis()) {
                current = new Window(now);
            }
            if (current.count >= rule.maxRequests()) {
                retryAfter[0] = Math.max(1, rule.windowMillis() - (now - current.start));
            } else {
                current.count++;
            }
            return current;
        });
        return retryAfter[0];
    }

    private void evict(long now) {
        long maxWindow = rules.stream().mapToLong(Rule::windowMillis).max().orElse(0);
        windows.entrySet().removeIf(e -> now - e.getValue().start >= maxWindow);
        if (windows.size() >= maxTrackedKeys) {
            windows.clear();
        }
    }

    int trackedKeys() {
        return windows.size();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (StringUtils.hasText(clientIpHeader)) {
            String header = request.getHeader(clientIpHeader);
            if (StringUtils.hasText(header)) {
                String[] parts = header.split(",");
                String last = parts[parts.length - 1].trim();
                if (!last.isEmpty()) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterMillis) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Demasiadas solicitudes. Por favor, intenta de nuevo en unos minutos.")
                .build();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf((retryAfterMillis + 999) / 1000));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
