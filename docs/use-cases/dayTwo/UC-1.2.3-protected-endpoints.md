# UC-1.2.3: Protected Endpoints

## Current state

| Endpoint | Method | Protection | Status |
|---|---|---|---|
| `/auth/register` | `POST` | None (`permitAll`) | Correct — public |
| `/auth/signIn` | `POST` | None (`permitAll`) | Correct — public |
| `/auth/signOut` | `POST` | None (`permitAll`) | **Should require JWT** |
| `/auth/activate` | `GET` | None (`permitAll`) | **Leftover — can be removed** |
| `/auth/protected` | `GET` | JWT required | Demo endpoint |
| `/users` | `GET` | JWT + `ROLE_ADMIN` | Correct |
| `/upload/{username}` | `POST` | None (`permitAll`) | **Should require JWT** |
| `/download/{username}` | `GET` | JWT required | Correct |

## Issues

### 1. `/auth/signOut` is `permitAll`
Sign-out clears the token on the `Account` entity. Without authentication, any unauthenticated request can call it. It should require a valid JWT so only the token owner can sign out.

**Fix:** remove `/auth/signOut` from the `permitAll` list in `SecurityConfig`.

### 2. `/upload/{username}` is `permitAll`
File uploads are currently open to everyone — no JWT required. The `FileUploadController` checks that the path variable `{username}` matches the authenticated user, but since the endpoint is `permitAll`, no authentication is enforced before the controller runs.

**Fix:** remove `/upload/**` from the `permitAll` list in `SecurityConfig`.

### 3. `/auth/activate` is still registered
The email activation endpoint is a leftover from a previous implementation and no longer has any function. It should be removed from both `SecurityConfig` and the controller.

## Target state after fixes

| Endpoint | Method | Required | Who |
|---|---|---|---|
| `/auth/register` | `POST` | None | Public |
| `/auth/signIn` | `POST` | None | Public |
| `/auth/signOut` | `POST` | JWT | Any authenticated user |
| `/auth/protected` | `GET` | JWT | Any authenticated user |
| `/users` | `GET` | JWT + `ROLE_ADMIN` | Admin only |
| `/upload/{username}` | `POST` | JWT | Owner only (enforced in controller) |
| `/download/{username}` | `GET` | JWT | Owner only (enforced in controller) |