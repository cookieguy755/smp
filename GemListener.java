package dev.gems;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GemListener implements Listener {

    private final GemsPlugin plugin;
    private final Map<UUID, List<ItemStack>> savedGems = new HashMap<>();

    public GemListener(GemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        GemType type = plugin.getGemManager().getGemType(event.getItem());
        if (type == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.getStunManager().isStunned(player)) return;
        plugin.getAbilityManager().use(player, type, player.isSneaking());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        FileConfiguration cfg = plugin.getConfig();
        int xp;
        if (victim instanceof Player) {
            xp = cfg.getInt("xp.kill-player", 40);
        } else if (victim instanceof Monster) {
            xp = cfg.getInt("xp.kill-mob", 3);
        } else {
            return;
        }
        for (GemType type : plugin.getGemManager().getGemsInInventory(killer)) {
            plugin.getLevelManager().addXp(killer, type, xp);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        boolean keepGems = plugin.getConfig().getBoolean("keep-gems-on-death", true);
        boolean amethystDropsOnPvp = plugin.getConfig().getBoolean("amethyst.drop-on-pvp-death", true);
        boolean killedByPlayer = victim.getKiller() != null;

        List<ItemStack> kept = new ArrayList<>();
        event.getDrops().removeIf(item -> {
            GemType type = plugin.getGemManager().getGemType(item);
            if (type == null) return false;

            // Das Amethyst-Gem ist ein Risiko: stirbt man im PvP, kann es fallen gelassen werden.
            boolean amethystDrops = type == GemType.AMETHYST && killedByPlayer && amethystDropsOnPvp;
            if (amethystDrops) return false; // bleibt als normaler Death-Drop liegen

            if (!keepGems) return false;
            kept.add(item);
            return true;
        });
        if (!kept.isEmpty()) {
            savedGems.put(victim.getUniqueId(), kept);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> kept = savedGems.remove(player.getUniqueId());
        if (kept == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> giveItems(player, kept));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        List<ItemStack> kept = savedGems.remove(player.getUniqueId());
        if (kept != null) giveItems(player, kept);

        if (plugin.getConfig().getBoolean("give-random-gem-on-first-join", true)
                && !plugin.getPlayerData().hasStarter(player.getUniqueId())) {
            GemType[] common = GemType.common(); // niemals das Amethyst-Gem per Zufall
            GemType type = common[ThreadLocalRandom.current().nextInt(common.length)];
            giveItems(player, List.of(plugin.getGemManager().createGem(type)));
            plugin.getPlayerData().setStarter(player.getUniqueId());
            player.sendMessage(Component.text("Du hast das " + type.getDisplayName() + " erhalten!", type.getColor()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerData().save();
    }

    public static void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
    }
}
