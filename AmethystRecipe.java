package dev.gems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

/**
 * Registriert und verwaltet das Crafting-Rezept für das Amethyst-Gem.
 * Das Gem ist damit nicht per Zufall erhältlich, sondern muss bewusst
 * hergestellt werden - und ist serverweit begrenzt (siehe config.yml).
 */
public class AmethystRecipe implements Listener {

    private final GemsPlugin plugin;
    private final NamespacedKey recipeKey;

    public AmethystRecipe(GemsPlugin plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "amethyst_gem");
    }

    public void register() {
        if (!plugin.getConfig().getBoolean("amethyst.craftable", true)) return;

        Bukkit.removeRecipe(recipeKey);
        ItemStack result = plugin.getGemManager().createGem(GemType.AMETHYST);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape("DAD", "ASA", "DAD");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        recipe.setIngredient('S', Material.NETHER_STAR);
        recipe.setCategory(CraftingBookCategory.MISC);

        Bukkit.addRecipe(recipe);
    }

    public void unregister() {
        Bukkit.removeRecipe(recipeKey);
    }

    private int maxCrafts() {
        return plugin.getConfig().getInt("amethyst.max-crafts", 3);
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null || !matches(event.getRecipe())) return;
        int max = maxCrafts();
        if (max < 0) return; // unbegrenzt
        if (plugin.getPlayerData().getAmethystCrafts() >= max) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe() == null || !matches(event.getRecipe())) return;
        int max = maxCrafts();
        if (max >= 0 && plugin.getPlayerData().getAmethystCrafts() >= max) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getPlayerData().incrementAmethystCrafts();
            plugin.getPlayerData().save();
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "Du hast das Amethyst-Gem hergestellt! (" + plugin.getPlayerData().getAmethystCrafts()
                            + (max >= 0 ? "/" + max : "") + " auf diesem Server)",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
        }
    }

    private boolean matches(org.bukkit.inventory.Recipe recipe) {
        return recipe instanceof ShapedRecipe shaped && shaped.getKey().equals(recipeKey);
    }
}
