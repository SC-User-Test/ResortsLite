package com.demo.resortslite;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Stateless JWT utility — replaces server-side HttpSession storage (cz-java-0063).
 *
 * The signing secret is injected at runtime via the JWT_SECRET environment variable,
 * which ECS Fargate resolves from AWS Secrets Manager. This ensures no session state
 * is held in container memory, making the service safe for horizontal scaling and
 * container restarts.
 *
 * Usage in ECS task definition:
 *   "secrets": [{ "name": "JWT_SECRET", "valueFrom": "arn:aws:secretsmanager:..." }]
 */
@Component
public class JwtUtil {

    /** Token validity: 1 hour (3 600 000 ms). */
    private static final long EXPIRATION_MS = 3_600_000L;

    /**
     * Derives the HMAC-SHA256 signing key from the JWT_SECRET environment variable.
     * Falls back to a development-only placeholder when the variable is absent so
     * the application still starts locally; the placeholder MUST NOT be used in
     * production ECS deployments.
     */
    private SecretKey signingKey() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isEmpty()) {
            secret = "dev-only-placeholder-secret-32chars!";
        }
        // Pad / truncate to exactly 32 bytes for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
        return Keys.hmacShaKeyFor(padded);
    }

    /**
     * Generates a signed JWT embedding the supplied claims.
     *
     * @param claims arbitrary key/value pairs to embed (e.g. guestName, bookingId)
     * @param subject token subject (e.g. guestName)
     * @return compact, URL-safe JWT string
     */
    public String generateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates and parses a JWT, returning its claims body.
     *
     * @param token compact JWT string from the Authorization header
     * @return parsed {@link Claims}
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Convenience method: extracts the subject claim (guestName) from a Bearer token.
     *
     * @param bearerHeader value of the Authorization header ("Bearer &lt;token&gt;")
     * @return subject string, or {@code null} if the header is absent / malformed
     */
    public String extractSubjectFromBearer(String bearerHeader) {
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return parseToken(bearerHeader.substring(7)).getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
