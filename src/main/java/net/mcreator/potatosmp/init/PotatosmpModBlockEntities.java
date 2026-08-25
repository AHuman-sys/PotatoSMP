/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;

import net.mcreator.potatosmp.block.entity.BankBlockEntity;
import net.mcreator.potatosmp.PotatosmpMod;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

public class PotatosmpModBlockEntities {
	public static BlockEntityType<BankBlockEntity> BANK;

	public static void load() {
		BANK = register("bank", PotatosmpModBlocks.BANK, BankBlockEntity::new);
	}

	// Start of user code block custom block entities
	// End of user code block custom block entities
	private static <T extends BlockEntity> BlockEntityType<T> register(String registryname, Block block, FabricBlockEntityTypeBuilder.Factory<? extends T> supplier) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, registryname), FabricBlockEntityTypeBuilder.<T>create(supplier, block).build());
	}
}