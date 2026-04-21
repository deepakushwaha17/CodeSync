package com.codesync.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List;

/**
 * Role-Based Authorization Filter.
 * Restricts admin-only routes to ADMIN role users.
 */
@Component
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {

    // Routes that require ADMIN role
    private static final List<String> ADMIN_PATHS = List.of(
        "/admin/",
        "/auth/admin",
        "/executions/admin"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");

        boolean requiresAdmin = ADMIN_PATHS.stream().anyMatch(path::startsWith);
        if (requiresAdmin && !"ADMIN".equals(role)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0; // Run after JwtAuthFilter (order -1)
    }
}
