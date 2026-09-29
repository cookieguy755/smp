package dev.gems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Wird von Frosts "Stun" und Amethysts kurzem "Crystal Burst"-Stun genutzt. */
public class StunManager implements Listener {

    private final GemsPlugin plugin;
    private final Map<UUID, Long> stunnedPlayers = new HashMap<>();
    private final Map<UUID, Mob> stunnedMobs = new HashMap<>();
    private final Map<UUID, BukkitTask> mobTasks = new HashMap<>();

    public StunManager(GemsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isStunned(Player player) {
        Long until = stunnedPlayers.get(player.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            stunnedPlayers.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void stun(LivingEntity target, int ticks) {
        if (ticks <= 0) return;
        target.setFreezeTicks(target.getMaxFreezeTicks());

        if (target instanceof Player player) {
            long newUntil = System.currentTimeMillis() + ticks * 50L;
            stunnedPlayers.merge(player.getUniqueId(), newUntil, Math::max);
            player.sendActionBar(Component.text("Du wurdest gestunnt!", NamedTextColor.AQUA));
        } else if (target instanceof Mob mob) {
            UUID id = mob.getUniqueId();
            BukkitTask old = mobTasks.remove(id);
            if (old != null) {
                old.cancel(); // schon von uns gestunnt -> Timer neu starten
            } else if (!mob.hasAI()) {
                return; // KI ist aus einem anderen Grund aus, nicht anfassen
            }
            mob.setAI(false);
            stunnedMobs.put(id, mob);
            mobTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                mobTasks.remove(id);
                stunnedMobs.remove(id);
                if (mob.isValid()) mob.setAI(true);
            }, ticks));
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 5, false, true));
    }

    public void shutdown() {
        mobTasks.values().forEach(BukkitTask::cancel);
        mobTasks.clear();
        for (Mob mob : stunnedMobs.values()) {
            if (mob.isValid()) mob.setAI(true);
        }
        stunnedMobs.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isStunned(event.getPlayer())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location fixed = new Location(from.getWorld(), from.getX(), Math.min(from.getY(), to.getY()), from.getZ(),
                    to.getYaw(), to.getPitch());
            event.setTo(fixed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && isStunned(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isStunned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stunnedPlayers.remove(event.getPlayer().getUniqueId());
    }
}
