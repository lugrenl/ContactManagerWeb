package org.example.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.web.dto.UserDto;
import org.example.web.dto.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserApiClientTest {

    private static final String BASE_URL = "http://api.test";
    private static final String TOKEN = "admin-token";
    private static final String USER_JSON = """
            {
              "id": 5,
              "name": "alice",
              "email": "alice@example.com",
              "role": "ROLE_USER",
              "contactIds": [1, 2]
            }
            """;

    private MockRestServiceServer server;
    private UserApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UserApiClient(builder.build(), new ObjectMapper());
    }

    @Test
    void listSendsBearerTokenAndParsesUsers() {
        server.expect(once(), requestTo(BASE_URL + "/api/users"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        [
                          {"id":1,"name":"admin","email":"admin@example.com","role":"ROLE_ADMIN","contactIds":[]},
                          {"id":5,"name":"alice","email":"alice@example.com","role":"ROLE_USER","contactIds":[1,2]}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<UserDto> users = client.list(TOKEN);

        assertThat(users).hasSize(2);
        assertThat(users.get(0).getId()).isEqualTo(1);
        assertThat(users.get(1).getRole()).isEqualTo("ROLE_USER");
        assertThat(users.get(1).getRoleLabel()).isEqualTo("Пользователь");
        server.verify();
    }

    @Test
    void getUsesUserIdAndBearerToken() {
        server.expect(once(), requestTo(BASE_URL + "/api/users/5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        UserDto user = client.get(TOKEN, 5L);

        assertThat(user.getId()).isEqualTo(5);
        assertThat(user.getName()).isEqualTo("alice");
        server.verify();
    }

    @Test
    void updateSendsRequiredFieldsAndRole() {
        server.expect(once(), requestTo(BASE_URL + "/api/users/5"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json("""
                        {
                          "name": "alice2",
                          "password": "newpassword123",
                          "email": "alice2@example.com",
                          "role": "ROLE_ADMIN"
                        }
                        """))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        UserDto user = client.update(TOKEN, 5L,
                new UserUpdateRequest("alice2", "newpassword123", "alice2@example.com", "ROLE_ADMIN"));

        assertThat(user.getId()).isEqualTo(5);
        server.verify();
    }

    @Test
    void updateOmitsRoleWhenItIsNotProvided() {
        server.expect(once(), requestTo(BASE_URL + "/api/users/5"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"role\""))))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        client.update(TOKEN, 5L,
                new UserUpdateRequest("alice", "newpassword123", "alice@example.com", null));

        server.verify();
    }

    @Test
    void deleteAcceptsNoContentWithoutJsonBody() {
        server.expect(once(), requestTo(BASE_URL + "/api/users/5"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.delete(TOKEN, 5L);

        server.verify();
    }

    @Test
    void unauthorizedResponseProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/users"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Unauthorized\",\"timestamp\":1700000000000}"));

        assertThatThrownBy(() -> client.list(TOKEN))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(401);
                    assertThat(exception.getMessage())
                            .isEqualTo("Сессия недействительна или недостаточно прав. Войдите снова.");
                });
        server.verify();
    }

    @Test
    void forbiddenResponseProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/users/5"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Forbidden\",\"timestamp\":1700000000000}"));

        assertThatThrownBy(() -> client.get(TOKEN, 5L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(403);
                    assertThat(exception.getMessage()).isEqualTo("Недостаточно прав для административной операции.");
                });
        server.verify();
    }

    @Test
    void conflictResponseProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/users/5"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Username already exists\",\"timestamp\":1700000000000}"));

        assertThatThrownBy(() -> client.update(TOKEN, 5L,
                new UserUpdateRequest("admin", "newpassword123", "alice@example.com", null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(409);
                    assertThat(exception.getMessage()).isEqualTo("Пользователь с таким именем уже существует.");
                });
        server.verify();
    }

    @Test
    void notFoundResponseProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/users/5"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"User not found\",\"timestamp\":1700000000000}"));

        assertThatThrownBy(() -> client.get(TOKEN, 5L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(404);
                    assertThat(exception.getMessage()).isEqualTo("Пользователь не найден.");
                });
        server.verify();
    }
}
