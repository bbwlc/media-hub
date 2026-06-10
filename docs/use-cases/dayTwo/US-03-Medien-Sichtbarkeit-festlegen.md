# US-03: Medien-Sichtbarkeit festlegen

## User Story

> Als Benutzer möchte ich festlegen können, ob meine Medien privat oder öffentlich sind,
> damit ich selbst entscheiden kann, wer meine Inhalte sehen darf.

---

## Akzeptanzkriterien

- Jede Datei hat eine Sichtbarkeit: `PUBLIC` oder `PRIVATE`.
- `PRIVATE` (Standard): nur der Eigentümer und explizit berechtigte Benutzer haben Zugriff.
- `PUBLIC`: jeder — auch nicht eingeloggte Benutzer — kann die Datei herunterladen.
- Der Eigentümer kann die Sichtbarkeit jederzeit ändern.
- Admins haben unabhängig von der Sichtbarkeit immer Zugriff.

---

## Endpoints

| Method | Pfad | Beschreibung | Auth |
|---|---|---|---|
| `POST` | `/share/{filename}` | Datei öffentlich oder mit Benutzer teilen | JWT |
| `DELETE` | `/share/{filename}` | Freigabe widerrufen | JWT |
| `GET` | `/share` | Eigene Freigaben auflisten | JWT |
| `GET` | `/shared` | Für mich freigegebene Dateien | JWT |
| `GET` | `/shared/{ownerUsername}?file=<name>` | Freigegebene Datei herunterladen | JWT |
| `GET` | `/download/{username}?file=<name>` | Eigene oder freigegebene Datei | JWT |

---

## Technische Umsetzung

### Sichtbarkeits-Modell (aktuell)
Die Sichtbarkeit wird über die `shared_file`-Tabelle gesteuert.
Das Feld `shared_with` bestimmt, wer Zugriff hat:

| `shared_with` | Bedeutung |
|---|---|
| `null` | Öffentlich (PUBLIC) — alle authentifizierten Benutzer |
| `"username"` | Geteilt mit einem bestimmten Benutzer (PRIVATE-shared) |
| Kein Eintrag | Privat (PRIVATE) — nur Eigentümer |

```java
// null = public
@Column(name = "shared_with")
private String sharedWith;
```

### Ziel-Modell (noch nicht implementiert)
Ein explizites `visibility`-Attribut direkt auf der Datei (z.B. in der `shared_file`-Tabelle oder als separates Metadaten-Objekt):

```java
public enum Visibility { PUBLIC, PRIVATE }
```

---

### Gruppe 5 — Role-Based Access Control (RBAC)
Die Zugriffskontrolle beim Download prüft mehrere Bedingungen:

```java
boolean allowed = username.equals(loggedInUser)   // Eigentümer
        || isAdmin                                  // Admin-Rolle
        || sharedFileRepository.canAccess(username, filename, loggedInUser); // Freigabe
```

Admins (`ROLE_ADMIN`) haben immer Zugriff — unabhängig von der Sichtbarkeit.

---

### Unterscheidung anonymer vs. authentifizierter Zugriff
Aktuell erfordert jeder Download-Endpunkt einen JWT.
Für `PUBLIC`-Dateien müsste der Endpunkt `permitAll()` werden und der Zugriff anhand der Sichtbarkeit in der DB geprüft werden:

```
Anonym:         GET /public/{username}?file=<name>  → kein Token nötig, nur wenn PUBLIC
Authentifiziert: GET /download/{username}?file=<name> → JWT + Owner/Admin/Shared-Check
```

---

### Konflikterkennung und Transaktionen (`@Transactional`)
Beim Erstellen einer Freigabe wird auf Duplikate geprüft (`409 Conflict`).

`@Transactional` gehört auf die **Service-Schicht**, nicht auf das Repository.
Der Service definiert die Transaktionsgrenze — mehrere Repository-Aufrufe in einer Methode werden so als eine atomare Operation behandelt.

```java
// Richtig: Service-Schicht
@Transactional
public void revokeShare(String owner, String filename, String sharedWith) {
    // mehrere Repo-Aufrufe hier — alles in einer Transaktion
}

// Falsch: Repository-Schicht (pragmatischer Workaround, aber falsche Ebene)
@Modifying
@Transactional
void deletePublicShare(String owner, String filename);
```

**Umsetzung:** Die Geschäftslogik aus `ShareController` wird in einen `FileShareService` ausgelagert.
Der Controller ruft nur noch den Service auf. `@Transactional` sitzt auf den Service-Methoden.

---

### Gruppe 7 — Security-Konfiguration
Für anonymen Zugriff auf PUBLIC-Dateien muss `SecurityConfig` angepasst werden:

```java
.requestMatchers("/public/**").permitAll()   // anonymer Zugriff auf PUBLIC
.anyRequest().authenticated()                // alles andere erfordert JWT
```

---

## Implementierungsstand

| Anforderung | Status | Bemerkung |
|---|---|---|
| Datei öffentlich teilen (`sharedWith = null`) | ✅ | `POST /share/{filename}` ohne Body |
| Datei mit Benutzer teilen | ✅ | `POST /share/{filename}` mit `{ "sharedWith": "user2" }` |
| Freigabe widerrufen | ✅ | `DELETE /share/{filename}` |
| Eigene Freigaben auflisten | ✅ | `GET /share` |
| Freigegebene Dateien abrufen | ✅ | `GET /shared` |
| Zugriffscheck: Eigentümer / Admin / Freigabe | ✅ | In `FileUploadController.downloadFromUserFolder` |
| Admin hat immer Zugriff (`ROLE_ADMIN`) | ✅ | RBAC-Check im Controller |
| Konflikterkennung bei Duplikat-Freigaben | ✅ | `409 Conflict` in `ShareController` |
| `@Transactional` auf Service-Schicht | ✅ | In `FileShareService` (nach Refactoring) |
| Explizites `visibility`-Attribut (PUBLIC/PRIVATE) | ❌ | Kein Enum, nur implizit über `sharedWith = null` |
| Anonymer Zugriff auf PUBLIC-Dateien | ❌ | Alle Endpunkte erfordern aktuell JWT |
| Öffentlicher Endpunkt ohne JWT (`/public/**`) | ❌ | Nicht in `SecurityConfig` konfiguriert |

### Offene Punkte

1. **Explizites `visibility`-Feld** — aktuell wird PUBLIC nur implizit über `sharedWith = null` in `shared_file` abgebildet. Ein `visibility`-Enum direkt pro Datei wäre sauberer.
2. **Anonymer Zugriff** — PUBLIC-Dateien sind aktuell nur für eingeloggte Benutzer erreichbar. Ein neuer Endpunkt `/public/{username}?file=<name>` mit `permitAll()` wäre nötig.
3. **`/upload/**` noch `permitAll`** — Sicherheitslücke aus US-02, noch nicht behoben.