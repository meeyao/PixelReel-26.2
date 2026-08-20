package com.pixelreel.items;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** tooltip contains the "pixelReel", "TV", "screen", "cinema" and "monitor" keywords */
public class DisplayBlockItem extends BlockItem {
	private final String tooltipKey;

	public DisplayBlockItem(Block block, Item.Properties properties, String tooltipKey) {
		super(block, properties);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltipComponents, flag);
		tooltipComponents.add(Component.translatable("item.pixelreel.display.keywords").withStyle(ChatFormatting.DARK_GRAY));
		tooltipComponents.add(Component.translatable(this.tooltipKey).withStyle(ChatFormatting.GRAY));
	}
}
