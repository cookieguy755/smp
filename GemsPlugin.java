package dev.gems;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class GemsPlugin extends JavaPlugin {

    private PlayerDataManager playerData;
    private GemManager gemManager;
    private LevelManager levelManager;
    private StunManager stunManager;
    private AbilityManager abilityManager;
    private AmethystRecipe amethystRecipe;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        playerData = new PlayerDataManager(this);
        gemManager = new GemManager(this);
        levelManager = new LevelManager(this);
        stunManager = new StunManager(this);
        abilityManager = new AbilityManager(this);
        amethystRecipe = new AmethystRecipe(this);

        Bukkit.getPluginManager().registerEvents(new GemListener(this), this);
        Bukkit.getPluginManager().registerEvents(stunManager, this);
        Bukkit.getPluginManager().registerEvents(abilityManager, this); // Crystal-Aegis-Reflect
        Bukkit.getPluginManager().registerEvents(amethystRecipe, this); // Craft-Limit

        // Echter Paper-Brigadier-Command: erscheint mit Tab-Completion als /gem, nicht nur als Text.
        GemCommand gemCommand = new GemCommand(this);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(gemCommand.build(), "Gems-Verwaltung", java.util.List.of("gems"));
        });

        amethystRecipe.register();

        // Passive Effekte alle 2 Sekunden
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("passives", true)) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (GemType type : gemManager.getGemsInInventory(p)) {
                    abilityManager.applyPassive(p, type);
                }
            }
        }, 40L, 40L);

        // Autosave alle 5 Minuten
        Bukkit.getScheduler().runTaskTimer(this, () -> playerData.save(), 6000L, 6000L);

        getLogger().info("GemsSMP aktiviert.");
    }

    @Override
    public void onDisable() {
        if (amethystRecipe != null) amethystRecipe.unregister();
        if (stunManager != null) stunManager.shutdown();
        if (playerData != null) playerData.save();
    }

    public PlayerDataManager getPlayerData() { return playerData; }
    public GemManager getGemManager() { return gemManager; }
    public LevelManager getLevelManager() { return levelManager; }
    public StunManager getStunManager() { return stunManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public AmethystRecipe getAmethystRecipe() { return amethystRecipe; }
}
