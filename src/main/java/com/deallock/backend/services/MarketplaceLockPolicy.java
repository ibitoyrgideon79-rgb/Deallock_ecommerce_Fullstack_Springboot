package com.deallock.backend.services;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceLockPolicy {

    private final Duration lockDuration;

    public MarketplaceLockPolicy(@Value("${app.marketplace.lock-days:7}") int lockDays) {
        int days = Math.max(1, lockDays);
        this.lockDuration = Duration.ofDays(days);
    }

    public Instant lockedUntil(Instant securedAt) {
        if (securedAt == null) return null;
        return securedAt.plus(lockDuration);
    }

    public boolean isStillLocked(Instant securedAt, Instant now) {
        Instant until = lockedUntil(securedAt);
        return until != null && now != null && now.isBefore(until);
    }
}

