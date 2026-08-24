/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;

public class PotatosmpModBrewingRecipes {
	public static void load() {
		FabricPotionBrewingBuilder.BUILD.register((builder) -> {
			builder.registerPotionRecipe(PotatosmpModPotions.POTATION, Ingredient.of(Items.WHEAT), PotatosmpModPotions.VODKA_POTION);
		});
	}
}