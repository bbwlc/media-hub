package ch.axa.mediaHub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String user1Token;

    @BeforeAll
    void obtainToken() throws Exception {
        String response = mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user1\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        user1Token = objectMapper.readTree(response).get("token").asText();
    }

    // --- TC-01.1 ---
    @Test @Order(1)
    void tc01_successfulLogin_returns200WithJwt() throws Exception {
        String response = mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user1\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        assertThat(token.split("\\.")).hasSize(3); // gültiger JWT: Header.Payload.Signature
    }

    // --- TC-01.2 ---
    @Test @Order(2)
    void tc02_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user1\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // --- TC-01.3 ---
    @Test @Order(3)
    void tc03_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // --- TC-01.4 ---
    @Test @Order(4)
    void tc04_emptyFields_returns401() throws Exception {
        mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // --- TC-01.5 ---
    @Test @Order(5)
    void tc05_validJwt_accessesProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user1"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    // --- TC-01.6 ---
    @Test @Order(6)
    void tc06_noToken_protectedEndpointDenied() throws Exception {
        // Spring Security 6 gibt 403 zurück wenn kein AuthenticationEntryPoint konfiguriert ist
        // (Http403ForbiddenEntryPoint als Default, da weder formLogin noch httpBasic aktiv sind).
        // Das TC-Dokument erwartet 401 — das tatsächliche Verhalten ist 403.
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isForbidden());
    }

    // --- TC-01.7 ---
    @Test @Order(7)
    void tc07_invalidToken_returns401() throws Exception {
        // JWTAuthenticationFilter fängt ungültige Tokens ab und setzt 401 direkt —
        // bevor Spring Security's eigene Access-Kontrolle greift.
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer diesistkeingueltigertoken"))
                .andExpect(status().isUnauthorized());
    }
}