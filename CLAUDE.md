# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=PostsBackendApplicationTests

# Build without tests
./mvnw clean package -DskipTests
```

The H2 console is available at `http://localhost:8080/h2-console` while the app is running (JDBC URL: `jdbc:h2:mem:media-hub-db`, user: `mh-user`, password: `123456`).

## Architecture

Spring Boot 3.5 / Java 21 REST backend with JWT-based stateless authentication and per-user file storage.

**Request flow:**
```
Request → JWTAuthenticationFilter → UsernamePasswordAuthenticationFilter → DebugFilter → Controller
```

`JWTAuthenticationFilter` reads the `Authorization: Bearer <token>` header, validates it via `JWTUtil` (HS256, 1h expiry), and sets the `SecurityContext`. Requests without a valid token still pass through — the filter only returns 401 if a token is present but invalid.

**Auth flow:**
- `POST /signIn` → `AuthController` → `AuthenticationService` authenticates against BCrypt hash in DB, generates a JWT via `JWTUtil`, stores the token on the `Account` entity, and returns it.
- `POST /signOut` → clears the token field on the `Account` entity. Note: the JWT itself remains valid until expiry since there is no token blocklist.

**User details:**
`SecurityConfig` uses `JdbcUserDetailsManager` with custom queries mapping `account.username` / `account.password_hash` to Spring Security's user model. All users get the hardcoded authority `ROLE_USER`. A `CustomUserDetailsService` backed by `AccountRepository` also exists but is commented out.

**File storage:**
`FileService` stores files at `<working-dir>/uploads/<username>/<filename>` on the local filesystem. `FileUploadController` enforces that the path variable `{username}` matches the authenticated principal — users can only access their own folder. Note: there is a bug in `FileService.downloadFile` — it builds the path from `uploads/<filename>` instead of `uploads/<username>/<filename>`.

**Database:**
In-memory H2. Schema is defined in `schema.sql` (the `account` table), seeded via `data.sql` (three test users with BCrypt passwords). JPA `ddl-auto` is set to `validate`, so schema changes must go in `schema.sql`.

**Known TODOs in the code:**
- JWT secret key is hardcoded in `JWTUtil` (should move to `application.properties`)
- `@CrossOrigin("*")` in `AuthController` should be restricted for production
- `upload/**` is currently `permitAll()` in `SecurityConfig` (commented-out line would require auth)
- `Account.token` should be `unique = true` once sign-out is fully implemented