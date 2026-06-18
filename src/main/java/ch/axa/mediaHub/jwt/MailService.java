package ch.axa.mediaHub.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

    public void sendRegistrationVerification(String email, String regToken) {
        String link = "http://localhost:8080/auth/register/confirm/" + regToken;
        log.info("Registrierungslink fuer {}: {}", email, link);
    }
}