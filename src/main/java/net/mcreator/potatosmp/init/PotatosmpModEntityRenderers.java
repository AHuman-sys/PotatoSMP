/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

@Environment(EnvType.CLIENT)
public class PotatosmpModEntityRenderers {
	public static void clientLoad() {
		EntityRendererRegistry.register(PotatosmpModEntities.POTATO_ARROW, ThrownItemRenderer::new);
	}
}