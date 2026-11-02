package org.example.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.web.session.SessionTokenStore;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionTokenStore tokenStore;

    public AuthInterceptor(SessionTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (!tokenStore.hasToken(session)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (tokenStore.isExpired(session)) {
            tokenStore.clear(session);
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?expired");
            return false;
        }

        if (isAdminPath(request) && !tokenStore.isAdmin(session)) {
            response.sendRedirect(request.getContextPath() + "/?accessDenied");
            return false;
        }

        return true;
    }

    private static boolean isAdminPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (path != null && contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path != null && (path.equals("/admin") || path.startsWith("/admin/"));
    }
}
