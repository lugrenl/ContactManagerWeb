package org.example.web.controller;

import org.example.web.client.ApiException;
import org.example.web.client.ContactApiClient;
import org.example.web.session.SessionTokenStore;
import org.example.web.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ContactImportControllerTest {

    private static final String TOKEN = JwtTestTokens.valid("alice", 3600);
    private static final String CSV = "Иван,Иванов,ivan@example.com,+79990000000\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenStore tokenStore;

    @MockitoBean
    private ContactApiClient contactApiClient;

    @Test
    void importPageIsAvailableWithSessionAndContainsCsrfForm() throws Exception {
        mockMvc.perform(get("/contacts/import").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/import"))
                .andExpect(content().string(containsString("<h1>Импорт CSV</h1>")))
                .andExpect(content().string(containsString("action=\"/contacts/import\"")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("type=\"file\"")))
                .andExpect(content().string(containsString("name=\"file\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("data-csrf-header=\"X-CSRF-TOKEN\"")))
                .andExpect(content().string(containsString("data-csrf-token=")))
                .andExpect(content().string(containsString("data-max-file-size=\"1048576\"")))
                .andExpect(content().string(containsString("1 MB")))
                .andExpect(content().string(not(containsString(TOKEN))))
                .andExpect(content().string(not(containsString("jwt-token"))));

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void importAcceptsCsrfTokenFromConfiguredHeader() throws Exception {
        MockHttpSession session = authenticatedSession();
        MvcResult page = mockMvc.perform(get("/contacts/import").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String html = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(
                        "data-csrf-header=\"([^\"]+)\"[^>]*data-csrf-token=\"([^\"]+)\"")
                .matcher(html);
        assertThat(matcher.find()).isTrue();
        String headerName = matcher.group(1);
        String token = matcher.group(2);

        mockMvc.perform(multipart("/contacts/import")
                        .file(csvFile(CSV))
                        .session(session)
                        .header(headerName, token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts?importSuccess=true"));
    }

    @Test
    void importPageWithoutSessionRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/contacts/import"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void importPageShowsSizeErrorFromQuery() throws Exception {
        mockMvc.perform(get("/contacts/import?importError=size").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/import"))
                .andExpect(content().string(containsString(
                        "Файл превышает допустимый размер. Максимум — 1 MB.")));
    }

    @Test
    void successfulImportSendsFileAndRedirectsToContacts() throws Exception {
        MockMultipartFile file = csvFile(CSV);

        mockMvc.perform(multipart("/contacts/import")
                        .file(file)
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts?importSuccess=true"));

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(contactApiClient).importCsv(eq(TOKEN), captor.capture());
        MultipartFile sent = captor.getValue();
        assertThat(sent.getOriginalFilename()).isEqualTo("contacts.csv");
        assertThat(sent.getBytes()).isEqualTo(CSV.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void contactsListShowsImportLinkAndSuccessMessage() throws Exception {
        when(contactApiClient.list(TOKEN)).thenReturn(List.of());

        mockMvc.perform(get("/contacts?importSuccess=true").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/list"))
                .andExpect(content().string(containsString("Импорт CSV завершён.")))
                .andExpect(content().string(containsString("href=\"/contacts/import\"")));
    }

    @Test
    void invalidCsvApiResponseShowsImportError() throws Exception {
        doThrow(new ApiException(400, "Invalid CSV"))
                .when(contactApiClient).importCsv(eq(TOKEN), any(MultipartFile.class));

        mockMvc.perform(multipart("/contacts/import")
                        .file(csvFile(CSV))
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts/import?importError=invalid"));
    }

    @Test
    void tooLargeApiResponseShowsSizeError() throws Exception {
        doThrow(new ApiException(413, "Too large"))
                .when(contactApiClient).importCsv(eq(TOKEN), any(MultipartFile.class));

        mockMvc.perform(multipart("/contacts/import")
                        .file(csvFile(CSV))
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts/import?importError=size"));
    }

    @Test
    void unavailableApiShowsUnavailableError() throws Exception {
        doThrow(new ApiException(0, "Unavailable"))
                .when(contactApiClient).importCsv(eq(TOKEN), any(MultipartFile.class));

        mockMvc.perform(multipart("/contacts/import")
                        .file(csvFile(CSV))
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts/import?importError=unavailable"));
    }

    @Test
    void unauthorizedImportClearsSessionAndRedirectsToLogin() throws Exception {
        doThrow(new ApiException(401, "Unauthorized"))
                .when(contactApiClient).importCsv(eq(TOKEN), any(MultipartFile.class));
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(multipart("/contacts/import")
                        .file(csvFile(CSV))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void emptyFileIsRejectedBeforeApiCall() throws Exception {
        mockMvc.perform(multipart("/contacts/import")
                        .file(new MockMultipartFile("file", "contacts.csv", "text/csv", new byte[0]))
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts/import?importError=empty"));

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void missingFileIsRejectedBeforeApiCall() throws Exception {
        mockMvc.perform(multipart("/contacts/import")
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts/import?importError=empty"));

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void oversizedFileIsRejectedBeforeApiCall() throws Exception {
        byte[] oversized = new byte[1024 * 1024 + 1];

        mockMvc.perform(multipart("/contacts/import")
                        .file(new MockMultipartFile("file", "contacts.csv", "text/csv", oversized))
                        .session(authenticatedSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts/import?importError=size"));

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void importWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(multipart("/contacts/import")
                        .file(csvFile(CSV))
                        .session(authenticatedSession()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(contactApiClient);
    }

    @Test
    void importErrorPageDoesNotLeakTokenOrFileContent() throws Exception {
        String secretCsv = "SecretPerson,SecretSurname,secret@example.com,12345\n";
        doThrow(new ApiException(400, "Invalid CSV"))
                .when(contactApiClient).importCsv(eq(TOKEN), any(MultipartFile.class));
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(multipart("/contacts/import")
                        .file(csvFile(secretCsv))
                        .session(session)
                        .with(csrf()))
                .andExpect(redirectedUrl("/contacts/import?importError=invalid"));

        mockMvc.perform(get("/contacts/import?importError=invalid").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(TOKEN))))
                .andExpect(content().string(not(containsString("SecretPerson"))))
                .andExpect(content().string(not(containsString("secret@example.com"))));
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        tokenStore.store(session, TOKEN, "alice", Instant.now().plusSeconds(3600), false);
        return session;
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "contacts.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
