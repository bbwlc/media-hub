# US-01: Login

## User Story

> Als Benutzer möchte ich mich mit einem eigenen Konto anmelden und einloggen können,
> damit ich meine persönlichen Inhalte im Media-Hub verwalten kann.

---

## Akzeptanzkriterien

- Ein Benutzer kann sich mit Benutzername und Passwort einloggen.
- Bei korrekten Zugangsdaten erhält der Benutzer einen JWT-Token zurück.
- Bei falschen Zugangsdaten wird `401 Unauthorized` zurückgegeben.
- Das Passwort wird niemals im Klartext gespeichert oder übertragen.
- Nach dem Login kann der Benutzer auf geschützte Endpunkte zugreifen.

---

## Endpoint

| Feld      | Wert                  |
|-----------|-----------------------|
| Method    | `POST`                |
| Path      | `/auth/signIn`        |
| Auth      | Keine                 |
| Erfolg    | `200 OK` + JWT-Token  |
| Fehler    | `401 Unauthorized`    |

### Request Body
```json
{
  "username": "user1",
  "password": "password123"
}
```

### Response Body
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

## Technische Umsetzung

### Gruppe 1 — UserDetailsService
Der `UserDetailsService` lädt den Benutzer anhand des Benutzernamens aus der Datenbank.
In diesem Projekt wird `JdbcUserDetailsManager` verwendet, der direkt auf die `account`-Tabelle zugreift:

```java
manager.setUsersByUsernameQuery(
    "SELECT username, password_hash, true FROM account WHERE username = ?"
);
manager.setAuthoritiesByUsernameQuery(
    "SELECT username, role FROM account WHERE username = ?"
);
```

Der zurückgegebene `UserDetails`-Objekt enthält Benutzername, Passwort-Hash und Rollen.

---

### Gruppe 4 — AuthenticationManager
Der `AuthenticationManager` ist verantwortlich für die eigentliche Authentifizierung.
In diesem Projekt übernimmt `AuthenticationService` diese Aufgabe manuell:

```java
accountRepository.findByUsername(username)
    .filter(account -> passwordEncoder.matches(password, account.getPasswordHash()))
    .map(account -> new TokenData(jwtUtil.generateToken(username)));
```

1. Benutzer wird aus der DB geladen
2. Das eingegebene Passwort wird mit `BCryptPasswordEncoder.matches()` gegen den gespeicherten Hash geprüft
3. Bei Übereinstimmung wird ein JWT generiert und zurückgegeben

---

### Gruppe 7 — Security-Konfiguration
Die `SecurityConfig` legt fest, welche Endpunkte öffentlich zugänglich sind und welche einen JWT erfordern:

```java
.requestMatchers("/auth/signIn", "/auth/register").permitAll()
.anyRequest().authenticated()
```

Der `JWTAuthenticationFilter` wird vor dem Standard-Filter von Spring Security eingefügt und validiert bei jedem Request den `Authorization: Bearer <token>` Header:

```
Request → JWTAuthenticationFilter → UsernamePasswordAuthenticationFilter → Controller
```

---

### PasswordEncoder
Passwörter werden mit **BCrypt** gehasht — einem adaptiven Hash-Algorithmus, der absichtlich langsam ist, um Brute-Force-Angriffe zu erschweren.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Beim Login wird `passwordEncoder.matches(plaintext, hash)` aufgerufen — das Klartextpasswort wird niemals gespeichert.

---

## Implementierungsstand

| Anforderung | Status | Bemerkung |
|---|---|---|
| `POST /auth/signIn` Endpunkt | ✅ | Implementiert in `AuthController` |
| `200 OK` + JWT bei Erfolg | ✅ | `AuthenticationService` gibt `TokenData` zurück |
| `401 Unauthorized` bei falschen Daten | ✅ | Implementiert in `AuthController.signIn` |
| Passwort niemals im Klartext gespeichert | ✅ | BCrypt-Hash in `password_hash` |
| `PasswordEncoder` (BCrypt) | ✅ | Bean in `SecurityConfig` |
| `UserDetailsService` (JdbcUserDetailsManager) | ✅ | Mit eigenen SQL-Queries konfiguriert |
| Rolle wird aus DB gelesen | ✅ | `SELECT username, role FROM account` |
| `JWTAuthenticationFilter` in der Chain | ✅ | Vor `UsernamePasswordAuthenticationFilter` eingehängt |
| `anyRequest().authenticated()` | ✅ | In `SecurityConfig` konfiguriert |

### Offene Punkte

| Problem | Status |
|---|---|
| `/auth/signOut` ist `permitAll` — sollte JWT erfordern | ❌ Offen |
| `/upload/**` ist `permitAll` — sollte JWT erfordern | ❌ Offen |
| `/auth/activate` noch in `permitAll` — Endpunkt existiert nicht mehr | ❌ Überbleibsel |

---

## Ablauf (Sequenz)

```
Client                          Server
  |                                |
  |-- POST /auth/signIn ----------→|
  |   { username, password }       |
  |                                |
  |                    AuthenticationService
  |                    → findByUsername()
  |                    → BCrypt.matches()
  |                    → jwtUtil.generateToken()
  |                                |
  |←-- 200 OK + { token: "..." } --|
  |                                |
  |-- GET /users -----------------→|
  |   Authorization: Bearer <token>|
  |                    JWTAuthenticationFilter
  |                    → validateToken()
  |                    → SecurityContext gesetzt
  |←-- 200 OK -------------------- |
```