package com.eewms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String redirectToHomePage(org.springframework.security.core.Authentication auth) {
        boolean loggedIn = auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        return loggedIn ? "redirect:/dashboard" : "redirect:/login";
    }
}

