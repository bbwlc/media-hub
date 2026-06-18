package ch.axa.mediaHub.jwt;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException() {
        super("Registration token has expired");
    }
}