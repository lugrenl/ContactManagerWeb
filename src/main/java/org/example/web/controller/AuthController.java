package org.example.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.web.client.ApiException;
import org.example.web.dto.LoginForm;
import org.example.web.dto.RegisterForm;
import org.example.web.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(name = "expired", required = false) String expired,
            @RequestParam(name = "loggedOut", required = false) String loggedOut,
            @RequestParam(name = "logoutWarning", required = false) String logoutWarning,
            @RequestParam(name = "selfDeleted", required = false) String selfDeleted,
            Model model) {
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        if (expired != null) {
            model.addAttribute("infoMessage",
                    "Сессия истекла или стала недействительной. Войдите снова.");
        } else if (logoutWarning != null) {
            model.addAttribute("infoMessage",
                    "Локальная сессия завершена, но подтвердить отзыв токена на сервере не удалось. "
                            + "Токен может оставаться действительным до истечения срока действия.");
        } else if (loggedOut != null) {
            model.addAttribute("infoMessage", "Вы вышли из системы.");
        } else if (selfDeleted != null) {
            model.addAttribute("infoMessage", "Ваша учётная запись удалена. Войдите снова.");
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(
            @Valid @ModelAttribute("loginForm") LoginForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            Model model) {
        if (bindingResult.hasErrors()) {
            form.setPassword("");
            return "auth/login";
        }

        try {
            authService.login(request, form.getName(), form.getPassword());
            return "redirect:/";
        } catch (ApiException exception) {
            return loginError(form, model, exception);
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registerForm") RegisterForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            Model model) {
        if (bindingResult.hasErrors()) {
            form.setPassword("");
            return "auth/register";
        }

        try {
            authService.register(request, form.getName(), form.getPassword(), form.getEmail());
            return "redirect:/";
        } catch (ApiException exception) {
            return registerError(form, model, exception);
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        boolean revocationConfirmed = authService.logout(session);
        return revocationConfirmed
                ? "redirect:/login?loggedOut"
                : "redirect:/login?logoutWarning";
    }

    private static String loginError(LoginForm form, Model model, ApiException exception) {
        form.setPassword("");
        model.addAttribute("errorMessage", exception.getMessage());
        if (!exception.getDetails().isEmpty()) {
            model.addAttribute("errorDetails", exception.getDetails());
        }
        return "auth/login";
    }

    private static String registerError(RegisterForm form, Model model, ApiException exception) {
        form.setPassword("");
        model.addAttribute("errorMessage", exception.getMessage());
        if (!exception.getDetails().isEmpty()) {
            model.addAttribute("errorDetails", exception.getDetails());
        }
        return "auth/register";
    }
}
