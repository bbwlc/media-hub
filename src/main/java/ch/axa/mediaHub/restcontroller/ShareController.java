package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.media.FileService;
import ch.axa.mediaHub.model.SharedFile;
import ch.axa.mediaHub.model.authentication.ShareRequestDto;
import ch.axa.mediaHub.repository.SharedFileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ShareController {

    private final SharedFileRepository sharedFileRepository;
    private final FileService fileService;

    public ShareController(SharedFileRepository sharedFileRepository, FileService fileService) {
        this.sharedFileRepository = sharedFileRepository;
        this.fileService = fileService;
    }

    @PostMapping("/share/{filename}")
    public ResponseEntity<?> shareFile(
            @PathVariable String filename,
            @RequestBody(required = false) ShareRequestDto dto) {

        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // Validate file exists
        List<String> myFiles = fileService.listFiles(loggedInUser);
        if (!myFiles.contains(filename)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found.");
        }

        String sharedWith = (dto != null) ? dto.sharedWith() : null;

        // Check for duplicate
        if (sharedWith == null) {
            if (sharedFileRepository.existsPublicShare(loggedInUser, filename)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Already shared publicly.");
            }
        } else {
            if (sharedFileRepository.existsByOwnerAndFilenameAndSharedWith(loggedInUser, filename, sharedWith)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Already shared with " + sharedWith + ".");
            }
        }

        SharedFile sharedFile = new SharedFile();
        sharedFile.setOwner(loggedInUser);
        sharedFile.setFilename(filename);
        sharedFile.setSharedWith(sharedWith);
        sharedFileRepository.save(sharedFile);

        return ResponseEntity.status(HttpStatus.CREATED).body(sharedFile);
    }

    @GetMapping("/share")
    public ResponseEntity<List<SharedFile>> getMyShares() {
        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return ResponseEntity.ok(sharedFileRepository.findByOwner(loggedInUser));
    }

    @GetMapping("/shared")
    public ResponseEntity<List<SharedFile>> getSharedWithMe() {
        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return ResponseEntity.ok(sharedFileRepository.findAccessibleBy(loggedInUser));
    }

    @GetMapping("/shared/{ownerUsername}")
    public ResponseEntity<?> downloadSharedFile(
            @PathVariable String ownerUsername,
            @RequestParam("file") String filename) {

        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        if (!sharedFileRepository.canAccess(ownerUsername, filename, loggedInUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You do not have access to this file.");
        }

        Optional<byte[]> fileData = fileService.downloadFile(ownerUsername, filename);
        if (fileData.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found.");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(fileData.get());
    }

    @DeleteMapping("/share/{filename}")
    public ResponseEntity<?> revokeShare(
            @PathVariable String filename,
            @RequestBody(required = false) ShareRequestDto dto) {

        String loggedInUser = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        String sharedWith = (dto != null) ? dto.sharedWith() : null;

        if (sharedWith == null) {
            sharedFileRepository.deletePublicShare(loggedInUser, filename);
        } else {
            sharedFileRepository.deleteByOwnerAndFilenameAndSharedWith(loggedInUser, filename, sharedWith);
        }

        return ResponseEntity.ok().build();
    }
}