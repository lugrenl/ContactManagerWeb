package org.example.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.web.dto.AdminContactDto;
import org.example.web.dto.ContactDto;
import org.example.web.dto.ContactRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

class ContactApiClientTest {

    private static final String BASE_URL = "http://api.test";
    private static final String TOKEN = "token-value";
    private static final String CONTACT_JSON = """
            {
              "id": 7,
              "name": "Анна",
              "surname": "Иванова",
              "email": "anna@example.com",
              "phoneNumber": "+79990000000"
            }
            """;

    private MockRestServiceServer server;
    private ContactApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ContactApiClient(builder.build(), new ObjectMapper());
    }

    @Test
    void listSendsBearerTokenAndParsesContacts() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        [
                          {"id":2,"name":"Анна","surname":"Иванова","email":"anna@example.com","phoneNumber":"+79990000000"},
                          {"id":1,"name":"Борис","surname":"Алексеев","email":null,"phoneNumber":null}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ContactDto> contacts = client.list(TOKEN);

        assertThat(contacts).hasSize(2);
        assertThat(contacts.get(0).getId()).isEqualTo(2);
        assertThat(contacts.get(0).getName()).isEqualTo("Анна");
        assertThat(contacts.get(0).getSurname()).isEqualTo("Иванова");
        assertThat(contacts.get(1).getEmail()).isNull();
        server.verify();
    }

    @Test
    void adminListSendsBearerTokenAndParsesOwnerId() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/admin"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        [
                          {"id":10,"name":"Анна","surname":"Иванова","email":"anna@example.com",
                           "phoneNumber":"+79990000000","ownerId":2}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<AdminContactDto> contacts = client.listAllContacts(TOKEN);

        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getId()).isEqualTo(10);
        assertThat(contacts.get(0).getOwnerId()).isEqualTo(2);
        server.verify();
    }

    @Test
    void getUsesContactIdAndBearerToken() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(CONTACT_JSON, MediaType.APPLICATION_JSON));

        ContactDto contact = client.get(TOKEN, 7L);

        assertThat(contact.getId()).isEqualTo(7);
        assertThat(contact.getDisplayName()).isEqualTo("Иванова Анна");
        server.verify();
    }

    @Test
    void createSendsJsonAndReturnsExistingContactForDuplicate() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json("""
                        {
                          "name": "Анна",
                          "surname": "Иванова",
                          "email": "anna@example.com",
                          "phoneNumber": "+79990000000"
                        }
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(CONTACT_JSON));

        ContactDto contact = client.create(TOKEN,
                new ContactRequest("Анна", "Иванова", "anna@example.com", "+79990000000"));

        assertThat(contact.getId()).isEqualTo(7);
        server.verify();
    }

    @Test
    void updateUsesPutWithSameId() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/7"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json("""
                        {"name":"Анна","surname":"Петрова","email":null,"phoneNumber":null}
                        """))
                .andRespond(withSuccess(CONTACT_JSON, MediaType.APPLICATION_JSON));

        ContactDto contact = client.update(TOKEN, 7L,
                new ContactRequest("Анна", "Петрова", null, null));

        assertThat(contact.getId()).isEqualTo(7);
        server.verify();
    }

    @Test
    void deleteUsesDeleteWithBearerToken() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/7"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withStatus(HttpStatus.OK));

        client.delete(TOKEN, 7L);

        server.verify();
    }

    @Test
    void updateConflictProducesContactSpecificMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/7"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Another contact with the same data already exists","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.update(TOKEN, 7L,
                new ContactRequest("Анна", "Иванова", "anna@example.com", null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(409);
                    assertThat(exception.getMessage())
                            .isEqualTo("Контакт с такими данными уже существует.");
                });
        server.verify();
    }

    @Test
    void forbiddenContactProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/7"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Contact does not belong to the current user","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.get(TOKEN, 7L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(403);
                    assertThat(exception.getMessage()).isEqualTo("Доступ к этому контакту запрещён.");
                });
        server.verify();
    }

    @Test
    void missingContactProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/7"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Contact not found","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.get(TOKEN, 7L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(404);
                    assertThat(exception.getMessage()).isEqualTo("Контакт не найден.");
                });
        server.verify();
    }

    @Test
    void unauthorizedListReturnsStatusForSessionCleanup() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Unauthorized","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.list(TOKEN))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(401);
                    assertThat(exception.getMessage()).isEqualTo("Сессия недействительна. Войдите снова.");
                });
        server.verify();
    }

    @Test
    void unavailableApiProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts"))
                .andRespond(withException(new SocketTimeoutException("timed out")));

        assertThatThrownBy(() -> client.list(TOKEN))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isZero();
                    assertThat(exception.getMessage()).isEqualTo(
                            "Сервер API недоступен. Проверьте соединение и попробуйте снова.");
                });
        server.verify();
    }

    @Test
    void importCsvSendsMultipartPartWithFilenameContentAndBearerToken() throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);

        httpServer.createContext("/api/contacts/import", exchange -> {
            try {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                body.set(exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            } finally {
                exchange.close();
                requestReceived.countDown();
            }
        });
        httpServer.start();

        try {
            String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
            ContactApiClient importClient = new ContactApiClient(
                    RestClient.builder().baseUrl(baseUrl).build(), new ObjectMapper());
            String csv = "Иван,Иванов,ivan@example.com,+79990000000\n";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "contacts.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

            importClient.importCsv(TOKEN, file);

            assertThat(requestReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
            assertThat(contentType.get()).contains("multipart/form-data");
            assertThat(contentType.get()).contains("boundary=");
            String multipartBody = new String(body.get(), StandardCharsets.UTF_8);
            assertThat(multipartBody).contains("name=\"file\"");
            assertThat(multipartBody).contains("filename=\"contacts.csv\"");
            assertThat(multipartBody).contains(csv);
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void importInvalidCsvProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/import"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Invalid CSV record 2","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.importCsv(TOKEN, csvFile()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(400);
                    assertThat(exception.getMessage())
                            .isEqualTo("Не удалось импортировать CSV. Проверьте формат файла и данные.");
                });
        server.verify();
    }

    @Test
    void importTooLargeProducesSafeMessage() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/import"))
                .andRespond(withStatus(HttpStatus.PAYLOAD_TOO_LARGE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Uploaded file exceeds the configured size limit","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.importCsv(TOKEN, csvFile()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(413);
                    assertThat(exception.getMessage())
                            .isEqualTo("Файл превышает допустимый размер. Максимум — 1 MB.");
                });
        server.verify();
    }

    @Test
    void importUnauthorizedProducesStatusForSessionCleanup() {
        server.expect(once(), requestTo(BASE_URL + "/api/contacts/import"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Unauthorized","timestamp":1700000000000}
                                """));

        assertThatThrownBy(() -> client.importCsv(TOKEN, csvFile()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(401);
                    assertThat(exception.getMessage()).isEqualTo("Сессия недействительна. Войдите снова.");
                });
        server.verify();
    }

    private static MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "contacts.csv", "text/csv",
                "Иван,Иванов,ivan@example.com,+79990000000\n".getBytes(StandardCharsets.UTF_8));
    }
}
