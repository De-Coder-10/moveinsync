package com.moveinsync.vehicletracking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;


// AuthInterceptor is used to validate authentication tokens before requests 
// reach the controller, ensuring secure access to protected endpoints.

// So it acts as a security gate before business logic runs.

@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_KEY = "authenticated";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            log.debug("Auth: access granted — {} {}", request.getMethod(), request.getRequestURI());
            return true;
        }

        log.warn("Auth: UNAUTHORIZED access attempt — {} {} (no valid session)",
                request.getMethod(), request.getRequestURI());

        String accept = request.getHeader("Accept");
        String ct     = request.getHeader("Content-Type");
        boolean wantsJson = (accept != null && accept.contains("application/json"))
                || (ct != null && ct.contains("application/json"))
                || request.getRequestURI().startsWith("/api/");

        if (wantsJson) {
            // Return JSON 401 so API / fetch() callers can handle it
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Unauthorized — please log in at /login.html\",\"redirect\":\"/login.html\"}"
            );
        } else {
            response.sendRedirect("/login.html");
        }

        return false; // block the request
    }
}
