package org.example.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.web.dto.JwtResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AuthApiClient {

    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String LOGOUT_PATH = "/api/auth/logout";
    private static final String USERS_PATH = "/api/users";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AuthApiClient(RestClient contactManagerRestClient, ObjectMapper objectMapper) {
        this.restClient = contactManagerRestClient;
        this.objectMapper = objectMapper;
    }

    public JwtResponse login(String name, String password) {
        return postForToken(LOGIN_PATH, new LoginPayload(name, password), "Вход невозможен");
    }

    public JwtResponse register(String name, String password, String email) {
        return postForToken(REGISTER_PATH, new RegisterPayload(name, password, email), "Регистрация невозможна");
    }

    public void logout(String token) {
        try {
            restClient.post()
                    .uri(LOGOUT_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw ApiErrors.from(
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    objectMapper);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public boolean isAdmin(String token) {
        try {
            restClient.get()
                    .uri(USERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException | ResourceAccessException exception) {
            return false;
        }
    }

    private JwtResponse postForToken(String path, Object payload, String failureMessage) {
        try {
            JwtResponse response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JwtResponse.class);
            if (response == null || response.jwtToken() == null || response.jwtToken().isBlank()) {
                throw new ApiException(502, failureMessage + ": сервер не вернул токен.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw ApiErrors.from(
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    objectMapper);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record LoginPayload(String name, String password) {
    }

    private record RegisterPayload(String name, String password, String email) {
    }
}
