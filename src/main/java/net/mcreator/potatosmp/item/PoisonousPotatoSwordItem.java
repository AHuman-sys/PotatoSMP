package net.mcreator.potatosmp.item;

import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;

import net.mcreator.potatosmp.procedures.PoisonousPotatoSwordLivingEntityIsHitWithToolProcedure;

public class PoisonousPotatoSwordItem extends Item {
	private static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 250, 4f, 0, 2, TagKey.create(Registries.ITEM, Identifier.parse("potatosmp:poisonous_potato_sword_repair_items")));

	public PoisonousPotatoSwordItem(Item.Properties properties) {
		super(properties.sword(TOOL_MATERIAL, 1f, -2.2f));
	}

	@Override
	public void hurtEnemy(ItemStack itemstack, LivingEntity entity, LivingEntity sourceentity) {
		super.hurtEnemy(itemstack, entity, sourceentity);
		PoisonousPotatoSwordLivingEntityIsHitWithToolProcedure.execute(entity);
	}
}