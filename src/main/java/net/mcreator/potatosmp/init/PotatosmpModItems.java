/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;

import net.mcreator.potatosmp.item.PotatoBillItem;
import net.mcreator.potatosmp.PotatosmpMod;

import java.util.function.Function;

public class PotatosmpModItems {
	public static Item COMPRESSED_POTATO;
	public static Item POTATO_BILL;
	public static Item BANK;

	public static void load() {
		COMPRESSED_POTATO = block(PotatosmpModBlocks.COMPRESSED_POTATO, "compressed_potato");
		POTATO_BILL = register("potato_bill", PotatoBillItem::new);
		BANK = block(PotatosmpModBlocks.BANK, "bank", new Item.Properties().stacksTo(1).fireResistant());
	}

	// Start of user code block custom items
	// End of user code block custom items
	private static <I extends Item> I register(String name, Function<Item.Properties, ? extends I> supplier) {
		return (I) Items.registerItem(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, name)), (Function<Item.Properties, Item>) supplier);
	}

	private static Item block(Block block, String name) {
		return block(block, name, new Item.Properties());
	}

	private static Item block(Block block, String name, Item.Properties properties) {
		return Items.registerItem(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, name)), prop -> new BlockItem(block, prop), properties);
	}
}