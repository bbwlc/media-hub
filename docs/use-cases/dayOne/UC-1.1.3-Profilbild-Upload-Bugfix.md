# UC-1.1.3 — Profilbild hochladen: Buganalyse und Fixes

## Ziel

Beim Versuch, ein Profilbild hochzuladen, trat ein Fehler auf. Dieses Dokument beschreibt die
drei Ursachen, die im `FileUploadController` gefunden wurden, sowie die jeweiligen Korrekturen.

---

## Fehler 1 — `Path`-Objekt nicht serialisierbar (500 bei jedem Upload)

### Symptom

`POST /upload/{username}` gibt **500 Internal Server Error** zurück, obwohl die Datei korrekt
auf dem Dateisystem gespeichert wurde.

### Ursache

```java
// FileUploadController.java — vorher
return ResponseEntity.ok(result.get());   // result ist Optional<java.nio.file.Path>
```

Jackson (der JSON-Serializer von Spring) weiss nicht, wie er `java.nio.file.Path` in JSON
umwandeln soll. `Path` implementiert `Iterable<Path>` — Jackson würde versuchen, es als Array
der einzelnen Pfad-Elemente zu serialisieren, was zu einem Fehler oder unerwartetem JSON führt.

> Wichtig: Die Datei selbst wurde korrekt gespeichert. Nur die HTTP-Antwort schlug fehl.
> Das Profilbild-Feld im Account wurde ebenfalls korrekt gesetzt (Code läuft vor dem `return`).
> Der Client sah trotzdem einen Fehler und meinte, der Upload sei fehlgeschlagen.

### Fix

```java
// FileUploadController.java — nachher
return ResponseEntity.ok(result.get().toString());   // Path als String zurückgeben
```

---

## Fehler 2 — Avatar-Endpoint erfordert Authentifizierung (401 ohne JWT)

### Symptom

`GET /users/{username}/avatar` gibt **401 Unauthorized** zurück, wenn kein JWT mitgesendet wird.

### Ursache

In `SecurityConfig` stand `/users/*/avatar` nicht in der `permitAll()`-Liste. Die Regel
`.anyRequest().authenticated()` griff — also war ein JWT-Token nötig, um das Profilbild
eines Benutzers anzuzeigen. Das ist für ein Profilbild in der Regel nicht sinnvoll.

```java
// SecurityConfig.java — vorher
.requestMatchers("/upload/**",
                 "/v3/api-docs/**", ...).permitAll()
```

### Fix

```java
// SecurityConfig.java — nachher
.requestMatchers("/upload/**",
                 "/users/*/avatar",     // neu
                 "/v3/api-docs/**", ...).permitAll()
```

---

## Fehler 3 — Avatar-Response ohne `Content-Type` (Browser zeigt kein Bild)

### Symptom

`GET /users/{username}/avatar` lieferte die Bildbytes, aber der Browser zeigte kein Bild an —
er wusste nicht, welches Format die Antwort hat.

### Ursache

Die Response setzte nur `Content-Disposition`, aber keinen `Content-Type`-Header:

```java
// FileUploadController.java — vorher
return ResponseEntity.ok()
        .header("Content-Disposition", "inline; filename=\"" + profilePicture + "\"")
        .body(fileData.get());
```

Ohne `Content-Type: image/jpeg` (oder entsprechendes Format) kann der Browser die Bytes
nicht als Bild interpretieren.

### Fix

```java
// FileUploadController.java — nachher
String contentType;
try {
    contentType = Files.probeContentType(Paths.get(profilePicture));
} catch (Exception e) {
    contentType = null;
}
MediaType mediaType = contentType != null
        ? MediaType.parseMediaType(contentType)
        : MediaType.APPLICATION_OCTET_STREAM;

return ResponseEntity.ok()
        .contentType(mediaType)
        .header("Content-Disposition", "inline; filename=\"" + profilePicture + "\"")
        .body(fileData.get());
```

`Files.probeContentType()` bestimmt den MIME-Typ anhand der Dateiendung (`.jpg` → `image/jpeg`,
`.png` → `image/png`, usw.).

---

## Zusammenfassung der Änderungen

| # | Datei | Was geändert |
|---|---|---|
| 1 | `FileUploadController.java` | `result.get()` → `result.get().toString()` |
| 2 | `SecurityConfig.java` | `/users/*/avatar` zu `permitAll()` hinzugefügt |
| 3 | `FileUploadController.java` | `Content-Type` via `Files.probeContentType()` gesetzt |

---

## Lernpunkte

| Thema | Erkenntnis |
|---|---|
| Jackson-Serialisierung | Nur Standard-Java-Typen (String, int, List, Map, ...) und annotierte Klassen sind automatisch serialisierbar. `java.nio.file.Path` ist es nicht. |
| `permitAll()` | Betrifft nur Spring Securitys Zugangskontrolle, nicht die Logik im Controller selbst. |
| `Content-Type` | Jede HTTP-Response, die Binärdaten enthält, muss den MIME-Typ deklarieren — der Browser rät nicht. |
