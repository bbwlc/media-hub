# UC-01 — Swagger / OpenAPI-Dokumentation implementieren

## Ziel

Die REST-API des Media Hub Backends wird mit **SpringDoc OpenAPI** (Swagger UI) dokumentiert.
Ziel ist eine automatisch generierte, browserbasierte API-Übersicht, die alle Endpoints inklusive
Request-/Response-Schema, Authentifizierungspflicht und Beispielwerten anzeigt.

---

## Dependency hinzufügen

In `pom.xml` innerhalb von `<dependencies>`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.9</version>
</dependency>
```

> **Warum SpringDoc und nicht Springfox?**
> Springfox unterstützt Spring Boot 3.x nicht mehr. SpringDoc ist der aktive Nachfolger
> und läuft nativ mit Spring Boot 3.5 / Jakarta EE.

---

## URLs nach dem Start

| Zweck | URL |
|---|---|
| Swagger UI (Browser) | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |

---

## SecurityConfig anpassen

Die Swagger-Routen müssen in `SecurityConfig` als `permitAll()` freigegeben werden, sonst
blockt Spring Security den Zugriff:

```java
.requestMatchers(
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
).permitAll()
```

**Wo einfügen:** In `SecurityConfig.java`, in der `securityFilterChain`-Methode, zusammen
mit den bestehenden `.requestMatchers("/signIn", "/register/**").permitAll()`-Zeilen.

---

## JWT-Authentifizierung in Swagger konfigurieren

Damit gesicherte Endpoints direkt in der Swagger UI testbar sind, wird ein
`OpenAPI`-Bean mit Bearer-Auth-Schema definiert.

Neue Klasse `SwaggerConfig.java` im Package `ch.axa.mediaHub`:

```java
package ch.axa.mediaHub;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Media Hub API")
                        .version("1.0")
                        .description("REST API für den Media Hub – Authentifizierung via JWT Bearer Token"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

---

## Endpoints annotieren (optional, aber empfohlen)

SpringDoc generiert die Dokumentation automatisch aus den `@RestController`-Klassen.
Mit den folgenden Annotationen können Beschreibungen und Beispiele ergänzt werden:

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Authentifizierung", description = "Login, Logout und Registrierung")
@RestController
public class AuthController {

    @Operation(summary = "Login", description = "Gibt einen JWT zurück, der für alle gesicherten Endpoints benötigt wird.")
    @PostMapping("/signIn")
    public ResponseEntity<?> signIn(...) { ... }
}
```

---

## Ablauf

```
Entwickler / Tester
        |
        |-- GET http://localhost:8080/swagger-ui/index.html
        |
        v
┌──────────────────────────────────────────┐
│  Swagger UI                              │
│                                          │
│  1. Endpoint auswählen (z.B. POST /signIn│
│  2. Request-Body eingeben                │
│  3. "Execute" klicken                    │
│  4. JWT aus Response kopieren            │
│  5. "Authorize" → Bearer-Token einfügen  │
│  6. Gesicherte Endpoints aufrufen        │
└──────────────────────────────────────────┘
```

---

## Offene TODOs

| # | Beschreibung | Wo |
|---|---|---|
| 1 | Dependency `springdoc-openapi-starter-webmvc-ui` in `pom.xml` eintragen | `pom.xml` |
| 2 | Swagger-Routen in `SecurityConfig` als `permitAll()` freigeben | `SecurityConfig.java` |
| 3 | `SwaggerConfig`-Klasse mit `OpenAPI`-Bean anlegen (JWT Bearer Auth) | `ch.axa.mediaHub.SwaggerConfig` |
| 4 | Anwendung starten und `http://localhost:8080/swagger-ui/index.html` prüfen | Browser |
| 5 | `@Tag` und `@Operation` auf `AuthController` und `FileUploadController` ergänzen | Controller-Klassen |