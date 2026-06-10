# Code Review — Initial Upload Feature (Branch: `fileupload`)

**Date:** 2026-06-10  
**Reviewer:** Claude Code (automated, high-effort)  
**Scope:** All changes on branch `fileupload` vs `main`, including uncommitted working-tree changes  
**Files reviewed:**
- `src/main/java/ch/axa/mediaHub/media/FileService.java`
- `src/main/java/ch/axa/mediaHub/media/FileUploadController.java`
- `src/main/java/ch/axa/mediaHub/SecurityConfig.java`
- `src/main/java/ch/axa/mediaHub/restcontroller/AuthController.java`
- `src/main/resources/data.sql`

---

## Summary

The branch introduces file upload/download functionality and switches the `UserDetailsService` from a custom implementation to `JdbcUserDetailsManager`. There are **4 critical/high bugs** that will either prevent the feature from working at all or allow security bypasses. These must be fixed before merging.

---

## Findings

### F-01 — Download endpoint always returns 404 (Critical — Functional Breakage)

**File:** `FileUploadController.java:53`

```java
Optional<byte[]> fileData = fileService.downloadFile(loggedInUser, filename);
fileData.ifPresent(bytes -> ResponseEntity.ok()          // result is DISCARDED
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(bytes));
return ResponseEntity                                    // always reached
        .status(HttpStatus.NOT_FOUND)
        .body("Datei nicht gefunden.");
```

`Optional.ifPresent()` returns `void` — the `ResponseEntity` built inside the lambda is immediately thrown away. The method falls through unconditionally to the `NOT_FOUND` response. **Every download will return HTTP 404**, regardless of whether the file exists.

**Fix:** Replace with the same `.map(...).orElse(...)` pattern already used correctly in the upload handler:

```java
return fileData
    .map(bytes -> ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .<byte[]>body(bytes))
    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
```

---

### F-02 — `downloadFile` ignores `username`, breaking file isolation (Critical — Security / IDOR)

**File:** `FileService.java:37`

```java
public Optional<byte[]> downloadFile(String username, String filename) {
    ...
    Path filePath = Paths.get(basePath, "uploads", filename);  // username NOT used!
    ...
}
```

The method accepts `username` but never uses it. Files are uploaded to `uploads/{username}/{filename}` (correctly in `uploadFile`), but downloaded from `uploads/{filename}` — a path that never exists. This causes two problems:

1. **No file is ever found** — legitimate downloads always return empty because the path is wrong.
2. **Horizontal privilege escalation** — once the path is "fixed" naively without the username, any authenticated user can read any other user's file by guessing the filename.

**Fix:**

```java
Path filePath = Paths.get(basePath, "uploads", username, filename);
```

---

### F-03 — `/upload/**` is `permitAll()`, enabling unauthenticated uploads (High — Auth Bypass)

**File:** `SecurityConfig.java:40`

```java
.requestMatchers("/h2-console/**",
                 "/signIn", "/signOut",
                 "/upload/**").permitAll()    // <-- upload is open to the world
//.requestMatchers("/upload/**").authenticated()  // <-- this is commented out
```

The upload endpoint is accessible to unauthenticated callers. When no JWT is provided, Spring injects an `AnonymousAuthenticationToken` whose `getName()` returns `"anonymousUser"`. The controller's ownership check then compares `username` (from the path) to `"anonymousUser"`:

```
POST /upload/anonymousUser  →  loggedInUser = "anonymousUser"  →  check passes  →  file written
```

An unauthenticated attacker can upload arbitrary files to the server with no credentials.

**Fix:** Remove `/upload/**` from the permit-all list and uncomment the `.authenticated()` rule, or apply it as a separate matcher:

```java
.requestMatchers("/h2-console/**", "/signIn", "/signOut").permitAll()
.requestMatchers("/upload/**", "/download/**").authenticated()
```

---

### F-04 — Unsanitized `getOriginalFilename()` enables path traversal on upload (High — Security)

**File:** `FileService.java:24`

```java
Path filePath = userFolder.resolve(Objects.requireNonNull(file.getOriginalFilename()));
file.transferTo(filePath.toFile());
```

`getOriginalFilename()` is fully attacker-controlled. A filename of `../../application.properties` will cause `resolve()` to escape the user's upload directory and overwrite an arbitrary file on the server.

**Fix:** Strip any path components from the filename before resolving:

```java
String safeName = Paths.get(file.getOriginalFilename()).getFileName().toString();
Path filePath = userFolder.resolve(safeName);
```

---

### F-05 — `signIn` response changed from a bare token string to a `TokenData` object (Medium — Breaking API Change)

**File:** `AuthController.java:41`

Before:

```java
.map(token -> ResponseEntity.ok(token.getToken()))   // body: "eyJ..."
```

After:

```java
return ResponseEntity.ok(tokenData.get());           // body: {"token":"eyJ...","...":"..."}
```

Any existing frontend doing `const token = response.data` now receives a JavaScript object instead of a string. `localStorage.setItem('token', response.data)` would store `[object Object]`, breaking all subsequent authenticated requests.

**Fix:** Either keep returning the plain string (no change to the existing API), or coordinate a version bump with the frontend and update it to use `response.data.token`.

---

### F-06 — `DataSource` injected redundantly as a method parameter, shadowing the class field (Low — Confusion)

**File:** `SecurityConfig.java:34`

```java
public SecurityConfig(DataSource dataSource) {
    this.dataSource = dataSource;          // field set via constructor injection
}

@Bean
public SecurityFilterChain securityFilterChain(...,
                                               DataSource dataSource) throws Exception {  // redundant
    // dataSource (parameter) shadows this.dataSource within this method
    // but the parameter is never actually used here
```

The `DataSource` parameter is never used inside `securityFilterChain`. It silently shadows `this.dataSource` for the method scope and adds an unnecessary second Spring-managed injection. Remove the parameter.

---

### F-07 — `Files.readAllBytes` loads the entire file into heap (Low — Efficiency)

**File:** `FileService.java:40`

```java
return Optional.of(Files.readAllBytes(filePath));
```

The entire file is buffered into a `byte[]` before a single byte is sent to the client. For large media files this doubles memory usage and risks `OutOfMemoryError` under concurrent load.

**Fix:** Return a `Resource` (e.g., `FileSystemResource`) and change the controller to return `ResponseEntity<Resource>`. Spring MVC will then stream bytes directly to the response with O(1) memory cost.

---

### F-08 — Ownership check duplicated in every endpoint handler (Low — Maintainability)

**File:** `FileUploadController.java:24` and `:43`

```java
String loggedInUser = SecurityContextHolder.getContext().getAuthentication().getName();
if (!username.equals(loggedInUser)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("...");
}
```

This three-line guard is copy-pasted verbatim in both handlers. `@EnableMethodSecurity` is already active in `SecurityConfig`, so the idiomatic solution is a single annotation — and harder to forget on new endpoints:

```java
@PreAuthorize("#username == authentication.name")
@PostMapping("/upload/{username}")
public ResponseEntity<?> uploadToUserFolder(@PathVariable String username, ...) {
    // no manual check needed
}
```

---

### F-09 — Upload base path hardcoded and duplicated (Low — Configuration)

**File:** `FileService.java:20` and `:36`

```java
String basePath = System.getProperty("user.dir");
Path userFolder = Paths.get(basePath, "uploads", username);
```

`System.getProperty("user.dir")` is called on every request and the string `"uploads"` is a magic literal repeated in both methods. If the deployment target requires `/data/uploads` (e.g. a Docker container), every occurrence must be found and changed manually.

**Fix:** Inject the path once via `@Value`:

```java
@Value("${media.upload-dir:uploads}")
private String uploadDir;

private Path resolveUserDir(String username) {
    return Paths.get(uploadDir).toAbsolutePath().resolve(username);
}
```

---

### F-10 — `Logger.getAnonymousLogger()` creates a new unregistered logger on every call (Low — Logging)

**File:** `FileService.java:29` and `:43`

```java
Logger.getAnonymousLogger().log(Level.WARNING, e.getMessage(), e);
```

`Logger.getAnonymousLogger()` allocates a new `Logger` instance that is not registered in the logger hierarchy. Spring Boot's `application.properties` logging configuration and `logback-spring.xml` appenders have no effect on these entries.

**Fix:** Use a single static SLF4J logger (the Spring Boot standard):

```java
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileService.class);
// ...
log.warn("Failed to upload file: {}", e.getMessage(), e);
```

---

## Quick Reference

| # | File | Line | Severity | Category | One-liner |
|---|------|------|----------|----------|-----------|
| F-01 | `FileUploadController.java` | 53 | **Critical** | Functional | Download always returns 404 — `ifPresent` result discarded |
| F-02 | `FileService.java` | 37 | **Critical** | Security (IDOR) | `username` ignored in `downloadFile`, files never found and isolation broken |
| F-03 | `SecurityConfig.java` | 40 | **High** | Security (Auth) | `/upload/**` is `permitAll()`, anonymous upload bypass via `"anonymousUser"` |
| F-04 | `FileService.java` | 24 | **High** | Security (Path Traversal) | `getOriginalFilename()` unsanitized, can overwrite arbitrary server files |
| F-05 | `AuthController.java` | 41 | **Medium** | API Contract | `signIn` now returns `TokenData` object instead of bare token string |
| F-06 | `SecurityConfig.java` | 34 | **Low** | Confusion | `DataSource` param shadows class field, never used |
| F-07 | `FileService.java` | 40 | **Low** | Efficiency | `readAllBytes` buffers entire file in heap |
| F-08 | `FileUploadController.java` | 24 | **Low** | Maintainability | Ownership check copy-pasted; use `@PreAuthorize` instead |
| F-09 | `FileService.java` | 20 | **Low** | Configuration | Upload path hardcoded; inject via `@Value` |
| F-10 | `FileService.java` | 29 | **Low** | Logging | Anonymous logger bypasses Spring Boot log config |