package org.example.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.web.dto.UserDto;
import org.example.web.dto.UserUpdateRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.List;

@Component
public class UserApiClient {

    private static final String USERS_PATH = "/api/users";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public UserApiClient(RestClient contactManagerRestClient, ObjectMapper objectMapper) {
        this.restClient = contactManagerRestClient;
        this.objectMapper = objectMapper;
    }

    public List<UserDto> list(String token) {
        try {
            UserDto[] users = restClient.get()
                    .uri(USERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(UserDto[].class);
            return users == null ? List.of() : Arrays.asList(users);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public UserDto get(String token, long id) {
        try {
            UserDto user = restClient.get()
                    .uri(userUri(id))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(UserDto.class);
            return requireUser(user);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public UserDto update(String token, long id, UserUpdateRequest request) {
        try {
            UserDto user = restClient.put()
                    .uri(userUri(id))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(UserDto.class);
            return requireUser(user);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public void delete(String token, long id) {
        try {
            restClient.delete()
                    .uri(userUri(id))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    private ApiException toApiException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String message = switch (status) {
            case 401 -> "Сессия недействительна или недостаточно прав. Войдите снова.";
            case 403 -> "Недостаточно прав для административной операции.";
            case 404 -> "Пользователь не найден.";
            case 409 -> "Пользователь с таким именем уже существует.";
            default -> null;
        };
        if (message != null) {
            return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper, message);
        }
        return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper);
    }

    private static UserDto requireUser(UserDto user) {
        if (user == null) {
            throw new ApiException(502, "Сервер вернул пустой ответ при работе с пользователем.");
        }
        return user;
    }

    private static String userUri(long id) {
        return USERS_PATH + "/" + id;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
