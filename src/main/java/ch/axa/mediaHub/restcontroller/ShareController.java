package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.media.FileShareService;
import ch.axa.mediaHub.media.FileService;
import ch.axa.mediaHub.model.SharedFile;
import ch.axa.mediaHub.model.authentication.ShareRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ShareController {

    private final FileShareService fileShareService;
    private final FileService fileService;

    public ShareController(FileShareService fileShareService, FileService fileService) {
        this.fileShareService = fileShareService;
        this.fileService = fileService;
    }

    @PostMapping("/share/{filename}")
    public ResponseEntity<?> shareFile(
            @PathVariable String filename,
            @RequestBody(required = false) ShareRequestDto dto) {

        String loggedInUser = SecurityContextHolder.getContext().getAuthentication().getName();
        String sharedWith = (dto != null) ? dto.sharedWith() : null;

        try {
            SharedFile result = fileShareService.share(loggedInUser, filename, sharedWith);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @DeleteMapping("/share/{filename}")
    public ResponseEntity<?> revokeShare(
            @PathVariable String filename,
            @RequestBody(required = false) ShareRequestDto dto) {

        String loggedInUser = SecurityContextHolder.getContext().getAuthentication().getName();
        String sharedWith = (dto != null) ? dto.sharedWith() : null;

        fileShareService.revoke(loggedInUser, filename, sharedWith);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/share")
    public ResponseEntity<List<SharedFile>> getMyShares() {
        String loggedInUser = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(fileShareService.getMyShares(loggedInUser));
    }

    @GetMapping("/shared")
    public ResponseEntity<List<SharedFile>> getSharedWithMe() {
        String loggedInUser = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(fileShareService.getSharedWithMe(loggedInUser));
    }

    @GetMapping("/shared/{ownerUsername}")
    public ResponseEntity<?> downloadSharedFile(
            @PathVariable String ownerUsername,
            @RequestParam("file") String filename) {

        String loggedInUser = SecurityContextHolder.getContext().getAuthentication().getName();

        if (!fileShareService.canAccess(ownerUsername, filename, loggedInUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have access to this file.");
        }

        Optional<byte[]> fileData = fileService.downloadFile(ownerUsername, filename);
        if (fileData.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found.");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(fileData.get());
    }
}