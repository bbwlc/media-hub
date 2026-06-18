package ch.axa.mediaHub;

import ch.axa.mediaHub.jwt.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegisterJwtWorkflowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean MailService mailService;

    private String capturedRegToken;    // gespeichert in TC-02.1, genutzt ab TC-02.5
    private String authTokenAfterConfirm; // gespeichert in TC-02.5, genutzt in TC-02.8

    // --- TC-02.1 ---
    @Test @Order(1)
    void tc01_successfulRegistration_returns202() throws Exception {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"email\":\"newuser@example.com\",\"password\":\"sicher123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());

        // Token aus MailService-Stub abgreifen (statt Konsole ablesen)
        verify(mailService).sendRegistrationVerification(
                eq("newuser@example.com"), tokenCaptor.capture());
        capturedRegToken = tokenCaptor.getValue();
        assertThat(capturedRegToken).isNotBlank();
        assertThat(capturedRegToken.split("\\.")).hasSize(3); // gültiger JWT
    }

    // --- TC-02.2 ---
    @Test @Order(2)
    void tc02_usernameAlreadyTaken_returns409() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user1\",\"email\":\"other@example.com\",\"password\":\"sicher123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // --- TC-02.3 ---
    @Test @Order(3)
    void tc03_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"email\":\"keineemail\",\"password\":\"sicher123\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- TC-02.4 ---
    @Test @Order(4)
    void tc04_emptyPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"email\":\"test@example.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- TC-02.5 ---
    @Test @Order(5)
    void tc05_validConfirmLink_returns201WithAuthJwt() throws Exception {
        assertThat(capturedRegToken).as("Token aus TC-02.1 fehlt — in Reihenfolge ausführen").isNotBlank();

        String response = mockMvc.perform(get("/auth/register/confirm/{token}", capturedRegToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();

        authTokenAfterConfirm = objectMapper.readTree(response).get("token").asText();
        assertThat(authTokenAfterConfirm.split("\\.")).hasSize(3);
    }

    // --- TC-02.6 ---
    @Test @Order(6)
    void tc06_confirmLinkUsedTwice_returns409() throws Exception {
        assertThat(capturedRegToken).as("Token aus TC-02.1 fehlt").isNotBlank();

        // Account existiert bereits (nach TC-02.5) → UsernameAlreadyExistsException
        // → GlobalExceptionHandler → 409 Conflict (semantisch korrekt: Username ist vergeben)
        mockMvc.perform(get("/auth/register/confirm/{token}", capturedRegToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    // --- TC-02.7 ---
    @Test @Order(7)
    void tc07_manipulatedToken_returns404() throws Exception {
        // Signatur-Teil verfälscht → JwtException → TokenNotFoundException → 404
        String manipulated = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.invalidsignature";

        mockMvc.perform(get("/auth/register/confirm/{token}", manipulated))
                .andExpect(status().isNotFound());
    }

    // --- TC-02.8 ---
    @Test @Order(8)
    void tc08_authJwtAfterConfirm_accessesProtectedEndpoint() throws Exception {
        assertThat(authTokenAfterConfirm).as("Auth-Token aus TC-02.5 fehlt").isNotBlank();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + authTokenAfterConfirm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }
}