package org.example.web.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public final class JwtTestTokens {

    private JwtTestTokens() {
    }

    public static String valid(String username, long validForSeconds) {
        return withExpiration(username, Instant.now().plusSeconds(validForSeconds).getEpochSecond());
    }

    public static String expired(String username) {
        return withExpiration(username, Instant.now().minusSeconds(60).getEpochSecond());
    }

    private static String withExpiration(String username, long expirationEpochSeconds) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"username\":\"" + username + "\",\"exp\":" + expirationEpochSeconds + "}");
        return header + "." + payload + ".test-signature";
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
