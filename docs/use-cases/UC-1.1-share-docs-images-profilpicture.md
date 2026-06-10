# UC-1.1: Share Documents, Images and Profile Picture

## Description
An authenticated user can upload files (documents, images, profile picture) to their personal folder and download them again. Each user can only access their own files.

## Actor
Authenticated user

## Preconditions
- The user is signed in and holds a valid JWT token.

## Use Cases

### UC-1.1.1 — Upload a file
1. Client sends `POST /upload/{username}` with the file as `multipart/form-data`.
2. The system checks that the `{username}` in the path matches the authenticated user.
3. The file is saved to `uploads/<username>/<filename>` on the server.
4. The system responds with `200 OK` and the file path.

### UC-1.1.2 — Download a file
1. Client sends `GET /download/{username}?file=<filename>`.
2. The system checks that the `{username}` in the path matches the authenticated user.
3. The file is read from `uploads/<username>/<filename>`.
4. The system responds with `200 OK` and the file content as a byte stream.

### UC-1.1.3 — Upload a profile picture
Same flow as UC-1.1.1. The client uploads an image file (e.g. `profile.jpg`). The filename is used to identify the profile picture — agreed convention: `profile.<ext>`.

## Alternative Flows

| Situation | Response |
|---|---|
| `{username}` does not match the authenticated user | `403 Forbidden` |
| File not found on download | `404 Not Found` |
| Upload fails (I/O error) | `500 Internal Server Error` |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/upload/{username}` | Upload a file to the user's folder |
| `GET` | `/download/{username}?file=<filename>` | Download a file from the user's folder |

### Upload request (multipart/form-data)
```
POST /upload/user1
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <binary>
```

### Download request
```
GET /download/user1?file=profile.jpg
Authorization: Bearer <token>
```

## Postconditions
- Uploaded files are stored under `uploads/<username>/` on the server filesystem.
- No user can read or overwrite another user's files.