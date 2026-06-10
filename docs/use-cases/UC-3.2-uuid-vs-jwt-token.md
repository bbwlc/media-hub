# UC-3.2: UUID Token vs. JWT Token

Both are strings used as tokens, but they work differently.

## UUID Token

A UUID is a random string with no embedded information, e.g.:
```
550e8400-e29b-41d4-a716-446655440000
```

- Carries **no data** — it is just a key to look something up on the server
- The server must **store** it (e.g. in a database or in-memory map) and look it up on every request
- If the server restarts, all tokens are lost
- Used in this project for the email activation flow (`ConcurrentHashMap<String, RegisterDto> pending`)

## JWT Token

A JWT (JSON Web Token) is a structured string with three Base64-encoded parts separated by dots:
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.abc123
      Header                  Payload            Signature
```

The payload carries information directly inside the token, e.g. username and expiry date. The signature is created with a secret key, so the server can verify the token without any database lookup.

- Carries **data inside** (username, expiry, roles)
- **Stateless** — server only needs the secret key to verify it
- Works across server restarts as long as the secret key stays the same
- Used in this project for authentication (`JWTUtil`, `JWTAuthenticationFilter`)

## Comparison

| | UUID Token | JWT Token |
|---|---|---|
| Contains data | No | Yes (username, expiry, ...) |
| Server storage required | Yes | No |
| Survives server restart | No | Yes |
| Verification | Look up in store | Verify signature with secret key |
| Typical use | One-time links (activation, password reset) | Session authentication |

## In this project

| Token | Where | Purpose |
|---|---|---|
| UUID | `ConcurrentHashMap` in `AuthController` | Email activation link |
| JWT | Client-side (sent as `Authorization: Bearer <token>`) | Authenticate API requests |