package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.jwt.AuthenticationService;
import ch.axa.mediaHub.jwt.JWTUtil;
import ch.axa.mediaHub.model.Account;
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

    @Autowired
    public AuthController(AccountRepository accountRepository,
                          AuthenticationService authenticationService,
                          PasswordEncoder passwordEncoder,
                          JWTUtil jwtUtil) {
        this.accountRepository = accountRepository;
        this.authenticationService = authenticationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDto dto) {
        if (dto.username() == null || dto.username().isBlank() ||
            dto.password() == null || dto.password().isBlank() ||
            dto.email()    == null || dto.email().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (accountRepository.existsByUsername(dto.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Account account = new Account();
        account.setUsername(dto.username());
        account.setPasswordHash(passwordEncoder.encode(dto.password()));
        account.setEmail(dto.email());
        accountRepository.save(account);
        TokenData tokenData = new TokenData(jwtUtil.generateToken(dto.username()));
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenData);
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