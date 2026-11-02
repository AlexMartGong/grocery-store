package com.amx.grocerystore.users.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly purge of refresh tokens expired or revoked longer than the retention window. Recently
 * revoked rows are kept on purpose: they are what lets a replayed stolen token trigger reuse detection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "0 30 3 * * ?")
    public void purgeStaleRefreshTokens() {
        int deleted = refreshTokenService.purgeStale();
        log.info("Refresh-token cleanup: {} stale row(s) deleted", deleted);
    }
}
