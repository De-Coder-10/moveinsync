package com.moveinsync.vehicletracking.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


//  WebMvcConfig — registers the AuthInterceptor on protected API routes.
 
//   THIS IS A SIMULATED AUTHENTICATION MECHANISM FOR DEMONSTRATION PURPOSES ONLY.
 
//  Protected patterns:
//  /api/dashboard/**  — dashboard data & trip control
//  /api/location/**   — GPS updates & batch sync
//  /api/trip/**       — manual trip closure
//  /api/audit/**      — audit log queries
 
 

// WebMvcConfig customizes Spring MVC configuration, mainly to register interceptors 
// like AuthInterceptor and configure request handling behavior.
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
                        "/error",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}
