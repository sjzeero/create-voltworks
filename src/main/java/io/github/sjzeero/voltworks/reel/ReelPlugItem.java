package io.github.sjzeero.voltworks.reel;

import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.cord.LooseCords;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

public class ReelPlugItem extends Item {
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

    public static ItemStack look(boolean cord) {
        ItemStack stack = new ItemStack(VoltworksItems.REEL_PLUG.get());
        if (cord) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Cord", true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

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
        if (LooseCords.isLooseCord(stack)) {
            if (LooseCords.get((ServerLevel) level).plugHeld(player, stack, ctx.getClickedPos(), ctx.getClickLocation()))
                stack.setCount(0);
            return InteractionResult.SUCCESS;
        }
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
        if (!level.isClientSide && LooseCords.isLooseCord(stack)) {
            if (player instanceof ServerPlayer sp)
                LooseCords.get((ServerLevel) level).sneakHeld(sp, stack);
        } else if (!level.isClientSide) {
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
        if (LooseCords.isLooseCord(stack)) {
            LooseCords.get((ServerLevel) level).heldTick(stack, player, selected);
            return;
        }
        CableReelBlockEntity be = reel(level, stack);
        if (be == null) {
            stack.setCount(0);
            return;
        }
        if (!selected || player.getMainHandItem() != stack) {
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

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide && LooseCords.isLooseCord(stack)) {
            LooseCords.get((ServerLevel) entity.level()).thrown(stack, entity);
            entity.discard();
        } else if (!entity.level().isClientSide) {
            CableReelBlockEntity be = reel(entity.level(), stack);
            if (be != null)
                be.dropLoose(entity.position(), entity.getDeltaMovement());
            entity.discard();
        }
        return true;
    }
}
