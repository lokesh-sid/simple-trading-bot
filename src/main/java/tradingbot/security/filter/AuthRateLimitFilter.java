package tradingbot.security.filter;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP rate limiter for authentication endpoints.
 *
 * <p>Uses Resilience4j's {@link RateLimiterRegistry} to create one
 * {@link RateLimiter} per client IP (keyed as {@code "auth-ip-<ip>"}).
 * All per-IP instances share the {@code "auth-endpoints"} configuration
 * block defined in {@code application.properties}.
 *
 * <p>Returns HTTP 429 with an RFC 6749 error body when the limit is
 * exceeded.  Requests that do not target the protected paths pass
 * through without any rate-limiting overhead.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String CONFIG_NAME = "auth-endpoints";
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    );

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RateLimiterConfig perIpConfig;

    public AuthRateLimitFilter(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        // Pull the named config from the registry (populated by Spring Boot
        // autoconfiguration from the auth-endpoints.* properties block).
        // Fall back to the registry's default config if not defined.
        this.perIpConfig = rateLimiterRegistry.getConfiguration(CONFIG_NAME)
                .orElseGet(rateLimiterRegistry::getDefaultConfig);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }

        if (!PROTECTED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("auth-ip-" + clientIp, perIpConfig);

        if (limiter.acquirePermission()) {
            filterChain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded for IP {} on {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"too_many_requests\","
                    + "\"error_description\":\"Too many requests — please slow down and try again later.\"}");
        }
    }

    /**
     * Extracts the real client IP, honouring {@code X-Forwarded-For} when
     * the application sits behind a reverse proxy.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated chain; the first
            // entry is the originating client IP.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
