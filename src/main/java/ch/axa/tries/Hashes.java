package ch.axa.tries;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static com.google.common.hash.Hashing.hmacSha256;

public class Hashes {

    public static void main(String[] args) {
        String password = "123456";
        String hash = Hashing.sha256()
                .hashString(password, StandardCharsets.UTF_8)
                .toString();
        System.out.println(hash);

        String part1 = "{ \"message\": \"good afternoon\" }";
        String part2 = "{ \"a\": 0815, \"b\": 1234 }";

        Base64.Encoder encoder = Base64.getEncoder();
        String encodedPart1 = encoder.encodeToString(part1.getBytes(StandardCharsets.UTF_8));
        String encodedPart2 = encoder.encodeToString(part2.getBytes(StandardCharsets.UTF_8));

        System.out.println("Encoded Part 1: " + encodedPart1);
        System.out.println("Encoded Part 2: " + encodedPart2);
    }

    private static String base64UrlEncode(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    private static String createSignature(String data, String secret) {
        HashFunction hashFunction = hmacSha256(secret.getBytes(StandardCharsets.UTF_8));
        HashCode hashCode = hashFunction.hashString(data, StandardCharsets.UTF_8);
        return base64UrlEncode(hashCode.asBytes());
    }
}
