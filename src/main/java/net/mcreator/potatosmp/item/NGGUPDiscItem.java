package net.mcreator.potatosmp.item;

import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;

import net.mcreator.potatosmp.PotatosmpMod;

public class NGGUPDiscItem extends Item {
	public NGGUPDiscItem(Item.Properties properties) {
		super(properties.jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, "nggup_disc"))));
	}
}