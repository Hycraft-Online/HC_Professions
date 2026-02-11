package com.hcprofessions.models;

import javax.annotation.Nullable;
import java.util.UUID;

public class PlayerProfessionData {

    private final UUID playerUuid;
    private Profession profession;
    private int level;
    private long currentXp;
    private long totalXpEarned;
    private int totalItemsCrafted;
    private int respecCount;
    private boolean dirty;

    public PlayerProfessionData(UUID playerUuid, @Nullable Profession profession, int level,
                                long currentXp, long totalXpEarned, int totalItemsCrafted, int respecCount) {
        this.playerUuid = playerUuid;
        this.profession = profession;
        this.level = level;
        this.currentXp = currentXp;
        this.totalXpEarned = totalXpEarned;
        this.totalItemsCrafted = totalItemsCrafted;
        this.respecCount = respecCount;
        this.dirty = false;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    @Nullable
    public Profession getProfession() { return profession; }
    public int getLevel() { return level; }
    public long getCurrentXp() { return currentXp; }
    public long getTotalXpEarned() { return totalXpEarned; }
    public int getTotalItemsCrafted() { return totalItemsCrafted; }
    public int getRespecCount() { return respecCount; }
    public boolean isDirty() { return dirty; }

    public boolean hasProfession() {
        return profession != null;
    }

    public void setProfession(@Nullable Profession profession) {
        this.profession = profession;
        this.dirty = true;
    }

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

    public void incrementItemsCrafted() {
        this.totalItemsCrafted++;
        this.dirty = true;
    }

    public void incrementRespecCount() {
        this.respecCount++;
        this.dirty = true;
    }

    public void resetProgression() {
        this.level = 0;
        this.currentXp = 0;
        this.totalXpEarned = 0;
        this.totalItemsCrafted = 0;
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
    }
}
