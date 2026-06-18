package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.model.Account;
import ch.axa.mediaHub.model.ProfileStatus;
import ch.axa.mediaHub.repository.AccountRepository;
import ch.axa.mediaHub.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "http://localhost:3000")
public class UserController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public UserController(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Account>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PatchMapping("/{username}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(
            @PathVariable String username,
            @RequestParam ProfileStatus status) {

        return accountRepository.findByUsername(username)
                .map(account -> {
                    account.setStatus(status);
                    accountRepository.save(account);
                    return ResponseEntity.ok(Map.of(
                            "username", account.getUsername(),
                            "status",   account.getStatus()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
