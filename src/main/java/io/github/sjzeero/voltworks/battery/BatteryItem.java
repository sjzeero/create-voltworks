package io.github.sjzeero.voltworks.battery;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

public class BatteryItem extends BlockItem {
    public BatteryItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        double e = BatteryBlock.storedEnergy(stack);
        tooltip.add(Component.translatable("voltworks.battery.tooltip.charge",
                String.format(Locale.ROOT, "%.0f", e / BatterySpec.CAPACITY * 100),
                BatteryBlockEntity.fmt(e / 3600), BatteryBlockEntity.fmt(BatterySpec.CAPACITY_WH)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BatteryBlock.storedEnergy(stack) < BatterySpec.CAPACITY - 1;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * (float) (BatteryBlock.storedEnergy(stack) / BatterySpec.CAPACITY));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x5FD35A;
    }
}
