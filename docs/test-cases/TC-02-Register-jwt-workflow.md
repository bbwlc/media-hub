# TC-02 — Register JWT Workflow

**Referenz:** CR-10.3.6, UC-10.3.1, UC-10.3.4, UC-10.3.5  
**Endpoints:**
- `POST /auth/register` — Registrierung starten
- `GET /auth/register/confirm/{token}` — Registrierung bestätigen

**Hinweis zum Bestätigungslink:**  
Der Registrierungslink wird nicht per E-Mail verschickt — `MailService` ist ein Stub, der den Link ins **Konsolen-Log** schreibt. Den Token aus dem Log kopieren und für TC-02.3–TC-02.7 verwenden.

---

## TC-02.1 — Erfolgreiche Registrierung starten

**Ziel:** Gültige Daten liefern `202 Accepted` und schreiben den Bestätigungslink ins Log.

**Vorbedingung:** Benutzername `newuser` existiert noch nicht in der Datenbank.

**Schritte:**

1. `POST /auth/register` aufrufen
2. Request Body:
```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "sicher123"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `202 Accepted`
- Response Body:
```json
{
  "message": "Registration pending. Check your email to activate your account."
}
```
- Im Konsolen-Log erscheint eine Zeile wie:
```
INFO  MailService - Registrierungslink fuer newuser@example.com: http://localhost:8080/auth/register/confirm/eyJhbGci...
```

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-02.2 — Bereits vorhandener Benutzername

**Ziel:** Ein bereits registrierter Benutzername wird mit `409 Conflict` abgelehnt.

**Vorbedingung:** `user1` existiert bereits in der Datenbank (`data.sql`).

**Schritte:**

1. `POST /auth/register` aufrufen
2. Request Body:
```json
{
  "username": "user1",
  "email": "neu@example.com",
  "password": "sicher123"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `409 Conflict`
- Kein Bestätigungslink im Log

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-02.3 — Ungültige E-Mail-Adresse

**Ziel:** Fehlerhaftes E-Mail-Format wird mit `400 Bad Request` abgelehnt.

**Schritte:**

1. `POST /auth/register` aufrufen
2. Request Body:
```json
{
  "username": "testuser",
  "email": "keineemail",
  "password": "sicher123"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `400 Bad Request`
- Kein Account angelegt, kein Bestätigungslink im Log

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-02.4 — Fehlende Pflichtfelder

**Ziel:** Leere Pflichtfelder werden abgelehnt.

**Schritte:**

1. `POST /auth/register` aufrufen
2. Request Body (Passwort fehlt):
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": ""
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `400 Bad Request`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-02.5 — Gültigen Bestätigungslink verwenden (Confirm)

**Ziel:** Gültiger JWT-Link legt den Account an und liefert direkt einen Auth-JWT.

**Vorbedingung:** TC-02.1 wurde bestanden. JWT-Token aus dem Konsolen-Log kopiert.

**Schritte:**

1. JWT-Token aus dem Log-Output von TC-02.1 kopieren
2. `GET /auth/register/confirm/{token}` aufrufen — Token in die URL einfügen
3. Kein `Authorization`-Header nötig (Endpoint ist `permitAll()`)

**Erwartetes Ergebnis:**

- HTTP Status: `201 Created`
- Response Body enthält Auth-JWT:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
- In der H2-Konsole (`http://localhost:8080/h2-console`) existiert ein neuer Eintrag in der `account`-Tabelle mit `username = 'newuser'`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-02.6 — Bestätigungslink ein zweites Mal verwenden (bereits aktiviert)

**Ziel:** Ein bereits verwendeter Link wird mit `404` abgelehnt — der Account ist schon in der DB.

**Vorbedingung:** TC-02.5 wurde bestanden. Denselben Token erneut verwenden.

**Schritte:**

1. Denselben JWT-Token aus TC-02.1 erneut aufrufen:
   `GET /auth/register/confirm/{gleicher-token}`

**Erwartetes Ergebnis:**

- HTTP Status: `404 Not Found`
- Kein zweiter Account angelegt

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-02.7 — Manipulierter / gefälschter Token

**Ziel:** Ein veränderter Token wird mit `404` abgelehnt — die JWT-Signatur ist ungültig.

**Schritte:**

1. Token aus TC-02.1 nehmen und das letzte Zeichen ändern (z.B. `...xYZ` → `...xYy`)
2. `GET /auth/register/confirm/{manipulierter-token}` aufrufen

**Erwartetes Ergebnis:**

- HTTP Status: `404 Not Found`
- Kein Account angelegt

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-02.8 — Auth-JWT nach Confirm für geschützten Endpoint verwenden

**Ziel:** Der bei Confirm zurückgegebene Auth-JWT funktioniert sofort.

**Vorbedingung:** TC-02.5 wurde bestanden. Auth-JWT aus der Confirm-Response vorhanden.

**Schritte:**

1. `GET /auth/me` aufrufen
2. Header setzen:
```
Authorization: Bearer <auth-token aus TC-02.5>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body:
```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "role": "ROLE_USER"
}
```

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## Testergebnis-Übersicht

| TC | Beschreibung | Ergebnis |
|---|---|---|
| TC-02.1 | Erfolgreiche Registrierung starten | ☐ |
| TC-02.2 | Bereits vorhandener Benutzername | ☐ |
| TC-02.3 | Ungültige E-Mail-Adresse | ☐ |
| TC-02.4 | Fehlende Pflichtfelder | ☐ |
| TC-02.5 | Gültigen Bestätigungslink verwenden | ☐ |
| TC-02.6 | Bestätigungslink zweimal verwenden | ☐ |
| TC-02.7 | Manipulierter Token | ☐ |
| TC-02.8 | Auth-JWT nach Confirm verwenden | ☐ |

**Tester:** _______________  
**Datum:** _______________  
**Version:** media-hub-backend `0.0.1-SNAPSHOT`