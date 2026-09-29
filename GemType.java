package dev.gems;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum GemType {
    FIRE("Feuer-Gem", NamedTextColor.RED, Material.BLAZE_POWDER, "Burn", "Flame Beam", false),
    STRENGTH("Stärke-Gem", NamedTextColor.DARK_RED, Material.NETHERITE_SCRAP, "Smash", "Rage", false),
    FROST("Frost-Gem", NamedTextColor.AQUA, Material.PRISMARINE_CRYSTALS, "Stun", "Frost Nova", false),
    NATURE("Natur-Gem", NamedTextColor.GREEN, Material.MOSS_CARPET, "Entangle", "Regrowth", false),
    AMETHYST("Amethyst-Gem", NamedTextColor.LIGHT_PURPLE, Material.AMETHYST_SHARD, "Crystal Burst", "Crystal Aegis", true);

    private final String displayName;
    private final NamedTextColor color;
    private final Material material;
    private final String ability1;
    private final String ability2;
    private final boolean rare;

    GemType(String displayName, NamedTextColor color, Material material, String ability1, String ability2, boolean rare) {
        this.displayName = displayName;
        this.color = color;
        this.material = material;
        this.ability1 = ability1;
        this.ability2 = ability2;
        this.rare = rare;
    }

    public String getDisplayName() { return displayName; }
    public NamedTextColor getColor() { return color; }
    public Material getMaterial() { return material; }
    public String getAbility1() { return ability1; }
    public String getAbility2() { return ability2; }
    /** Amethyst = das seltene, stärkste Gem (kein Zufalls-Drop, eigene XP-Kurve). */
    public boolean isRare() { return rare; }

    public static GemType fromString(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Alle normalen (nicht-seltenen) Gems, für den Zufalls-Join-Drop. */
    public static GemType[] common() {
        return new GemType[]{FIRE, STRENGTH, FROST, NATURE};
    }
}
