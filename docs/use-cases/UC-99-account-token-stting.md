# UC-99: Account Token Field — Current State and Open Issues

## What the `token` field does

The `Account` entity stores the last issued JWT in a `token` column. It is set on sign-in and cleared (set to `null`) on sign-out.

```java
@Column(nullable = true /* TODO: unique = true */)
private String token;
```

## The TODO: `unique = true`

Adding `unique = true` would enforce at the database level that no two users hold the same token. The constraint was not added because of a null problem: when multiple users sign out, multiple rows would have `token = null`. Depending on the database, a UNIQUE constraint may reject multiple NULL values, causing sign-out to fail.

## Why the current design does not provide real security

JWT tokens are **stateless** — they are validated by their signature, not by looking up the database. The `JWTAuthenticationFilter` only checks the signature and expiry. It never reads the `token` column.

This means:

- A signed-out user still holds a valid JWT until it expires (1 hour)
- Deleting or changing the `token` column does not invalidate the JWT
- The sign-out only clears the database field — it does not actually revoke access

## Proper solution: token blocklist

A blocklist stores invalidated tokens and rejects them in `JWTAuthenticationFilter`.

**Flow:**
1. User calls `POST /auth/signOut`
2. The current token is added to a blocklist table (e.g. `invalidated_token`)
3. On every request, `JWTAuthenticationFilter` checks whether the token is in the blocklist
4. If it is → return `401 Unauthorized`

**Trade-off:** this reintroduces state on the server, which partially removes the stateless advantage of JWT. The blocklist only needs to hold tokens until they expire, so it can be cleaned up automatically by a scheduled job.

## Decision

Not implemented yet. The `token` field on `Account` remains as-is until a blocklist or an alternative invalidation strategy is introduced.

**Related:** `JWTUtil` — token expiry is set to 1 hour. Shortening this reduces the window of risk after sign-out without needing a blocklist.