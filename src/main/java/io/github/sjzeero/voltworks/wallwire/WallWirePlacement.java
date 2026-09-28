package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.VoltworksConfig;
import io.github.sjzeero.voltworks.mixin.WireSpoolItemAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Server side checks for a wall wire and the actual connection. The client only suggests the points. */
public final class WallWirePlacement {
    private WallWirePlacement() {
    }

    public static WireType wireType(WireSpoolItem spool, ServerPlayer player) {
        WireType type = ((WireSpoolItemAccessor) spool).voltworks$wireType().get();
        if(type instanceof WireType.Dyeable dyeable && player.getOffhandItem().getItem() instanceof DyeItem dye)
            type = dyeable.getDyed(dye.getDyeColor());
        return type;
    }

    public static void place(ServerPlayer player, PlaceWallWirePayload p) {
        ServerLevel level = player.serverLevel();
        ItemStack held = player.getMainHandItem();
        if(!(held.getItem() instanceof WireSpoolItem spool)) {
            deny(player, "invalid", null);
            return;
        }
        WireType type = wireType(spool, player);
        if(!WallRouter.canLay(type)) {
            deny(player, "not_this_wire", null);
            return;
        }
        List<WallCell> points = p.points();
        if(points.isEmpty() || points.size() > VoltworksConfig.wallWireMaxPoints()) {
            deny(player, "invalid", null);
            return;
        }

        InWorldNode start = p.start(), end = p.end();
        if(start.equals(end)) {
            deny(player, "invalid", end.sourcePos());
            return;
        }
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3;
        Vec3 eye = player.getEyePosition();
        if(eye.distanceTo(end.sourcePos().getCenter()) > reach + 1) {
            deny(player, "invalid", null);
            return;
        }
        for(WallCell c : points) {
            if(!level.isLoaded(c.pos()) || !WallRouter.supports(level, c)) {
                deny(player, "no_path", c.pos());
                return;
            }
        }
        // the last point is clicked right before the end node; earlier points may be behind the player now
        if(eye.distanceTo(points.getLast().point()) > reach + 1) {
            deny(player, "invalid", null);
            return;
        }

        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        BlockState startState = level.getBlockState(start.sourcePos());
        BlockState endState = level.getBlockState(end.sourcePos());
        if(!(startState.getBlock() instanceof ElectricalDeviceBlock<?> startBlock) || !(endState.getBlock() instanceof ElectricalDeviceBlock<?> endBlock)) {
            deny(player, "invalid", end.sourcePos());
            return;
        }
        if(start.sourcePos().equals(end.sourcePos()) && !endBlock.canSelfConnect(level, end.sourcePos(), endState, end.id(), start.id())) {
            deny(player, "invalid", end.sourcePos());
            return;
        }
        startBlock.ensureNodesExist(level, start.sourcePos(), startState);
        endBlock.ensureNodesExist(level, end.sourcePos(), endState);
        if(!sd.getNodes().contains(start) || !sd.getNodes().contains(end) || sd.isConnected(start, end)) {
            deny(player, "invalid", end.sourcePos());
            return;
        }

        double maxLength = Math.min(VoltworksConfig.wallWireMaxLength(), type.getMaxLength());
        List<WallCell> cells = WallRouter.route(level, points, (int) Math.floor(maxLength * 16));
        if(cells == null) {
            deny(player, "no_path", end.sourcePos());
            return;
        }
        Vec3 startPos = sd.getNodePositionOrCenter(start);
        Vec3 endPos = sd.getNodePositionOrCenter(end);
        double lead1 = startPos.distanceTo(cells.getFirst().point());
        double lead2 = endPos.distanceTo(cells.getLast().point());
        double maxLead = VoltworksConfig.wallWireMaxLead();
        if(lead1 > maxLead || lead2 > maxLead) {
            deny(player, "lead_too_long", end.sourcePos());
            return;
        }
        double length = (cells.size() - 1) / 16.0 + lead1 + lead2;
        if(length > maxLength) {
            deny(player, "too_long", end.sourcePos());
            return;
        }

        WallWireData.get(level).put(WallRoute.oriented(start, end, cells, length));
        sd.connect(start, end, WireData.ofLength(type, length));
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, end.sourcePos());
        player.displayClientMessage(Component.translatable("voltworks.wall_wire.placed", String.format(java.util.Locale.ROOT, "%.2f", length)), true);

        if(!player.isCreative()) {
            held.shrink(1);
            if(!CEEConfigs.server().alternateWirePlacement.get())
                player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
        }
    }

    private static void deny(ServerPlayer player, String key, BlockPos pos) {
        player.displayClientMessage(Component.translatable("voltworks.wall_wire." + key), true);
        AllSoundEvents.DENY.playOnServer(player.level(), pos != null ? pos : player.blockPosition());
    }
}
