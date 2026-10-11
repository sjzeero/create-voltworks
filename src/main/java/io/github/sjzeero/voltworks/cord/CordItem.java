package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Copper cord, ported from Create: Power Grid (patryk3211, Apache-2.0). Click one end, then the other. */
public class CordItem extends Item {
    private static final String START = "voltworks_cord_start";

    public CordItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static BlockPos start(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(START))
            return null;
        return BlockPos.of(data.copyTag().getLong(START));
    }

    private static void setStart(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null)
                tag.remove(START);
            else
                tag.putLong(START, pos.asLong());
        });
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return start(stack) != null;
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable("voltworks.cord." + key), true);
    }

    private static InteractionResult deny(Player player, Level level, BlockPos pos, String key) {
        message(player, key);
        AllSoundEvents.DENY.playOnServer(level, pos);
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (player == null)
            return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (start(stack) == null)
                return InteractionResult.PASS;
            if (!level.isClientSide) {
                setStart(stack, null);
                message(player, "cancelled");
            }
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(pos);
        int[] here = CordWires.endpointNodes(level, pos, state);
        if (here == null)
            return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl))
            return InteractionResult.SUCCESS;
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        boolean hereSocket = state.getBlock() instanceof SocketBlock;
        BlockPos start = start(stack);
        if (start == null || start.equals(pos)) {
            if (start != null) {
                setStart(stack, null);
                message(player, "cancelled");
                return InteractionResult.SUCCESS;
            }
            if (hereSocket && CordWires.hasCord(sd, pos))
                return deny(player, level, pos, "socket_taken");
            setStart(stack, pos);
            message(player, "next");
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
            return InteractionResult.SUCCESS;
        }
        BlockState startState = level.getBlockState(start);
        int[] from = CordWires.endpointNodes(level, start, startState);
        if (from == null) {
            setStart(stack, null);
            return deny(player, level, pos, "start_gone");
        }
        String error = CordWires.connect(sd, start, from, startState.getBlock() instanceof SocketBlock, pos, here, hereSocket);
        if (error != null)
            return deny(player, level, pos, error);
        setStart(stack, null);
        if (!player.getAbilities().instabuild)
            stack.shrink(1);
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        return InteractionResult.SUCCESS;
    }
}
