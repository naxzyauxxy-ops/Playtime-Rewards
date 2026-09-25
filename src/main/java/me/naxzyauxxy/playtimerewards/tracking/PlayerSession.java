package me.naxzyauxxy.playtimerewards.tracking;

/** Transient per-login state (never persisted). */
public final class PlayerSession {

    /** System.nanoTime() of the last accrual. */
    long lastAccrualNanos;
    /** Sub-millisecond remainder carried between accruals so nothing is lost to rounding. */
    long nanoRemainder;
    /** Wall-clock millis of the last player activity (chat, look, interact...). Written async by chat. */
    volatile long lastActivityMillis;
    /** Current AFK state as last evaluated. */
    volatile boolean afk;
    /** How many (sorted) tiers had been reached at the last check - drives "reward ready" notices. */
    int reachedCount;

    PlayerSession(long nowNanos, long nowMillis) {
        this.lastAccrualNanos = nowNanos;
        this.lastActivityMillis = nowMillis;
    }

    public boolean isAfk() {
        return afk;
    }
}
