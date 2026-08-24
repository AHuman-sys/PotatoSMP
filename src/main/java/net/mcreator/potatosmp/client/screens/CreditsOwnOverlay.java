package net.mcreator.potatosmp.client.screens;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;

import net.mcreator.potatosmp.procedures.CreditsOwnOverlayProcedureProcedure;

import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

@Environment(EnvType.CLIENT)
public class CreditsOwnOverlay {
	public static void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
		int w = guiGraphics.guiWidth();
		int h = guiGraphics.guiHeight();
		Level world = null;
		double x = 0;
		double y = 0;
		double z = 0;
		Player entity = Minecraft.getInstance().player;
		if (entity != null) {
			world = entity.level();
			x = entity.getX();
			y = entity.getY();
			z = entity.getZ();
		}
		if (true) {
			guiGraphics.text(Minecraft.getInstance().font, Component.translatable("gui.potatosmp.credits_own.label_empty"), w - 423, 4, -1, false);
			guiGraphics.text(Minecraft.getInstance().font,

					CreditsOwnOverlayProcedureProcedure.execute(entity), w / 2 + -164, h / 2 + -116, -26368, false);
		}
	}
}