# UC — Exception Handling: Zentralisierte Fehlerbehandlung

## Ziel

Alle Fehlerfälle der API werden an einem einzigen Ort behandelt und liefern eine einheitliche
JSON-Antwort. Controllers enthalten keine try-catch-Blöcke mehr. Services kennen keine
HTTP-Statuscodes.

---

## Ist-Zustand (Problem)

### 1. Fehlerbehandlung verstreut in den Controllers

Jeder Controller löst Exceptions selbst auf und mappt sie manuell auf HTTP-Statuscodes:

```java
// AuthController.java — aktuell
try {
    registrierungsService.starteRegistrierung(dto);
} catch (UsernameAlreadyExistsException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).build();
}
// ...
try {
    PendingRegistration pending = registrierungsService.bestaetigen(token);
    ...
} catch (TokenNotFoundException | UsernameAlreadyExistsException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
} catch (TokenExpiredException e) {
    return ResponseEntity.status(HttpStatus.GONE).build();
}
```

### 2. `FileShareService` wirft HTTP-Exceptions aus dem Service heraus

```java
// FileShareService.java — aktuell
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
throw new ResponseStatusException(HttpStatus.CONFLICT, "Already shared publicly.");
```

`ResponseStatusException` ist eine Spring-Web-Klasse — sie gehört nicht in die Service-Schicht.
Der Service kennt dadurch HTTP-Konzepte, was gegen das Single-Responsibility-Prinzip verstösst.

### 3. Inkonsistente Fehler-Responses

| Situation | Aktueller Body |
|---|---|
| Username vergeben | *(leer)* |
| Token abgelaufen | *(leer)* |
| Datei nicht gefunden (Share) | `"File not found."` (plain String) |
| Zugriff verweigert | `"You are only allowed to upload to your own folder."` |

Der Client kann nicht zuverlässig auf Fehler reagieren, weil Format und Inhalt je nach Endpoint unterschiedlich sind.

---

## Soll-Zustand

### Einheitliches Fehler-DTO

Jede Fehlerantwort liefert denselben JSON-Body:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Username already taken: user1"
}
```

### `@RestControllerAdvice` — ein zentraler Handler

Spring fängt alle Exceptions ab, bevor sie den Client erreichen, und leitet sie an den
`GlobalExceptionHandler` weiter. Controllers enthalten keine try-catch-Blöcke mehr.

```
Service wirft Exception
        ↓
Controller (kein try-catch)
        ↓
Spring delegiert an GlobalExceptionHandler
        ↓
Einheitlicher JSON-Fehler-Body zurück an Client
```

---

## Implementierung

### Schritt 1 — `ErrorResponse`-DTO anlegen

Neues Record im Package `ch.axa.mediaHub.model`:

```java
package ch.axa.mediaHub.model;

public record ErrorResponse(int status, String error, String message) { }
```

---

### Schritt 2 — `GlobalExceptionHandler` anlegen

Neue Klasse `ch.axa.mediaHub.GlobalExceptionHandler`:

```java
package ch.axa.mediaHub;

import ch.axa.mediaHub.jwt.TokenExpiredException;
import ch.axa.mediaHub.jwt.TokenNotFoundException;
import ch.axa.mediaHub.jwt.UsernameAlreadyExistsException;
import ch.axa.mediaHub.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExists(UsernameAlreadyExistsException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(TokenNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTokenNotFound(TokenNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException e) {
        return error(HttpStatus.GONE, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
```

---

### Schritt 3 — Try-Catch aus `AuthController` entfernen

```java
// AuthController.java — nachher
@PostMapping("/register")
public ResponseEntity<?> register(@RequestBody RegisterDto dto) {
    if (dto.username() == null || dto.username().isBlank() || ...) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
    registrierungsService.starteRegistrierung(dto);   // Exception bubbles up → GlobalExceptionHandler
    return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Map.of("message", "Registration pending. Check your email."));
}

@GetMapping("/register/confirm/{token}")
public ResponseEntity<?> confirm(@PathVariable String token) {
    PendingRegistration pending = registrierungsService.bestaetigen(token);  // bubbles up
    Account account = accountService.erstelleAccount(pending);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TokenData(jwtUtil.generateToken(account.getUsername())));
}
```

---

### Schritt 4 — `ResponseStatusException` aus `FileShareService` entfernen

Neue domänenspezifische Exceptions anlegen:

```java
// ch.axa.mediaHub.media.FileNotFoundException.java
public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(String filename) {
        super("File not found: " + filename);
    }
}

// ch.axa.mediaHub.media.ShareAlreadyExistsException.java
public class ShareAlreadyExistsException extends RuntimeException {
    public ShareAlreadyExistsException(String message) {
        super(message);
    }
}
```

`FileShareService` wirft jetzt domänenspezifische Exceptions — kein Spring-Import mehr:

```java
// FileShareService.java — nachher
if (!fileService.listFiles(owner).contains(filename)) {
    throw new FileNotFoundException(filename);
}
if (sharedFileRepository.existsPublicShare(owner, filename)) {
    throw new ShareAlreadyExistsException("Already shared publicly.");
}
```

`GlobalExceptionHandler` erhält zwei neue Handler:

```java
@ExceptionHandler(FileNotFoundException.class)
public ResponseEntity<ErrorResponse> handleFileNotFound(FileNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, e.getMessage());
}

@ExceptionHandler(ShareAlreadyExistsException.class)
public ResponseEntity<ErrorResponse> handleShareAlreadyExists(ShareAlreadyExistsException e) {
    return error(HttpStatus.CONFLICT, e.getMessage());
}
```

`ShareController` entfernt seinen try-catch — der Handler übernimmt:

```java
// ShareController.java — nachher
@PostMapping("/share/{filename}")
public ResponseEntity<?> shareFile(@PathVariable String filename,
                                   @RequestBody(required = false) ShareRequestDto dto) {
    String loggedInUser = SecurityContextHolder.getContext().getAuthentication().getName();
    SharedFile result = fileShareService.share(loggedInUser, filename,
                                               dto != null ? dto.sharedWith() : null);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
}
```

---

## Exception-zu-HTTP-Status-Mapping (vollständig)

| Exception | HTTP-Status | Bedeutung |
|---|---|---|
| `UsernameAlreadyExistsException` | `409 Conflict` | Username bereits vergeben |
| `TokenNotFoundException` | `404 Not Found` | JWT ungültig oder gefälscht |
| `TokenExpiredException` | `410 Gone` | JWT abgelaufen (> 24h) |
| `FileNotFoundException` | `404 Not Found` | Datei nicht auf Dateisystem |
| `ShareAlreadyExistsException` | `409 Conflict` | Freigabe bereits vorhanden |
| `Exception` (Fallback) | `500 Internal Server Error` | Unerwarteter Fehler |

---

## Warum `@RestControllerAdvice`?

| Ansatz | Problem |
|---|---|
| try-catch im Controller | Doppelter Code, jeder Controller muss selbst mappen |
| `ResponseStatusException` im Service | Service-Schicht kennt HTTP-Konzepte |
| `@RestControllerAdvice` | Ein Ort, konsistentes Format, Services bleiben sauber |

`@RestControllerAdvice` ist eine Kombination aus `@ControllerAdvice` (fängt Exceptions aus
allen Controllers ab) und `@ResponseBody` (serialisiert die Antwort als JSON automatisch).

---

## Offene TODOs

| # | Beschreibung | Wo |
|---|---|---|
| 1 | `ErrorResponse`-Record anlegen | `ch.axa.mediaHub.model.ErrorResponse` |
| 2 | `GlobalExceptionHandler` mit `@RestControllerAdvice` anlegen | `ch.axa.mediaHub.GlobalExceptionHandler` |
| 3 | Try-catch aus `AuthController.register()` und `confirm()` entfernen | `AuthController` |
| 4 | `FileNotFoundException` anlegen | `ch.axa.mediaHub.media` |
| 5 | `ShareAlreadyExistsException` anlegen | `ch.axa.mediaHub.media` |
| 6 | `ResponseStatusException` in `FileShareService` ersetzen | `FileShareService` |
| 7 | Try-catch aus `ShareController.shareFile()` entfernen | `ShareController` |
| 8 | Handler für `FileNotFoundException` und `ShareAlreadyExistsException` ergänzen | `GlobalExceptionHandler` |