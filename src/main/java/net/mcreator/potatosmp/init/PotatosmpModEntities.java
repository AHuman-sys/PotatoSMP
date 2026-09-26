/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;

import net.mcreator.potatosmp.PotatosmpMod;

public class PotatosmpModEntities {
	public static EntityType<PotatoArrowEntity> POTATO_ARROW = register("potato_arrow", EntityType.Builder.<PotatoArrowEntity>of(PotatoArrowEntity::new, MobCategory.MISC).clientTrackingRange(64).updateInterval(1).sized(0.5f, 0.5f));

	public static void load() {
	}

	// Start of user code block custom entities
	// End of user code block custom entities
	private static <T extends Entity> EntityType<T> register(String registryname, EntityType.Builder<T> entityTypeBuilder) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, registryname),
				(EntityType<T>) entityTypeBuilder.build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, registryname))));
	}
}