package com.amx.grocerystore.users.infrastructure;

import com.amx.grocerystore.users.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Bulk updates/deletes deliberately omit {@code clearAutomatically}: callers (store/user
 * deactivation) run them inside a transaction that still holds dirty {@code Store}/{@code User}
 * entities, and clearing the persistence context would silently drop those changes.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** {@code SELECT ... FOR UPDATE}: serializes concurrent refreshes presenting the same token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.id = :id")
    Optional<RefreshToken> findLockedById(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.storeId = :storeId AND t.revokedAt IS NULL")
    int revokeAllByStoreId(@Param("storeId") UUID storeId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff OR t.revokedAt < :cutoff")
    int deleteStale(@Param("cutoff") OffsetDateTime cutoff);
}
