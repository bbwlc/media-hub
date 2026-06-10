package ch.axa.mediaHub.media;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class FileService {

    public Optional<Path> uploadFile(String username, MultipartFile file) {

        try {
            String basePath = System.getProperty("user.dir");
            Path userFolder = Paths.get(basePath, "uploads", username);

            Files.createDirectories(userFolder);
            Path filePath = userFolder.resolve(Objects.requireNonNull(file.getOriginalFilename()));
            file.transferTo(filePath.toFile());

            return Optional.of(filePath);
        } catch (IOException | NullPointerException e) {
            Logger.getAnonymousLogger().log(Level.WARNING, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<byte[]> downloadFile(String username, String filename) {
        try {
            String basePath = System.getProperty("user.dir");
            Path filePath = Paths.get(basePath, "uploads", username, filename);

            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                return Optional.of(Files.readAllBytes(filePath));
            }
        } catch (IOException e) {
            Logger.getAnonymousLogger().log(Level.WARNING, e.getMessage(), e);
        }
        return Optional.empty();
    }
}
