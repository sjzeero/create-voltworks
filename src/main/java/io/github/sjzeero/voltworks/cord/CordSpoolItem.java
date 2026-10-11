package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/** Copper cord spool: laid only by Voltworks' surface wiring, never hung as a single Electro Energetics wire. */
public class CordSpoolItem extends WireSpoolItem {
    public CordSpoolItem(Properties properties, Supplier<WireType> wireType) {
        super(properties, wireType);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void interactDetachedNode(InWorldNode node, Player player, Level level, ItemStack stack) {
    }
}
