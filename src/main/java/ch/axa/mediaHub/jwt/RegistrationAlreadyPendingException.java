package ch.axa.mediaHub.jwt;

public class RegistrationAlreadyPendingException extends RuntimeException {
    public RegistrationAlreadyPendingException(String email) {
        super("Registration already pending for: " + email);
    }
}