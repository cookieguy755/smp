package dev.gems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class LevelManager {

    private final GemsPlugin plugin;

    public LevelManager(GemsPlugin plugin) {
        this.plugin = plugin;
    }

    public int getMaxLevel() {
        return plugin.getConfig().getIntegerList("xp-per-level").size() + 1;
    }

    private double multiplierFor(GemType type) {
        return plugin.getConfig().getDouble("xp-multiplier." + type.name().toLowerCase(), 1.0);
    }

    /** XP bis zum nächsten Level, oder -1 wenn Maximallevel erreicht. */
    public int xpNeeded(GemType type, int level) {
        List<Integer> list = plugin.getConfig().getIntegerList("xp-per-level");
        if (level < 1 || level > list.size()) return -1;
        return (int) Math.round(list.get(level - 1) * multiplierFor(type));
    }

    public void addXp(Player player, GemType type, int amount) {
        if (amount <= 0) return;
        PlayerDataManager data = plugin.getPlayerData();
        UUID id = player.getUniqueId();
        int level = data.getLevel(id, type);
        int xp = data.getXp(id, type) + amount;
        boolean leveledUp = false;

        int needed;
        while ((needed = xpNeeded(type, level)) > 0 && xp >= needed) {
            xp -= needed;
            level++;
            leveledUp = true;
        }
        if (xpNeeded(type, level) < 0) xp = 0; // Maximallevel

        data.setLevel(id, type, level);
        data.setXp(id, type, xp);

        if (leveledUp) {
            player.showTitle(Title.title(
                    Component.text(type.getDisplayName(), type.getColor()),
                    Component.text("Level " + level + " erreicht!", NamedTextColor.GOLD)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    public void setLevel(UUID id, GemType type, int level) {
        int clamped = Math.max(1, Math.min(getMaxLevel(), level));
        plugin.getPlayerData().setLevel(id, type, clamped);
        plugin.getPlayerData().setXp(id, type, 0);
    }
}
