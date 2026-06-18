package ch.axa.mediaHub.jwt;

public class TokenNotFoundException extends RuntimeException {
    public TokenNotFoundException() {
        super("Registration token not found");
    }
}