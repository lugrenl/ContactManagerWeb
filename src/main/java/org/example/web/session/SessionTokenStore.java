package org.example.web.session;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class SessionTokenStore {

    private static final String TOKEN_ATTRIBUTE = SessionTokenStore.class.getName() + ".token";
    private static final String USERNAME_ATTRIBUTE = SessionTokenStore.class.getName() + ".username";
    private static final String EXPIRES_AT_ATTRIBUTE = SessionTokenStore.class.getName() + ".expiresAt";
    private static final String ADMIN_ATTRIBUTE = SessionTokenStore.class.getName() + ".admin";

    public void store(HttpSession session, String token, String username, Instant expiresAt, boolean isAdmin) {
        if (session == null) {
            throw new IllegalArgumentException("HttpSession is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Token expiration is required");
        }
        session.setAttribute(TOKEN_ATTRIBUTE, token);
        session.setAttribute(USERNAME_ATTRIBUTE, username);
        session.setAttribute(EXPIRES_AT_ATTRIBUTE, expiresAt);
        session.setAttribute(ADMIN_ATTRIBUTE, isAdmin);
    }

    public Optional<String> getToken(HttpSession session) {
        return stringAttribute(session, TOKEN_ATTRIBUTE);
    }

    public Optional<String> getUsername(HttpSession session) {
        return stringAttribute(session, USERNAME_ATTRIBUTE);
    }

    public Optional<Instant> getExpiresAt(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(EXPIRES_AT_ATTRIBUTE);
        return value instanceof Instant expiresAt ? Optional.of(expiresAt) : Optional.empty();
    }

    public boolean hasToken(HttpSession session) {
        return getToken(session).isPresent();
    }

    public boolean isAdmin(HttpSession session) {
        if (session == null) {
            return false;
        }
        return Boolean.TRUE.equals(session.getAttribute(ADMIN_ATTRIBUTE));
    }

    public boolean isExpired(HttpSession session) {
        if (!hasToken(session)) {
            return false;
        }
        return getExpiresAt(session)
                .map(expiresAt -> !expiresAt.isAfter(Instant.now()))
                .orElse(true);
    }

    public void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(TOKEN_ATTRIBUTE);
            session.removeAttribute(USERNAME_ATTRIBUTE);
            session.removeAttribute(EXPIRES_AT_ATTRIBUTE);
            session.removeAttribute(ADMIN_ATTRIBUTE);
        }
    }

    private static Optional<String> stringAttribute(HttpSession session, String name) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(name);
        return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }
}
