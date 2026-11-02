package org.example.web.controller;

import org.example.web.client.ApiException;
import org.example.web.client.ContactApiClient;
import org.example.web.client.UserApiClient;
import org.example.web.dto.AdminContactDto;
import org.example.web.dto.UserDto;
import org.example.web.dto.UserUpdateRequest;
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
import static org.mockito.Mockito.times;
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
class AdminControllerTest {

    private static final String TOKEN = JwtTestTokens.valid("admin", 3600);
    private static final String PASSWORD = "newpassword123";

    private final UserDto admin = new UserDto(1, "admin", "admin@example.com", "ROLE_ADMIN");
    private final UserDto alice = new UserDto(5, "alice", "alice@example.com", "ROLE_USER");
    private final UserDto bob = new UserDto(2, "bob", "bob@example.com", "ROLE_USER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenStore tokenStore;

    @MockitoBean
    private UserApiClient userApiClient;

    @MockitoBean
    private ContactApiClient contactApiClient;

    @Test
    void adminCanOpenUsersList() throws Exception {
        when(userApiClient.list(TOKEN)).thenReturn(List.of(alice, admin));

        mockMvc.perform(get("/admin/users").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/list"))
                .andExpect(content().string(containsString("admin@example.com")))
                .andExpect(content().string(containsString("alice@example.com")))
                .andExpect(content().string(containsString("Администратор")))
                .andExpect(content().string(containsString("Пользователь")))
                .andExpect(content().string(containsString("href=\"/admin/users/5/edit\"")))
                .andExpect(content().string(containsString("href=\"/admin/contacts\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString(TOKEN))))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("Создать пользователя"))));
    }

    @Test
    void usersListDoesNotMaskApiFailureAsEmptyState() throws Exception {
        when(userApiClient.list(TOKEN))
                .thenThrow(new ApiException(500, "Сервис временно недоступен. Попробуйте позже."));

        mockMvc.perform(get("/admin/users").session(adminSession()))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("admin/error"))
                .andExpect(content().string(containsString("Сервис временно недоступен")))
                .andExpect(content().string(not(containsString("Пользователи не найдены."))));
    }

    @Test
    void usersListShowsEmptyState() throws Exception {
        when(userApiClient.list(TOKEN)).thenReturn(List.of());

        mockMvc.perform(get("/admin/users").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/list"))
                .andExpect(content().string(containsString("Пользователи не найдены.")));
    }

    @Test
    void regularUserCannotOpenAdminPages() throws Exception {
        MockHttpSession session = regularSession();

        mockMvc.perform(get("/admin/users").session(session))
                .andExpect(redirectedUrl("/?accessDenied"));
        mockMvc.perform(get("/admin/contacts").session(session))
                .andExpect(redirectedUrl("/?accessDenied"));
        mockMvc.perform(get("/admin/users/5/edit").session(session))
                .andExpect(redirectedUrl("/?accessDenied"));

        verifyNoInteractions(userApiClient, contactApiClient);
    }

    @Test
    void regularUserCannotPostAdminForms() throws Exception {
        MockHttpSession session = regularSession();

        mockMvc.perform(post("/admin/users/5/edit")
                        .session(session)
                        .with(csrf())
                        .param("name", "alice")
                        .param("email", "alice@example.com")
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/?accessDenied"));
        mockMvc.perform(post("/admin/users/5/delete")
                        .session(session)
                        .with(csrf()))
                .andExpect(redirectedUrl("/?accessDenied"));

        verifyNoInteractions(userApiClient, contactApiClient);
    }

    @Test
    void userWithoutSessionCannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin/contacts"))
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(userApiClient, contactApiClient);
    }

    @Test
    void editFormLoadsCorrectUserAndKeepsPasswordBlank() throws Exception {
        when(userApiClient.get(TOKEN, 5L)).thenReturn(alice);

        mockMvc.perform(get("/admin/users/5/edit").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/form"))
                .andExpect(content().string(containsString("action=\"/admin/users/5/edit\"")))
                .andExpect(content().string(containsString("value=\"alice\"")))
                .andExpect(content().string(containsString("value=\"alice@example.com\"")))
                .andExpect(content().string(containsString("value=\"ROLE_USER\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(not(containsString("name=\"password\" value="))))
                .andExpect(content().string(not(containsString(TOKEN))));

        verify(userApiClient).get(TOKEN, 5L);
    }

    @Test
    void updateSendsRequiredFieldsAndRole() throws Exception {
        when(userApiClient.update(eq(TOKEN), eq(5L), any(UserUpdateRequest.class))).thenReturn(alice);

        mockMvc.perform(post("/admin/users/5/edit")
                        .session(adminSession())
                        .with(csrf())
                        .param("name", "alice2")
                        .param("email", "alice2@example.com")
                        .param("role", "ROLE_ADMIN")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("infoMessage", "Пользователь обновлён."));

        ArgumentCaptor<UserUpdateRequest> captor = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userApiClient).update(eq(TOKEN), eq(5L), captor.capture());
        UserUpdateRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("alice2");
        assertThat(request.email()).isEqualTo("alice2@example.com");
        assertThat(request.role()).isEqualTo("ROLE_ADMIN");
        assertThat(request.password()).isEqualTo(PASSWORD);
    }

    @Test
    void updateWithoutRoleSendsNoRole() throws Exception {
        when(userApiClient.update(eq(TOKEN), eq(5L), any(UserUpdateRequest.class))).thenReturn(alice);

        mockMvc.perform(post("/admin/users/5/edit")
                        .session(adminSession())
                        .with(csrf())
                        .param("name", "alice")
                        .param("email", "alice@example.com")
                        .param("role", "")
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/admin/users"));

        ArgumentCaptor<UserUpdateRequest> captor = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userApiClient).update(eq(TOKEN), eq(5L), captor.capture());
        assertThat(captor.getValue().role()).isNull();
    }

    @Test
    void updateValidationErrorKeepsNonPasswordFieldsAndHidesPassword() throws Exception {
        when(userApiClient.update(eq(TOKEN), eq(5L), any(UserUpdateRequest.class)))
                .thenThrow(new ApiException(400, "Проверьте правильность заполнения полей.",
                        List.of("name: Username is required")));

        mockMvc.perform(post("/admin/users/5/edit")
                        .session(adminSession())
                        .with(csrf())
                        .param("name", "alice2")
                        .param("email", "alice2@example.com")
                        .param("role", "ROLE_USER")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/form"))
                .andExpect(content().string(containsString("Проверьте правильность заполнения полей.")))
                .andExpect(content().string(containsString("Username is required")))
                .andExpect(content().string(containsString("value=\"alice2\"")))
                .andExpect(content().string(containsString("value=\"alice2@example.com\"")))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void updateConflictKeepsNonPasswordFieldsAndHidesPassword() throws Exception {
        when(userApiClient.update(eq(TOKEN), eq(5L), any(UserUpdateRequest.class)))
                .thenThrow(new ApiException(409, "Пользователь с таким именем уже существует."));

        mockMvc.perform(post("/admin/users/5/edit")
                        .session(adminSession())
                        .with(csrf())
                        .param("name", "admin")
                        .param("email", "alice@example.com")
                        .param("role", "ROLE_USER")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/form"))
                .andExpect(content().string(containsString("Пользователь с таким именем уже существует.")))
                .andExpect(content().string(containsString("value=\"admin\"")))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void updateWithoutPasswordShowsValidationErrorBeforeApi() throws Exception {
        mockMvc.perform(post("/admin/users/5/edit")
                        .session(adminSession())
                        .with(csrf())
                        .param("name", "alice")
                        .param("email", "alice@example.com")
                        .param("role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/form"))
                .andExpect(content().string(containsString("Введите новый пароль.")))
                .andExpect(content().string(not(containsString(PASSWORD))));

        verifyNoInteractions(userApiClient, contactApiClient);
    }

    @Test
    void updateWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/admin/users/5/edit")
                        .session(adminSession())
                        .param("name", "alice")
                        .param("email", "alice@example.com")
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userApiClient, contactApiClient);
    }

    @Test
    void deleteOtherUserCallsDeleteAndRedirects() throws Exception {
        when(userApiClient.list(TOKEN)).thenReturn(List.of(admin));

        mockMvc.perform(post("/admin/users/2/delete")
                        .session(adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("infoMessage", "Пользователь удалён."));

        verify(userApiClient).delete(TOKEN, 2L);
    }

    @Test
    void deleteOwnAccountInvalidatesSession() throws Exception {
        when(userApiClient.list(TOKEN)).thenReturn(List.of(admin));
        MockHttpSession session = adminSession();

        mockMvc.perform(post("/admin/users/1/delete")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?selfDeleted=true"));

        verify(userApiClient).delete(TOKEN, 1L);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void deleteNotFoundRedirectsWithMessage() throws Exception {
        when(userApiClient.list(TOKEN)).thenReturn(List.of(admin));
        doThrow(new ApiException(404, "Пользователь не найден."))
                .when(userApiClient).delete(TOKEN, 2L);

        mockMvc.perform(post("/admin/users/2/delete")
                        .session(adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("errorMessage", "Пользователь не найден."));
    }

    @Test
    void deleteWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/admin/users/2/delete").session(adminSession()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userApiClient, contactApiClient);
    }

    @Test
    void adminContactsMapsOwnerByIdWithoutPerContactRequests() throws Exception {
        AdminContactDto anna = new AdminContactDto(10, "Анна", "Иванова",
                "anna@example.com", "+79990000000", 2);
        AdminContactDto unknownOwner = new AdminContactDto(11, "Иван", "Петров",
                null, null, 99);
        when(userApiClient.list(TOKEN)).thenReturn(List.of(admin, bob));
        when(contactApiClient.listAllContacts(TOKEN)).thenReturn(List.of(unknownOwner, anna));

        mockMvc.perform(get("/admin/contacts").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/contacts"))
                .andExpect(content().string(containsString("Анна")))
                .andExpect(content().string(containsString("bob (ID 2)")))
                .andExpect(content().string(containsString("ID 99")))
                .andExpect(content().string(not(containsString("Редактировать"))))
                .andExpect(content().string(not(containsString("Удалить"))))
                .andExpect(content().string(not(containsString(TOKEN))));

        verify(userApiClient, times(1)).list(TOKEN);
        verify(contactApiClient, times(1)).listAllContacts(TOKEN);
    }

    @Test
    void contactsPageDoesNotMaskUserListFailureAsEmptyState() throws Exception {
        when(userApiClient.list(TOKEN))
                .thenThrow(new ApiException(500, "Сервис временно недоступен. Попробуйте позже."));

        mockMvc.perform(get("/admin/contacts").session(adminSession()))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("admin/error"))
                .andExpect(content().string(containsString("Сервис временно недоступен")))
                .andExpect(content().string(not(containsString("Контакты не найдены."))));

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void contactsPageDoesNotMaskContactListFailure() throws Exception {
        when(userApiClient.list(TOKEN)).thenReturn(List.of(admin));
        when(contactApiClient.listAllContacts(TOKEN))
                .thenThrow(new ApiException(503, "Сервис временно недоступен. Попробуйте позже."));

        mockMvc.perform(get("/admin/contacts").session(adminSession()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(view().name("admin/error"))
                .andExpect(content().string(not(containsString("Контакты не найдены."))));
    }

    @Test
    void editNotFoundShowsAdminErrorPage() throws Exception {
        when(userApiClient.get(TOKEN, 99L))
                .thenThrow(new ApiException(404, "Пользователь не найден."));

        mockMvc.perform(get("/admin/users/99/edit").session(adminSession()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("admin/error"))
                .andExpect(content().string(containsString("Пользователь не найден")));
    }

    @Test
    void unauthorizedAdminApiClearsSession() throws Exception {
        when(userApiClient.list(TOKEN))
                .thenThrow(new ApiException(401, "Сессия недействительна или недостаточно прав."));
        MockHttpSession session = adminSession();

        mockMvc.perform(get("/admin/users").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void forbiddenAdminApiDoesNotClearSession() throws Exception {
        when(userApiClient.list(TOKEN))
                .thenThrow(new ApiException(403, "Недостаточно прав для административной операции."));
        MockHttpSession session = adminSession();

        mockMvc.perform(get("/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(view().name("admin/error"))
                .andExpect(content().string(containsString("Доступ запрещён")));

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void adminNavigationIsVisibleOnlyForAdmin() throws Exception {
        mockMvc.perform(get("/").session(adminSession()))
                .andExpect(content().string(containsString("Администрирование")))
                .andExpect(content().string(containsString("href=\"/admin/users\"")));

        mockMvc.perform(get("/").session(regularSession()))
                .andExpect(content().string(not(containsString("Администрирование"))))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))));
    }

    @Test
    void selfDeletedLoginMessageIsShown() throws Exception {
        mockMvc.perform(get("/login?selfDeleted=true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ваша учётная запись удалена.")));
    }

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        tokenStore.store(session, TOKEN, "admin", Instant.now().plusSeconds(3600), true);
        return session;
    }

    private MockHttpSession regularSession() {
        MockHttpSession session = new MockHttpSession();
        tokenStore.store(session, JwtTestTokens.valid("alice", 3600), "alice",
                Instant.now().plusSeconds(3600), false);
        return session;
    }
}
