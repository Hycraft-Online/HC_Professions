package com.hcprofessions.models;

import java.util.UUID;

public class PlayerAllProfessionData {

    private final UUID playerUuid;
    private final Profession profession;
    private int level;
    private long currentXp;
    private long totalXpEarned;
    private boolean dirty;

    public PlayerAllProfessionData(UUID playerUuid, Profession profession, int level, long currentXp, long totalXpEarned) {
        this.playerUuid = playerUuid;
        this.profession = profession;
        this.level = level;
        this.currentXp = currentXp;
        this.totalXpEarned = totalXpEarned;
        this.dirty = false;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public Profession getProfession() { return profession; }
    public int getLevel() { return level; }
    public long getCurrentXp() { return currentXp; }
    public long getTotalXpEarned() { return totalXpEarned; }
    public boolean isDirty() { return dirty; }

    public void setLevel(int level) {
        this.level = level;
        this.dirty = true;
    }

    public void setCurrentXp(long currentXp) {
        this.currentXp = currentXp;
        this.dirty = true;
    }

    public void addXp(long xp) {
        this.currentXp += xp;
        this.totalXpEarned += xp;
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
    }
}
