package org.example.web.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.web.client.ApiException;
import org.example.web.session.SessionTokenStore;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = AdminController.class)
public class AdminApiExceptionHandler {

    private final SessionTokenStore tokenStore;

    public AdminApiExceptionHandler(SessionTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @ExceptionHandler(ApiException.class)
    public String handle(ApiException exception,
                         HttpSession session,
                         Model model,
                         HttpServletResponse response) {
        if (exception.getStatus() == 401) {
            if (session != null) {
                tokenStore.clear(session);
                session.invalidate();
            }
            return "redirect:/login?expired";
        }

        HttpStatus status = resolveStatus(exception.getStatus());
        response.setStatus(status.value());
        model.addAttribute("errorTitle", title(exception.getStatus()));
        model.addAttribute("errorMessage", exception.getMessage());
        if (!exception.getDetails().isEmpty()) {
            model.addAttribute("errorDetails", exception.getDetails());
        }
        return "admin/error";
    }

    private static HttpStatus resolveStatus(int status) {
        if (status <= 0) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
    }

    private static String title(int status) {
        return switch (status) {
            case 403 -> "Доступ запрещён";
            case 404 -> "Пользователь не найден";
            case 0, 500, 502, 503, 504 -> "Сервис временно недоступен";
            default -> "Не удалось выполнить операцию";
        };
    }
}
