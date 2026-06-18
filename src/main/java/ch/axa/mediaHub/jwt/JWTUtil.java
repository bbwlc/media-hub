package ch.axa.mediaHub.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JWTUtil {

    // TODO: Move this to a properties file
    private static final String SECRET_KEY = "qQnaHN7TCVjgS2xKJBUfX4LvE9PrZA6s";

    public String generateToken(String subject) {
        long currentTimeMillis = System.currentTimeMillis();
        Date now = new Date(currentTimeMillis);
        Date expiryDate = new Date(currentTimeMillis + 3600000);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateRegistrationToken(String username, String email, String passwordHash) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .claim("email",  email)
                .claim("pwHash", passwordHash)
                .claim("type",   "registration")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 24 * 3_600_000L))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
    }

    public Claims parseRegistrationToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
        if (!"registration".equals(claims.get("type", String.class))) {
            throw new JwtException("Not a registration token");
        }
        return claims;
    }
}
