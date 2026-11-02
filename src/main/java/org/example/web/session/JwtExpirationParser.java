package org.example.web.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.web.client.ApiException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;

@Component
public class JwtExpirationParser {

    private static final int INVALID_TOKEN_STATUS = 502;

    private final ObjectMapper objectMapper;

    public JwtExpirationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Instant parseExpiration(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2 || parts[1].isBlank()) {
                throw invalidToken();
            }

            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(payload);
            JsonNode expiration = node.get("exp");
            if (expiration == null || !expiration.canConvertToLong()) {
                throw invalidToken();
            }

            Instant expiresAt = Instant.ofEpochSecond(expiration.asLong());
            if (!expiresAt.isAfter(Instant.now())) {
                throw invalidToken();
            }
            return expiresAt;
        } catch (IllegalArgumentException | IOException exception) {
            throw invalidToken();
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(INVALID_TOKEN_STATUS,
                "Сервер вернул некорректный токен. Вход невозможен.");
    }
}
