package ch.axa.mediaHub.jwt;

import io.jsonwebtoken.Claims;
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
}
