package dev.gems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Führt alle Ability-Effekte aus und verwaltet Cooldowns.
 * Auch als Listener registriert (für den Crystal-Aegis-Reflect).
 */
public class AbilityManager implements Listener {

    private final GemsPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    /** Spieler mit aktivem Crystal Aegis -> Ablaufzeitpunkt (Amethyst-Gem). */
    private final Map<UUID, Long> aegisActive = new HashMap<>();

    public AbilityManager(GemsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Ability-Aufruf
    // ------------------------------------------------------------------

    public void use(Player player, GemType type, boolean secondary) {
        String abilityId = type.name() + (secondary ? "_2" : "_1");
        int level = plugin.getPlayerData().getLevel(player.getUniqueId(), type);

        long remaining = getRemaining(player, abilityId);
        if (remaining > 0) {
            player.sendActionBar(Component.text(
                    "Cooldown: " + String.format(Locale.US, "%.1f", remaining / 1000.0) + "s", NamedTextColor.RED));
            return;
        }

        boolean success = switch (type) {
            case FIRE -> secondary ? flameBeam(player, level) : burn(player, level);
            case STRENGTH -> secondary ? rage(player, level) : smash(player, level);
            case FROST -> secondary ? frostNova(player, level) : stun(player, level);
            case NATURE -> secondary ? regrowth(player, level) : entangle(player, level);
            case AMETHYST -> secondary ? crystalAegis(player, level) : crystalBurst(player, level);
        };

        if (!success) return;

        setCooldown(player, abilityId, type, secondary, level);
        String name = secondary ? type.getAbility2() : type.getAbility1();
        player.sendActionBar(Component.text(name + " (Lv. " + level + ")", type.getColor()));
        plugin.getLevelManager().addXp(player, type, plugin.getConfig().getInt("xp.ability-use", 2));
    }

    // ------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------

    private long getRemaining(Player p, String id) {
        Map<String, Long> map = cooldowns.get(p.getUniqueId());
        if (map == null) return 0;
        Long until = map.get(id);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    private void setCooldown(Player p, String id, GemType type, boolean secondary, int level) {
        String path = "cooldowns." + type.name().toLowerCase() + "." + (secondary ? "ability2" : "ability1");
        double base = plugin.getConfig().getDouble(path, 20.0);
        double reduction = plugin.getConfig().getDouble("cooldown-reduction-per-level", 0.05);
        double factor = Math.max(0.3, 1.0 - (level - 1) * reduction);
        long millis = (long) (base * factor * 1000);
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(id, System.currentTimeMillis() + millis);
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    private boolean isValidTarget(Player caster, LivingEntity le) {
        if (le.equals(caster) || le instanceof ArmorStand || le.isDead() || !le.isValid()) return false;
        if (le instanceof Player other) {
            if (!plugin.getConfig().getBoolean("affect-players", true)) return false;
            GameMode gm = other.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return false;
        }
        if (le instanceof Tameable t && t.isTamed() && caster.getUniqueId().equals(t.getOwnerUniqueId())) {
            return false;
        }
        return true;
    }

    private boolean isFriendly(Player caster, LivingEntity le) {
        if (le.equals(caster)) return true;
        if (le instanceof Player) return true; // in einem SMP gelten andere Spieler als Freunde für Heilung
        if (le instanceof Tameable t && t.isTamed() && caster.getUniqueId().equals(t.getOwnerUniqueId())) return true;
        return false;
    }

    private List<LivingEntity> nearbyEnemies(Player caster, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!isValidTarget(caster, le)) continue;
            if (le.getLocation().distanceSquared(caster.getLocation()) > r2) continue;
            list.add(le);
        }
        return list;
    }

    private List<LivingEntity> nearbyFriendlies(Player caster, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!isFriendly(caster, le)) continue;
            if (le.getLocation().distanceSquared(caster.getLocation()) > r2) continue;
            list.add(le);
        }
        return list;
    }

    private void noTarget(Player p) {
        p.sendActionBar(Component.text("Kein Ziel in Reichweite", NamedTextColor.GRAY));
    }

    private Location center(LivingEntity e) {
        return e.getLocation().add(0, e.getHeight() / 2, 0);
    }

    private double maxHealth(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attribute.MAX_HEALTH);
        return inst != null ? inst.getValue() : 20.0;
    }

    private void healEntity(LivingEntity e, double amount) {
        e.setHealth(Math.min(maxHealth(e), e.getHealth() + amount));
    }

    // ------------------------------------------------------------------
    // FEUER-GEM
    // ------------------------------------------------------------------

    /** Burn: zündet alle Gegner im Umkreis an. */
    private boolean burn(Player p, int level) {
        double radius = 4 + level;
        List<LivingEntity> targets = nearbyEnemies(p, radius);
        if (targets.isEmpty()) {
            noTarget(p);
            return false;
        }
        int fireTicks = (3 + level * 2) * 20;
        World w = p.getWorld();
        w.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 120, radius / 3, 0.5, radius / 3, 0.05);
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        for (LivingEntity t : targets) {
            t.setFireTicks(fireTicks);
            t.damage(1 + level * 0.5, p);
            w.spawnParticle(Particle.FLAME, center(t), 20, 0.3, 0.5, 0.3, 0.02);
        }
        return true;
    }

    /** Flame Beam: Strahl in Blickrichtung, trifft das erste Ziel. */
    private boolean flameBeam(Player p, int level) {
        double range = 10 + level * 2;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult blockHit = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double blockDist = blockHit != null ? blockHit.getHitPosition().distance(eye.toVector()) : range;

        RayTraceResult hit = w.rayTraceEntities(eye, dir, range, 0.6,
                e -> e instanceof LivingEntity le && isValidTarget(p, le));

        double length = blockDist;
        LivingEntity victim = null;
        if (hit != null && hit.getHitEntity() instanceof LivingEntity le) {
            double entityDist = hit.getHitPosition().distance(eye.toVector());
            if (entityDist <= blockDist) {
                victim = le;
                length = entityDist;
            }
        }

        for (double d = 1; d < length; d += 0.5) {
            w.spawnParticle(Particle.FLAME, eye.clone().add(dir.clone().multiply(d)), 2, 0.05, 0.05, 0.05, 0);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.2f);

        if (victim != null) {
            victim.damage(5 + level * 1.5, p);
            victim.setFireTicks((4 + level) * 20);
            w.spawnParticle(Particle.EXPLOSION, center(victim), 1);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // STÄRKE-GEM
    // ------------------------------------------------------------------

    /** Smash: Flächenschaden mit Knockback. */
    private boolean smash(Player p, int level) {
        double radius = 3 + level * 0.5;
        List<LivingEntity> targets = nearbyEnemies(p, radius);
        if (targets.isEmpty()) {
            noTarget(p);
            return false;
        }
        double damage = 4 + level * 1.5;
        World w = p.getWorld();
        for (LivingEntity t : targets) {
            t.damage(damage, p);
            Vector kb = t.getLocation().toVector().subtract(p.getLocation().toVector());
            kb.setY(0);
            if (kb.lengthSquared() > 0) kb.normalize().multiply(0.8);
            kb.setY(0.5);
            t.setVelocity(kb);
            w.spawnParticle(Particle.CRIT, center(t), 25, 0.3, 0.5, 0.3, 0.2);
        }
        w.spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 0.5, 0), 3, radius / 3, 0.2, radius / 3, 0);
        w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        return true;
    }

    /** Rage: Stärke + Resistenz für einige Sekunden. */
    private boolean rage(Player p, int level) {
        int duration = (6 + level * 2) * 20;
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, level >= 4 ? 1 : 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0));
        p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 2, 0), 10, 0.4, 0.3, 0.4, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1f);
        return true;
    }

    // ------------------------------------------------------------------
    // FROST-GEM
    // ------------------------------------------------------------------

    /** Stun: alle Gegner im Umkreis können sich für kurze Zeit nicht bewegen/angreifen. */
    private boolean stun(Player p, int level) {
        double radius = 4 + level * 0.5;
        List<LivingEntity> targets = nearbyEnemies(p, radius);
        if (targets.isEmpty()) {
            noTarget(p);
            return false;
        }
        int ticks = (int) ((1.5 + level * 0.5) * 20);
        World w = p.getWorld();
        for (LivingEntity t : targets) {
            plugin.getStunManager().stun(t, ticks);
            w.spawnParticle(Particle.SNOWFLAKE, center(t), 40, 0.4, 0.6, 0.4, 0.02);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        return true;
    }

    /** Frost Nova: Schaden + starke Verlangsamung im Umkreis. */
    private boolean frostNova(Player p, int level) {
        double radius = 5 + level * 0.5;
        List<LivingEntity> targets = nearbyEnemies(p, radius);
        if (targets.isEmpty()) {
            noTarget(p);
            return false;
        }
        World w = p.getWorld();
        int slowTicks = (4 + level) * 20;
        int amp = level >= 4 ? 2 : 1;
        for (LivingEntity t : targets) {
            t.damage(3 + level, p);
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, amp));
            t.setFreezeTicks(t.getMaxFreezeTicks() + 40);
        }
        w.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 150, radius / 3, 0.6, radius / 3, 0.05);
        w.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------
    // NATUR-GEM
    // ------------------------------------------------------------------

    /**
     * Entangle: Wurzeln fesseln alle Gegner im Umkreis (fast bewegungsunfähig,
     * können aber weiter angreifen) und vergiften sie leicht.
     * Anders als Frosts Stun: kein voller Stillstand, dafür Schaden über Zeit.
     */
    private boolean entangle(Player p, int level) {
        double radius = 4 + level * 0.5;
        List<LivingEntity> targets = nearbyEnemies(p, radius);
        if (targets.isEmpty()) {
            noTarget(p);
            return false;
        }
        World w = p.getWorld();
        int ticks = (int) ((2.5 + level * 0.6) * 20);
        int poisonAmp = level >= 4 ? 1 : 0;
        for (LivingEntity t : targets) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, true));
            t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, poisonAmp, false, true));
            w.spawnParticle(Particle.HAPPY_VILLAGER, center(t), 15, 0.3, 0.5, 0.3, 0);
            w.spawnParticle(Particle.BLOCK, t.getLocation(), 30, 0.4, 0.1, 0.4, 0,
                    org.bukkit.Material.MOSS_CARPET.createBlockData());
        }
        w.playSound(p.getLocation(), Sound.BLOCK_GRASS_BREAK, 1f, 0.6f);
        return true;
    }

    /** Regrowth: heilt dich und nahe Verbündete sofort und über Zeit. */
    private boolean regrowth(Player p, int level) {
        double radius = 4 + level * 0.5;
        List<LivingEntity> targets = nearbyFriendlies(p, radius);
        World w = p.getWorld();
        int regenTicks = (4 + level) * 20;
        int amp = level >= 4 ? 1 : 0;
        for (LivingEntity t : targets) {
            healEntity(t, 2 + level);
            t.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, amp));
            w.spawnParticle(Particle.HAPPY_VILLAGER, center(t), 20, 0.3, 0.5, 0.3, 0);
        }
        w.playSound(p.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------
    // AMETHYST-GEM (das seltenste und stärkste Gem)
    // ------------------------------------------------------------------

    /**
     * Crystal Burst: starker Flächenschaden + Knockback + kurzer Stun.
     * Größerer Radius und mehr Schaden als die anderen Flächenangriffe,
     * dafür deutlich längerer Cooldown (siehe config.yml).
     */
    private boolean crystalBurst(Player p, int level) {
        double radius = 5 + level * 0.6;
        List<LivingEntity> targets = nearbyEnemies(p, radius);
        if (targets.isEmpty()) {
            noTarget(p);
            return false;
        }
        double damage = 6 + level * 2.0;
        int stunTicks = (int) ((0.5 + level * 0.2) * 20); // kurzer Stun, kein Dauer-Lockdown
        World w = p.getWorld();
        for (LivingEntity t : targets) {
            t.damage(damage, p);
            plugin.getStunManager().stun(t, stunTicks);
            Vector kb = t.getLocation().toVector().subtract(p.getLocation().toVector());
            kb.setY(0.3);
            if (kb.lengthSquared() > 0.09) kb.normalize().multiply(1.1).setY(0.4);
            t.setVelocity(kb);
            w.spawnParticle(Particle.END_ROD, center(t), 30, 0.4, 0.6, 0.4, 0.05);
        }
        w.spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 0.5, 0), 2, radius / 3, 0.3, radius / 3, 0);
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        return true;
    }

    /**
     * Crystal Aegis: Schild aus Absorption + Resistenz, das einen Teil des
     * erlittenen Schadens an den Angreifer zurückwirft. Rein defensiv,
     * daher kein Flächenschaden — das hält das seltenste Gem stark, aber fair.
     */
    private boolean crystalAegis(Player p, int level) {
        int duration = (4 + level) * 20;
        int absorptionAmp = level >= 4 ? 2 : 1;
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, absorptionAmp, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, true));
        aegisActive.put(p.getUniqueId(), System.currentTimeMillis() + duration * 50L);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        return true;
    }

    private double aegisReflectPercent() {
        return 0.25; // wirft 25% des erlittenen Nahkampfschadens zurück
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAegisReflect(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender)) return;
        Long until = aegisActive.get(defender.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() >= until) {
            aegisActive.remove(defender.getUniqueId());
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity attacker) || attacker.equals(defender)) return;

        double reflect = event.getFinalDamage() * aegisReflectPercent();
        if (reflect <= 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (attacker.isValid() && !attacker.isDead()) {
                attacker.damage(reflect, defender);
            }
        });
        defender.getWorld().spawnParticle(Particle.END_ROD, center(defender), 10, 0.3, 0.4, 0.3, 0.02);
    }

    // ------------------------------------------------------------------
    // Passive Effekte (solange das Gem im Inventar ist)
    // ------------------------------------------------------------------

    public void applyPassive(Player p, GemType type) {
        int level = plugin.getPlayerData().getLevel(p.getUniqueId(), type);
        int dur = 100;
        switch (type) {
            case FIRE -> {
                if (level >= 2) passive(p, PotionEffectType.FIRE_RESISTANCE, dur, 0);
            }
            case STRENGTH -> {
                if (level >= 3) passive(p, PotionEffectType.STRENGTH, dur, 0);
            }
            case FROST -> {
                if (level >= 2) passive(p, PotionEffectType.WATER_BREATHING, dur, 0);
                if (level >= 4) passive(p, PotionEffectType.DOLPHINS_GRACE, dur, 0);
            }
            case NATURE -> {
                if (level >= 2) passive(p, PotionEffectType.SPEED, dur, 0);
                if (level >= 4) passive(p, PotionEffectType.SATURATION, dur, 0);
            }
            case AMETHYST -> {
                // Das stärkste Gem bekommt sein bestes Passiv erst spät, bleibt aber dezent.
                if (level >= 3) passive(p, PotionEffectType.HERO_OF_THE_VILLAGE, dur, 0);
                if (level >= 5) passive(p, PotionEffectType.LUCK, dur, 0);
            }
        }
    }

    private void passive(Player p, PotionEffectType effect, int duration, int amplifier) {
        p.addPotionEffect(new PotionEffect(effect, duration, amplifier, true, false, true));
    }
}
