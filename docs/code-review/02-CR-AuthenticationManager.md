# Code Review: AuthenticationManager

## Befund

Der `AuthenticationManager` existiert in diesem Projekt nicht — weder als Bean noch als explizite Klasse.

## Erklärung

Spring Security bietet einen `AuthenticationManager` als zentralen Einstiegspunkt für die Authentifizierung. Dieses Projekt verwendet ihn **nicht**, sondern implementiert den Login-Flow manuell.

### Wie die Authentifizierung stattdessen funktioniert

**`AuthenticationService`** (`src/main/java/ch/axa/mediaHub/jwt/AuthenticationService.java:26`):
- Lädt den Account direkt aus dem `AccountRepository` per Username.
- Vergleicht das eingegebene Passwort mit dem gespeicherten BCrypt-Hash via `PasswordEncoder.matches()`.
- Generiert bei Erfolg ein JWT über `JWTUtil` und speichert es auf dem `Account`-Entity.

**`JdbcUserDetailsManager`** (`src/main/java/ch/axa/mediaHub/SecurityConfig.java:63`):
- Ist nur für die Security-Filter-Chain zuständig (Spring Security kann damit prüfen, ob ein User existiert).
- Wird **nicht** im Sign-in-Flow verwendet.

### Fluss beim Login (`POST /auth/signIn`)

```
AuthController → AuthenticationService.authenticate()
    → AccountRepository.findByUsername()
    → passwordEncoder.matches()
    → JWTUtil.generateToken()
    → AccountRepository.save()
    → TokenData zurück an Controller
```

## Bewertung

| Aspekt | Bewertung |
|--------|-----------|
| Funktioniert | Ja |
| Nutzt Spring Security-Standard | Nein — manuell implementiert |
| Testbarkeit | Eingeschränkt, da kein Standard-`AuthenticationManager` mockbar |
| Risiko | Mittel — eigene Authentifizierungslogik ist fehleranfälliger als der bewährte Spring-Standard |

## Empfehlung

Für eine produktionsreife Implementierung sollte ein `AuthenticationManager`-Bean in `SecurityConfig` konfiguriert und im `AuthenticationService` verwendet werden:

```java
// SecurityConfig.java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}

// AuthenticationService.java
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(username, password)
);
```

Dies delegiert die Passwortprüfung an Spring Security und profitiert von dessen eingebautem Fehlerhandling und Erweiterbarkeit.