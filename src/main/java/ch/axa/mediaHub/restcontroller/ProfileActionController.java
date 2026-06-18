package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.model.Account;
import ch.axa.mediaHub.model.ProfileStatus;
import ch.axa.mediaHub.model.UserProfile;
import ch.axa.mediaHub.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ProfileActionController {

    private final AccountRepository accountRepository;

    public ProfileActionController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @PostMapping("/api/me/profile/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verify(@PathVariable Long id) {
        UserProfile profile = loadProfile(id);
        if (profile.getStatus() != ProfileStatus.UNVERIFIED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot verify: profile is already " + profile.getStatus());
        }
        profile.setStatus(ProfileStatus.VERIFIED);
        accountRepository.save(profile.getAccount());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/api/me/profile/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> lock(@PathVariable Long id) {
        UserProfile profile = loadProfile(id);
        if (profile.getStatus() == ProfileStatus.LOCKED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot lock: profile is already LOCKED");
        }
        profile.setStatus(ProfileStatus.LOCKED);
        accountRepository.save(profile.getAccount());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/api/me/profile/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> unlock(@PathVariable Long id) {
        UserProfile profile = loadProfile(id);
        if (profile.getStatus() != ProfileStatus.LOCKED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot unlock: profile is not LOCKED, current status is " + profile.getStatus());
        }
        profile.setStatus(ProfileStatus.VERIFIED);
        accountRepository.save(profile.getAccount());
        return ResponseEntity.ok(profile);
    }

    private UserProfile loadProfile(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return account.getProfile();
    }
}