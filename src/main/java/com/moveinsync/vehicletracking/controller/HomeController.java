package com.moveinsync.vehicletracking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;


//   HomeController — redirects root URL based on authentication state.
 
//             authenticated → dashboard.html
//             not authenticated → login.html

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(HttpSession session) {
        boolean authenticated = Boolean.TRUE.equals(session.getAttribute("authenticated"));
        return authenticated ? "redirect:/dashboard.html" : "redirect:/login.html";
    }
}
