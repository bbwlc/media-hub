# Reflexion 01 — Statusfeld-Update vs. Aktions-Endpunkte

---

## 1. Unterschied zwischen PUT/PATCH auf Statusfeld und Aktions-Endpunkten

### Der generische Ansatz: `PATCH /users/{username}/status`

Im ersten Schritt (UC-01) wurde ein generischer Endpoint implementiert:

```java
// UserController.java
@PatchMapping("/{username}/status")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> updateStatus(
        @PathVariable String username,
        @RequestParam ProfileStatus status) {

    return accountRepository.findByUsername(username)
            .map(account -> {
                account.setStatus(status);          // ← jeder Wert wird akzeptiert
                accountRepository.save(account);
                return ResponseEntity.ok(...);
            })
            .orElse(ResponseEntity.notFound().build());
}
```

Der Client entscheidet, welchen Status er setzt — der Server prüft nicht, ob der Übergang
überhaupt erlaubt ist. Ein Admin könnte z. B. direkt von `UNVERIFIED` auf `LOCKED` setzen,
ohne dass das Profil je `VERIFIED` war. Oder ein `LOCKED`-Profil auf `UNVERIFIED` zurücksetzen,
was laut Zustandsmaschine nicht vorgesehen ist.

---

### Der funktionale Ansatz: Aktions-Endpunkte

```java
// ProfileActionController.java
@PostMapping("/api/me/profile/{id}/verify")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> verify(@PathVariable Long id) {
    Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (account.getStatus() != ProfileStatus.UNVERIFIED) {      // ← Guard
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Cannot verify: profile is already " + account.getStatus());
    }

    account.setStatus(ProfileStatus.VERIFIED);
    return ResponseEntity.ok(accountRepository.save(account));
}
```

Hier drückt der Client eine **Absicht** aus (`verify`), nicht einen Zielwert. Der Server prüft,
ob die Absicht im aktuellen Zustand erlaubt ist — und lehnt sie andernfalls mit `409 Conflict` ab.

### Gegenüberstellung

| Kriterium | `PATCH /status?status=X` | `/verify`, `/lock`, `/unlock` |
|---|---|---|
| Was der Client sendet | Zielstatus (Datenwert) | Absicht (Aktion) |
| Wer die Regeln kennt | Client (Frontend) | Server |
| Ungültiger Übergang | wird akzeptiert | `409 Conflict` |
| Selbstdokumentation | nein — Regeln extern | ja — Endpoint-Name = Aktion |
| Erweiterbarkeit | eine Methode für alles | pro Übergang eine Methode |
| Testbarkeit | schwer: alle Kombinationen | klar: pro Aktion ein Testfall |

---

## 2. Warum unerlaubte Übergänge serverseitig abweisen?

### Das Frontend ist keine Sicherheitsschicht

Das Frontend kann übersprungen werden. Ein Angreifer oder ein fehlerhaftes Frontend kann
direkt HTTP-Requests senden — ohne jede Prüfung, die das Frontend vornimmt:

```
curl -X PATCH "http://localhost:8080/users/user3/status?status=VERIFIED" \
     -H "Authorization: Bearer <admin-token>"
```

Wenn der Server alle Status-Werte kommentarlos akzeptiert, ist der Übergang von `LOCKED`
zurück zu `UNVERIFIED` genauso möglich wie jeder andere — obwohl das laut Domänenlogik
nicht erlaubt sein soll.

### Race Conditions in Multi-User-Systemen

Stellen Sie sich vor, zwei Admins arbeiten gleichzeitig:
- Admin A liest: `user3` ist `UNVERIFIED`
- Admin B setzt: `user3` → `LOCKED`
- Admin A setzt: `user3` → `VERIFIED` (er hat den alten Stand gesehen)

Mit serverseitiger Guard-Prüfung liest Admin A den **aktuellen** Zustand direkt vor der
Änderung — und bekommt `409 Conflict`, weil `LOCKED → VERIFIED` über `/verify` nicht erlaubt ist.
Mit einem blinden PATCH würde Admin A den Status von `LOCKED` auf `VERIFIED` setzen,
obwohl das nie gewollt war.

### Konsistenz unabhängig vom Client

Mobile App, Browser-Frontend, Postman, automatisierte Skripts — alle Clients sprechen
dieselbe API. Die Regeln der Zustandsmaschine sind genau **einmal** implementiert, auf dem
Server. Es gibt keine Doppelung, keine Divergenz zwischen verschiedenen Clients.

---

## 3. Weitere Anwendungsfälle des Musters (Zustand + Aktions-Endpunkte)

### Beispiel: Bestellprozess (Order Lifecycle)

Eine Bestellung in einem Online-Shop durchläuft definierte Zustände:

```
CREATED → PAID → SHIPPED → DELIVERED
              ↓
           CANCELLED  (nur aus CREATED oder PAID erlaubt)
```

Statt eines generischen `PATCH /orders/{id}/status?status=SHIPPED` wären folgende
Aktions-Endpunkte sinnvoll:

```
POST /orders/{id}/pay       → CREATED → PAID
POST /orders/{id}/ship      → PAID → SHIPPED
POST /orders/{id}/deliver   → SHIPPED → DELIVERED
POST /orders/{id}/cancel    → CREATED oder PAID → CANCELLED
```

**Warum dieses Muster hier besonders wichtig ist:**

- Eine bereits versendete Bestellung (`SHIPPED`) darf nicht mehr storniert werden — das wäre
  ohne Guard mit PATCH möglich.
- Eine bezahlte Bestellung muss vor dem Versand als `PAID` vorliegen — `/ship` prüft
  serverseitig, ob die Bezahlung erfolgt ist.
- Rückerstattungen, Lagerbuchungen oder E-Mail-Benachrichtigungen können pro Aktions-Endpoint
  gezielt ausgelöst werden — ohne dass ein generisches PATCH entscheiden muss, welche
  Nebeneffekte eine bestimmte Statusänderung hat.

In einem Multi-User-System mit Lager, Buchhaltung und Versanddienstleister ist es kritisch,
dass der Server garantiert, dass keine Bestellung zweimal versendet oder nach der Lieferung
storniert werden kann — unabhängig davon, welches System den Request schickt.

---

## Fazit

Das Muster **Zustand + Aktions-Endpunkte** trennt zwei Verantwortlichkeiten klar:

- **Was** kann ein Objekt werden? → Zustandsmaschine (Enum + Guards auf dem Server)
- **Wann** darf es wechseln? → Aktions-Endpoint (jeder Übergang als eigene Route)

Das Frontend wird damit zur reinen Darstellungsschicht. Die Geschäftsregeln leben dort, wo
sie hingehören: auf dem Server, getestet, versioniert und für alle Clients verbindlich.