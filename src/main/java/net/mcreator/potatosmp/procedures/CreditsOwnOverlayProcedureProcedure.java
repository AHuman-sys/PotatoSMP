package net.mcreator.potatosmp.procedures;

import net.minecraft.world.entity.Entity;

import net.mcreator.potatosmp.network.PotatosmpModVariables;

public class CreditsOwnOverlayProcedureProcedure {
	public static String execute(Entity entity) {
		if (entity == null)
			return "";
		return "Balance: " + entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES).credits;
	}
}