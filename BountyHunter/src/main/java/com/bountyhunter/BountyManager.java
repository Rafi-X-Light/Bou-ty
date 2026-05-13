package com.bountyhunter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BountyManager {
    private final BountyHunterPlugin plugin;
    private final Gson gson;
    private final File dataFolder;

    private List<BountyData> activeBounties;
    private Map<UUID, LeaderboardEntry> leaderboard;
    private List<HistoryEntry> history;
    private Map<UUID, NotificationData> pendingNotifications;

    private final Map<UUID, Map<UUID, Long>> placementCooldowns = new ConcurrentHashMap<>();

    public BountyManager(BountyHunterPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        loadData();
        startExpiryTask();
        startAutoSaveTask();
    }

    private void loadData() {
        activeBounties = Collections.synchronizedList(loadList("bounties.json", new TypeToken<List<BountyData>>(){}.getType()));
        history = loadList("history.json", new TypeToken<List<HistoryEntry>>(){}.getType());
        
        List<LeaderboardEntry> lbList = loadList("leaderboard.json", new TypeToken<List<LeaderboardEntry>>(){}.getType());
        leaderboard = lbList.stream().collect(Collectors.toMap(LeaderboardEntry::getPlayerUUID, e -> e));

        List<NotificationData> notifList = loadList("notifications.json", new TypeToken<List<NotificationData>>(){}.getType());
        pendingNotifications = notifList.stream().collect(Collectors.toMap(NotificationData::getPlayerUUID, e -> e));
    }

    private <T> List<T> loadList(String fileName, Type type) {
        File file = new File(dataFolder, fileName);
        if (!file.exists()) return new ArrayList<>();
        try (Reader reader = new FileReader(file)) {
            List<T> list = gson.fromJson(reader, type);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not load " + fileName);
            return new ArrayList<>();
        }
    }

    public void saveData() {
        saveFile("bounties.json", activeBounties);
        saveFile("history.json", history);
        saveFile("leaderboard.json", new ArrayList<>(leaderboard.values()));
        saveFile("notifications.json", new ArrayList<>(pendingNotifications.values()));
    }

    private void saveFile(String fileName, Object data) {
        File file = new File(dataFolder, fileName);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + fileName);
        }
    }

    private void startExpiryTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            long warningMillis = plugin.getConfig().getInt("settings.expiry-warning-hours") * 3600000L;
            double refundPercent = plugin.getConfig().getDouble("settings.expiry-refund-percent") / 100.0;

            synchronized (activeBounties) {
                Iterator<BountyData> it = activeBounties.iterator();
                while (it.hasNext()) {
                    BountyData bounty = it.next();
                    if (now >= bounty.getExpiryTimestamp()) {
                        it.remove();
                        processExpiry(bounty, refundPercent);
                    } else if (now >= bounty.getExpiryTimestamp() - warningMillis && now < bounty.getExpiryTimestamp() - warningMillis + 60000L) {
                        sendExpiryWarning(bounty);
                    }
                }
            }
        }, 1200L, 1200L);
    }

    private void processExpiry(BountyData bounty, double refundPercent) {
        double refund = bounty.getAmount() * refundPercent;
        plugin.getEconomyHandler().deposit(bounty.getPlacedByUUID(), refund);
        
        String msg = "⌛ [BountyHunter] Your bounty on " + bounty.getTargetName() + " has expired. Refund: $" + String.format("%.2f", refund);
        addNotification(bounty.getPlacedByUUID(), msg);
        
        Player placer = Bukkit.getPlayer(bounty.getPlacedByUUID());
        if (placer != null) {
            plugin.getMessageUtil().sendMessage(placer, msg);
        }
    }

    private void sendExpiryWarning(BountyData bounty) {
        Player placer = Bukkit.getPlayer(bounty.getPlacedByUUID());
        if (placer != null) {
            long remainingHours = (bounty.getExpiryTimestamp() - System.currentTimeMillis()) / 3600000L;
            plugin.getMessageUtil().sendMessage(placer, "⏳ Your $" + bounty.getAmount() + " bounty on " + bounty.getTargetName() + " expires in " + remainingHours + " hours. Use /bounty extend " + bounty.getTargetName() + " to keep it active.");
            plugin.getMessageUtil().playSound(placer, "BLOCK_NOTE_BLOCK_PLING");
        }
    }

    private void startAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 6000L, 6000L);
    }

    public List<BountyData> getActiveBounties() { return activeBounties; }
    public List<HistoryEntry> getHistory() { return history; }
    public Map<UUID, LeaderboardEntry> getLeaderboard() { return leaderboard; }

    public double getTotalBounty(UUID targetUUID) {
        return activeBounties.stream()
                .filter(b -> b.getTargetUUID().equals(targetUUID))
                .mapToDouble(BountyData::getAmount)
                .sum();
    }

    public List<BountyData> getBountiesOn(UUID targetUUID) {
        return activeBounties.stream()
                .filter(b -> b.getTargetUUID().equals(targetUUID))
                .collect(Collectors.toList());
    }

    public void addBounty(BountyData bounty) {
        activeBounties.add(bounty);
        placementCooldowns.computeIfAbsent(bounty.getPlacedByUUID(), k -> new ConcurrentHashMap<>())
                .put(bounty.getTargetUUID(), System.currentTimeMillis());
    }

    public boolean isOnCooldown(UUID placer, UUID target) {
        Map<UUID, Long> targets = placementCooldowns.get(placer);
        if (targets == null) return false;
        Long lastPlaced = targets.get(target);
        if (lastPlaced == null) return false;
        long cooldownMillis = plugin.getConfig().getInt("settings.same-target-cooldown-seconds") * 1000L;
        boolean onCooldown = System.currentTimeMillis() - lastPlaced < cooldownMillis;
        if (!onCooldown) targets.remove(target);
        return onCooldown;
    }

    public long getCooldownRemaining(UUID placer, UUID target) {
        Map<UUID, Long> targets = placementCooldowns.get(placer);
        if (targets == null) return 0;
        Long lastPlaced = targets.get(target);
        if (lastPlaced == null) return 0;
        long cooldownMillis = plugin.getConfig().getInt("settings.same-target-cooldown-seconds") * 1000L;
        long remaining = Math.max(0, (lastPlaced + cooldownMillis) - System.currentTimeMillis()) / 1000L;
        if (remaining == 0) targets.remove(target);
        return remaining;
    }

    public void removeBounty(BountyData bounty) {
        activeBounties.remove(bounty);
    }

    public void addHistory(HistoryEntry entry) {
        history.add(0, entry);
        if (history.size() > 50) history.remove(50);
    }

    public void updateLeaderboard(UUID playerUUID, String name, boolean isBedrock, double amount) {
        LeaderboardEntry entry = leaderboard.computeIfAbsent(playerUUID, k -> new LeaderboardEntry(playerUUID, name, isBedrock));
        entry.addCollection(amount);
    }

    public void addNotification(UUID playerUUID, String message) {
        NotificationData data = pendingNotifications.computeIfAbsent(playerUUID, NotificationData::new);
        data.addMessage(message);
    }

    public NotificationData getPendingNotifications(UUID playerUUID) {
        return pendingNotifications.remove(playerUUID);
    }
}
