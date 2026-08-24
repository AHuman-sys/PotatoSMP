package net.mcreator.potatosmp;

import net.mcreator.potatosmp.network.PotatosmpModVariables;
import net.mcreator.potatosmp.init.PotatosmpModOverlays;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ClientModInitializer;

@Environment(EnvType.CLIENT)
public class PotatosmpModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Start of user code block mod constructor
		// End of user code block mod constructor
		PotatosmpModOverlays.clientLoad();
		ClientPlayNetworking.registerGlobalReceiver(PotatosmpModVariables.PlayerVariablesSyncMessage.TYPE, PotatosmpModVariables.PlayerVariablesSyncMessage::handleData);
		// Start of user code block mod init
		// End of user code block mod init
	}
	// Start of user code block mod methods
	// End of user code block mod methods
}