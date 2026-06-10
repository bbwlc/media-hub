# UC-1.1: User Registration — Spring Security, JWT and Session Cookies

## Description
Explains how user registration and authentication work in this project, and how Spring Security, JWT tokens, and session cookies compare as authentication strategies.

---

## How authentication works in this project

### 1. Registration (`POST /auth/register`)
1. Client sends username, password, and email.
2. Spring Security is **not involved** — the endpoint is `permitAll()`.
3. The password is hashed with **BCrypt** and saved to the database.
4. A **JWT token** is generated and returned to the client.

### 2. Sign in (`POST /auth/signIn`)
1. Client sends username and password.
2. `AuthenticationService` loads the user from the database and verifies the BCrypt hash.
3. A **JWT token** is generated and returned to the client.

### 3. Authenticated requests
1. Client sends the JWT in the `Authorization: Bearer <token>` header.
2. `JWTAuthenticationFilter` intercepts the request, validates the token, and sets the user in the `SecurityContext`.
3. Spring Security's authorization check passes — the request reaches the controller.

---

## Spring Security

Spring Security is a filter chain that sits in front of all HTTP requests. In this project it is configured to:
- Allow `/auth/register`, `/auth/signIn`, `/auth/signOut` without authentication (`permitAll()`)
- Require a valid JWT for all other endpoints (`anyRequest().authenticated()`)
- Disable CSRF (not needed for stateless JWT APIs)

The key filter added to the chain is `JWTAuthenticationFilter`, which runs before Spring's own `UsernamePasswordAuthenticationFilter`.

---

## JWT Token (used in this project)

A JWT (JSON Web Token) is a signed, self-contained token. Structure:
```
eyJhbGciOiJIUzI1NiJ9   ← Header (algorithm)
.eyJzdWIiOiJ1c2VyMSJ9  ← Payload (username, expiry)
.abc123signature        ← Signature (HMAC-SHA256 with secret key)
```

**Properties:**
- Stateless — the server does not store the token
- Expires after 1 hour (configured in `JWTUtil`)
- Sent by the client in every request as `Authorization: Bearer <token>`
- Verified by the server using the secret key — no database lookup needed

**Weakness:** A JWT cannot be invalidated before it expires (no blocklist). Sign-out only clears the token stored on the `Account` entity, but the token itself remains valid until expiry.

---

## Session Cookies (not used in this project)

A session cookie is the traditional alternative to JWT.

| | JWT (this project) | Session Cookie |
|---|---|---|
| Storage | Client (localStorage / memory) | Server (session store) |
| Stateless | Yes | No |
| Sent as | `Authorization` header | `Cookie` header (automatic) |
| Invalidation | Only on expiry | Immediate (delete session) |
| Scales horizontally | Yes (no shared state) | Needs sticky sessions or shared store (Redis) |
| CSRF risk | Low (not a cookie) | Yes (requires CSRF tokens) |

Session cookies are managed automatically by the browser. The server creates a session on login, stores it (in memory or Redis), and sends back a `Set-Cookie` header. On every request the browser sends the cookie automatically.

Spring Security supports both strategies. This project uses JWT because the API is consumed by a separate frontend (`media-hub-frontend`) and mobile clients, where stateless authentication is simpler.

---

## Flow diagram

```
Client                        Server
  |                              |
  |-- POST /auth/register -----→ |  permitAll, BCrypt hash, save user, return JWT
  |                              |
  |-- POST /auth/signIn -------→ |  permitAll, verify BCrypt, return JWT
  |                              |
  |-- GET /users               → |  JWTAuthenticationFilter validates token
  |   Authorization: Bearer xxx  |  SecurityContext set → controller runs
  |                              |
  |-- GET /users (no token)  --→ |  JWTAuthenticationFilter skips
  |                              |  anyRequest().authenticated() → 401
```