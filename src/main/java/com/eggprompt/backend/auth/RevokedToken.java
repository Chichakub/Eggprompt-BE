package com.eggprompt.backend.auth;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {
    @Id
    @Column(length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    protected RevokedToken() {}
    public RevokedToken(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
