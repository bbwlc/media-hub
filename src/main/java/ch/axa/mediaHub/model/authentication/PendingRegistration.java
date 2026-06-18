package ch.axa.mediaHub.model.authentication;

public record PendingRegistration(String username, String email, String passwordHash) { }