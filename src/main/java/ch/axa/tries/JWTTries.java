package ch.axa.tries;

import ch.axa.mediaHub.jwt.JWTUtil;

public class JWTTries {

    public static void main(String[] args) {

        JWTUtil jwtUtil = new JWTUtil();
        String token = jwtUtil.generateToken("Blubber");

        System.out.println(token);
    }
}
