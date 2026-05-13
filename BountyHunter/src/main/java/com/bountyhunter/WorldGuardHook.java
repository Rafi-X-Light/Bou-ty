package com.bountyhunter;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class WorldGuardHook {
    private final BountyHunterPlugin plugin;

    public WorldGuardHook(BountyHunterPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInSafeZone(Player player, List<String> safeZones) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return false;

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

        for (ProtectedRegion region : set) {
            if (safeZones.contains(region.getId())) return true;
        }
        return false;
    }
}