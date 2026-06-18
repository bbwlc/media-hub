package ch.axa.mediaHub;

import ch.axa.mediaHub.model.Account;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileStatusFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;

    private String adminToken;  // user1 — ROLE_ADMIN
    private String user2Token;  // user2 — ROLE_USER

    @BeforeAll
    void obtainTokens() throws Exception {
        adminToken = signIn("user1", "password123");
        user2Token = signIn("user2", "123456");
    }

    // --- TC-04.1 ---
    @Test
    @Order(1)
    void tc01_unverifiedAccountCanLogin() throws Exception {
        mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user3\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    // --- TC-04.2 ---
    @Test
    @Order(2)
    void tc02_adminSetsUnverifiedToVerified() throws Exception {
        mockMvc.perform(patch("/users/user3/status")
                        .param("status", "VERIFIED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user3"))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    // --- TC-04.3 ---
    @Test
    @Order(3)
    void tc03_adminSetsVerifiedToLocked() throws Exception {
        mockMvc.perform(patch("/users/user3/status")
                        .param("status", "LOCKED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user3"))
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    // --- TC-04.4 ---
    @Test
    @Order(4)
    void tc04_lockedAccountCannotLogin() throws Exception {
        mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user3\",\"password\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- TC-04.5 ---
    @Test
    @Order(5)
    void tc05_adminUnlocksAccount() throws Exception {
        mockMvc.perform(patch("/users/user3/status")
                        .param("status", "VERIFIED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    // --- TC-04.6 ---
    @Test
    @Order(6)
    void tc06_loginPossibleAfterUnlock() throws Exception {
        mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user3\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    // --- TC-04.7 ---
    @Test
    @Order(7)
    void tc07_nonAdminCannotChangeStatus() throws Exception {
        mockMvc.perform(patch("/users/user3/status")
                        .param("status", "LOCKED")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());

        Account user3 = accountRepository.findByUsername("user3").orElseThrow();
        assertThat(user3.getProfile().getStatus()).isNotEqualTo(ProfileStatus.LOCKED);
    }

    // --- TC-04.8 ---
    @Test
    @Order(8)
    void tc08_unknownUserReturns404() throws Exception {
        mockMvc.perform(patch("/users/ghost/status")
                        .param("status", "VERIFIED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // --- TC-04.9 ---
    @Test
    @Order(9)
    void tc09_newAccountDefaultStatusIsUnverified() {
        // Status liegt jetzt auf UserProfile — Default-Wert prüfen
        ch.axa.mediaHub.model.UserProfile freshProfile = new ch.axa.mediaHub.model.UserProfile();
        assertThat(freshProfile.getStatus()).isEqualTo(ProfileStatus.UNVERIFIED);
    }

    // --- helper ---
    private String signIn(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/signIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}