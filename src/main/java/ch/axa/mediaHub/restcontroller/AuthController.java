package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.jwt.AuthenticationService;
import ch.axa.mediaHub.model.authentication.TokenData;
import ch.axa.mediaHub.repository.AccountRepository;
import ch.axa.mediaHub.model.authentication.AuthenticationData;
import ch.axa.mediaHub.model.Account;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Optional;

@RestController
@CrossOrigin("*") // TODO: change for production
public class AuthController {

    private final AccountRepository accountRepository;
    private final AuthenticationService authenticationService;

    @Autowired
    public AuthController(AccountRepository accountRepository,
                          AuthenticationService authenticationService) {
        this.accountRepository = accountRepository;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/signIn")
    public ResponseEntity<?> signIn(@RequestBody AuthenticationData authData) {
        Optional<TokenData> tokenData = authenticationService.authenticate(
                                            authData.getUsername(),
                                            authData.getPassword());
        if (tokenData.isPresent()) {
            return ResponseEntity.ok(tokenData.get());
        } else {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("invalid username or password!");
        }
    }


    @PostMapping("/signOut")
    public ResponseEntity<?> signOut() {
        Authentication auth = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            Optional<Account> accountOpt = accountRepository.findByUsername(username);

            if (accountOpt.isPresent()) {
                Account account = accountOpt.get();
                account.setToken(null);
                accountRepository.save(account);
                return ResponseEntity.ok().build();
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
