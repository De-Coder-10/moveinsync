package com.moveinsync.vehicletracking.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvcConfig — registers the AuthInterceptor on protected API routes.
 *
 * ⚠️  THIS IS A SIMULATED AUTHENTICATION MECHANISM FOR DEMONSTRATION PURPOSES ONLY.
 *
 * Protected patterns:
 *   /api/dashboard/**  — dashboard data & trip control
 *   /api/location/**   — GPS updates & batch sync
 *   /api/trip/**       — manual trip closure
 *   /api/audit/**      — audit log queries
 *
 * Excluded (always public):
 *   /auth/**           — login / logout / status
 *   /login.html        — login page itself
 *   /                  — root redirect
 *   /ws/**             — WebSocket handshake (SockJS)
 *   /error             — Spring error page
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // Protect all real API routes
                .addPathPatterns(
                        "/api/dashboard/**",
                        "/api/location/**",
                        "/api/trip/**"
                )
                // Never intercept auth endpoints, login page, WS, or error handler
                .excludePathPatterns(
                        "/auth/**",
                        "/login.html",
                        "/",
                        "/ws/**",
                        "/error"
                );
    }
}
