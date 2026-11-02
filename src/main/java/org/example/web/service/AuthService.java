package org.example.web.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.web.client.ApiException;
import org.example.web.client.AuthApiClient;
import org.example.web.dto.JwtResponse;
import org.example.web.session.JwtExpirationParser;
import org.example.web.session.SessionTokenStore;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

    private final AuthApiClient authApiClient;
    private final SessionTokenStore tokenStore;
    private final JwtExpirationParser expirationParser;

    public AuthService(AuthApiClient authApiClient,
                       SessionTokenStore tokenStore,
                       JwtExpirationParser expirationParser) {
        this.authApiClient = authApiClient;
        this.tokenStore = tokenStore;
        this.expirationParser = expirationParser;
    }

    public void login(HttpServletRequest request, String name, String password) {
        String token = requireToken(authApiClient.login(name, password));
        Instant expiresAt = expirationParser.parseExpiration(token);
        boolean admin = authApiClient.isAdmin(token);

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        tokenStore.store(session, token, name, expiresAt, admin);
    }

    public void register(HttpServletRequest request, String name, String password, String email) {
        String token = requireToken(authApiClient.register(name, password, email));
        Instant expiresAt = expirationParser.parseExpiration(token);

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        tokenStore.store(session, token, name, expiresAt, false);
    }

    public boolean logout(HttpSession session) {
        boolean revocationConfirmed = true;
        String token = tokenStore.getToken(session).orElse(null);
        if (token != null) {
            try {
                authApiClient.logout(token);
            } catch (ApiException exception) {
                revocationConfirmed = false;
            }
        }

        tokenStore.clear(session);
        if (session != null) {
            session.invalidate();
        }
        return revocationConfirmed;
    }

    private static String requireToken(JwtResponse response) {
        if (response == null || response.jwtToken() == null || response.jwtToken().isBlank()) {
            throw new ApiException(502, "Сервер вернул некорректный ответ. Вход невозможен.");
        }
        return response.jwtToken();
    }
}
