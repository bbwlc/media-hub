package ch.axa.mediaHub.jwt;

import ch.axa.mediaHub.model.ProfileStatus;
import ch.axa.mediaHub.model.authentication.TokenData;
import ch.axa.mediaHub.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthenticationService {
    private final AccountRepository accountRepository;
    private final JWTUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthenticationService(AccountRepository accountRepository,
                                 JWTUtil jwtUtil,
                                 PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<TokenData> authenticate(String username, String password) {
        return accountRepository.findByUsername(username)
            .filter(account -> passwordEncoder.matches(password, account.getPasswordHash()))
            .filter(account -> account.getStatus() != ProfileStatus.LOCKED)
            .map(account -> {
                TokenData tokendata = new TokenData(jwtUtil.generateToken(username));
                account.setToken(tokendata.getToken());
                accountRepository.save(account);
                return tokendata;
            });
    }
}
