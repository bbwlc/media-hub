# TC-01 — Login Flow

**Referenz:** US-01-Login  
**Endpoint:** `POST /auth/signIn`  
**Testdaten:** Testbenutzer aus `data.sql`

| Benutzer | Passwort | Rolle |
|---|---|---|
| `user1` | `password123` | `ROLE_ADMIN` |
| `user2` | `123456` | `ROLE_USER` |
| `user3` | `123456` | `ROLE_USER` |

---

## TC-01.1 — Erfolgreicher Login

**Ziel:** Korrekte Zugangsdaten liefern einen JWT-Token zurück.

**Vorbedingung:** Anwendung läuft, `user1` ist in der Datenbank vorhanden.

**Schritte:**

1. `POST /auth/signIn` aufrufen
2. Request Body:
```json
{
  "username": "user1",
  "password": "password123"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body enthält ein `token`-Feld:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
- Das Token ist ein gültiger JWT (drei Base64-Teile, getrennt durch `.`)

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-01.2 — Falsches Passwort

**Ziel:** Falsches Passwort wird abgelehnt.

**Schritte:**

1. `POST /auth/signIn` aufrufen
2. Request Body:
```json
{
  "username": "user1",
  "password": "wrongpassword"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `401 Unauthorized`
- Kein JWT-Token im Response Body

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-01.3 — Nicht existierender Benutzer

**Ziel:** Unbekannter Benutzername wird abgelehnt.

**Schritte:**

1. `POST /auth/signIn` aufrufen
2. Request Body:
```json
{
  "username": "ghost",
  "password": "password123"
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `401 Unauthorized`
- Kein JWT-Token im Response Body

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-01.4 — Leere Felder

**Ziel:** Fehlende Pflichtfelder werden abgelehnt.

**Schritte:**

1. `POST /auth/signIn` aufrufen
2. Request Body:
```json
{
  "username": "",
  "password": ""
}
```

**Erwartetes Ergebnis:**

- HTTP Status: `401 Unauthorized` oder `400 Bad Request`
- Kein JWT-Token im Response Body

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-01.5 — JWT bei geschütztem Endpoint verwenden

**Ziel:** Der erhaltene Token ermöglicht den Zugriff auf geschützte Endpoints.

**Vorbedingung:** TC-01.1 wurde bestanden, Token ist vorhanden.

**Schritte:**

1. `GET /auth/me` aufrufen
2. Header setzen:
```
Authorization: Bearer <token aus TC-01.1>
```

**Erwartetes Ergebnis:**

- HTTP Status: `200 OK`
- Response Body enthält den Benutzernamen:
```json
{
  "username": "user1",
  "email": "",
  "role": "ROLE_ADMIN"
}
```

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-01.6 — Zugriff ohne Token abgelehnt

**Ziel:** Geschützte Endpoints ohne JWT liefern `401`.

**Schritte:**

1. `GET /auth/me` aufrufen — **ohne** `Authorization`-Header

**Erwartetes Ergebnis:**

- HTTP Status: `401 Unauthorized`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## TC-01.7 — Ungültiger Token abgelehnt

**Ziel:** Ein manipulierter Token wird abgelehnt.

**Schritte:**

1. `GET /auth/me` aufrufen
2. Header setzen:
```
Authorization: Bearer diesistkeingueltigertoken
```

**Erwartetes Ergebnis:**

- HTTP Status: `401 Unauthorized`

**Ergebnis:** ☐ Bestanden &nbsp; ☐ Fehlgeschlagen

---

## Testergebnis-Übersicht

| TC | Beschreibung | Ergebnis |
|---|---|---|
| TC-01.1 | Erfolgreicher Login | ☐ |
| TC-01.2 | Falsches Passwort | ☐ |
| TC-01.3 | Nicht existierender Benutzer | ☐ |
| TC-01.4 | Leere Felder | ☐ |
| TC-01.5 | JWT bei geschütztem Endpoint verwenden | ☐ |
| TC-01.6 | Zugriff ohne Token abgelehnt | ☐ |
| TC-01.7 | Ungültiger Token abgelehnt | ☐ |

**Tester:** _______________  
**Datum:** _______________  
**Version:** media-hub-backend `0.0.1-SNAPSHOT`