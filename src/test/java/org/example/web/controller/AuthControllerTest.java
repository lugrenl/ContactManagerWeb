package org.example.web.controller;

import org.example.web.client.ApiException;
import org.example.web.client.AuthApiClient;
import org.example.web.dto.JwtResponse;
import org.example.web.session.SessionTokenStore;
import org.example.web.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String USERNAME = "alice";
    private static final String PASSWORD = "password123";
    private static final String EMAIL = "alice@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenStore tokenStore;

    @MockitoBean
    private AuthApiClient authApiClient;

    @Test
    void loginStoresTokenAndChangesSessionId() throws Exception {
        String token = JwtTestTokens.valid(USERNAME, 3600);
        when(authApiClient.login(USERNAME, PASSWORD)).thenReturn(new JwtResponse(token));
        when(authApiClient.isAdmin(token)).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        String initialSessionId = session.getId();

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("name", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(session.getId()).isNotEqualTo(initialSessionId);
        assertThat(tokenStore.getToken(session)).contains(token);
        assertThat(tokenStore.getUsername(session)).contains(USERNAME);
        assertThat(tokenStore.isAdmin(session)).isFalse();
        assertThat(tokenStore.getExpiresAt(session)).isPresent();
    }

    @Test
    void loginUnauthorizedDoesNotCreateAuthenticatedSession() throws Exception {
        when(authApiClient.login(USERNAME, PASSWORD))
                .thenThrow(new ApiException(401, "Неверное имя пользователя или пароль."));

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("name", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("Неверное имя пользователя или пароль.")))
                .andExpect(content().string(containsString("value=\"alice\"")))
                .andExpect(content().string(not(containsString(PASSWORD))));

        assertThat(tokenStore.getToken(session)).isEmpty();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void registrationStoresRegularUserSession() throws Exception {
        String token = JwtTestTokens.valid(USERNAME, 3600);
        when(authApiClient.register(USERNAME, PASSWORD, EMAIL)).thenReturn(new JwtResponse(token));

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/register")
                        .session(session)
                        .with(csrf())
                        .param("name", USERNAME)
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(tokenStore.getToken(session)).contains(token);
        assertThat(tokenStore.isAdmin(session)).isFalse();
        verify(authApiClient, never()).isAdmin(anyString());
    }

    @Test
    void registrationConflictIsDisplayedWithoutLosingInput() throws Exception {
        when(authApiClient.register(USERNAME, PASSWORD, EMAIL))
                .thenThrow(new ApiException(409, "Пользователь с таким именем уже существует."));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("name", USERNAME)
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(content().string(containsString("Пользователь с таким именем уже существует.")))
                .andExpect(content().string(containsString("value=\"alice\"")))
                .andExpect(content().string(containsString("value=\"alice@example.com\"")))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void normalUserWithoutAdminRightsKeepsSession() throws Exception {
        String token = JwtTestTokens.valid(USERNAME, 3600);
        when(authApiClient.login(USERNAME, PASSWORD)).thenReturn(new JwtResponse(token));
        when(authApiClient.isAdmin(token)).thenReturn(false);

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("name", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/"));

        assertThat(tokenStore.getToken(session)).contains(token);
        assertThat(tokenStore.isAdmin(session)).isFalse();
        assertThat(session.isInvalid()).isFalse();

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(USERNAME)))
                .andExpect(content().string(not(containsString("Администратор"))));
    }

    @Test
    void tokenIsNotRenderedInHtml() throws Exception {
        String token = JwtTestTokens.valid(USERNAME, 3600);
        when(authApiClient.login(USERNAME, PASSWORD)).thenReturn(new JwtResponse(token));
        when(authApiClient.isAdmin(token)).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        MvcResult login = mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("name", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(authenticatedSession).isNotNull();

        mockMvc.perform(get("/").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(not(containsString(token))))
                .andExpect(content().string(not(containsString("jwt-token"))))
                .andExpect(content().string(containsString(USERNAME)));
    }

    @Test
    void logoutCallsApiAndInvalidatesSession() throws Exception {
        String token = JwtTestTokens.valid(USERNAME, 3600);
        MockHttpSession session = authenticatedSession(token, false);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?loggedOut"));

        verify(authApiClient).logout(token);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutInvalidatesSessionEvenWhenApiIsUnavailable() throws Exception {
        String token = JwtTestTokens.valid(USERNAME, 3600);
        doThrow(new ApiException(0, "Сервер API недоступен.")).when(authApiClient).logout(token);
        MockHttpSession session = authenticatedSession(token, false);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logoutWarning"));

        verify(authApiClient).logout(token);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/login")
                        .param("name", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authApiClient);
    }

    @Test
    void authPagesContainCsrfTokens() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        verifyNoInteractions(authApiClient);
    }

    @Test
    void expiredSessionIsRejectedAndCleared() throws Exception {
        MockHttpSession session = new MockHttpSession();
        tokenStore.store(session, JwtTestTokens.expired(USERNAME), USERNAME,
                Instant.now().minusSeconds(60), false);

        mockMvc.perform(get("/contacts").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void protectedPageWithoutSessionRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/contacts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void adminPageIsDeniedForRegularUser() throws Exception {
        MockHttpSession session = authenticatedSession(JwtTestTokens.valid(USERNAME, 3600), false);

        mockMvc.perform(get("/admin/users").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?accessDenied"));
    }

    private MockHttpSession authenticatedSession(String token, boolean admin) {
        MockHttpSession session = new MockHttpSession();
        tokenStore.store(session, token, USERNAME, Instant.now().plusSeconds(3600), admin);
        return session;
    }
}
