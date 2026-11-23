package com.potatosmp.database;

import java.util.UUID;

public final class PlayerProfile {

    private final UUID uuid;
    private String lastKnownName;
    private long credits;
    private long lifetimePotatoes;
    private long lifetimeGoldenPotatoes;
    private int potatoHeartsUsed;
    private int bountiesClaimedCount;
    private long bountyCreditsEarned;
    private boolean scoreboardEnabled;

    public PlayerProfile(UUID uuid, String lastKnownName, long credits, long lifetimePotatoes,
                         long lifetimeGoldenPotatoes, int potatoHeartsUsed, int bountiesClaimedCount,
                         long bountyCreditsEarned, boolean scoreboardEnabled) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.credits = credits;
        this.lifetimePotatoes = lifetimePotatoes;
        this.lifetimeGoldenPotatoes = lifetimeGoldenPotatoes;
        this.potatoHeartsUsed = potatoHeartsUsed;
        this.bountiesClaimedCount = bountiesClaimedCount;
        this.bountyCreditsEarned = bountyCreditsEarned;
        this.scoreboardEnabled = scoreboardEnabled;
    }

    public UUID getUuid() {
        return uuid;
    }

    public synchronized String getLastKnownName() {
        return lastKnownName;
    }

    public synchronized void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public synchronized long getCredits() {
        return credits;
    }

    public synchronized void setCredits(long credits) {
        this.credits = Math.max(0L, credits);
    }

    public synchronized long getLifetimePotatoes() {
        return lifetimePotatoes;
    }

    public synchronized void addLifetimePotatoes(long delta) {
        this.lifetimePotatoes = Math.max(0L, this.lifetimePotatoes + delta);
    }

    public synchronized void setLifetimePotatoes(long lifetimePotatoes) {
        this.lifetimePotatoes = Math.max(0L, lifetimePotatoes);
    }

    public synchronized long getLifetimeGoldenPotatoes() {
        return lifetimeGoldenPotatoes;
    }

    public synchronized void addLifetimeGoldenPotatoes(long delta) {
        this.lifetimeGoldenPotatoes = Math.max(0L, this.lifetimeGoldenPotatoes + delta);
    }

    public synchronized int getPotatoHeartsUsed() {
        return potatoHeartsUsed;
    }

    public synchronized void setPotatoHeartsUsed(int potatoHeartsUsed) {
        this.potatoHeartsUsed = Math.max(0, potatoHeartsUsed);
    }

    public synchronized int getBountiesClaimedCount() {
        return bountiesClaimedCount;
    }

    public synchronized void incrementBountiesClaimedCount() {
        this.bountiesClaimedCount++;
    }

    public synchronized long getBountyCreditsEarned() {
        return bountyCreditsEarned;
    }

    public synchronized void addBountyCreditsEarned(long delta) {
        this.bountyCreditsEarned = Math.max(0L, this.bountyCreditsEarned + delta);
    }

    public synchronized boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public synchronized void setScoreboardEnabled(boolean scoreboardEnabled) {
        this.scoreboardEnabled = scoreboardEnabled;
    }
}
