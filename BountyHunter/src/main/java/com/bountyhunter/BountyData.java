package com.bountyhunter;

import java.util.UUID;

public class BountyData {
    private String id;
    private UUID targetUUID;
    private String targetName;
    private UUID placedByUUID;
    private String placedByName;
    private boolean isBedrockPlacer;
    private double amount;
    private long placedTimestamp;
    private long expiryTimestamp;

    public BountyData(UUID targetUUID, String targetName, UUID placedByUUID, String placedByName, boolean isBedrockPlacer, double amount, long expiryTimestamp) {
        this.id = UUID.randomUUID().toString();
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.placedByUUID = placedByUUID;
        this.placedByName = placedByName;
        this.isBedrockPlacer = isBedrockPlacer;
        this.amount = amount;
        this.placedTimestamp = System.currentTimeMillis();
        this.expiryTimestamp = expiryTimestamp;
    }

    public String getId() { return id; }
    public UUID getTargetUUID() { return targetUUID; }
    public String getTargetName() { return targetName; }
    public UUID getPlacedByUUID() { return placedByUUID; }
    public String getPlacedByName() { return placedByName; }
    public boolean isBedrockPlacer() { return isBedrockPlacer; }
    public double getAmount() { return amount; }
    public long getPlacedTimestamp() { return placedTimestamp; }
    public long getExpiryTimestamp() { return expiryTimestamp; }

    public void setExpiryTimestamp(long expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }
}
