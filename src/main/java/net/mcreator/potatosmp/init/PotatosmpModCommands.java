/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.potatosmp.init;

import net.mcreator.potatosmp.command.PayCommand;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class PotatosmpModCommands {
	public static void load() {
		CommandRegistrationCallback.EVENT.register((dispatcher, commandBuildContext, environment) -> {
			PayCommand.register(dispatcher, commandBuildContext, environment);
		});
	}
}