package com.bountyhunter;

import java.util.UUID;

public class LeaderboardEntry {
    private UUID playerUUID;
    private String playerName;
    private boolean isBedrockPlayer;
    private double totalEarned;
    private int bountiesCollected;

    public LeaderboardEntry(UUID playerUUID, String playerName, boolean isBedrockPlayer) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.isBedrockPlayer = isBedrockPlayer;
        this.totalEarned = 0;
        this.bountiesCollected = 0;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public boolean isBedrockPlayer() { return isBedrockPlayer; }
    public double getTotalEarned() { return totalEarned; }
    public int getBountiesCollected() { return bountiesCollected; }

    public void addCollection(double amount) {
        this.totalEarned += amount;
        this.bountiesCollected++;
    }
}
