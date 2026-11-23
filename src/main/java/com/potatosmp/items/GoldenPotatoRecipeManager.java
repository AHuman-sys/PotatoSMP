package com.potatosmp.items;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

public final class GoldenPotatoRecipeManager {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public GoldenPotatoRecipeManager(PotatoSMPPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void registerRecipes() {
        unregisterRecipes();
        if (!configManager.isFeatureEnabled("golden-potatoes")
                || !configManager.getConfig().getBoolean("golden-potatoes.enable-special-recipes", true)) {
            return;
        }

        // 1. Golden Potato (crafted by surrounding a Potato with 8 Gold Ingots)
        NamespacedKey goldenPotatoKey = new NamespacedKey(plugin, "recipe_golden_potato");
        ShapedRecipe gpRecipe = new ShapedRecipe(goldenPotatoKey, ItemFactory.create(PotatoItemType.GOLDEN_POTATO));
        gpRecipe.shape("GGG", "GPG", "GGG");
        gpRecipe.setIngredient('G', Material.GOLD_INGOT);
        gpRecipe.setIngredient('P', Material.POTATO);
        Bukkit.addRecipe(gpRecipe);
        registeredKeys.add(goldenPotatoKey);

        // 2. Golden Baked Potato (crafting Golden Potato + Blaze Powder or Coal? Use exact Golden Potato choice)
        NamespacedKey bakedCraftKey = new NamespacedKey(plugin, "recipe_golden_baked_potato");
        ShapedRecipe bakedCraft = new ShapedRecipe(bakedCraftKey, ItemFactory.create(PotatoItemType.GOLDEN_BAKED_POTATO));
        bakedCraft.shape(" C ", " G ", " C ");
        bakedCraft.setIngredient('C', Material.BLAZE_POWDER);
        bakedCraft.setIngredient('G', new RecipeChoice.ExactChoice(ItemFactory.create(PotatoItemType.GOLDEN_POTATO)));
        Bukkit.addRecipe(bakedCraft);
        registeredKeys.add(bakedCraftKey);

        // 3. Furnace Smelt Golden Potato -> Golden Baked Potato
        NamespacedKey furnaceKey = new NamespacedKey(plugin, "recipe_smelt_golden_potato");
        FurnaceRecipe furnaceRecipe = new FurnaceRecipe(
                furnaceKey,
                ItemFactory.create(PotatoItemType.GOLDEN_BAKED_POTATO),
                new RecipeChoice.ExactChoice(ItemFactory.create(PotatoItemType.GOLDEN_POTATO)),
                2.0f,
                200
        );
        Bukkit.addRecipe(furnaceRecipe);
        registeredKeys.add(furnaceKey);
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
    }
}
