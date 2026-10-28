package io.github.sjzeero.voltworks.cord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PowerStripItem extends Item {
    public static final double SOCKET_GAP = 1.5;

    public PowerStripItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getDescriptionId() {
        return "block.voltworks.power_strip";
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl))
            return InteractionResult.FAIL;
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        Vec3 click = ctx.getClickLocation();
        Vec3 viewer = player != null ? player.position() : click;
        BlockPos socket = null;
        Vec3 at;
        double yaw;
        if (level.getBlockState(pos).getBlock() instanceof SocketBlock) {
            socket = pos;
            Vec3 plug = LooseCords.plugWorld(level, pos, SocketBlock.CORD_POSITIVE);
            Vec3 away = new Vec3(viewer.x - plug.x, 0, viewer.z - plug.z);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(SOCKET_GAP);
            at = new Vec3(plug.x + away.x, Math.min(viewer.y, plug.y), plug.z + away.z);
            yaw = StripGeometry.yawToward(plug.x - at.x, plug.z - at.z, 0);
        } else {
            Direction face = ctx.getClickedFace();
            at = face == Direction.UP ? click : click.add(face.getStepX() * 0.3, face.getStepY() * 0.3, face.getStepZ() * 0.3);
            yaw = StripGeometry.yawToward(viewer.x - at.x, viewer.z - at.z, 0);
        }
        PowerStripEntity strip = new PowerStripEntity(level, at, yaw);
        if (!level.addFreshEntity(strip))
            return InteractionResult.FAIL;
        LooseCords.get(sl).placeStrip(strip, socket, player instanceof ServerPlayer sp ? sp : null);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f);
        ctx.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
