package org.example.web.controller;

import org.example.web.client.ApiException;
import org.example.web.client.ContactApiClient;
import org.example.web.dto.ContactDto;
import org.example.web.dto.ContactRequest;
import org.example.web.session.SessionTokenStore;
import org.example.web.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ContactControllerTest {

    private static final String TOKEN = JwtTestTokens.valid("alice", 3600);

    private final ContactDto anna = new ContactDto(
            2, "Анна", "Иванова", "anna@example.com", "+79990000000");
    private final ContactDto boris = new ContactDto(
            1, "Борис", "Алексеев", null, null);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenStore tokenStore;

    @MockitoBean
    private ContactApiClient contactApiClient;

    @Test
    void listDisplaysContactsSortedBySurnameWithoutToken() throws Exception {
        when(contactApiClient.list(TOKEN)).thenReturn(List.of(anna, boris));

        MvcResult result = mockMvc.perform(get("/contacts").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/list"))
                .andExpect(content().string(containsString("Иванова")))
                .andExpect(content().string(containsString("Алексеев")))
                .andExpect(content().string(containsString("anna@example.com")))
                .andExpect(content().string(containsString("+79990000000")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString(TOKEN))))
                .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html.indexOf("Алексеев")).isLessThan(html.indexOf("Иванова"));
        assertThat(html).doesNotContain("jwt-token");
    }

    @Test
    void emptyListShowsEmptyState() throws Exception {
        when(contactApiClient.list(TOKEN)).thenReturn(List.of());

        mockMvc.perform(get("/contacts").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/list"))
                .andExpect(content().string(containsString("У вас пока нет контактов")));
    }

    @Test
    void createSendsEnteredValuesToApi() throws Exception {
        when(contactApiClient.create(eq(TOKEN), any(ContactRequest.class)))
                .thenReturn(new ContactDto(9, "Анна", "Иванова", "anna@example.com", "+79990000000"));

        mockMvc.perform(post("/contacts")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("name", "Анна")
                        .param("surname", "Иванова")
                        .param("email", "anna@example.com")
                        .param("phoneNumber", "+79990000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"))
                .andExpect(flash().attribute("infoMessage", "Контакт сохранён."));

        ArgumentCaptor<ContactRequest> captor = ArgumentCaptor.forClass(ContactRequest.class);
        verify(contactApiClient).create(eq(TOKEN), captor.capture());
        ContactRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("Анна");
        assertThat(request.surname()).isEqualTo("Иванова");
        assertThat(request.email()).isEqualTo("anna@example.com");
        assertThat(request.phoneNumber()).isEqualTo("+79990000000");
    }

    @Test
    void createAcceptsExistingContactResponseWithoutClaimingNewRecord() throws Exception {
        when(contactApiClient.create(eq(TOKEN), any(ContactRequest.class)))
                .thenReturn(new ContactDto(7, "Анна", "Иванова", "anna@example.com", null));

        mockMvc.perform(post("/contacts")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("name", "Анна")
                        .param("surname", "Иванова"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"))
                .andExpect(flash().attribute("infoMessage", "Контакт сохранён."))
                .andExpect(flash().attribute("infoMessage", not(containsString("создан"))));
    }

    @Test
    void createAllowsBlankOptionalFields() throws Exception {
        when(contactApiClient.create(eq(TOKEN), any(ContactRequest.class)))
                .thenReturn(new ContactDto(10, "Анна", "Иванова", null, null));

        mockMvc.perform(post("/contacts")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("name", "Анна")
                        .param("surname", "Иванова")
                        .param("email", "")
                        .param("phoneNumber", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"));

        ArgumentCaptor<ContactRequest> captor = ArgumentCaptor.forClass(ContactRequest.class);
        verify(contactApiClient).create(eq(TOKEN), captor.capture());
        assertThat(captor.getValue().email()).isNull();
        assertThat(captor.getValue().phoneNumber()).isNull();
    }

    @Test
    void createValidationErrorPreservesInputAndShowsDetails() throws Exception {
        when(contactApiClient.create(eq(TOKEN), any(ContactRequest.class)))
                .thenThrow(new ApiException(400, "Проверьте правильность заполнения полей.",
                        List.of("name: Name should be between 2 and 20 characters")));

        mockMvc.perform(post("/contacts")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("name", "Анна")
                        .param("surname", "Иванова")
                        .param("email", "anna@example.com")
                        .param("phoneNumber", "+79990000000"))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/form"))
                .andExpect(content().string(containsString("Проверьте правильность заполнения полей.")))
                .andExpect(content().string(containsString("Name should be between 2 and 20 characters")))
                .andExpect(content().string(containsString("value=\"Анна\"")))
                .andExpect(content().string(containsString("anna@example.com")))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void editFormLoadsContactById() throws Exception {
        when(contactApiClient.get(TOKEN, 5L))
                .thenReturn(new ContactDto(5, "Анна", "Иванова", "anna@example.com", "+79990000000"));

        mockMvc.perform(get("/contacts/5/edit").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/form"))
                .andExpect(content().string(containsString("Редактирование контакта")))
                .andExpect(content().string(containsString("action=\"/contacts/5/edit\"")))
                .andExpect(content().string(containsString("value=\"Анна\"")))
                .andExpect(content().string(containsString("value=\"anna@example.com\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        verify(contactApiClient).get(TOKEN, 5L);
    }

    @Test
    void updateCallsApiWithUrlIdAndPreservesId() throws Exception {
        when(contactApiClient.update(eq(TOKEN), eq(5L), any(ContactRequest.class)))
                .thenReturn(new ContactDto(5, "Анна", "Петрова", "anna@example.com", null));

        mockMvc.perform(post("/contacts/5/edit")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("name", "Анна")
                        .param("surname", "Петрова")
                        .param("email", "anna@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"))
                .andExpect(flash().attribute("infoMessage", "Контакт обновлён."));

        verify(contactApiClient).update(eq(TOKEN), eq(5L), any(ContactRequest.class));
    }

    @Test
    void updateConflictShowsFormWithEnteredValues() throws Exception {
        when(contactApiClient.update(eq(TOKEN), eq(5L), any(ContactRequest.class)))
                .thenThrow(new ApiException(409, "Контакт с такими данными уже существует."));

        mockMvc.perform(post("/contacts/5/edit")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("name", "Анна")
                        .param("surname", "Петрова")
                        .param("email", "anna@example.com")
                        .param("phoneNumber", "+79990000000"))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/form"))
                .andExpect(content().string(containsString("Контакт с такими данными уже существует.")))
                .andExpect(content().string(containsString("value=\"Петрова\"")))
                .andExpect(content().string(containsString("value=\"anna@example.com\"")))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void deleteUsesPostWithCsrfAndCallsDeleteApi() throws Exception {
        mockMvc.perform(post("/contacts/5/delete")
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"))
                .andExpect(flash().attribute("infoMessage", "Контакт удалён."));

        verify(contactApiClient).delete(TOKEN, 5L);
    }

    @Test
    void deleteWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/contacts/5/delete").session(authenticatedSession()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void deleteApiErrorRedirectsToContactsWithMessage() throws Exception {
        doThrow(new ApiException(404, "Контакт не найден."))
                .when(contactApiClient).delete(TOKEN, 5L);

        mockMvc.perform(post("/contacts/5/delete")
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"))
                .andExpect(flash().attribute("errorMessage", "Контакт не найден."));
    }

    @Test
    void listUnauthorizedClearsSessionAndRedirectsToLogin() throws Exception {
        when(contactApiClient.list(TOKEN))
                .thenThrow(new ApiException(401, "Сессия недействительна. Войдите снова."));
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(get("/contacts").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void listForbiddenShowsAccessDeniedPage() throws Exception {
        when(contactApiClient.list(TOKEN))
                .thenThrow(new ApiException(403, "Доступ к этому контакту запрещён."));
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(get("/contacts").session(session))
                .andExpect(status().isForbidden())
                .andExpect(view().name("contacts/error"))
                .andExpect(content().string(containsString("Доступ запрещён")))
                .andExpect(content().string(containsString("Доступ к этому контакту запрещён.")));

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void editNotFoundShowsFriendlyMessage() throws Exception {
        when(contactApiClient.get(TOKEN, 99L))
                .thenThrow(new ApiException(404, "Контакт не найден."));

        mockMvc.perform(get("/contacts/99/edit").session(authenticatedSession()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("contacts/error"))
                .andExpect(content().string(containsString("Контакт не найден")))
                .andExpect(content().string(containsString("Контакт не найден.")));
    }

    @Test
    void unavailableApiShowsSafeErrorPage() throws Exception {
        when(contactApiClient.list(TOKEN))
                .thenThrow(new ApiException(0, "Сервер API недоступен. Проверьте соединение и попробуйте снова."));

        mockMvc.perform(get("/contacts").session(authenticatedSession()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(view().name("contacts/error"))
                .andExpect(content().string(containsString("Сервис временно недоступен")))
                .andExpect(content().string(containsString("Сервер API недоступен")))
                .andExpect(content().string(not(containsString("java.lang"))));
    }

    @Test
    void contactRequestsWithoutSessionDoNotCallApi() throws Exception {
        mockMvc.perform(get("/contacts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/contacts")
                        .with(csrf())
                        .param("name", "Анна")
                        .param("surname", "Иванова"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void expiredSessionDoesNotCallApi() throws Exception {
        MockHttpSession session = new MockHttpSession();
        tokenStore.store(session, JwtTestTokens.expired("alice"), "alice",
                Instant.now().minusSeconds(60), false);

        mockMvc.perform(get("/contacts").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        assertThat(session.isInvalid()).isTrue();
        verifyNoInteractions(contactApiClient);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        tokenStore.store(session, TOKEN, "alice", Instant.now().plusSeconds(3600), false);
        return session;
    }
}
