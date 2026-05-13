package com.bountyhunter;

public class HistoryEntry {
    private String killerName;
    private String targetName;
    private double totalAmount;
    private long timestamp;

    public HistoryEntry(String killerName, String targetName, double totalAmount) {
        this.killerName = killerName;
        this.targetName = targetName;
        this.totalAmount = totalAmount;
        this.timestamp = System.currentTimeMillis();
    }

    public String getKillerName() { return killerName; }
    public String getTargetName() { return targetName; }
    public double getTotalAmount() { return totalAmount; }
    public long getTimestamp() { return timestamp; }
}
