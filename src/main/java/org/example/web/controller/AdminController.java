package org.example.web.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.web.client.ApiException;
import org.example.web.dto.AdminUserForm;
import org.example.web.dto.UserDto;
import org.example.web.service.AdminService;
import org.example.web.session.SessionTokenStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final SessionTokenStore tokenStore;

    public AdminController(AdminService adminService, SessionTokenStore tokenStore) {
        this.adminService = adminService;
        this.tokenStore = tokenStore;
    }

    @GetMapping("/users")
    public String users(HttpSession session, Model model) {
        model.addAttribute("users", adminService.listUsers(token(session)));
        return "admin/users/list";
    }

    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable long id, HttpSession session, Model model) {
        UserDto user = adminService.getUser(token(session), id);
        AdminUserForm form = new AdminUserForm();
        form.setName(user.getName());
        form.setEmail(user.getEmail());
        form.setRole(user.getRole());
        form.setPassword("");

        model.addAttribute("userForm", form);
        model.addAttribute("userId", id);
        return "admin/users/form";
    }

    @PostMapping("/users/{id}/edit")
    public String updateUser(@PathVariable long id,
                             @Valid @ModelAttribute("userForm") AdminUserForm form,
                             BindingResult bindingResult,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        model.addAttribute("userId", id);
        if (bindingResult.hasErrors()) {
            form.setPassword("");
            return "admin/users/form";
        }

        try {
            adminService.updateUser(token(session), id, form);
            redirectAttributes.addFlashAttribute("infoMessage", "Пользователь обновлён.");
            return "redirect:/admin/users";
        } catch (ApiException exception) {
            if (exception.getStatus() == 401) {
                throw exception;
            }
            if (exception.getStatus() == 400 || exception.getStatus() == 409) {
                form.setPassword("");
                model.addAttribute("errorMessage", exception.getMessage());
                if (!exception.getDetails().isEmpty()) {
                    model.addAttribute("errorDetails", exception.getDetails());
                }
                return "admin/users/form";
            }
            throw exception;
        }
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable long id,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            String token = token(session);
            String currentUsername = tokenStore.getUsername(session).orElse("");
            boolean deletingCurrentUser = adminService.isCurrentUser(token, currentUsername, id);

            adminService.deleteUser(token, id);

            if (deletingCurrentUser) {
                tokenStore.clear(session);
                session.invalidate();
                return "redirect:/login?selfDeleted=true";
            }

            redirectAttributes.addFlashAttribute("infoMessage", "Пользователь удалён.");
            return "redirect:/admin/users";
        } catch (ApiException exception) {
            if (exception.getStatus() == 401) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/admin/users";
        }
    }

    @GetMapping("/contacts")
    public String contacts(HttpSession session, Model model) {
        model.addAttribute("rows", adminService.listAllContacts(token(session)));
        return "admin/contacts";
    }

    private String token(HttpSession session) {
        return tokenStore.getToken(session)
                .orElseThrow(() -> new ApiException(401, "Сессия недействительна. Войдите снова."));
    }
}
