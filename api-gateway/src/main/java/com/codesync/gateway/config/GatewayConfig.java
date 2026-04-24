package com.codesync.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GatewayConfig {

//    @Bean
//    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
//        return builder.routes()
//
//                // Example: add custom header to all auth-service responses
//                .route("auth-service-with-header", r -> r
//                        .path("/api/v1/auth/**")
//                        .filters(f -> f
//                                .addResponseHeader("X-Gateway", "CodeSync-Gateway")
//                                .addResponseHeader("X-Service", "auth-service")
//                        )
//                        .uri("lb://auth-service")
//                )
//
//                .build();
//    }
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        // Routes are defined in application.yaml — nothing needed here
        return builder.routes().build();
    }
}