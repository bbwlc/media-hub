package ch.axa.mediaHub.model;

public record ErrorResponse(int status, String error, String message) { }