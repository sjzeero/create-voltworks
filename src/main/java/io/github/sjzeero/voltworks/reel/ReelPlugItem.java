package io.github.sjzeero.voltworks.reel;

import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The cable end taken from a reel. It only lives in the hand of the player carrying it: click a socket (cord reel) or
 * a terminal (cable reel) to plug it in, click the reel or sneak-click the air to let it wind back. Dropping or
 * throwing it, switching to another slot, or walking a few blocks further than the wound length (which first warns
 * on the action bar) lets it fall to the ground as a {@link CableEndEntity}, still pulled out of the reel.
 */
public class ReelPlugItem extends Item {
    /** How far past the wound length the player may stretch before the end slips out of the hand. */
    public static final double SNAP_SLACK = 6.0;

    public ReelPlugItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(BlockPos reel, long grab, boolean cord) {
        ItemStack stack = look(cord);
        CompoundTag tag = new CompoundTag();
        tag.putLong("Reel", reel.asLong());
        tag.putLong("Grab", grab);
        if (cord)
            tag.putBoolean("Cord", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** A bare stack that only picks the model: the socket plug ({@code cord}) or the copper lug. */
    public static ItemStack look(boolean cord) {
        ItemStack stack = new ItemStack(VoltworksItems.REEL_PLUG.get());
        if (cord) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Cord", true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    /** End of a cord reel (drawn as the plug that goes into a socket) rather than of a cable reel (a copper lug). */
    public static boolean isCord(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("Cord");
    }

    public static @Nullable BlockPos reelPos(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Reel"))
            return null;
        return BlockPos.of(data.copyTag().getLong("Reel"));
    }

    private static long grab(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getLong("Grab");
    }

    /** The reel this end belongs to, or null when the reel is gone or the end was taken back. */
    public static @Nullable CableReelBlockEntity reel(Level level, ItemStack stack) {
        BlockPos pos = reelPos(stack);
        if (pos == null || !level.isLoaded(pos))
            return null;
        if (level.getBlockEntity(pos) instanceof CableReelBlockEntity be && (level.isClientSide || (be.grabId() != 0 && be.grabId() == grab(stack))))
            return be;
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.FAIL;
        ItemStack stack = ctx.getItemInHand();
        CableReelBlockEntity be = reel(level, stack);
        if (be == null) {
            stack.setCount(0);
            return InteractionResult.SUCCESS;
        }
        if (ctx.getClickedPos().equals(be.getBlockPos())) {
            be.dropGrab();
            stack.setCount(0);
            level.playSound(null, be.getBlockPos(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.9f, 0.7f);
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.retracted", CableReelBlockEntity.fmt(be.stored())), true);
            return InteractionResult.SUCCESS;
        }
        if (be.connect(player, ctx.getClickedPos(), ctx.getClickLocation()))
            stack.setCount(0);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown())
            return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            CableReelBlockEntity be = reel(level, stack);
            if (be != null) {
                be.dropGrab();
                level.playSound(null, be.getBlockPos(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.9f, 0.7f);
            }
            stack.setCount(0);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || level.getGameTime() % 5 != 0)
            return;
        CableReelBlockEntity be = reel(level, stack);
        if (be == null) {
            stack.setCount(0);
            return;
        }
        if (!selected || player.getMainHandItem() != stack) {
            // moved to another slot (or the hotbar moved away from it): the end is no longer held, it falls
            stack.setCount(0);
            be.dropLoose(player.position().add(0, 0.5, 0), Vec3.ZERO);
            return;
        }
        if (level.getGameTime() % 10 != 0)
            return;
        double d = be.outletWorld().distanceTo(player.position().add(0, 1, 0));
        if (d > be.stored() + SNAP_SLACK) {
            stack.setCount(0);
            be.dropLoose(player.position().add(0, 1, 0), Vec3.ZERO);
            level.playSound(null, player.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            AllSoundEvents.DENY.playOnServer(level, player.blockPosition());
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.snapped").withStyle(ChatFormatting.RED), true);
        } else if (d > be.stored()) {
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.too_far",
                    CableReelBlockEntity.fmt(d), CableReelBlockEntity.fmt(be.stored())).withStyle(ChatFormatting.RED), true);
        }
    }

    /** A dropped or thrown cable end (Q, death, ...) keeps flying as a lying cable end, still pulled out of the reel. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide) {
            CableReelBlockEntity be = reel(entity.level(), stack);
            if (be != null)
                be.dropLoose(entity.position(), entity.getDeltaMovement());
            entity.discard();
        }
        return true;
    }
}
