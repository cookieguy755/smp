package dev.gems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class GemManager {

    private final NamespacedKey gemKey;

    public GemManager(GemsPlugin plugin) {
        this.gemKey = new NamespacedKey(plugin, "gem_type");
    }

    public NamespacedKey getGemKey() { return gemKey; }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack createGem(GemType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.getDisplayName(), type.getColor(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = type.isRare()
                ? List.of(
                        line("Rechtsklick: " + type.getAbility1(), NamedTextColor.GRAY),
                        line("Shift + Rechtsklick: " + type.getAbility2(), NamedTextColor.GRAY),
                        line("Passive Effekte im Inventar", NamedTextColor.DARK_GRAY),
                        line("✦ Seltenstes Gem — kann gecraftet werden ✦", NamedTextColor.LIGHT_PURPLE))
                : List.of(
                        line("Rechtsklick: " + type.getAbility1(), NamedTextColor.GRAY),
                        line("Shift + Rechtsklick: " + type.getAbility2(), NamedTextColor.GRAY),
                        line("Passive Effekte im Inventar", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(gemKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public GemType getGemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(gemKey, PersistentDataType.STRING);
        return GemType.fromString(value);
    }

    public Set<GemType> getGemsInInventory(Player player) {
        Set<GemType> set = EnumSet.noneOf(GemType.class);
        for (ItemStack item : player.getInventory().getContents()) {
            GemType t = getGemType(item);
            if (t != null) set.add(t);
        }
        return set;
    }
}
