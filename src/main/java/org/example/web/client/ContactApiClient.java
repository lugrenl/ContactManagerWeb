package org.example.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.web.dto.AdminContactDto;
import org.example.web.dto.ContactDto;
import org.example.web.dto.ContactRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class ContactApiClient {

    private static final String CONTACTS_PATH = "/api/contacts";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ContactApiClient(RestClient contactManagerRestClient, ObjectMapper objectMapper) {
        this.restClient = contactManagerRestClient;
        this.objectMapper = objectMapper;
    }

    public List<ContactDto> list(String token) {
        try {
            ContactDto[] contacts = restClient.get()
                    .uri(CONTACTS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ContactDto[].class);
            return contacts == null ? List.of() : Arrays.asList(contacts);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public List<AdminContactDto> listAllContacts(String token) {
        try {
            AdminContactDto[] contacts = restClient.get()
                    .uri(CONTACTS_PATH + "/admin")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AdminContactDto[].class);
            return contacts == null ? List.of() : Arrays.asList(contacts);
        } catch (RestClientResponseException exception) {
            throw toAdminListException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public ContactDto get(String token, long id) {
        try {
            ContactDto contact = restClient.get()
                    .uri(contactUri(id))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ContactDto.class);
            return requireContact(contact);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public ContactDto create(String token, ContactRequest request) {
        try {
            ContactDto contact = restClient.post()
                    .uri(CONTACTS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ContactDto.class);
            return requireContact(contact);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public ContactDto update(String token, long id, ContactRequest request) {
        try {
            ContactDto contact = restClient.put()
                    .uri(contactUri(id))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ContactDto.class);
            return requireContact(contact);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public void delete(String token, long id) {
        try {
            restClient.delete()
                    .uri(contactUri(id))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    public void importCsv(String token, MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename == null || originalFilename.isBlank()
                    ? "contacts.csv"
                    : originalFilename;

            ByteArrayResource content = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType("text/csv"));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(content, partHeaders));

            restClient.post()
                    .uri(CONTACTS_PATH + "/import")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException exception) {
            throw new ApiException(500, "Не удалось прочитать выбранный файл.");
        } catch (RestClientResponseException exception) {
            throw toImportException(exception);
        } catch (ResourceAccessException exception) {
            throw ApiErrors.unavailable();
        }
    }

    private ApiException toAdminListException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String message = switch (status) {
            case 401 -> "Сессия недействительна или недостаточно прав. Войдите снова.";
            case 403 -> "Недостаточно прав для административной операции.";
            default -> null;
        };
        if (message != null) {
            return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper, message);
        }
        return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper);
    }

    private ApiException toImportException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String message = switch (status) {
            case 400 -> "Не удалось импортировать CSV. Проверьте формат файла и данные.";
            case 401 -> "Сессия недействительна. Войдите снова.";
            case 403 -> "Недостаточно прав для импорта контактов.";
            case 413 -> "Файл превышает допустимый размер. Максимум — 1 MB.";
            default -> null;
        };
        if (message != null) {
            return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper, message);
        }
        return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper);
    }

    private ApiException toApiException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String message = contactMessage(status);
        if (message != null) {
            return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper, message);
        }
        return ApiErrors.from(status, exception.getResponseBodyAsString(), objectMapper);
    }

    private static String contactMessage(int status) {
        return switch (status) {
            case 401 -> "Сессия недействительна. Войдите снова.";
            case 403 -> "Доступ к этому контакту запрещён.";
            case 404 -> "Контакт не найден.";
            case 409 -> "Контакт с такими данными уже существует.";
            default -> null;
        };
    }

    private static ContactDto requireContact(ContactDto contact) {
        if (contact == null) {
            throw new ApiException(502, "Сервер вернул пустой ответ при работе с контактом.");
        }
        return contact;
    }

    private static String contactUri(long id) {
        return CONTACTS_PATH + "/" + id;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
