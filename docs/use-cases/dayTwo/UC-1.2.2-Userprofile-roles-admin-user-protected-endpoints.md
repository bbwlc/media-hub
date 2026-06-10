# UC-1.2.2: User Profile — Roles, Admin/User and Protected Endpoints

## Description
Explains how user roles work in Spring Security, how the project currently handles roles, and how endpoints can be protected per role.

---

## Roles in this project

Spring Security uses **authorities** (roles) to decide what an authenticated user is allowed to do. A role is a string prefixed with `ROLE_`, e.g. `ROLE_USER` or `ROLE_ADMIN`.

### Current state
All users in this project are assigned the hardcoded role `ROLE_USER`. This is configured in `SecurityConfig`:

```java
manager.setAuthoritiesByUsernameQuery(
    "SELECT username, 'ROLE_USER' FROM account WHERE username = ?"
);
```

There is no `ROLE_ADMIN` yet. Every authenticated user has the same permissions.

### What needs to change to support roles
1. Add a `role` column to the `account` table (`ROLE_USER` / `ROLE_ADMIN`)
2. Update the authorities query to read the role from the database
3. Protect endpoints with `@PreAuthorize`

---

## User Profile

The `Account` entity currently holds:

| Field | Description |
|---|---|
| `id` | Auto-generated primary key |
| `username` | Unique login name |
| `passwordHash` | BCrypt-hashed password |
| `email` | Contact address |
| `token` | Last issued JWT (cleared on sign-out) |

A future profile extension could add: `role`, `displayName`, `profilePicture`, `createdAt`.

---

## Role-based access control

Spring Security evaluates roles at two levels:

### 1. URL level — in `SecurityConfig`
Restrict entire paths to a specific role:
```java
.requestMatchers("/admin/**").hasRole("ADMIN")
.requestMatchers("/users/**").hasRole("USER")
```

### 2. Method level — with `@PreAuthorize`
Restrict individual controller methods. Enabled in this project via `@EnableMethodSecurity` on `SecurityConfig`.

```java
@GetMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<List<Account>> getAllUsers() { ... }

@GetMapping("/profile")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<?> getProfile() { ... }
```

---

## Protected endpoint examples

| Endpoint | Required role | Description |
|---|---|---|
| `GET /users` | `ROLE_ADMIN` | List all users — admin only |
| `GET /profile` | `ROLE_USER` | Own profile — any logged-in user |
| `POST /upload/{username}` | `ROLE_USER` | Upload to own folder |
| `DELETE /users/{id}` | `ROLE_ADMIN` | Delete a user account |

---

## Flow: role check on a protected request

```
Client                          Server
  |                                |
  |-- GET /users ----------------→ |
  |   Authorization: Bearer xxx    |
  |                                |
  |                     JWTAuthenticationFilter
  |                     validates token → sets ROLE_USER in SecurityContext
  |                                |
  |                     @PreAuthorize("hasRole('ADMIN')")
  |                     ROLE_USER ≠ ROLE_ADMIN → 403 Forbidden
  |←-- 403 Forbidden ------------- |
```

```
Client                          Server
  |                                |
  |-- GET /users ----------------→ |
  |   Authorization: Bearer xxx    |
  |                                |
  |                     JWTAuthenticationFilter
  |                     validates token → sets ROLE_ADMIN in SecurityContext
  |                                |
  |                     @PreAuthorize("hasRole('ADMIN')")
  |                     ROLE_ADMIN ✓ → controller runs
  |←-- 200 OK + user list -------- |
```

---

## What 401 vs 403 means

| Code | Meaning | Cause |
|---|---|---|
| `401 Unauthorized` | Not authenticated | No token or invalid token |
| `403 Forbidden` | Authenticated but not authorized | Valid token, but wrong role |