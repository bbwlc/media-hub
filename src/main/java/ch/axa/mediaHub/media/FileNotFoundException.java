package ch.axa.mediaHub.media;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(String filename) {
        super("File not found: " + filename);
    }
}