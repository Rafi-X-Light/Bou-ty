package com.bountyhunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class MessageUtil {
    private final MiniMessage mm = MiniMessage.miniMessage();

    public void sendMessage(Player player, String message) {
        player.sendMessage(mm.deserialize(message));
    }

    public void broadcast(String message) {
        Bukkit.broadcast(mm.deserialize(message));
    }

    public void sendActionbar(Player player, String message) {
        player.sendActionBar(mm.deserialize(message));
    }

    public void playSound(Player player, String soundName) {
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName), 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    public void broadcastSound(String soundName, float volume) {
        try {
            Sound sound = Sound.valueOf(soundName);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), sound, volume, 1.0f);
            }
        } catch (IllegalArgumentException ignored) {}
    }

    public Component parse(String message) {
        return mm.deserialize(message);
    }
}
