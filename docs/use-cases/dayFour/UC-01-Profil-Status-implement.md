# UC-01 — Profil-Lebenszyklus: Status UNVERIFIED / VERIFIED / LOCKED

## Ziel

Ein `Account` durchläuft nach seiner Erstellung definierte Zustände. Neu angelegte Profile
starten als `UNVERIFIED`. Ein Administrator kann ein Profil auf `VERIFIED` setzen oder bei
Regelverstössen auf `LOCKED`. Gesperrte Accounts können sich nicht mehr einloggen.

---

## Zustandsdiagramm

```
                  ┌─────────────┐
  Registrierung   │             │
  ─────────────►  │ UNVERIFIED  │
                  │             │
                  └──────┬──────┘
                         │
              ┌──────────┴──────────┐
              │ Admin: verify       │ Admin: lock
              ▼                     ▼
      ┌──────────────┐     ┌──────────────┐
      │              │     │              │
      │   VERIFIED   │◄───►│    LOCKED    │
      │              │     │              │
      └──────────────┘     └──────────────┘
       Login erlaubt         Login gesperrt
```

Erlaubte Übergänge:

| Von | Nach | Aktion |
|---|---|---|
| `UNVERIFIED` | `VERIFIED` | Admin bestätigt das Profil |
| `UNVERIFIED` | `LOCKED` | Admin sperrt das Profil |
| `VERIFIED` | `LOCKED` | Admin sperrt das Profil |
| `LOCKED` | `VERIFIED` | Admin hebt die Sperre auf |

> `UNVERIFIED` → `UNVERIFIED` und `LOCKED` → `UNVERIFIED` sind nicht erlaubt.

---

## Ist-Zustand

Die `Account`-Entität hat kein Status-Feld. Jeder angelegte Account kann sich sofort
einloggen, unabhängig davon ob er je überprüft wurde.

```java
// Account.java — aktuell: kein status-Feld
public class Account {
    private String username;
    private String passwordHash;
    private String email;
    private String role;
    private String token;
    private String profilePicture;
    // ← kein status
}
```

---

## Schritt 1 — `ProfileStatus` Enum anlegen

Neues Enum im Package `ch.axa.mediaHub.model`:

```java
package ch.axa.mediaHub.model;

public enum ProfileStatus {
    UNVERIFIED,
    VERIFIED,
    LOCKED
}
```

---

## Schritt 2 — `Account`-Entität erweitern

```java
// Account.java — neu
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Column(nullable = false)
@Enumerated(EnumType.STRING)
private ProfileStatus status = ProfileStatus.UNVERIFIED;
```

> **Warum `EnumType.STRING`?**
> JPA kann Enums als Zahl (`ORDINAL`: 0, 1, 2) oder als Text (`STRING`: "UNVERIFIED") speichern.
> `STRING` ist sicherer: wird später ein Wert in der Mitte der Enum-Definition eingefügt,
> bleiben bestehende DB-Einträge korrekt. Mit `ORDINAL` würden sie falsche Werte erhalten.

Vollständige `Account`-Klasse nach der Änderung:

```java
@Getter
@Setter
@Entity
@ToString
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = true)
    private String email;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Column(nullable = true)
    private String token;

    @Column(name = "profile_picture")
    private String profilePicture;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProfileStatus status = ProfileStatus.UNVERIFIED;   // neu
}
```

---

## Schritt 3 — `schema.sql` anpassen

```sql
CREATE TABLE account (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    username         VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    role             VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    token            VARCHAR(255) DEFAULT NULL,
    profile_picture  VARCHAR(255) DEFAULT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED'   -- neu
);
```

---

## Schritt 4 — `data.sql` anpassen

```sql
INSERT INTO account (username, password_hash, role, status) VALUES
('user1', '$2a$12$Q01Gsg2yq9rTzKOIDFJqEeBakU0TpUU/JPaavprd8ZMoIUzg8kt1K', 'ROLE_ADMIN', 'VERIFIED'),  -- Passwort: password123
('user2', '$2a$12$tGUuloNZ15UJ6hhTMqLRDeIoNNP.hg/73V0VCXz/s/SPTE.WoQnLe', 'ROLE_USER',  'VERIFIED'),  -- Passwort: 123456
('user3', '$2a$12$tGUuloNZ15UJ6hhTMqLRDeIoNNP.hg/73V0VCXz/s/SPTE.WoQnLe', 'ROLE_USER',  'UNVERIFIED'); -- Passwort: 123456
```

> `user1` und `user2` werden als `VERIFIED` gesetzt — sie sind bestehende Testbenutzer und
> sollen sich weiterhin einloggen können. `user3` bleibt `UNVERIFIED` um den gesperrten
> Login-Flow testen zu können.

---

## Schritt 5 — `AccountService` anpassen

Neu angelegte Profile erhalten automatisch `UNVERIFIED`. Da die Entität `UNVERIFIED` als
Default-Wert hat, reicht ein explizites Setzen zur Verdeutlichung:

```java
// AccountService.java
public Account erstelleAccount(PendingRegistration pending) {
    Account account = new Account();
    account.setUsername(pending.username());
    account.setPasswordHash(pending.passwordHash());
    account.setEmail(pending.email());
    account.setStatus(ProfileStatus.UNVERIFIED);   // explizit, zur Klarheit
    Account saved = accountRepository.save(account);
    log.info("account created: {} [{}]", saved.getUsername(), saved.getStatus());
    return saved;
}
```

---

## Schritt 6 — Login für `LOCKED` Accounts sperren

In `AuthenticationService.authenticate()` wird nach dem Passwort-Check der Status geprüft:

```java
// AuthenticationService.java
public Optional<TokenData> authenticate(String username, String password) {
    return accountRepository.findByUsername(username)
        .filter(account -> passwordEncoder.matches(password, account.getPasswordHash()))
        .filter(account -> account.getStatus() != ProfileStatus.LOCKED)  // neu
        .map(account -> {
            TokenData tokendata = new TokenData(jwtUtil.generateToken(username));
            account.setToken(tokendata.getToken());
            accountRepository.save(account);
            return tokendata;
        });
}
```

> Ein `LOCKED`-Account liefert dasselbe `401 Unauthorized` wie falsche Zugangsdaten —
> der Client erfährt nicht, warum der Login abgelehnt wurde (kein Information Leak).

---

## Schritt 7 — Admin-Endpoint für Status-Änderung

Neuer Endpoint in `UserController`, nur für `ROLE_ADMIN`:

```java
// UserController.java
@PatchMapping("/{username}/status")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> updateStatus(
        @PathVariable String username,
        @RequestParam ProfileStatus status) {

    return accountRepository.findByUsername(username)
            .map(account -> {
                account.setStatus(status);
                accountRepository.save(account);
                return ResponseEntity.ok(Map.of(
                        "username", account.getUsername(),
                        "status",   account.getStatus()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
}
```

Aufruf:
```
PATCH /users/user3/status?status=VERIFIED
Authorization: Bearer <admin-token>
```

---

## Zusammenfassung der Änderungen

| # | Datei | Änderung |
|---|---|---|
| 1 | `ch.axa.mediaHub.model.ProfileStatus` | Neues Enum anlegen |
| 2 | `Account.java` | Feld `status` mit `@Enumerated(EnumType.STRING)` hinzufügen |
| 3 | `schema.sql` | Spalte `status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED'` |
| 4 | `data.sql` | Status für Testbenutzer explizit setzen |
| 5 | `AccountService.java` | `account.setStatus(ProfileStatus.UNVERIFIED)` beim Erstellen |
| 6 | `AuthenticationService.java` | Filter auf `status != LOCKED` beim Login |
| 7 | `UserController.java` | `PATCH /users/{username}/status` für Admins |

---

## Offene TODOs

| # | Beschreibung | Wo |
|---|---|---|
| 1 | `ProfileStatus`-Enum anlegen | `ch.axa.mediaHub.model.ProfileStatus` |
| 2 | `status`-Feld in `Account` hinzufügen (`@Enumerated(EnumType.STRING)`) | `Account.java` |
| 3 | `status`-Spalte in `schema.sql` ergänzen | `schema.sql` |
| 4 | `data.sql` mit expliziten Status-Werten aktualisieren | `data.sql` |
| 5 | `account.setStatus(UNVERIFIED)` in `AccountService` | `AccountService.java` |
| 6 | Login-Filter auf `status != LOCKED` in `AuthenticationService` | `AuthenticationService.java` |
| 7 | `PATCH /users/{username}/status` Endpoint implementieren | `UserController.java` |
| 8 | Testfall TC-04 für Status-Flow schreiben | `docs/test-cases/` |