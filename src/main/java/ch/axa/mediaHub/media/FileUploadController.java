package ch.axa.mediaHub.media;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@RestController
public class FileUploadController {
    private final FileService fileService;

    public FileUploadController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload/{username}")
    public ResponseEntity<?> uploadToUserFolder(
            @PathVariable String username,
            @RequestParam("file") MultipartFile file) {

        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        if (!username.equals(loggedInUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You are only allowed to upload to your own folder.");
        }

        return fileService.uploadFile(loggedInUser, file)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    @GetMapping("/download/{username}")
    public ResponseEntity<?> downloadFromUserFolder(
            @PathVariable String username,
            @RequestParam("file") String filename) {

        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        if (!username.equals(loggedInUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You are only allowed to access your own folder.");
        }

        Optional<byte[]> fileData = fileService.downloadFile(loggedInUser, filename);
        fileData.ifPresent(bytes -> ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(bytes));
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Datei nicht gefunden.");
    }
}