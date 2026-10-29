package com.eggprompt.backend.auth;

import com.eggprompt.backend.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.key = secret.isBlank()
                ? Jwts.SIG.HS256.key().build()                       // ไม่ตั้งค่า → สร้างกุญแจสุ่มเอง
                : Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)); // ตั้งค่า → ใช้ค่านั้น
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("role", user.getUserRole())
                .claim("farmId", user.getFarmId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}