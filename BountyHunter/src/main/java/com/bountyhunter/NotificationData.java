package com.bountyhunter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationData {
    private UUID playerUUID;
    private List<String> messages;

    public NotificationData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.messages = new ArrayList<>();
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public List<String> getMessages() { return messages; }

    public void addMessage(String message) {
        this.messages.add(message);
    }
}
