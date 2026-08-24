/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;

import net.mcreator.potatosmp.PotatosmpMod;

public class PotatosmpModPotions {
	public static Holder<Potion> VODKA_POTION;
	public static Holder<Potion> POTATION;

	public static void load() {
		VODKA_POTION = register("vodka_potion",
				new Potion("vodka_potion", new MobEffectInstance(MobEffects.SPEED, 1200, 2, false, false), new MobEffectInstance(MobEffects.STRENGTH, 1200, 2, false, false), new MobEffectInstance(MobEffects.NAUSEA, 6000, 9, false, true)));
		POTATION = register("potation", new Potion("potation", new MobEffectInstance(MobEffects.LUCK, 3600, 4, false, true)));
	}

	private static Holder<Potion> register(String registryname, Potion element) {
		return Holder.direct(Registry.register(BuiltInRegistries.POTION, Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, registryname), element));
	}
}