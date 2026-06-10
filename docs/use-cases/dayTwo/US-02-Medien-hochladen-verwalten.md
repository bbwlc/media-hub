# US-02: Medien hochladen und verwalten

## User Story

> Als eingeloggter Benutzer möchte ich Bilder und Dokumente hochladen und löschen können,
> damit ich meine Inhalte zentral speichern und verwalten kann.

---

## Akzeptanzkriterien

- Ein eingeloggter Benutzer kann Dateien in seinen eigenen Ordner hochladen.
- Ein Benutzer kann nur auf seine eigenen Dateien zugreifen (ausser bei geteilten Dateien).
- Admins können auf alle Dateien zugreifen.
- Ein Benutzer kann eine Datei löschen.
- Bilder werden automatisch als Profilbild gesetzt.
- Jede Anfrage erfordert einen gültigen JWT-Token.

---

## Endpoints

| Method | Pfad | Beschreibung | Auth |
|---|---|---|---|
| `POST` | `/upload/{username}` | Datei hochladen | JWT |
| `GET` | `/download/{username}?file=<name>` | Datei herunterladen | JWT |
| `GET` | `/files` | Eigene Dateien auflisten | JWT |
| `GET` | `/users/{username}/avatar` | Profilbild abrufen | JWT |
| `DELETE` | `/files/{username}/{filename}` | Datei löschen | JWT |

---

## Technische Umsetzung

### Datei-Upload mit Benutzerbindung
Dateien werden unter `uploads/<username>/<filename>` auf dem Dateisystem gespeichert.
Der Controller prüft, ob der `{username}` im Pfad mit dem eingeloggten Benutzer übereinstimmt — andernfalls `403 Forbidden`.

```java
if (!username.equals(loggedInUser)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

Wird ein Bild hochgeladen (`image/*`), setzt der Controller automatisch das Profilbild auf den Dateinamen.

---

### Autorisierter Zugriff (Gruppe 7 — Security-Konfiguration)
Beim Download wird geprüft, ob der Benutzer:
1. der Eigentümer der Datei ist, **oder**
2. die Rolle `ROLE_ADMIN` besitzt, **oder**
3. explizit Zugriff über das Sharing-System erhalten hat (`SharedFileRepository.canAccess`)

```java
boolean allowed = username.equals(loggedInUser)
        || isAdmin
        || sharedFileRepository.canAccess(username, filename, loggedInUser);
```

---

### Token-basierte Authentifizierung
Jeder Request wird durch den `JWTAuthenticationFilter` geprüft.
Der Token wird im `Authorization: Bearer <token>` Header übermittelt.

---

### Konflikterkennung bei gleichzeitigen Änderungen
Beim Teilen einer Datei wird geprüft, ob eine identische Freigabe bereits existiert.
Bei Duplikat wird `409 Conflict` zurückgegeben:

```java
if (sharedFileRepository.existsPublicShare(loggedInUser, filename)) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body("Already shared publicly.");
}
```

---

### Transaktionen mit `@Transactional`
Schreiboperationen im `SharedFileRepository` (Freigabe löschen) sind mit `@Transactional` gesichert:

```java
@Modifying
@Transactional
@Query("DELETE FROM SharedFile s WHERE s.owner=:owner AND s.filename=:filename ...")
void deletePublicShare(...);
```

---

## Implementierungsstand

| Anforderung | Status | Bemerkung |
|---|---|---|
| `POST /upload/{username}` — Datei hochladen | ✅ | Mit Eigentümerprüfung |
| `GET /download/{username}` — Datei herunterladen | ✅ | Mit Owner/Admin/Shared-Check |
| `GET /files` — eigene Dateien auflisten | ✅ | Implementiert in `FileService` |
| `GET /users/{username}/avatar` — Profilbild | ✅ | Automatisch bei Bild-Upload gesetzt |
| Profilbild automatisch setzen bei Bild-Upload | ✅ | `image/*` wird erkannt |
| Admin kann alle Dateien herunterladen | ✅ | `ROLE_ADMIN` Check im Controller |
| Geteilte Dateien zugänglich | ✅ | `SharedFileRepository.canAccess()` |
| Konflikterkennung bei Freigabe-Duplikaten | ✅ | `409 Conflict` bei Duplikat |
| `@Transactional` bei Schreiboperationen | ✅ | In `SharedFileRepository` |
| `DELETE /files/{username}/{filename}` — Datei löschen | ❌ | Nicht implementiert |
| JWT für `/upload/**` verpflichtend | ❌ | Noch `permitAll` in `SecurityConfig` |
| `@Transactional` in `FileService` | ❌ | Dateisystem-Operationen nicht gesichert |

### Offene Punkte

1. **Löschen-Endpunkt fehlt** — `DELETE /files/{username}/{filename}` muss noch implementiert werden (in `FileService` und `FileUploadController`).
2. **Upload nicht geschützt** — `/upload/**` ist in `SecurityConfig` als `permitAll` konfiguriert. JWT wird zwar im Controller geprüft, aber Spring Security erzwingt es nicht.
3. **`@Transactional` in `FileService`** — Dateisystemoperationen sind aktuell nicht transaktional gesichert. Bei einem Fehler während des Uploads kann eine unvollständige Datei entstehen.