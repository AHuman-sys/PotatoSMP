package net.mcreator.potatosmp.procedures;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;

import net.mcreator.potatosmp.network.PotatosmpModVariables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;

public class PayProcedureProcedure {
	public static void execute(CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null)
			return;
		if (entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES).credits >= DoubleArgumentType.getDouble(arguments, "amount") && DoubleArgumentType.getDouble(arguments, "amount") > 0) {
			{
				PotatosmpModVariables.PlayerVariables _vars = entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES);
				_vars.credits = entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES).credits - DoubleArgumentType.getDouble(arguments, "amount");
				_vars.markSyncDirty();
			}
			{
				PotatosmpModVariables.PlayerVariables _vars = (commandParameterEntity(arguments, "target_player")).getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES);
				_vars.credits = (commandParameterEntity(arguments, "target_player")).getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES).credits + DoubleArgumentType.getDouble(arguments, "amount");
				_vars.markSyncDirty();
			}
			if (entity instanceof ServerPlayer _player)
				_player.sendSystemMessage(Component.literal(("Paid $" + DoubleArgumentType.getDouble(arguments, "amount") + " to " + (commandParameterEntity(arguments, "target_player")).getDisplayName().getString())), false);
			if ((commandParameterEntity(arguments, "target_player")) instanceof ServerPlayer _player)
				_player.sendSystemMessage(Component.literal(("Received $" + DoubleArgumentType.getDouble(arguments, "amount") + "from" + entity.getDisplayName().getString())), false);
		} else {
			if (entity instanceof ServerPlayer _player)
				_player.sendSystemMessage(Component.literal("Insufficient funds or invalid amount!"), false);
		}
	}

	private static Entity commandParameterEntity(CommandContext<CommandSourceStack> arguments, String parameter) {
		try {
			return EntityArgument.getEntity(arguments, parameter);
		} catch (CommandSyntaxException e) {
			e.printStackTrace();
			return null;
		}
	}
}