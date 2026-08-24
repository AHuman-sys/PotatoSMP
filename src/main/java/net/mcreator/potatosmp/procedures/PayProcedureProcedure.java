package net.mcreator.potatosmp.procedures;

import net.minecraft.world.entity.Entity;

import net.mcreator.potatosmp.network.PotatosmpModVariables;

public class PayProcedureProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		{
			PotatosmpModVariables.PlayerVariables _vars = entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES);
			_vars.credits = entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES).credits + 1;
			_vars.markSyncDirty();
		}
	}
}