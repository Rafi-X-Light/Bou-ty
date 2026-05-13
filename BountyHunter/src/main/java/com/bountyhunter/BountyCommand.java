package com.bountyhunter;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class BountyCommand implements CommandExecutor, TabCompleter {
    private final BountyHunterPlugin plugin;

    public BountyCommand(BountyHunterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "set":
                handleSet(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "extend":
                handleExtend(sender, args);
                break;
            case "list":
                handleList(sender, args);
                break;
            case "top":
                handleTop(sender);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "history":
                handleHistory(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can set bounties.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("Usage: /bounty set <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("Player not found.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("You cannot set a bounty on yourself.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid amount.");
            return;
        }

        double min = plugin.getConfig().getDouble("settings.min-bounty-amount");
        double max = plugin.getConfig().getDouble("settings.max-bounty-amount");
        if (amount < min || amount > max) {
            player.sendMessage("Bounty amount must be between $" + min + " and $" + max);
            return;
        }

        if (plugin.getBountyManager().isOnCooldown(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage("You must wait " + plugin.getBountyManager().getCooldownRemaining(player.getUniqueId(), target.getUniqueId()) + "s before placing another bounty on this player.");
            return;
        }

        double currentTotal = plugin.getBountyManager().getTotalBounty(target.getUniqueId());
        double wealthGatePercent = plugin.getConfig().getDouble("settings.wealth-gate-percent") / 100.0;
        double requiredBalance = currentTotal * wealthGatePercent;

        if (currentTotal > 0 && plugin.getEconomyHandler().getBalance(player.getUniqueId()) < requiredBalance) {
            player.sendMessage("⚠ " + target.getName() + " has $" + currentTotal + " on their head. You need $" + requiredBalance + " in your balance to place a bounty.");
            return;
        }

        if (!plugin.getEconomyHandler().has(player.getUniqueId(), amount)) {
            player.sendMessage("You do not have enough money.");
            return;
        }

        plugin.getEconomyHandler().withdraw(player.getUniqueId(), amount);
        long expiry = System.currentTimeMillis() + (plugin.getConfig().getInt("settings.bounty-expiry-hours") * 3600000L);
        
        boolean isBedrock = plugin.getCrossplayUtil().isBedrockPlayer(player);
        BountyData bounty = new BountyData(target.getUniqueId(), target.getName(), player.getUniqueId(), player.getName(), isBedrock, amount, expiry);
        plugin.getBountyManager().addBounty(bounty);

        String placerName = plugin.getCrossplayUtil().getDisplayName(player);
        String targetName = plugin.getCrossplayUtil().getDisplayName(target);
        if (plugin.getConfig().getBoolean("settings.broadcast-on-set")) {
            plugin.getMessageUtil().broadcast("🎯 <gold>[BountyHunter]</gold> <white>" + placerName + "</white> placed a <green>$" + amount + "</green> bounty on <red>" + targetName + "</red>! Total on " + targetName + ": <green>$" + (currentTotal + amount) + "</green>");
            plugin.getMessageUtil().broadcastSound("ENTITY_ENDER_DRAGON_GROWL", 0.5f);
        }

        plugin.getMessageUtil().sendMessage(target, "⚠ <red>A bounty of $" + amount + " has been placed on your head!</red>");
        plugin.getMessageUtil().playSound(target, "BLOCK_NOTE_BLOCK_BASS");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) {
            player.sendMessage("Usage: /bounty remove <player>");
            return;
        }

        String targetName = args[1];
        List<BountyData> playerBounties = plugin.getBountyManager().getActiveBounties().stream()
                .filter(b -> b.getPlacedByUUID().equals(player.getUniqueId()) && b.getTargetName().equalsIgnoreCase(targetName))
                .collect(Collectors.toList());

        if (playerBounties.isEmpty()) {
            player.sendMessage("You have no active bounties on " + targetName);
            return;
        }

        double refundPercent = plugin.getConfig().getDouble("settings.cancel-refund-percent") / 100.0;
        double totalRefund = 0;
        for (BountyData b : playerBounties) {
            totalRefund += b.getAmount() * refundPercent;
            plugin.getBountyManager().removeBounty(b);
        }

        plugin.getEconomyHandler().deposit(player.getUniqueId(), totalRefund);
        player.sendMessage("Cancelled your bounties on " + targetName + ". Refunded $" + String.format("%.2f", totalRefund) + " (after 20% fee).");
    }

    private void handleExtend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) {
            player.sendMessage("Usage: /bounty extend <player>");
            return;
        }

        String targetName = args[1];
        List<BountyData> playerBounties = plugin.getBountyManager().getActiveBounties().stream()
                .filter(b -> b.getPlacedByUUID().equals(player.getUniqueId()) && b.getTargetName().equalsIgnoreCase(targetName))
                .collect(Collectors.toList());

        if (playerBounties.isEmpty()) {
            player.sendMessage("You have no active bounties on " + targetName);
            return;
        }

        double fee = plugin.getConfig().getDouble("settings.extend-fee");
        if (!plugin.getEconomyHandler().has(player.getUniqueId(), fee)) {
            player.sendMessage("You need $" + fee + " to extend this bounty.");
            return;
        }

        plugin.getEconomyHandler().withdraw(player.getUniqueId(), fee);
        long extendMillis = plugin.getConfig().getInt("settings.extend-hours") * 3600000L;
        for (BountyData b : playerBounties) {
            b.setExpiryTimestamp(b.getExpiryTimestamp() + extendMillis);
        }

        plugin.getMessageUtil().sendMessage(player, "🕐 <gold>[BountyHunter]</gold> Your bounty on " + targetName + " has been extended by " + plugin.getConfig().getInt("settings.extend-hours") + " hours.");
        plugin.getMessageUtil().playSound(player, "BLOCK_NOTE_BLOCK_CHIME");
    }

    private void handleList(CommandSender sender, String[] args) {
        List<BountyData> active = plugin.getBountyManager().getActiveBounties();
        if (active.isEmpty()) {
            sender.sendMessage("The world is at peace... for now.");
            return;
        }

        Map<UUID, List<BountyData>> grouped = active.stream().collect(Collectors.groupingBy(BountyData::getTargetUUID));
        sender.sendMessage("<gold>--- Active Bounties ---</gold>");
        
        int page = 1;
        if (args.length > 1) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        List<UUID> targets = new ArrayList<>(grouped.keySet());
        int totalPages = (int) Math.ceil(targets.size() / 5.0);
        page = Math.max(1, Math.min(page, totalPages));

        for (int i = (page - 1) * 5; i < Math.min(page * 5, targets.size()); i++) {
            UUID targetUUID = targets.get(i);
            List<BountyData> bounties = grouped.get(targetUUID);
            double total = bounties.stream().mapToDouble(BountyData::getAmount).sum();
            String targetName = bounties.get(0).getTargetName();
            long minExpiry = bounties.stream().mapToLong(BountyData::getExpiryTimestamp).min().orElse(0);
            long remainingHours = (minExpiry - System.currentTimeMillis()) / 3600000L;

            sender.sendMessage("<red>" + targetName + "</red>: <green>$" + total + "</green> (" + bounties.size() + " contributors) - <gray>Expires in " + remainingHours + "h</gray>");
        }
        sender.sendMessage("<gold>Page " + page + "/" + totalPages + " (Use /bounty list <page>)</gold>");
    }

    private void handleTop(CommandSender sender) {
        List<LeaderboardEntry> top = new ArrayList<>(plugin.getBountyManager().getLeaderboard().values());
        top.sort((a, b) -> Double.compare(b.getTotalEarned(), a.getTotalEarned()));

        sender.sendMessage("<gold>--- Top Bounty Hunters ---</gold>");
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            LeaderboardEntry entry = top.get(i);
            String name = entry.isBedrockPlayer() ? "[BE] " + entry.getPlayerName() : entry.getPlayerName();
            sender.sendMessage("<yellow>" + (i + 1) + ". " + name + "</yellow>: <green>$" + String.format("%.2f", entry.getTotalEarned()) + "</green> (" + entry.getBountiesCollected() + " collected)");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /bounty info <player>");
            return;
        }

        String targetName = args[1];
        List<BountyData> bounties = plugin.getBountyManager().getActiveBounties().stream()
                .filter(b -> b.getTargetName().equalsIgnoreCase(targetName))
                .collect(Collectors.toList());

        if (bounties.isEmpty()) {
            sender.sendMessage("No active bounties on " + targetName);
            return;
        }

        UUID targetUUID = bounties.get(0).getTargetUUID();
        double total = bounties.stream().mapToDouble(BountyData::getAmount).sum();
        sender.sendMessage("<gold>--- Bounty Info: " + targetName + " ---</gold>");
        sender.sendMessage("Total Pool: <green>$" + total + "</green>");
        
        for (BountyData b : bounties) {
            String placer = b.isBedrockPlacer() ? "[BE] " + b.getPlacedByName() : b.getPlacedByName();
            long remainingHours = (b.getExpiryTimestamp() - System.currentTimeMillis()) / 3600000L;
            sender.sendMessage("- <yellow>" + placer + "</yellow>: <green>$" + b.getAmount() + "</green> (Expires in " + remainingHours + "h)");
        }

        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            long nearby = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(targetUUID) && p.getWorld().equals(target.getWorld()) && p.getLocation().distance(target.getLocation()) <= 500)
                    .count();
            sender.sendMessage("<gray>Online players within 500 blocks: " + nearby + "</gray>");
        }
    }

    private void handleHistory(CommandSender sender, String[] args) {
        List<HistoryEntry> history = plugin.getBountyManager().getHistory();
        if (history.isEmpty()) {
            sender.sendMessage("No bounty history yet.");
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        int totalPages = (int) Math.ceil(history.size() / 10.0);
        page = Math.max(1, Math.min(page, totalPages));

        sender.sendMessage("<gold>--- Recent Collections ---</gold>");
        for (int i = (page - 1) * 10; i < Math.min(page * 10, history.size()); i++) {
            HistoryEntry e = history.get(i);
            long ago = (System.currentTimeMillis() - e.getTimestamp()) / 60000L;
            sender.sendMessage("<red>" + e.getTargetName() + "</red> was killed by <yellow>" + e.getKillerName() + "</yellow> for <green>$" + e.getTotalAmount() + "</green> (" + ago + "m ago)");
        }
        sender.sendMessage("<gold>Page " + page + "/" + totalPages + " (Use /bounty history <page>)</gold>");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("bountyhunter.admin")) {
            sender.sendMessage("No permission.");
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage("BountyHunter config reloaded.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("<gold>--- BountyHunter Commands ---</gold>");
        sender.sendMessage("/bounty set <player> <amount> - Place a bounty");
        sender.sendMessage("/bounty remove <player> - Cancel your bounty");
        sender.sendMessage("/bounty extend <player> - Extend your bounty (+12h)");
        sender.sendMessage("/bounty list [page] - View active bounties");
        sender.sendMessage("/bounty top - Hunter leaderboard");
        sender.sendMessage("/bounty info <player> - Detailed bounty info");
        sender.sendMessage("/bounty history [page] - Recent collections");
        if (sender.hasPermission("bountyhunter.admin")) {
            sender.sendMessage("/bounty reload - Reload config");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "remove", "extend", "list", "top", "info", "history", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("set") || sub.equals("remove") || sub.equals("extend") || sub.equals("info")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}
