package io.github.sjzeero.voltworks.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class RedstoneCableItem extends Item {
    public RedstoneCableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if(player == null || !player.isShiftKeyDown())
            return InteractionResult.PASS;
        if(!(context.getLevel() instanceof ServerLevel level))
            return InteractionResult.sidedSuccess(true);
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        int n = RedstoneLinks.get(level).removeAt(pos, face, !player.isCreative());
        player.displayClientMessage(Component.translatable("voltworks.redstone_cable." + (n > 0 ? "removed" : "nothing")), true);
        if(n > 0)
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS;
    }
}
