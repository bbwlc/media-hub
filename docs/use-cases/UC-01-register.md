# UC-01: Register

## Description
A new user creates an account by providing a username, password, and email address. On success the system returns a JWT token so the user is immediately authenticated.

## Actor
Unauthenticated user

## Preconditions
- The username is not already taken.

## Main Flow
1. Client sends `POST /auth/register` with a JSON body containing `username`, `password`, and `email`.
2. The system checks whether the username already exists.
3. The password is hashed with BCrypt.
4. A new `User` entity is persisted with the username, hashed password, and email.
5. The system generates a signed JWT token (HS256, 1h expiry) with the username as subject.
6. The system responds with `201 Created` and the JWT token in the response body.

## Alternative Flow — Username already taken
- At step 2: if the username exists, the system returns `409 Conflict` and no user is created.

## Endpoint

| Field       | Value                  |
|-------------|------------------------|
| Method      | `POST`                 |
| Path        | `/auth/register`       |
| Auth        | None required          |
| Request     | `application/json`     |
| Success     | `201 Created`          |
| Conflict    | `409 Conflict`         |

### Request body (`RegisterDto`)
```json
{
  "username": "user1",
  "password": "password123",
  "email": "user1@example.com"
}
```

### Response body (`TokenData`)
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

## Postconditions
- A new `User` row exists in the database with a BCrypt-hashed password.
- The plain-text password is never stored.
- The client holds a JWT token and can immediately make authenticated requests.