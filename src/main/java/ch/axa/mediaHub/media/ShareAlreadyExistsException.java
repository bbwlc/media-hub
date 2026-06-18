package ch.axa.mediaHub.media;

public class ShareAlreadyExistsException extends RuntimeException {
    public ShareAlreadyExistsException(String message) {
        super(message);
    }
}