package ch.axa.mediaHub.jwt;

import ch.axa.mediaHub.model.Account;
import ch.axa.mediaHub.model.ProfileStatus;
import ch.axa.mediaHub.model.UserProfile;
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

        UserProfile profile = new UserProfile();
        profile.setEmail(pending.email());
        profile.setStatus(ProfileStatus.UNVERIFIED);
        profile.setAccount(account);

        account.setProfile(profile);

        Account saved = accountRepository.save(account); // CASCADE speichert UserProfile mit
        log.info("account created: {} [{}]", saved.getUsername(), saved.getProfile().getStatus());
        return saved;
    }
}