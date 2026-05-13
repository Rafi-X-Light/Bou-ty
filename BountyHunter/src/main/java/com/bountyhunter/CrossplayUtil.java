package com.bountyhunter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

public class CrossplayUtil {
    private final boolean floodgateEnabled;

    public CrossplayUtil() {
        this.floodgateEnabled = Bukkit.getPluginManager().isPluginEnabled("Floodgate");
    }

    public boolean isBedrockPlayer(Player player) {
        if (floodgateEnabled) {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        }
        // Fallback: Floodgate UUIDs usually start with 00000000-0000-0000
        return player.getUniqueId().toString().startsWith("00000000-0000-0000-");
    }

    public String getDisplayName(Player player) {
        if (isBedrockPlayer(player)) {
            return "[BE] " + player.getName();
        }
        return player.getName();
    }
}
