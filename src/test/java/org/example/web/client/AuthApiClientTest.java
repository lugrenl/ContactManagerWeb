package org.example.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.web.dto.JwtResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthApiClientTest {

    private static final String BASE_URL = "http://api.test";

    private MockRestServiceServer server;
    private AuthApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuthApiClient(builder.build(), new ObjectMapper());
    }

    @Test
    void loginReadsJwtTokenJsonProperty() {
        server.expect(once(), requestTo(BASE_URL + "/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"name":"alice","password":"password123"}
                        """))
                .andRespond(withSuccess("""
                        {"jwt-token":"header.payload.signature"}
                        """, MediaType.APPLICATION_JSON));

        JwtResponse response = client.login("alice", "password123");

        assertThat(response.jwtToken()).isEqualTo("header.payload.signature");
        server.verify();
    }

    @Test
    void registerSendsEmailAndReadsJwtToken() {
        server.expect(once(), requestTo(BASE_URL + "/api/auth/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"name":"alice","password":"password123","email":"alice@example.com"}
                        """))
                .andRespond(withSuccess("""
                        {"jwt-token":"header.payload.signature"}
                        """, MediaType.APPLICATION_JSON));

        JwtResponse response = client.register("alice", "password123", "alice@example.com");

        assertThat(response.jwtToken()).isEqualTo("header.payload.signature");
        server.verify();
    }

    @Test
    void loginUnauthorizedProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/auth/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Incorrect credentials!","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.login("alice", "wrongpass123"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(401);
                    assertThat(exception.getMessage()).isEqualTo("Неверное имя пользователя или пароль.");
                    assertThat(exception.getMessage()).doesNotContain("Incorrect credentials");
                });
        server.verify();
    }

    @Test
    void registerConflictProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/auth/register"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"User with this username already exists","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.register("alice", "password123", "alice@example.com"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(409);
                    assertThat(exception.getMessage()).isEqualTo("Пользователь с таким именем уже существует.");
                });
        server.verify();
    }

    @Test
    void validationProblemDetailDetailsAreExtracted() {
        server.expect(once(), requestTo(BASE_URL + "/api/auth/register"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type":"about:blank",
                                  "title":"Bad Request",
                                  "status":400,
                                  "detail":"errors.400.title",
                                  "errors":["Username is required","User email should be valid"]
                                }
                                """));

        assertThatThrownBy(() -> client.register("a", "password123", "bad"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(400);
                    assertThat(exception.getDetails())
                            .containsExactly("Username is required", "User email should be valid");
                });
        server.verify();
    }

    @Test
    void usersEndpointUnauthorizedMeansNotAdmin() {
        server.expect(once(), requestTo(BASE_URL + "/api/users"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-value"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Unauthorized","timestamp":1700000000000}
                                """));

        assertThat(client.isAdmin("token-value")).isFalse();
        server.verify();
    }

    @Test
    void usersEndpointSuccessMeansAdmin() {
        server.expect(once(), requestTo(BASE_URL + "/api/users"))
                .andExpect(header("Authorization", "Bearer token-value"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.isAdmin("token-value")).isTrue();
        server.verify();
    }

    @Test
    void logoutSendsBearerTokenExplicitly() {
        server.expect(once(), requestTo(BASE_URL + "/api/auth/logout"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-value"))
                .andRespond(withSuccess("Logged out successfully", MediaType.TEXT_PLAIN));

        client.logout("token-value");

        server.verify();
    }

    @Test
    void unavailableApiProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/auth/login"))
                .andRespond(withException(new SocketTimeoutException("timed out")));

        assertThatThrownBy(() -> client.login("alice", "password123"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isZero();
                    assertThat(exception.getMessage()).isEqualTo(
                            "Сервер API недоступен. Проверьте соединение и попробуйте снова.");
                });
        server.verify();
    }
}
