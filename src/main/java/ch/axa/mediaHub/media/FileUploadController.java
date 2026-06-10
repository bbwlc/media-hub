package ch.axa.mediaHub.media;

import ch.axa.mediaHub.model.Account;
import ch.axa.mediaHub.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class FileUploadController {

    private final FileService fileService;
    private final AccountRepository accountRepository;
    private final FileShareService fileShareService;

    public FileUploadController(FileService fileService,
                                AccountRepository accountRepository,
                                FileShareService fileShareService) {
        this.fileService = fileService;
        this.accountRepository = accountRepository;
        this.fileShareService = fileShareService;
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

        Optional<java.nio.file.Path> result = fileService.uploadFile(loggedInUser, file);
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // If it's an image, update the profile picture
        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
            accountRepository.findByUsername(loggedInUser).ifPresent(account -> {
                account.setProfilePicture(file.getOriginalFilename());
                accountRepository.save(account);
            });
        }

        return ResponseEntity.ok(result.get());
    }

    @GetMapping("/download/{username}")
    public ResponseEntity<?> downloadFromUserFolder(
            @PathVariable String username,
            @RequestParam("file") String filename) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String loggedInUser = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        boolean allowed = username.equals(loggedInUser)
                || isAdmin
                || fileShareService.canAccess(username, filename, loggedInUser);

        if (!allowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You are not allowed to access this file.");
        }

        Optional<byte[]> fileData = fileService.downloadFile(username, filename);
        if (fileData.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Datei nicht gefunden.");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(fileData.get());
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> listMyFiles() {
        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return ResponseEntity.ok(fileService.listFiles(loggedInUser));
    }

    @GetMapping("/users/{username}/avatar")
    public ResponseEntity<?> getAvatar(@PathVariable String username) {
        Optional<Account> accountOpt = accountRepository.findByUsername(username);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");
        }
        String profilePicture = accountOpt.get().getProfilePicture();
        if (profilePicture == null || profilePicture.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No avatar set.");
        }
        Optional<byte[]> fileData = fileService.downloadFile(username, profilePicture);
        if (fileData.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Avatar file not found.");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"" + profilePicture + "\"")
                .body(fileData.get());
    }
}