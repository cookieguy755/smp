package dev.gems;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerDataManager {

    private final GemsPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;
    private boolean dirty = false;

    public PlayerDataManager(GemsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    private String path(UUID id, GemType type, String key) {
        return "players." + id + "." + type.name() + "." + key;
    }

    public int getLevel(UUID id, GemType type) {
        return Math.max(1, yaml.getInt(path(id, type, "level"), 1));
    }

    public int getXp(UUID id, GemType type) {
        return Math.max(0, yaml.getInt(path(id, type, "xp"), 0));
    }

    public void setLevel(UUID id, GemType type, int level) {
        yaml.set(path(id, type, "level"), level);
        dirty = true;
    }

    public void setXp(UUID id, GemType type, int xp) {
        yaml.set(path(id, type, "xp"), xp);
        dirty = true;
    }

    public boolean hasStarter(UUID id) {
        return yaml.getBoolean("players." + id + ".starter", false);
    }

    public void setStarter(UUID id) {
        yaml.set("players." + id + ".starter", true);
        dirty = true;
    }

    /** Wie oft das Amethyst-Gem serverweit schon gecraftet wurde. */
    public int getAmethystCrafts() {
        return yaml.getInt("server.amethyst-crafts", 0);
    }

    public void incrementAmethystCrafts() {
        yaml.set("server.amethyst-crafts", getAmethystCrafts() + 1);
        dirty = true;
    }

    public void save() {
        if (!dirty) return;
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte players.yml nicht speichern: " + e.getMessage());
        }
    }
}
