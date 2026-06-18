package ch.axa.mediaHub;

import ch.axa.mediaHub.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileUploadFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;

    private String user1Token;  // ROLE_ADMIN
    private String user2Token;  // ROLE_USER

    private static final String DOC_NAME   = "tc03_test.txt";
    private static final String IMAGE_NAME = "tc03_avatar.jpg";

    @BeforeAll
    void obtainTokens() throws Exception {
        user1Token = signIn("user1", "password123");
        user2Token = signIn("user2", "123456");
    }

    @AfterAll
    void cleanup() throws IOException {
        // Testdateien vom Dateisystem entfernen
        for (String user : new String[]{"user1", "user2"}) {
            Path folder = Paths.get(System.getProperty("user.dir"), "uploads", user);
            if (Files.exists(folder)) {
                Files.walk(folder)
                        .filter(p -> p.getFileName().toString().startsWith("tc03_"))
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { /* ignore */ } });
            }
        }
    }

    // --- TC-03.1 ---
    @Test @Order(1)
    void tc01_uploadDocument_returns200WithPathString() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", DOC_NAME, "text/plain", "Testinhalt".getBytes());

        String response = mockMvc.perform(multipart("/upload/user2").file(file)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Response ist ein Pfad-String (kein JSON-Objekt)
        assertThat(response).contains("uploads").contains(DOC_NAME);
    }

    // --- TC-03.2 ---
    @Test @Order(2)
    void tc02_uploadImage_setsProfilePicture() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", IMAGE_NAME, "image/jpeg", "FAKEJPEG".getBytes());

        mockMvc.perform(multipart("/upload/user2").file(image)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk());

        String profilePicture = accountRepository.findByUsername("user2")
                .orElseThrow().getProfile().getProfilePicture();
        assertThat(profilePicture).isEqualTo(IMAGE_NAME);
    }

    // --- TC-03.3 ---
    @Test @Order(3)
    void tc03_uploadToOtherUsersFolder_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", DOC_NAME, "text/plain", "Inhalt".getBytes());

        mockMvc.perform(multipart("/upload/user1").file(file)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    // --- TC-03.4 ---
    @Test @Order(4)
    void tc04_uploadWithoutToken_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", DOC_NAME, "text/plain", "Inhalt".getBytes());

        mockMvc.perform(multipart("/upload/user2").file(file))
                .andExpect(status().isForbidden());
    }

    // --- TC-03.5 ---
    @Test @Order(5)
    void tc05_downloadOwnFile_returns200() throws Exception {
        mockMvc.perform(get("/download/user2").param("file", DOC_NAME)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + DOC_NAME + "\""))
                .andExpect(content().string("Testinhalt"));
    }

    // --- TC-03.6 ---
    @Test @Order(6)
    void tc06_downloadOtherUsersFile_returns403() throws Exception {
        // user2 versucht auf user1s Ordner zuzugreifen
        mockMvc.perform(get("/download/user1").param("file", DOC_NAME)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    // --- TC-03.7 ---
    @Test @Order(7)
    void tc07_adminDownloadsOtherUsersFile_returns200() throws Exception {
        // user1 (ROLE_ADMIN) darf auf user2s Dateien zugreifen
        mockMvc.perform(get("/download/user2").param("file", DOC_NAME)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(content().string("Testinhalt"));
    }

    // --- TC-03.8 ---
    @Test @Order(8)
    void tc08_downloadNonExistentFile_returns404() throws Exception {
        mockMvc.perform(get("/download/user2").param("file", "nichtvorhanden.pdf")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    // --- TC-03.9 ---
    @Test @Order(9)
    void tc09_listFiles_returnsOnlyOwnFiles() throws Exception {
        String response = mockMvc.perform(get("/files")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String[] files = objectMapper.readValue(response, String[].class);
        assertThat(files).contains(DOC_NAME, IMAGE_NAME);
        // Keine Dateien anderer User enthalten
        for (String f : files) {
            assertThat(f).doesNotContain("user1");
        }
    }

    // --- TC-03.10 ---
    @Test @Order(10)
    void tc10_getAvatar_withoutToken_returns200WithContentType() throws Exception {
        // Avatar ist permitAll() — kein JWT nötig
        mockMvc.perform(get("/users/user2/avatar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"" + IMAGE_NAME + "\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));
    }

    // --- TC-03.11 ---
    @Test @Order(11)
    void tc11_getAvatar_noneSet_returns404() throws Exception {
        // user1 hat kein Profilbild gesetzt
        mockMvc.perform(get("/users/user1/avatar"))
                .andExpect(status().isNotFound());
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