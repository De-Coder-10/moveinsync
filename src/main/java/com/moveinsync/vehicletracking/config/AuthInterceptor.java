package com.moveinsync.vehicletracking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * AuthInterceptor — Simulated Session-Based Authentication Guard
 *
 * ⚠️  THIS IS A SIMULATED AUTHENTICATION MECHANISM FOR DEMONSTRATION PURPOSES ONLY.
 *      It checks for a Boolean "authenticated" flag stored in the HTTP session.
 *      A real system would validate a signed JWT or cryptographic token here.
 *
 * Behaviour:
 *  - If session flag is TRUE  → request proceeds normally
 *  - If session flag is FALSE → browser requests redirected to /login.html
 *                               API/JSON requests get HTTP 401 response body
 *
 * Protected path patterns (registered in WebMvcConfig):
 *  /api/dashboard/**  — all dashboard data & control endpoints
 *  /api/location/**   — GPS ping + batch sync endpoints
 *  /api/trip/**       — manual trip closure
 *  /api/audit/**      — audit log queries
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    /** Must match AuthController.SESSION_KEY */
    private static final String SESSION_KEY = "authenticated";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // ⚠️  THIS IS A SIMULATED AUTHENTICATION MECHANISM FOR DEMONSTRATION PURPOSES ONLY.
        HttpSession session = request.getSession(false); // false = don't create new session
        boolean authenticated = session != null
                && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));

        if (authenticated) {
            log.debug("Auth: access granted — {} {}", request.getMethod(), request.getRequestURI());
            return true; // allow the request through
        }

        log.warn("Auth: UNAUTHORIZED access attempt — {} {} (no valid session)",
                request.getMethod(), request.getRequestURI());

        // Detect whether caller expects JSON (API client) or HTML (browser)
        String acceptHeader = request.getHeader("Accept");
        String contentType  = request.getHeader("Content-Type");
        boolean wantsJson = (acceptHeader != null && acceptHeader.contains("application/json"))
                || (contentType  != null && contentType.contains("application/json"))
                || request.getRequestURI().startsWith("/api/");

        if (wantsJson) {
            // Return JSON 401 so API / fetch() callers can handle it
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Unauthorized — please log in at /login.html\",\"redirect\":\"/login.html\"}"
            );
        } else {
            // Browser: redirect to login page
            response.sendRedirect("/login.html");
        }

        return false; // block the request
    }
}
