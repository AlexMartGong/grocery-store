package com.amx.grocerystore.users.application;

import com.amx.grocerystore.core.security.AccountInactiveException;
import com.amx.grocerystore.core.security.RefreshTokenProperties;
import com.amx.grocerystore.core.web.exception.RefreshTokenException;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.users.domain.RefreshToken;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.infrastructure.RefreshTokenRepository;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Lifecycle of store-user refresh tokens: issue, rotate (with reuse detection), revoke, purge.
 * Raw values are 256-bit {@link SecureRandom} strings handed to the caller exactly once; only their
 * SHA-256 hex is persisted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final RefreshTokenProperties properties;

    /** Starts a new token family (one per login/device) and returns the raw token. */
    @Transactional
    public String issue(User user, String userAgent) {
        return persistNew(user, UUID.randomUUID(), userAgent, OffsetDateTime.now()).rawValue();
    }

    /**
     * Validates the presented token and rotates it: the presented row is revoked and points to a new
     * child in the same family whose expiry slides to {@code now + days}.
     * <p>
     * <b>Single transaction boundary of the refresh flow.</b> Failure paths revoke the family and then
     * throw; {@code noRollbackFor} makes that revocation durable. Never wrap this call in another
     * {@code @Transactional} that lacks the same {@code noRollbackFor} — the outer interceptor would
     * mark the shared transaction rollback-only and the revocation would be lost.
     */
    @Transactional(noRollbackFor = {RefreshTokenException.class, AccountInactiveException.class})
    public RotatedSession rotate(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw RefreshTokenException.invalid();
        }
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken presented = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(RefreshTokenException::invalid);

        RefreshToken unusedChild = null;
        if (presented.isRevoked()) {
            Optional<RefreshToken> benignRace = unusedChildWithinGrace(presented, now);
            if (benignRace.isEmpty()) {
                refreshTokenRepository.revokeFamily(presented.getFamilyId(), now);
                log.warn("Refresh token reuse detected: family {} of user {} revoked",
                        presented.getFamilyId(), presented.getUserId());
                throw RefreshTokenException.reused();
            }
            unusedChild = benignRace.get();
        }

        if (presented.isExpired(now)) {
            throw RefreshTokenException.invalid();
        }

        User user = revalidate(presented, now);

        Issued child = persistNew(user, presented.getFamilyId(), userAgent, now);
        UUID childId = child.token().getId();
        if (unusedChild != null) {
            unusedChild.setRevokedAt(now);
            unusedChild.setReplacedBy(childId);
        }
        if (!presented.isRevoked()) {
            presented.setRevokedAt(now);
        }
        presented.setReplacedBy(childId);
        return new RotatedSession(user, child.rawValue());
    }

    /** Logout: revokes the presented token if it is still active. Idempotent, never throws. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(OffsetDateTime.now()));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now());
        log.info("Revoked {} refresh token(s) of user {}", revoked, userId);
    }

    @Transactional
    public void revokeAllForStore(UUID storeId) {
        int revoked = refreshTokenRepository.revokeAllByStoreId(storeId, OffsetDateTime.now());
        log.info("Revoked {} refresh token(s) of store {}", revoked, storeId);
    }

    /** Deletes tokens expired or revoked more than {@code cleanupRetentionDays} ago. */
    @Transactional
    public int purgeStale() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(properties.cleanupRetentionDays());
        return refreshTokenRepository.deleteStale(cutoff);
    }

    /**
     * A rotated token replayed shortly after its rotation, whose child has never been used, is the
     * multi-tab / lost-response race rather than theft. Tokens revoked by logout or deactivation
     * have no {@code replacedBy} and never qualify.
     */
    private Optional<RefreshToken> unusedChildWithinGrace(RefreshToken presented, OffsetDateTime now) {
        if (presented.getReplacedBy() == null
                || presented.getRevokedAt().isBefore(now.minusSeconds(properties.reuseGraceSeconds()))) {
            return Optional.empty();
        }
        return refreshTokenRepository.findLockedById(presented.getReplacedBy())
                .filter(child -> !child.isRevoked());
    }

    /** Same checks as {@code JwtAuthenticationFilter}; a failure revokes the family before throwing. */
    private User revalidate(RefreshToken token, OffsetDateTime now) {
        Optional<User> user = userRepository.findById(token.getUserId())
                .filter(User::isActive)
                .filter(u -> u.getStoreId().equals(token.getStoreId()));
        if (user.isEmpty()) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new AccountInactiveException("user_inactive");
        }
        boolean storeActive = storeRepository.findById(token.getStoreId())
                .map(store -> store.getStatus() == StoreStatus.ACTIVE)
                .orElse(false);
        if (!storeActive) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new AccountInactiveException("store_inactive");
        }
        return user.get();
    }

    private Issued persistNew(User user, UUID familyId, String userAgent, OffsetDateTime now) {
        String rawValue = generateRawToken();
        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .storeId(user.getStoreId())
                .tokenHash(hash(rawValue))
                .familyId(familyId)
                .expiresAt(now.plusDays(properties.days()))
                .userAgent(truncate(userAgent))
                .build());
        return new Issued(token, rawValue);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= RefreshToken.USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, RefreshToken.USER_AGENT_MAX_LENGTH);
    }

    private record Issued(RefreshToken token, String rawValue) {
    }
}
