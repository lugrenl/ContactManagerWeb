package org.example.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.web.session.SessionTokenStore;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private final SessionTokenStore tokenStore;

    public GlobalModelAttributes(SessionTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @ModelAttribute
    public void addAuthenticationState(Model model, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean authenticated = tokenStore.hasToken(session) && !tokenStore.isExpired(session);

        model.addAttribute("authenticated", authenticated);
        model.addAttribute("username", authenticated ? tokenStore.getUsername(session).orElse("") : "");
        model.addAttribute("isAdmin", authenticated && tokenStore.isAdmin(session));
    }
}
