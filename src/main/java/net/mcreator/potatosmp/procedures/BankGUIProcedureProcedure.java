package net.mcreator.potatosmp.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;

import net.mcreator.potatosmp.network.PotatosmpModVariables;
import net.mcreator.potatosmp.init.PotatosmpModMenus;
import net.mcreator.potatosmp.init.PotatosmpModItems;

public class BankGUIProcedureProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if ((entity instanceof Player _plrSlotItem && _plrSlotItem.containerMenu instanceof PotatosmpModMenus.MenuAccessor _menu0 ? _menu0.getSlots().get(0).getItem() : ItemStack.EMPTY).getItem() == PotatosmpModItems.POTATO_BILL) {
			{
				PotatosmpModVariables.PlayerVariables _vars = entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES);
				_vars.credits = entity.getAttachedOrCreate(PotatosmpModVariables.PLAYER_VARIABLES).credits + getAmountInGUISlot(entity, 0);
				_vars.markSyncDirty();
			}
			if (entity instanceof Player _player && _player.containerMenu instanceof PotatosmpModMenus.MenuAccessor _menu) {
				_menu.getSlots().get(0).set(ItemStack.EMPTY);
				_player.containerMenu.broadcastChanges();
			}
		}
	}

	private static int getAmountInGUISlot(Entity entity, int sltid) {
		if (entity instanceof Player player && player.containerMenu instanceof PotatosmpModMenus.MenuAccessor menuAccessor) {
			ItemStack stack = menuAccessor.getSlots().get(sltid).getItem();
			if (stack != null)
				return stack.getCount();
		}
		return 0;
	}
}