/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;

import net.mcreator.potatosmp.PotatosmpMod;

public class PotatosmpModSounds {
	public static SoundEvent NEVER_GONNA_GIVE_YOU_UP;

	public static void load() {
		NEVER_GONNA_GIVE_YOU_UP = register("never_gonna_give_you_up", SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("potatosmp", "never_gonna_give_you_up")));
	}

	private static SoundEvent register(String registryname, SoundEvent element) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, registryname), element);
	}
}