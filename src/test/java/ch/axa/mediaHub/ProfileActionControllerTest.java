package ch.axa.mediaHub;

import ch.axa.mediaHub.model.ProfileStatus;
import ch.axa.mediaHub.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileActionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;

    private String adminToken;   // user1 — ROLE_ADMIN, VERIFIED
    private String user2Token;   // user2 — ROLE_USER,  VERIFIED
    private Long   user3Id;      // user3 — ROLE_USER,  UNVERIFIED (Startzustand)

    @BeforeAll
    void setup() throws Exception {
        adminToken = signIn("user1", "password123");
        user2Token = signIn("user2", "123456");
        user3Id = accountRepository.findByUsername("user3").orElseThrow().getId();
    }

    // ── /verify ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void verify_unverified_succeeds() throws Exception {
        mockMvc.perform(post("/api/me/profile/{id}/verify", user3Id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        assertThat(accountRepository.findById(user3Id).orElseThrow().getProfile().getStatus())
                .isEqualTo(ProfileStatus.VERIFIED);
    }

    @Test @Order(2)
    void verify_alreadyVerified_returns409() throws Exception {
        // user3 is VERIFIED after Order(1)
        mockMvc.perform(post("/api/me/profile/{id}/verify", user3Id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ── /lock ────────────────────────────────────────────────────────────────

    @Test @Order(3)
    void lock_verifiedAccount_succeeds() throws Exception {
        // user3 is VERIFIED after Order(1)
        mockMvc.perform(post("/api/me/profile/{id}/lock", user3Id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test @Order(4)
    void lock_alreadyLocked_returns409() throws Exception {
        // user3 is LOCKED after Order(3)
        mockMvc.perform(post("/api/me/profile/{id}/lock", user3Id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ── /unlock ──────────────────────────────────────────────────────────────

    @Test @Order(5)
    void unlock_lockedAccount_succeeds() throws Exception {
        // user3 is LOCKED after Order(3)
        mockMvc.perform(post("/api/me/profile/{id}/unlock", user3Id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test @Order(6)
    void unlock_verifiedAccount_returns409() throws Exception {
        // user3 is VERIFIED after Order(5)
        mockMvc.perform(post("/api/me/profile/{id}/unlock", user3Id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ── auth guards ──────────────────────────────────────────────────────────

    @Test @Order(7)
    void nonAdmin_verify_returns403() throws Exception {
        mockMvc.perform(post("/api/me/profile/{id}/verify", user3Id)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test @Order(8)
    void nonAdmin_lock_returns403() throws Exception {
        mockMvc.perform(post("/api/me/profile/{id}/lock", user3Id)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test @Order(9)
    void nonAdmin_unlock_returns403() throws Exception {
        mockMvc.perform(post("/api/me/profile/{id}/unlock", user3Id)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    // ── 404 ─────────────────────────────────────────────────────────────────

    @Test @Order(10)
    void verify_unknownId_returns404() throws Exception {
        mockMvc.perform(post("/api/me/profile/9999/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test @Order(11)
    void lock_unknownId_returns404() throws Exception {
        mockMvc.perform(post("/api/me/profile/9999/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test @Order(12)
    void unlock_unknownId_returns404() throws Exception {
        mockMvc.perform(post("/api/me/profile/9999/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── no token ─────────────────────────────────────────────────────────────

    @Test @Order(13)
    void noToken_returns403() throws Exception {
        // Spring Security 6 returns 403 (not 401) when no AuthenticationEntryPoint is configured.
        // Http403ForbiddenEntryPoint is the default for endpoints without formLogin/httpBasic.
        mockMvc.perform(post("/api/me/profile/{id}/verify", user3Id))
                .andExpect(status().isForbidden());
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private String signIn(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}