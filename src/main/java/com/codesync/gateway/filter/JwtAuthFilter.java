package com.codesync.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.util.List;

/**
 * Global JWT Authentication Filter.
 * Intercepts every request, validates Bearer token,
 * and forwards userId + role as headers to downstream services.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_PATHS = List.of(
        "/auth/register",
        "/auth/login",
        "/auth/refresh",
        "/projects/public",
        "/executions/languages"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Allow public paths through without auth
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        if (isPublic) {
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            // Validate JWT and extract claims
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            // Forward userId and role as headers to downstream microservices
            ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                )
                .build();

            return chain.filter(mutatedExchange);

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -1; // Run before all other filters
    }
}
