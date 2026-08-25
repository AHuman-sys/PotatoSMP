package net.mcreator.potatosmp.network;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.SectionPos;

import net.mcreator.potatosmp.procedures.BankGUIProcedureProcedure;
import net.mcreator.potatosmp.PotatosmpMod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public record BankGUISlotMessage(int slotID, int x, int y, int z, int changeType, int meta) implements CustomPacketPayload {
	public static final Type<BankGUISlotMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(PotatosmpMod.MODID, "bank_gui_slots"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BankGUISlotMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, BankGUISlotMessage message) -> {
		buffer.writeInt(message.slotID);
		buffer.writeInt(message.x);
		buffer.writeInt(message.y);
		buffer.writeInt(message.z);
		buffer.writeInt(message.changeType);
		buffer.writeInt(message.meta);
	}, (RegistryFriendlyByteBuf buffer) -> new BankGUISlotMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt()));

	@Override
	public Type<BankGUISlotMessage> type() {
		return TYPE;
	}

	public static void handleData(final BankGUISlotMessage message, final ServerPlayNetworking.Context context) {
		context.server().execute(() -> handleSlotAction(context.player(), message.slotID, message.changeType, message.meta, message.x, message.y, message.z));
	}

	public static void handleSlotAction(Player entity, int slot, int changeType, int meta, int x, int y, int z) {
		Level world = entity.level();
		// security measure to prevent arbitrary chunk generation
		if (!world.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
			return;
		if (slot == 0 && changeType == 0) {

			BankGUIProcedureProcedure.execute(entity);
		}
	}
}