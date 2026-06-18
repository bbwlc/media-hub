package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.jwt.AccountService;
import ch.axa.mediaHub.jwt.AuthenticationService;
import ch.axa.mediaHub.jwt.JWTUtil;
import ch.axa.mediaHub.jwt.RegistrierungsService;
import ch.axa.mediaHub.jwt.TokenExpiredException;
import ch.axa.mediaHub.jwt.TokenNotFoundException;
import ch.axa.mediaHub.jwt.UsernameAlreadyExistsException;
import ch.axa.mediaHub.model.Account;
import ch.axa.mediaHub.model.authentication.PendingRegistration;
import ch.axa.mediaHub.model.authentication.AuthenticationData;
import ch.axa.mediaHub.model.authentication.RegisterDto;
import ch.axa.mediaHub.model.authentication.TokenData;
import ch.axa.mediaHub.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class AuthController {

    private final AccountRepository accountRepository;
    private final AuthenticationService authenticationService;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    private final RegistrierungsService registrierungsService;
    private final AccountService accountService;

    @Autowired
    public AuthController(AccountRepository accountRepository,
                          AuthenticationService authenticationService,
                          PasswordEncoder passwordEncoder,
                          JWTUtil jwtUtil,
                          RegistrierungsService registrierungsService,
                          AccountService accountService) {
        this.accountRepository = accountRepository;
        this.authenticationService = authenticationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.registrierungsService = registrierungsService;
        this.accountService = accountService;
    }

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDto dto) {
        // TODO #5: Pflichtfelder und E-Mail-Format prüfen
        if (dto.username() == null || dto.username().isBlank() ||
            dto.password() == null || dto.password().isBlank() ||
            dto.email()    == null || dto.email().isBlank() ||
            !EMAIL_PATTERN.matcher(dto.email()).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            registrierungsService.starteRegistrierung(dto);
        } catch (UsernameAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("message", "Registration pending. Check your email to activate your account."));
    }

    @GetMapping("/register/confirm/{token}")
    public ResponseEntity<?> confirm(@PathVariable String token) {
        try {
            PendingRegistration pending = registrierungsService.bestaetigen(token);
            Account account = accountService.erstelleAccount(pending);
            TokenData jwt = new TokenData(jwtUtil.generateToken(account.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(jwt);
        } catch (TokenNotFoundException | UsernameAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (TokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
    }

    @GetMapping("/protected")
    public ResponseEntity<?> protectedResource(
            @RequestHeader("Authorization") String authHeader) {
        log.info("Accessing protected resource with Authorization header: {}", authHeader);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }
        try {
            String token = authHeader.substring(7);
            String username = jwtUtil.validateToken(token).getSubject();
            return ResponseEntity.ok("Hello, " + username + "! You are authenticated.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired token");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return accountRepository.findByUsername(auth.getName())
                .map(account -> ResponseEntity.ok(Map.of(
                        "username", account.getUsername(),
                        "email",    account.getEmail() != null ? account.getEmail() : "",
                        "role",     account.getRole()
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
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