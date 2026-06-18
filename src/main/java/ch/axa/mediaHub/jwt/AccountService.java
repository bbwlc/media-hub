package ch.axa.mediaHub.jwt;

import ch.axa.mediaHub.model.Account;
import ch.axa.mediaHub.model.ProfileStatus;
import ch.axa.mediaHub.model.authentication.PendingRegistration;
import ch.axa.mediaHub.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account erstelleAccount(PendingRegistration pending) {
        Account account = new Account();
        account.setUsername(pending.username());
        account.setPasswordHash(pending.passwordHash());
        account.setEmail(pending.email());
        account.setStatus(ProfileStatus.UNVERIFIED);
        Account saved = accountRepository.save(account);
        log.info("account created: {}", saved.getUsername());
        return saved;
    }
}