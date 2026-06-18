package ch.axa.mediaHub.jwt;

import ch.axa.mediaHub.model.authentication.PendingRegistration;
import ch.axa.mediaHub.model.authentication.RegisterDto;
import ch.axa.mediaHub.repository.AccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrierungsService {

    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final MailService mailService;
    private final JWTUtil jwtUtil;

    public RegistrierungsService(PasswordEncoder passwordEncoder,
                                 AccountRepository accountRepository,
                                 MailService mailService,
                                 JWTUtil jwtUtil) {
        this.passwordEncoder = passwordEncoder;
        this.accountRepository = accountRepository;
        this.mailService = mailService;
        this.jwtUtil = jwtUtil;
    }

    public void starteRegistrierung(RegisterDto dto) {
        if (accountRepository.existsByUsername(dto.username())) {
            throw new UsernameAlreadyExistsException(dto.username());
        }

        String passwordHash = passwordEncoder.encode(dto.password());
        String regToken = jwtUtil.generateRegistrationToken(dto.username(), dto.email(), passwordHash);

        mailService.sendRegistrationVerification(dto.email(), regToken);
    }

    public PendingRegistration bestaetigen(String token) {
        Claims claims;
        try {
            claims = jwtUtil.parseRegistrationToken(token);
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException e) {
            throw new TokenNotFoundException();
        }

        String username = claims.getSubject();
        if (accountRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }

        return new PendingRegistration(
                username,
                claims.get("email",  String.class),
                claims.get("pwHash", String.class));
    }
}