# TC-03 — File Upload Flow

**Referenz:** US-02-Medien-hochladen-verwalten, UC-1.1-share-docs-images-profilpicture, UC-1.1.3-Profilbild-Upload-Bugfix  
**Endpoints:**
- `POST /upload/{username}` — Datei hochladen
- `GET /download/{username}?file=<name>` — Datei herunterladen
- `GET /files` — eigene Dateien auflisten
- `GET /users/{username}/avatar` — Profilbild abrufen

**Vorbedingung für alle TCs:** Anwendung läuft. JWT-Token via `POST /auth/signIn` holen (Testdaten aus `data.sql`):

| Benutzer | Passwort | Rolle |
|---|---|---|
| `user1` | `password123` | `ROLE_ADMIN` |
| `user2` | `123456` | `ROLE_USER` |

---

## TC-03.1 — Dokument erfolgreich hochladen

**Ziel:** Eine Nicht-Bild-Datei wird hochgeladen und der Pfad wird als String zurückgegeben.

**Vorbedingung:** JWT-Token von `user2` vorhanden.

**Schritte:**

1. `POST /upload/user2` aufrufen
2. Header:
```
Authorization: Bearer <token von user2>
Content-Type: multipart/form-data
```
3. Form-Parameter: `file` = beliebige Textdatei (z.B. `test.txt`)

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body ist ein String (absoluter Dateipfad):
```
".../uploads/user2/test.txt"
```
- Kein `profilePicture`-Feld in der DB wird gesetzt (da kein Bild)

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.2 — Bild hochladen → Profilbild wird automatisch gesetzt

**Ziel:** Ein Bild-Upload setzt automatisch das `profilePicture`-Feld des Accounts.

**Vorbedingung:** JWT-Token von `user2` vorhanden.

**Schritte:**

1. `POST /upload/user2` aufrufen
2. Header:
```
Authorization: Bearer <token von user2>
Content-Type: multipart/form-data
```
3. Form-Parameter: `file` = Bilddatei (z.B. `avatar.jpg`, MIME-Typ `image/jpeg`)

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body: Dateipfad als String
- In der H2-Konsole (`http://localhost:8080/h2-console`) ist bei `user2` das Feld `profile_picture = 'avatar.jpg'` gesetzt

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.3 — Upload in fremden Ordner wird abgelehnt

**Ziel:** Ein Benutzer kann nicht in den Ordner eines anderen Benutzers hochladen.

**Vorbedingung:** JWT-Token von `user2` vorhanden.

**Schritte:**

1. `POST /upload/user1` aufrufen (Pfad = `user1`, aber Token = `user2`)
2. Header:
```
Authorization: Bearer <token von user2>
Content-Type: multipart/form-data
```
3. Form-Parameter: `file` = beliebige Datei

**Erwartetes Ergebnis:**

- HTTP Status: `403 Forbidden`
- Response Body: `"You are only allowed to upload to your own folder."`
- Keine Datei gespeichert

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.4 — Upload ohne JWT wird abgelehnt

**Ziel:** Ein Upload ohne Authentifizierung scheitert am Username-Check.

**Schritte:**

1. `POST /upload/user2` aufrufen — **ohne** `Authorization`-Header
2. Form-Parameter: `file` = beliebige Datei

**Erwartetes Ergebnis:**

- HTTP Status: `403 Forbidden`
- Keine Datei gespeichert

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.5 — Eigene Datei herunterladen

**Ziel:** Ein Benutzer kann eine eigene hochgeladene Datei herunterladen.

**Vorbedingung:** TC-03.1 wurde bestanden — `test.txt` liegt unter `uploads/user2/`.

**Schritte:**

1. `GET /download/user2?file=test.txt` aufrufen
2. Header:
```
Authorization: Bearer <token von user2>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Header: `Content-Disposition: attachment; filename="test.txt"`
- Response Body: Inhalt der Datei

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.6 — Fremde Datei herunterladen wird abgelehnt

**Ziel:** Ein Benutzer kann nicht die Datei eines anderen Benutzers herunterladen.

**Vorbedingung:** `test.txt` liegt unter `uploads/user2/` (aus TC-03.1).

**Schritte:**

1. `GET /download/user2?file=test.txt` aufrufen mit Token von `user1` (kein Admin-Check — `user1` ist Admin, daher `user3` nutzen wenn vorhanden, sonst Token weglassen)
2. Header:
```
Authorization: Bearer <token von user2>
```
3. Pfad-Variable `user2` durch einen anderen Benutzer ersetzen, während Token von `user2` bleibt:
   `GET /download/user1?file=somefile.txt` mit Token von `user2`

**Erwartetes Ergebnis:**

- HTTP Status: `403 Forbidden`
- Response Body: `"You are not allowed to access this file."`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.7 — Admin kann fremde Datei herunterladen

**Ziel:** Ein Benutzer mit `ROLE_ADMIN` kann auf alle Dateien zugreifen.

**Vorbedingung:** `test.txt` liegt unter `uploads/user2/` (aus TC-03.1). JWT-Token von `user1` (Admin) vorhanden.

**Schritte:**

1. `GET /download/user2?file=test.txt` aufrufen
2. Header:
```
Authorization: Bearer <token von user1>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body: Inhalt der Datei

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.8 — Nicht vorhandene Datei herunterladen

**Ziel:** Eine nicht existierende Datei liefert `404`.

**Vorbedingung:** JWT-Token von `user2` vorhanden.

**Schritte:**

1. `GET /download/user2?file=nichtvorhanden.pdf` aufrufen
2. Header:
```
Authorization: Bearer <token von user2>
```

**Erwartetes Ergebnis:**

- HTTP Status: `404 Not Found`
- Response Body: `"Datei nicht gefunden."`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.9 — Eigene Dateien auflisten

**Ziel:** `GET /files` gibt nur die Dateien des eingeloggten Benutzers zurück.

**Vorbedingung:** TC-03.1 und TC-03.2 wurden bestanden — mindestens zwei Dateien von `user2` vorhanden.

**Schritte:**

1. `GET /files` aufrufen
2. Header:
```
Authorization: Bearer <token von user2>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body: JSON-Array mit den Dateinamen von `user2` (z.B. `["avatar.jpg", "test.txt"]`)
- Keine Dateien anderer Benutzer enthalten

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.10 — Profilbild abrufen (ohne JWT)

**Ziel:** `GET /users/{username}/avatar` ist öffentlich zugänglich und liefert das Bild mit korrektem `Content-Type`.

**Vorbedingung:** TC-03.2 wurde bestanden — `avatar.jpg` liegt unter `uploads/user2/` und `profile_picture = 'avatar.jpg'` ist gesetzt.

**Schritte:**

1. `GET /users/user2/avatar` aufrufen — **ohne** `Authorization`-Header

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Header: `Content-Type: image/jpeg`
- Header: `Content-Disposition: inline; filename="avatar.jpg"`
- Response Body: Bildbytes (im Browser sichtbares Bild)

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-03.11 — Profilbild abrufen wenn keines gesetzt

**Ziel:** `GET /users/{username}/avatar` liefert `404` wenn kein Profilbild gesetzt ist.

**Vorbedingung:** `user1` hat kein Profilbild gesetzt (nur Textdateien hochgeladen oder gar nichts).

**Schritte:**

1. `GET /users/user1/avatar` aufrufen

**Erwartetes Ergebnis:**

- HTTP Status: `404 Not Found`
- Response Body: `"No avatar set."` oder `"User not found."`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## Testergebnis-Übersicht

| TC | Beschreibung | Ergebnis |
|---|---|---|
| TC-03.1 | Dokument hochladen → Pfad als String | ☐ |
| TC-03.2 | Bild hochladen → Profilbild automatisch gesetzt | ☐ |
| TC-03.3 | Upload in fremden Ordner → 403 | ☐ |
| TC-03.4 | Upload ohne JWT → 403 | ☐ |
| TC-03.5 | Eigene Datei herunterladen → 200 | ☐ |
| TC-03.6 | Fremde Datei herunterladen → 403 | ☐ |
| TC-03.7 | Admin lädt fremde Datei → 200 | ☐ |
| TC-03.8 | Nicht vorhandene Datei → 404 | ☐ |
| TC-03.9 | Eigene Dateien auflisten → nur eigene | ☐ |
| TC-03.10 | Profilbild ohne JWT abrufen → 200 + Content-Type | ☐ |
| TC-03.11 | Profilbild abrufen wenn keines gesetzt → 404 | ☐ |

**Tester:** _______________  
**Datum:** _______________  
**Version:** media-hub-backend `0.0.1-SNAPSHOT`
