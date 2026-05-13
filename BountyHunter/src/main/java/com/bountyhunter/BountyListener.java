package com.bountyhunter;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BountyListener implements Listener {
    private final BountyHunterPlugin plugin;
    private final WorldGuardHook worldGuardHook;

    public BountyListener(BountyHunterPlugin plugin) {
        this.plugin = plugin;
        this.worldGuardHook = Bukkit.getPluginManager().getPlugin("WorldGuard") != null ? new WorldGuardHook(plugin) : null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Show pending notifications
        NotificationData notifs = plugin.getBountyManager().getPendingNotifications(player.getUniqueId());
        if (notifs != null) {
            for (String msg : notifs.getMessages()) {
                plugin.getMessageUtil().sendMessage(player, msg);
            }
        }

        // Start actionbar reminder
        startActionbarTask(player);
    }

    private void startActionbarTask(Player player) {
        int interval = plugin.getConfig().getInt("settings.actionbar-reminder-interval-seconds") * 20;
        if (interval <= 0) return;

        Bukkit.getScheduler().runTaskTimer(plugin, (task) -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }

            double total = plugin.getBountyManager().getTotalBounty(player.getUniqueId());
            if (total > 0) {
                int contributors = plugin.getBountyManager().getBountiesOn(player.getUniqueId()).size();
                plugin.getMessageUtil().sendActionbar(player, "<red>⚠ You have a $" + total + " bounty on your head! " + contributors + " contributors.</red>");
            }
        }, 20L, interval);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Player killer = target.getKiller();
        if (killer == null) return;

        List<BountyData> bounties = plugin.getBountyManager().getBountiesOn(target.getUniqueId());
        if (bounties.isEmpty()) return;

        // 1. Check killer is not in creative/spectator
        if (killer.getGameMode() == GameMode.CREATIVE || killer.getGameMode() == GameMode.SPECTATOR) {
            killer.sendMessage("You cannot collect bounties in " + killer.getGameMode().name().toLowerCase() + " mode.");
            return;
        }

        // 2. Check death location is not in a safe zone
        List<String> safeZones = plugin.getConfig().getStringList("settings.safe-zones");
        if (worldGuardHook != null && worldGuardHook.isInSafeZone(target, safeZones)) {
            killer.sendMessage("You cannot collect bounties in a safe zone.");
            return;
        }

        // 3. Check killer is not the same person as any bounty placer
        boolean killerIsPlacer = bounties.stream().anyMatch(b -> b.getPlacedByUUID().equals(killer.getUniqueId()));
        if (killerIsPlacer) {
            // Check if ALL bounties were placed by the killer
            boolean allByKiller = bounties.stream().allMatch(b -> b.getPlacedByUUID().equals(killer.getUniqueId()));
            if (allByKiller) {
                killer.sendMessage("You cannot collect your own bounty. Bounties refunded.");
                refundBounties(bounties);
                return;
            } else {
                // Remove killer's bounties from the pool
                List<BountyData> killerBounties = bounties.stream()
                        .filter(b -> b.getPlacedByUUID().equals(killer.getUniqueId()))
                        .collect(Collectors.toList());
                refundBounties(killerBounties);
                bounties.removeAll(killerBounties);
            }
        }

        // 4. Sum ALL active bounty entries
        double total = bounties.stream().mapToDouble(BountyData::getAmount).sum();

        // 5. Deposit full sum to killer
        plugin.getEconomyHandler().deposit(killer.getUniqueId(), total);

        // 6. Record collection
        plugin.getBountyManager().addHistory(new HistoryEntry(killer.getName(), target.getName(), total));
        boolean isBedrockKiller = plugin.getCrossplayUtil().isBedrockPlayer(killer);
        plugin.getBountyManager().updateLeaderboard(killer.getUniqueId(), killer.getName(), isBedrockKiller, total);

        // 7. Remove all bounty entries
        for (BountyData b : bounties) {
            plugin.getBountyManager().removeBounty(b);
            String msg = "💀 [BountyHunter] Your bounty on " + target.getName() + " was collected by " + killer.getName() + ".";
            plugin.getBountyManager().addNotification(b.getPlacedByUUID(), msg);
            Player placer = Bukkit.getPlayer(b.getPlacedByUUID());
            if (placer != null) plugin.getMessageUtil().sendMessage(placer, msg);
        }

        // 8. Broadcast
        if (plugin.getConfig().getBoolean("settings.broadcast-on-collect")) {
            String killerName = plugin.getCrossplayUtil().getDisplayName(killer);
            String targetName = plugin.getCrossplayUtil().getDisplayName(target);
            plugin.getMessageUtil().broadcast("💀 <gold>[BountyHunter]</gold> <yellow>" + killerName + "</yellow> hunted down <red>" + targetName + "</red> and collected <green>$" + total + "</green> in bounties!");
            plugin.getMessageUtil().broadcastSound("ENTITY_WITHER_DEATH", 0.3f);
        }
        plugin.getMessageUtil().playSound(killer, "ENTITY_EXPERIENCE_ORB_PICKUP");

        // 9. Log IP check warning
        if (plugin.getConfig().getBoolean("settings.log-ip-warnings")
                && killer.getAddress() != null && target.getAddress() != null) {
            String killerIP = killer.getAddress().getAddress().getHostAddress();
            String targetIP = target.getAddress().getAddress().getHostAddress();
            if (killerIP.equals(targetIP)) {
                plugin.getLogger().warning("Potential self-farming detected! Killer: " + killer.getName() + " and Target: " + target.getName() + " have the same IP: " + killerIP);
            }
        }
    }

    private void refundBounties(List<BountyData> bounties) {
        for (BountyData b : bounties) {
            plugin.getEconomyHandler().deposit(b.getPlacedByUUID(), b.getAmount());
            plugin.getBountyManager().removeBounty(b);
        }
    }
}
