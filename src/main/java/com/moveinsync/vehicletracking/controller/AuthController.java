package com.moveinsync.vehicletracking.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;


//  AuthController — Simulated Authentication for Demonstration Purposes

//  THIS IS A SIMULATED AUTHENTICATION MECHANISM FOR DEMONSTRATION PURPOSES ONLY.

//       It uses hardcoded credentials and HTTP session flags.
//       In production, replace with JWT + BCrypt password hashing + user DB table.

//  Endpoints:
//    POST /auth/login   — validates credentials, sets session flag
//    GET  /auth/logout  — invalidates session, redirects to login page
//    GET  /auth/status  — returns current authentication state (for frontend checks)


 
@Controller
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    //  THIS IS A SIMULATED AUTHENTICATION MECHANISM FOR DEMONSTRATION PURPOSES ONLY.
    //      Do NOT use hardcoded credentials in a production system.

    private static final String VALID_EMAIL    = "admin@moveinsync.com";
    private static final String VALID_PASSWORD = "Move@123";

    static final String SESSION_KEY = "authenticated";

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpSession session) {

        String email    = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        log.info("Auth: login attempt for email='{}'", email);




        if (VALID_EMAIL.equals(email) && VALID_PASSWORD.equals(password)) {
            // Mark session as authenticated
            session.setAttribute(SESSION_KEY, true);
            session.setAttribute("user", email);
            session.setMaxInactiveInterval(3600); // 1 hour simulated session

            log.info("Auth: login SUCCESS for '{}' — session id={}", email, session.getId());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Login successful");
            resp.put("user", email);
            resp.put("redirect", "/dashboard.html");
            return ResponseEntity.ok(resp);
        }

        log.warn("Auth: login FAILED for email='{}'", email);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        resp.put("message", "Invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
    }

    //  GET /auth/logout
    //  Invalidates the session and redirects to the login page.
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        String user = (String) session.getAttribute("user");
        session.invalidate();
        log.info("Auth: '{}' logged out — session cleared", user != null ? user : "unknown");
        return "redirect:/login.html";
    }


    //  GET /auth/status
    //  Used by the frontend to check whether the current session is authenticated
    //  without triggering the interceptor redirect.

    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status(HttpSession session) {
        boolean authenticated = Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));
        String  user          = (String) session.getAttribute("user");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("authenticated", authenticated);
        resp.put("user", user != null ? user : null);
        return ResponseEntity.ok(resp);
    }
}
