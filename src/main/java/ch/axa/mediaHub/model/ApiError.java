package ch.axa.mediaHub.model;

public record ApiError(java.time.Instant now, int status, String error, String message) { }