# TC-04 — Profile Status Flow

**Referenz:** UC-01-Profil-Status-implement  
**Endpoint:** `PATCH /users/{username}/status?status=<VERIFIED|UNVERIFIED|LOCKED>`

**Testdaten aus `data.sql`:**

| Benutzer | Passwort | Rolle | Status (Startzustand) |
|---|---|---|---|
| `user1` | `password123` | `ROLE_ADMIN` | `VERIFIED` |
| `user2` | `123456` | `ROLE_USER` | `VERIFIED` |
| `user3` | `123456` | `ROLE_USER` | `UNVERIFIED` |

**Hinweis:** Die H2-Datenbank wird bei jedem Neustart neu befüllt (`data.sql`). Zwischen den
Testfällen die App **nicht neu starten**, da TCs aufeinander aufbauen. Alternativ den
Startzustand vor jedem TC in der H2-Konsole prüfen.

---

## TC-04.1 — UNVERIFIED-Account kann sich einloggen

**Ziel:** Ein `UNVERIFIED`-Account ist nicht gesperrt — Login ist erlaubt.

**Schritte:**

1. `POST /auth/signIn` aufrufen
2. Request Body:
```json
{
  "username": "user3",
  "password": "123456"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body enthält JWT-Token:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.2 — Admin setzt UNVERIFIED → VERIFIED

**Ziel:** Ein Admin kann ein Profil auf `VERIFIED` setzen.

**Vorbedingung:** JWT-Token von `user1` (Admin) vorhanden.

**Schritte:**

1. `PATCH /users/user3/status?status=VERIFIED` aufrufen
2. Header:
```
Authorization: Bearer <token von user1>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body:
```json
{
  "username": "user3",
  "status": "VERIFIED"
}
```
- In der H2-Konsole: `SELECT status FROM account WHERE username = 'user3'` liefert `VERIFIED`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.3 — Admin setzt VERIFIED → LOCKED

**Ziel:** Ein Admin kann ein Profil sperren.

**Vorbedingung:** TC-04.2 wurde bestanden (`user3` ist jetzt `VERIFIED`). JWT-Token von `user1` vorhanden.

**Schritte:**

1. `PATCH /users/user3/status?status=LOCKED` aufrufen
2. Header:
```
Authorization: Bearer <token von user1>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body:
```json
{
  "username": "user3",
  "status": "LOCKED"
}
```

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.4 — LOCKED-Account kann sich nicht einloggen

**Ziel:** Ein gesperrter Account wird beim Login abgelehnt — gleiche Antwort wie falsches Passwort.

**Vorbedingung:** TC-04.3 wurde bestanden (`user3` ist `LOCKED`).

**Schritte:**

1. `POST /auth/signIn` aufrufen
2. Request Body:
```json
{
  "username": "user3",
  "password": "123456"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `401 Unauthorized`
- Kein JWT-Token im Response Body
- Die Antwort ist **identisch** mit der bei falschem Passwort (kein Information Leak)

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.5 — Admin hebt Sperre auf: LOCKED → VERIFIED

**Ziel:** Ein Admin kann die Sperre eines Accounts aufheben.

**Vorbedingung:** TC-04.3 wurde bestanden (`user3` ist `LOCKED`). JWT-Token von `user1` vorhanden.

**Schritte:**

1. `PATCH /users/user3/status?status=VERIFIED` aufrufen
2. Header:
```
Authorization: Bearer <token von user1>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body:
```json
{
  "username": "user3",
  "status": "VERIFIED"
}
```

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.6 — Login nach Entsperrung wieder möglich

**Ziel:** Nach Aufhebung der Sperre kann sich der Account wieder einloggen.

**Vorbedingung:** TC-04.5 wurde bestanden (`user3` ist wieder `VERIFIED`).

**Schritte:**

1. `POST /auth/signIn` aufrufen
2. Request Body:
```json
{
  "username": "user3",
  "password": "123456"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body enthält JWT-Token

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.7 — Nicht-Admin kann Status nicht ändern

**Ziel:** Ein normaler Benutzer hat keinen Zugriff auf den Status-Endpoint.

**Vorbedingung:** JWT-Token von `user2` (ROLE_USER) vorhanden.

**Schritte:**

1. `PATCH /users/user3/status?status=LOCKED` aufrufen
2. Header:
```
Authorization: Bearer <token von user2>
```

**Erwartetes Ergebnis:**

- HTTP Status: `403 Forbidden`
- Status von `user3` bleibt unverändert

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.8 — Status-Änderung für nicht existierenden User

**Ziel:** Ein unbekannter Benutzername liefert `404`.

**Vorbedingung:** JWT-Token von `user1` (Admin) vorhanden.

**Schritte:**

1. `PATCH /users/ghost/status?status=VERIFIED` aufrufen
2. Header:
```
Authorization: Bearer <token von user1>
```

**Erwartetes Ergebnis:**

- HTTP Status: `404 Not Found`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-04.9 — Neu registrierter Account startet als UNVERIFIED

**Ziel:** Ein via Registrierungs-Flow angelegter Account hat automatisch den Status `UNVERIFIED`.

**Vorbedingung:** TC-02.5 (Register Confirm) wurde bestanden — `newuser` existiert in der DB.

**Schritte:**

1. H2-Konsole öffnen: `http://localhost:8080/h2-console`
2. SQL ausführen:
```sql
SELECT username, status FROM account WHERE username = 'newuser';
```

**Erwartetes Ergebnis:**

| username | status |
|---|---|
| newuser | UNVERIFIED |

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## Testergebnis-Übersicht

| TC | Beschreibung | Ergebnis |
|---|---|---|
| TC-04.1 | UNVERIFIED-Account kann sich einloggen | ☐ |
| TC-04.2 | Admin: UNVERIFIED → VERIFIED | ☐ |
| TC-04.3 | Admin: VERIFIED → LOCKED | ☐ |
| TC-04.4 | LOCKED-Account: Login abgelehnt (401) | ☐ |
| TC-04.5 | Admin: LOCKED → VERIFIED (Entsperrung) | ☐ |
| TC-04.6 | Login nach Entsperrung wieder möglich | ☐ |
| TC-04.7 | Nicht-Admin: Status-Änderung → 403 | ☐ |
| TC-04.8 | Unbekannter User: Status-Änderung → 404 | ☐ |
| TC-04.9 | Neuer Account via Registrierung → UNVERIFIED | ☐ |

**Tester:** _______________  
**Datum:** _______________  
**Version:** media-hub-backend `0.0.1-SNAPSHOT`