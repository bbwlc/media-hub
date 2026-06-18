# UC-02 — Aktions-Endpunkte für Statusübergänge (Functional Resources)

## Ziel

Jeder erlaubte Zustandsübergang erhält einen **eigenen Endpoint**. Der Client drückt damit
eine Absicht aus, keine Datenmutation. Ungültige Übergänge werden vom Server mit `409 Conflict`
abgelehnt — der Client muss die Reihenfolge nicht selbst kennen.

---

## Warum nicht `PATCH /users/{username}/status?status=VERIFIED`?

Der bestehende Ansatz aus UC-01 (generisches PATCH auf das Statusfeld) hat Schwächen:

| Kriterium | PATCH auf Statusfeld | Aktions-Endpunkte |
|---|---|---|
| Übergangsprüfung | Client muss wissen welche Übergänge erlaubt sind | Server erzwingt die Regeln |
| Lesbarkeit | `PATCH /users/x/status?status=LOCKED` — was ist die Absicht? | `POST /profile/{id}/lock` — eindeutig |
| Fehlerbehandlung | Jeder Status akzeptiert — kein 409 | Ungültige Übergänge → 409 Conflict |
| REST-Prinzip | Ressource direkt mutieren | Aktion auf Ressource ausführen (Functional Resource) |
| Dokumentation | Erfordert externe Beschreibung der Regeln | Endpoint-Namen dokumentieren die Regeln selbst |

---

## Zustandsmaschine und erlaubte Übergänge

```
                  ┌─────────────┐
  Registrierung   │             │
  ─────────────►  │ UNVERIFIED  │──────────────────┐
                  │             │                  │
                  └──────┬──────┘                  │
                         │ /verify                 │ /lock
                         ▼                         ▼
                  ┌──────────────┐        ┌──────────────┐
                  │              │        │              │
                  │   VERIFIED   │──────► │    LOCKED    │
                  │              │ /lock  │              │
                  └──────────────┘        └──────┬───────┘
                                                 │ /unlock
                                                 ▼
                                          zurück zu VERIFIED
```

| Endpoint | Erlaubt aus | Verboten aus | Ergebnis |
|---|---|---|---|
| `/verify` | `UNVERIFIED` | `VERIFIED`, `LOCKED` | → `VERIFIED` |
| `/lock` | `UNVERIFIED`, `VERIFIED` | `LOCKED` | → `LOCKED` |
| `/unlock` | `LOCKED` | `UNVERIFIED`, `VERIFIED` | → `VERIFIED` |

---

## Endpoints

### `POST /api/me/profile/{id}/verify`

Setzt ein `UNVERIFIED`-Profil auf `VERIFIED`.

**Guard:** Nur erlaubt wenn aktueller Status `UNVERIFIED` ist.

```
POST /api/me/profile/3/verify
Authorization: Bearer <admin-token>
```

| Situation | HTTP-Status | Body |
|---|---|---|
| Profil nicht gefunden | `404 Not Found` | — |
| Status ist `VERIFIED` oder `LOCKED` | `409 Conflict` | Fehlermeldung |
| Erfolg | `200 OK` | Account mit neuem Status |

**Implementierungs-Gerüst:**

```java
@PostMapping("/api/me/profile/{id}/verify")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> verify(@PathVariable Long id) {
    // 1. Profil laden, sonst 404
    Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    // 2. Übergang prüfen: nur aus UNVERIFIED erlaubt
    if (account.getStatus() != ProfileStatus.UNVERIFIED) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Cannot verify: profile is already " + account.getStatus());
    }

    // 3. Status setzen und speichern
    account.setStatus(ProfileStatus.VERIFIED);
    accountRepository.save(account);

    // 4. 200 OK zurückgeben
    return ResponseEntity.ok(account);
}
```

---

### `POST /api/me/profile/{id}/lock`

Sperrt ein `UNVERIFIED`- oder `VERIFIED`-Profil.

**Guard:** Nur erlaubt wenn aktueller Status **nicht** `LOCKED` ist.

```
POST /api/me/profile/3/lock
Authorization: Bearer <admin-token>
```

| Situation | HTTP-Status | Body |
|---|---|---|
| Profil nicht gefunden | `404 Not Found` | — |
| Status ist bereits `LOCKED` | `409 Conflict` | Fehlermeldung |
| Erfolg | `200 OK` | Account mit neuem Status |

**Implementierungs-Gerüst:**

```java
@PostMapping("/api/me/profile/{id}/lock")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> lock(@PathVariable Long id) {
    // 1. Profil laden, sonst 404
    Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    // 2. Übergang prüfen: nicht erlaubt wenn bereits LOCKED
    if (account.getStatus() == ProfileStatus.LOCKED) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Cannot lock: profile is already LOCKED");
    }

    // 3. Status setzen und speichern
    account.setStatus(ProfileStatus.LOCKED);
    accountRepository.save(account);

    // 4. 200 OK zurückgeben
    return ResponseEntity.ok(account);
}
```

---

### `POST /api/me/profile/{id}/unlock`

Setzt ein `LOCKED`-Profil zurück auf `VERIFIED`.

**Guard:** Nur erlaubt wenn aktueller Status `LOCKED` ist.

```
POST /api/me/profile/3/unlock
Authorization: Bearer <admin-token>
```

| Situation | HTTP-Status | Body |
|---|---|---|
| Profil nicht gefunden | `404 Not Found` | — |
| Status ist `UNVERIFIED` oder `VERIFIED` | `409 Conflict` | Fehlermeldung |
| Erfolg | `200 OK` | Account mit neuem Status |

**Implementierungs-Gerüst:**

```java
@PostMapping("/api/me/profile/{id}/unlock")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> unlock(@PathVariable Long id) {
    // 1. Profil laden, sonst 404
    Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    // 2. Übergang prüfen: nur aus LOCKED erlaubt
    if (account.getStatus() != ProfileStatus.LOCKED) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Cannot unlock: profile is not LOCKED, current status is " + account.getStatus());
    }

    // 3. Status auf VERIFIED setzen und speichern
    account.setStatus(ProfileStatus.VERIFIED);
    accountRepository.save(account);

    // 4. 200 OK zurückgeben
    return ResponseEntity.ok(account);
}
```

---

## Vollständiger Controller

Die drei Methoden leben in einem neuen `ProfileActionController`:

```java
package ch.axa.mediaHub.restcontroller;

import ch.axa.mediaHub.model.Account;
import ch.axa.mediaHub.model.ProfileStatus;
import ch.axa.mediaHub.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ProfileActionController {

    private final AccountRepository accountRepository;

    public ProfileActionController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @PostMapping("/api/me/profile/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verify(@PathVariable Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (account.getStatus() != ProfileStatus.UNVERIFIED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot verify: profile is already " + account.getStatus());
        }
        account.setStatus(ProfileStatus.VERIFIED);
        return ResponseEntity.ok(accountRepository.save(account));
    }

    @PostMapping("/api/me/profile/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> lock(@PathVariable Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (account.getStatus() == ProfileStatus.LOCKED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot lock: profile is already LOCKED");
        }
        account.setStatus(ProfileStatus.LOCKED);
        return ResponseEntity.ok(accountRepository.save(account));
    }

    @PostMapping("/api/me/profile/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> unlock(@PathVariable Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (account.getStatus() != ProfileStatus.LOCKED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot unlock: profile is not LOCKED, current status is " + account.getStatus());
        }
        account.setStatus(ProfileStatus.VERIFIED);
        return ResponseEntity.ok(accountRepository.save(account));
    }
}
```

---

## Vergleich: PATCH vs. Aktions-Endpunkte

```
PATCH /users/user3/status?status=LOCKED   ← Client entscheidet den Zielstatus
                                            ← Server prüft nicht ob der Übergang erlaubt ist

POST /api/me/profile/3/lock               ← Client drückt eine Absicht aus
                                            ← Server prüft, führt aus oder antwortet 409
```

---

## Offene TODOs

| # | Beschreibung | Wo |
|---|---|---|
| 1 | `ProfileActionController` anlegen mit den drei Methoden | `ch.axa.mediaHub.restcontroller` |
| 2 | `/api/me/profile/**` in `SecurityConfig` — Authentifizierung prüfen (kein `permitAll`) | `SecurityConfig.java` |
| 3 | Account-ID von `user1`, `user2`, `user3` aus H2-Konsole ablesen für Tests | H2-Konsole |
| 4 | Testfälle für 409 Conflict (ungültige Übergänge) schreiben | `docs/test-cases/` |