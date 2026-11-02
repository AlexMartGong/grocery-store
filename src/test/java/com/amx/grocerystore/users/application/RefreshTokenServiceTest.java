package com.amx.grocerystore.users.application;

import com.amx.grocerystore.core.security.AccountInactiveException;
import com.amx.grocerystore.core.security.RefreshTokenProperties;
import com.amx.grocerystore.core.web.exception.RefreshTokenException;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.users.domain.RefreshToken;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;
import com.amx.grocerystore.users.infrastructure.RefreshTokenRepository;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STORE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FAMILY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String RAW = "presented-raw-token-value";
    private static final int DAYS = 60;
    private static final long GRACE_SECONDS = 30;
    private static final int RETENTION_DAYS = 30;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;

    private RefreshTokenService service;

    /** Every entity passed to save(), in order; save() assigns an id like Hibernate's persist would. */
    private final List<RefreshToken> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, userRepository, storeRepository,
                new RefreshTokenProperties(DAYS, true, GRACE_SECONDS, RETENTION_DAYS));
    }

    // ── fixtures ────────────────────────────────────────────────────────────────

    private User user(boolean active) {
        return User.builder()
                .id(USER_ID)
                .storeId(STORE_ID)
                .fullName("Cajero")
                .username("cashier1")
                .passwordHash("hash")
                .role(UserRole.CASHIER)
                .active(active)
                .build();
    }

    private Store store(StoreStatus status) {
        return Store.builder().id(STORE_ID).code("tienda").name("Tienda").status(status).build();
    }

    private RefreshToken activeToken(OffsetDateTime expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .storeId(STORE_ID)
                .tokenHash(RefreshTokenService.hash(RAW))
                .familyId(FAMILY_ID)
                .expiresAt(expiresAt)
                .build();
    }

    private RefreshToken rotatedToken(OffsetDateTime rotatedAt, UUID childId) {
        RefreshToken token = activeToken(OffsetDateTime.now().plusDays(10));
        token.setRevokedAt(rotatedAt);
        token.setReplacedBy(childId);
        return token;
    }

    private void givenPresented(RefreshToken token) {
        given(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(RAW))).willReturn(Optional.of(token));
    }

    private void givenHealthyAccount() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(true)));
        given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store(StoreStatus.ACTIVE)));
    }

    private void givenSaveAssignsId() {
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            saved.add(t);
            return t;
        });
    }

    private static void assertReused(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(RefreshTokenException.class)
                .extracting("code").isEqualTo("refresh_token_reused");
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(RefreshTokenException.class)
                .extracting("code").isEqualTo("invalid_refresh_token");
    }

    private static void assertAccountInactive(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AccountInactiveException.class)
                .extracting("code").isEqualTo(code);
    }

    // ── issue ───────────────────────────────────────────────────────────────────

    @Test
    void issue_persistsOnlySha256OfRawValue_newFamily_expiresInConfiguredDays() {
        givenSaveAssignsId();

        String raw = service.issue(user(true), "Mozilla/5.0");

        RefreshToken token = saved.getFirst();
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43).matches("[A-Za-z0-9_-]+");
        assertThat(token.getTokenHash())
                .hasSize(64)
                .isEqualTo(RefreshTokenService.hash(raw))
                .isNotEqualTo(raw);
        assertThat(token.getUserId()).isEqualTo(USER_ID);
        assertThat(token.getStoreId()).isEqualTo(STORE_ID);
        assertThat(token.getFamilyId()).isNotNull();
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(token.getExpiresAt())
                .isCloseTo(OffsetDateTime.now().plusDays(DAYS), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void issue_eachLoginGetsDistinctTokenAndFamily() {
        givenSaveAssignsId();

        String first = service.issue(user(true), null);
        String second = service.issue(user(true), null);

        assertThat(first).isNotEqualTo(second);
        assertThat(saved.get(0).getFamilyId()).isNotEqualTo(saved.get(1).getFamilyId());
    }

    @Test
    void issue_truncatesUserAgentToColumnLength() {
        givenSaveAssignsId();

        service.issue(user(true), "x".repeat(300));

        assertThat(saved.getFirst().getUserAgent()).hasSize(RefreshToken.USER_AGENT_MAX_LENGTH);
    }

    @Test
    void hash_isStableSha256Hex() {
        // SHA-256("abc") test vector (FIPS 180-2)
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // ── rotate: happy path ──────────────────────────────────────────────────────

    @Test
    void rotate_activeToken_revokesParentAndIssuesChildInSameFamilyWithSlidingExpiry() {
        RefreshToken presented = activeToken(OffsetDateTime.now().plusDays(1));
        givenPresented(presented);
        givenHealthyAccount();
        givenSaveAssignsId();

        RotatedSession result = service.rotate(RAW, "UA");

        RefreshToken child = saved.getFirst();
        assertThat(result.user().getId()).isEqualTo(USER_ID);
        assertThat(result.refreshToken()).isNotEqualTo(RAW);
        assertThat(child.getTokenHash()).isEqualTo(RefreshTokenService.hash(result.refreshToken()));
        assertThat(child.getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(child.getExpiresAt())
                .as("sliding expiration: a fresh full window, not the parent's remaining day")
                .isCloseTo(OffsetDateTime.now().plusDays(DAYS), within(5, ChronoUnit.SECONDS));
        assertThat(presented.getRevokedAt()).isNotNull();
        assertThat(presented.getReplacedBy()).isEqualTo(child.getId());
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void rotate_isTheTransactionBoundaryThatKeepsRevocationsOnFailure() throws Exception {
        Transactional tx = RefreshTokenService.class
                .getMethod("rotate", String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isFalse();
        assertThat(tx.noRollbackFor())
                .contains(RefreshTokenException.class, AccountInactiveException.class);
    }

    // ── rotate: invalid ─────────────────────────────────────────────────────────

    @Test
    void rotate_nullOrBlank_invalidWithoutTouchingDb() {
        assertInvalid(() -> service.rotate(null, null));
        assertInvalid(() -> service.rotate("  ", null));
        verifyNoInteractions(refreshTokenRepository, userRepository, storeRepository);
    }

    @Test
    void rotate_unknownToken_invalid() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertInvalid(() -> service.rotate(RAW, null));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_expiredToken_invalid_withoutRevokingFamily() {
        givenPresented(activeToken(OffsetDateTime.now().minusMinutes(1)));

        assertInvalid(() -> service.rotate(RAW, null));
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    // ── rotate: reuse detection ─────────────────────────────────────────────────

    @Test
    void rotate_tokenRotatedOutsideGrace_revokesWholeFamily_reused() {
        givenPresented(rotatedToken(OffsetDateTime.now().minusSeconds(GRACE_SECONDS + 30), UUID.randomUUID()));

        assertReused(() -> service.rotate(RAW, null));
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_tokenRevokedByLogout_revokesWholeFamily_reused() {
        RefreshToken loggedOut = activeToken(OffsetDateTime.now().plusDays(10));
        loggedOut.setRevokedAt(OffsetDateTime.now().minusSeconds(1)); // no replacedBy → not a rotation
        givenPresented(loggedOut);

        assertReused(() -> service.rotate(RAW, null));
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
    }

    @Test
    void rotate_withinGraceButChildAlreadyUsed_revokesWholeFamily_reused() {
        UUID childId = UUID.randomUUID();
        givenPresented(rotatedToken(OffsetDateTime.now().minusSeconds(5), childId));
        RefreshToken usedChild = activeToken(OffsetDateTime.now().plusDays(DAYS));
        usedChild.setId(childId);
        usedChild.setRevokedAt(OffsetDateTime.now().minusSeconds(2));
        given(refreshTokenRepository.findLockedById(childId)).willReturn(Optional.of(usedChild));

        assertReused(() -> service.rotate(RAW, null));
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_withinGraceAndChildUnused_reRotatesInsteadOfRevokingFamily() {
        UUID childId = UUID.randomUUID();
        OffsetDateTime rotatedAt = OffsetDateTime.now().minusSeconds(5);
        RefreshToken presented = rotatedToken(rotatedAt, childId);
        givenPresented(presented);
        RefreshToken unusedChild = activeToken(OffsetDateTime.now().plusDays(DAYS));
        unusedChild.setId(childId);
        given(refreshTokenRepository.findLockedById(childId)).willReturn(Optional.of(unusedChild));
        givenHealthyAccount();
        givenSaveAssignsId();

        RotatedSession result = service.rotate(RAW, null);

        RefreshToken newest = saved.getFirst();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(newest.getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(unusedChild.getRevokedAt()).as("the never-delivered child is retired").isNotNull();
        assertThat(unusedChild.getReplacedBy()).isEqualTo(newest.getId());
        assertThat(presented.getReplacedBy()).isEqualTo(newest.getId());
        assertThat(presented.getRevokedAt()).as("original rotation time is kept").isEqualTo(rotatedAt);
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    // ── rotate: account revalidation ────────────────────────────────────────────

    @Test
    void rotate_userInactive_revokesFamily_userInactive() {
        givenPresented(activeToken(OffsetDateTime.now().plusDays(1)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(false)));

        assertAccountInactive(() -> service.rotate(RAW, null), "user_inactive");
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_userMissing_revokesFamily_userInactive() {
        givenPresented(activeToken(OffsetDateTime.now().plusDays(1)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertAccountInactive(() -> service.rotate(RAW, null), "user_inactive");
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
    }

    @Test
    void rotate_userNowBelongsToAnotherStore_revokesFamily_userInactive() {
        givenPresented(activeToken(OffsetDateTime.now().plusDays(1)));
        User moved = user(true);
        moved.setStoreId(UUID.randomUUID());
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(moved));

        assertAccountInactive(() -> service.rotate(RAW, null), "user_inactive");
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
    }

    @Test
    void rotate_storeSuspended_revokesFamily_storeInactive() {
        givenPresented(activeToken(OffsetDateTime.now().plusDays(1)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(true)));
        given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store(StoreStatus.SUSPENDED)));

        assertAccountInactive(() -> service.rotate(RAW, null), "store_inactive");
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_storeCanceled_revokesFamily_storeInactive() {
        givenPresented(activeToken(OffsetDateTime.now().plusDays(1)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(true)));
        given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store(StoreStatus.CANCELED)));

        assertAccountInactive(() -> service.rotate(RAW, null), "store_inactive");
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
    }

    @Test
    void rotate_storeMissing_revokesFamily_storeInactive() {
        givenPresented(activeToken(OffsetDateTime.now().plusDays(1)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(true)));
        given(storeRepository.findById(STORE_ID)).willReturn(Optional.empty());

        assertAccountInactive(() -> service.rotate(RAW, null), "store_inactive");
        verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any());
    }

    // ── revoke (logout) ─────────────────────────────────────────────────────────

    @Test
    void revoke_activeToken_setsRevokedAt() {
        RefreshToken token = activeToken(OffsetDateTime.now().plusDays(1));
        givenPresented(token);

        service.revoke(RAW);

        assertThat(token.getRevokedAt()).isNotNull();
        assertThat(token.getReplacedBy()).isNull();
    }

    @Test
    void revoke_alreadyRevoked_keepsOriginalTimestamp() {
        OffsetDateTime original = OffsetDateTime.now().minusDays(2);
        RefreshToken token = activeToken(OffsetDateTime.now().plusDays(1));
        token.setRevokedAt(original);
        givenPresented(token);

        service.revoke(RAW);

        assertThat(token.getRevokedAt()).isEqualTo(original);
    }

    @Test
    void revoke_unknownOrMissingToken_isNoOp() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        service.revoke(RAW);
        service.revoke(null);
        service.revoke("");

        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    // ── bulk revocation & cleanup ───────────────────────────────────────────────

    @Test
    void revokeAllForUser_delegatesBulkUpdate() {
        service.revokeAllForUser(USER_ID);

        verify(refreshTokenRepository).revokeAllByUserId(eq(USER_ID), any(OffsetDateTime.class));
    }

    @Test
    void revokeAllForStore_delegatesBulkUpdate() {
        service.revokeAllForStore(STORE_ID);

        verify(refreshTokenRepository).revokeAllByStoreId(eq(STORE_ID), any(OffsetDateTime.class));
    }

    @Test
    void purgeStale_usesRetentionCutoff() {
        given(refreshTokenRepository.deleteStale(any())).willReturn(7);

        int deleted = service.purgeStale();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(refreshTokenRepository).deleteStale(cutoff.capture());
        assertThat(deleted).isEqualTo(7);
        assertThat(cutoff.getValue())
                .isCloseTo(OffsetDateTime.now().minusDays(RETENTION_DAYS), within(5, ChronoUnit.SECONDS));
    }
}
