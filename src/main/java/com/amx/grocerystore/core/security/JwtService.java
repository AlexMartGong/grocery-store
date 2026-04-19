package com.amx.grocerystore.core.security;

import com.amx.grocerystore.users.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    public static final String CLAIM_STORE_ID = "storeId";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.expirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .claim(CLAIM_STORE_ID, user.getStoreId().toString())
                .claim(CLAIM_USER_ID, user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(exp))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token);
    }

    public long getExpirationSeconds() {
        return properties.expirationMinutes() * 60L;
    }
}
