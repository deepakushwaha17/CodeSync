package com.codesync.gateway.filter;

import com.codesync.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    /**
     * Public routes that do NOT require JWT authentication.
     * These are open to guests and unauthenticated users.
     */
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/validate",
            "/api/v1/auth/oauth2",        // covers /authorize and /callback
            "/oauth2",                     // Spring Security internal
            "/login/oauth2",
            "/api/v1/projects/public",          // browse public projects
            "/api/v1/projects/search",           // search projects (guest allowed)
            "/api/v1/executions/languages",      // view supported languages
            "/actuator"                          // health checks
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        log.debug("Incoming request: {} {}", method, path);

        // ── Allow all OPTIONS preflight requests (CORS) ──────────────
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // ── Allow public routes through without token ─────────────────
        if (isPublicRoute(path)) {
            log.debug("Public route accessed: {}", path);
            return chain.filter(exchange);
        }

        // ── Extract Authorization header ──────────────────────────────
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return buildUnauthorizedResponse(exchange,
                    "Authorization header is missing or does not start with 'Bearer '");
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        // ── Validate token ────────────────────────────────────────────
        if (!jwtUtil.isTokenValid(token)) {
            log.warn("Invalid or expired JWT token for path: {}", path);
            return buildUnauthorizedResponse(exchange,
                    "JWT token is invalid or has expired. Please login again.");
        }

        // ── Token is valid — extract user info and forward as headers ─
        String username = jwtUtil.extractUsername(token);
        String userId   = jwtUtil.extractUserId(token);
        String role     = jwtUtil.extractRole(token);

        log.debug("Authenticated user: {} (id={}, role={}) → {}", username, userId, role, path);

        // Add user info as headers so downstream services can read them
        // without re-parsing the JWT token themselves
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Auth-User-Id",   userId)
                .header("X-Auth-Username",  username)
                .header("X-Auth-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Check if the incoming path matches any configured public route.
     */
    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::startsWith)
                || path.startsWith("/oauth2")
                || path.startsWith("/login");
    }

    /**
     * Build and return a 401 Unauthorized response with a JSON error body.
     */
    private Mono<Void> buildUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\": 401, \"error\": \"Unauthorized\", \"message\": \"%s\"}",
                message
        );

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}