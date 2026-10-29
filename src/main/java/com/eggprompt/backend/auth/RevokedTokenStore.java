package com.eggprompt.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RevokedTokenStore {
    private final RevokedTokenRepository repository;

    @Transactional
    public void revoke(String token, Instant expiresAt) {
        repository.deleteByExpiresAtBefore(Instant.now());
        repository.save(new RevokedToken(hash(token), expiresAt));
    }

    public boolean isRevoked(String token) {
        return repository.existsById(hash(token));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
