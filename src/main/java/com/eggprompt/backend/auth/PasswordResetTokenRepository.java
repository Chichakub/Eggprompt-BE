package com.eggprompt.backend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
        UPDATE PasswordResetToken token
        SET token.usedAt = :usedAt
        WHERE token.user.userId = :userId
          AND token.usedAt IS NULL
        """)
    int invalidateUnusedTokensForUser(
            @Param("userId") Integer userId,
            @Param("usedAt") Instant usedAt
    );

    @Query("""
        SELECT token.user.userId
        FROM PasswordResetToken token
        WHERE token.tokenHash = :tokenHash
        """)
    Optional<Integer> findUserIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );
}