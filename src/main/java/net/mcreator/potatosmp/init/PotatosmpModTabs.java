/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.world.item.CreativeModeTabs;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

public class PotatosmpModTabs {
	public static void load() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(tabData -> {
			tabData.accept(PotatosmpModItems.POTATO_SWORD);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(tabData -> {
			tabData.accept(PotatosmpModItems.POTATO_AXE);
		});
	}
}