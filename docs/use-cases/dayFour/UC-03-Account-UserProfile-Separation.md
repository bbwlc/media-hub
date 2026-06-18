# UC-03 — Trennung von Account und UserProfile

## Ziel

Die `Account`-Entität wird in zwei klar getrennte Entitäten aufgeteilt:
- **`Account`** — Authentifizierungsdaten (Benutzername, Passwort-Hash, Rolle, Token)
- **`UserProfile`** — Profildaten (E-Mail, Profilbild, Status)

Jede Entität hat eine einzige Verantwortung (**Single Responsibility Principle**).

---

## Warum trennen?

### Ist-Zustand (Problem)

```java
// Account.java — vorher: alles in einer Klasse
public class Account {
    private String username;       // Auth
    private String passwordHash;   // Auth
    private String role;           // Auth
    private String token;          // Auth
    private String email;          // Profil ← falsch platziert
    private String profilePicture; // Profil ← falsch platziert
    private ProfileStatus status;  // Profil ← falsch platziert
}
```

`Account` wurde bei jeder HTTP-Anfrage für die Authentifizierung geladen — inklusive
Profilbild, Status und E-Mail, die dabei gar nicht gebraucht werden.

### Soll-Zustand

| `Account` | `UserProfile` |
|---|---|
| `id` | `id` |
| `username` | `email` |
| `passwordHash` | `profilePicture` |
| `role` | `status` (ProfileStatus) |
| `token` | `account_id` (FK) |

---

## Datenbankschema

```sql
CREATE TABLE account (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    token         VARCHAR(255) DEFAULT NULL
);

CREATE TABLE user_profile (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id      BIGINT       NOT NULL UNIQUE,
    email           VARCHAR(255),
    profile_picture VARCHAR(255) DEFAULT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    FOREIGN KEY (account_id) REFERENCES account(id)
);
```

Der Fremdschlüssel liegt in `user_profile` (`account_id`), nicht in `account`.
`account` kennt das Profil nicht auf DB-Ebene — die Beziehung ist unidirektional im Schema.

---

## JPA-Beziehung

```
Account (1) ──────────── (1) UserProfile
  id                           id
  username                     email
  passwordHash                 profilePicture
  role                         status
  token                        account  ← @OneToOne @JoinColumn(account_id)
  profile ← @OneToOne(mappedBy)
```

### `Account.java`

```java
@OneToOne(mappedBy = "account", cascade = CascadeType.ALL,
          fetch = FetchType.EAGER, optional = false)
private UserProfile profile;
```

- `mappedBy = "account"` — der FK liegt bei `UserProfile`, nicht bei `Account`
- `cascade = ALL` — ein `accountRepository.save(account)` speichert das Profil automatisch mit
- `fetch = EAGER` — Profil wird immer zusammen mit dem Account geladen (1:1-Beziehung)

### `UserProfile.java`

```java
@JsonIgnore                          // verhindert zirkuläre JSON-Serialisierung
@OneToOne
@JoinColumn(name = "account_id", nullable = false, unique = true)
private Account account;
```

`@JsonIgnore` ist nötig: ohne es würde Jackson `account.profile.account.profile...`
endlos serialisieren.

---

## Erstellung eines neuen Accounts

`AccountService.erstelleAccount()` erstellt beide Objekte in einer einzigen Transaktion.
Das `cascade = ALL` auf der `Account`-Seite sorgt dafür, dass `accountRepository.save(account)`
das `UserProfile` automatisch mitpersistiert:

```java
public Account erstelleAccount(PendingRegistration pending) {
    Account account = new Account();
    account.setUsername(pending.username());
    account.setPasswordHash(pending.passwordHash());

    UserProfile profile = new UserProfile();
    profile.setEmail(pending.email());
    profile.setStatus(ProfileStatus.UNVERIFIED);
    profile.setAccount(account);   // Rückverweis setzen (für FK)

    account.setProfile(profile);   // Vorwärtsverweis setzen (für CASCADE)

    return accountRepository.save(account); // speichert Account + UserProfile
}
```

> **Wichtig:** Beide Verweise müssen gesetzt sein:
> - `profile.setAccount(account)` → damit JPA den FK `account_id` befüllen kann
> - `account.setProfile(profile)` → damit `cascade = ALL` das Profil mitpersistiert

---

## Auswirkungen auf bestehende Klassen

### `AuthenticationService` — LOCKED-Check

```java
// vorher
.filter(account -> account.getStatus() != ProfileStatus.LOCKED)

// nachher
.filter(account -> account.getProfile() == null ||
                   account.getProfile().getStatus() != ProfileStatus.LOCKED)
```

### `FileUploadController` — Profilbild

```java
// vorher
account.setProfilePicture(file.getOriginalFilename());

// nachher
account.getProfile().setProfilePicture(file.getOriginalFilename());
```

### `ProfileActionController` — Status-Aktionen

```java
// vorher: account.getStatus() / account.setStatus()
// nachher: profile wird direkt aus Account geladen

private UserProfile loadProfile(Long accountId) {
    Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return account.getProfile();
}
```

Die Action-Endpoints geben jetzt `UserProfile` zurück (statt `Account`), damit
`$.status` im Response-Body direkt verfügbar bleibt.

### `UserController` — Status-Patch

```java
// vorher
account.setStatus(status);
return Map.of("username", account.getUsername(), "status", account.getStatus());

// nachher
account.getProfile().setStatus(status);
return Map.of("username", account.getUsername(), "status", account.getProfile().getStatus());
```

---

## Neues Repository

```java
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByAccountUsername(String username);
}
```

Ermöglicht direkten Zugriff auf Profile ohne Umweg über den Account.

---

## Zusammenfassung der Änderungen

| # | Datei | Was geändert |
|---|---|---|
| 1 | `model/UserProfile.java` | Neue Entität mit `email`, `profilePicture`, `status` |
| 2 | `model/Account.java` | `email`, `profilePicture`, `status` entfernt → `@OneToOne UserProfile profile` |
| 3 | `repository/UserProfileRepository.java` | Neues Repository |
| 4 | `schema.sql` | Neue Tabelle `user_profile`, Spalten aus `account` entfernt |
| 5 | `data.sql` | `INSERT INTO user_profile` für alle Testbenutzer |
| 6 | `AccountService.java` | Erstellt `Account` + `UserProfile` in einer Transaktion |
| 7 | `AuthenticationService.java` | LOCKED-Check via `account.getProfile().getStatus()` |
| 8 | `FileUploadController.java` | Profilbild via `account.getProfile()` |
| 9 | `AuthController.java` | E-Mail via `account.getProfile().getEmail()` |
| 10 | `ProfileActionController.java` | Status-Operationen auf `UserProfile`, gibt `UserProfile` zurück |
| 11 | `UserController.java` | `PATCH /status` via `account.getProfile().setStatus()` |

---

## Lernpunkte

| Thema | Erkenntnis |
|---|---|
| `@OneToOne` | FK-Seite (`@JoinColumn`) trägt den FK in der DB — hier `user_profile.account_id` |
| `cascade = ALL` | `save(account)` persistiert `UserProfile` automatisch mit — kein separater `profileRepository.save()` nötig |
| `@JsonIgnore` | Zirkuläre Beziehungen (A → B → A) müssen beim einen Ende unterbrochen werden |
| `mappedBy` | Zeigt JPA, auf welcher Seite der FK liegt — die Seite mit `mappedBy` hat keinen FK in der DB |
| SRP | Auth-Daten und Profil-Daten ändern sich aus unterschiedlichen Gründen → eigene Tabellen, eigene Entitäten |