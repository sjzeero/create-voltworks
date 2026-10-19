package io.github.sjzeero.voltworks.wallwire;

import io.github.sjzeero.voltworks.cord.CordPair;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
        if(points.isEmpty() || !WallSpace.sameSpace(level, p.start().sourcePos(), p.end().sourcePos())) {
            hang(player, level, held, type, p.start(), p.end());
            return;
        }
        if(points.isEmpty() || points.size() > VoltworksConfig.wallWireMaxPoints()) {
            deny(player, "invalid", null);
            return;
        }

        InWorldNode start = p.start(), end = p.end();
        boolean cord = CordPair.isCord(type);
        InWorldNode startRet = null, endRet = null;
        if(cord) {
            InWorldNode[] a = CordPair.ends(level, start.sourcePos()), b = CordPair.ends(level, end.sourcePos());
            if(a == null || b == null || start.sourcePos().equals(end.sourcePos())) {
                deny(player, "cord_ends", end.sourcePos());
                return;
            }
            start = a[0];
            startRet = a[1];
            end = b[0];
            endRet = b[1];
        }
        if(start.equals(end)) {
            deny(player, "invalid", end.sourcePos());
            return;
        }
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3;
        Vec3 eye = player.getEyePosition();
        if(WallSpace.distance(level, eye, end.sourcePos().getCenter()) > reach + 1) {
            deny(player, "invalid", null);
            return;
        }
        for(WallCell c : points) {
            if(!level.isLoaded(c.pos()) || !WallRouter.supports(level, c)) {
                deny(player, "no_path", c.pos());
                return;
            }
            if(!WallSpace.sameSpace(level, c.pos(), start.sourcePos()) || !WallSpace.sameSpace(level, c.pos(), end.sourcePos())) {
                deny(player, "invalid", c.pos());
                return;
            }
        }
        // the last point is clicked right before the end node; earlier points may be behind the player now
        if(WallSpace.distance(level, eye, points.getLast().point()) > reach + 1) {
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
        if(!sd.getNodes().contains(start) || !sd.getNodes().contains(end) || sd.isConnected(start, end)
                || cord && (!sd.getNodes().contains(startRet) || !sd.getNodes().contains(endRet) || sd.isConnected(startRet, endRet))) {
            deny(player, "invalid", end.sourcePos());
            return;
        }

        if(cord && busy(sd, start, end, startRet, endRet)) {
            deny(player, "socket_busy", end.sourcePos());
            return;
        }
        double maxLength = Math.min(VoltworksConfig.wallWireMaxLength(), type.getMaxLength());
        List<WallCell> cells = WallRouter.route(level, points, (int) Math.floor(maxLength * 16));
        if(cells == null) {
            deny(player, "no_path", end.sourcePos());
            return;
        }
        Vec3 startPos = WallSpace.nodePos(level, start);
        Vec3 endPos = WallSpace.nodePos(level, end);
        double lead1 = startPos.distanceTo(cells.getFirst().point());
        double lead2 = endPos.distanceTo(cells.getLast().point());
        double maxLead = VoltworksConfig.wallWireMaxLead();
        if(lead1 > maxLead || lead2 > maxLead) {
            deny(player, "lead_too_long", end.sourcePos());
            return;
        }
        double length = WallRouter.pixels(cells) / 16.0 + lead1 + lead2;
        if(length > maxLength) {
            deny(player, "too_long", end.sourcePos());
            return;
        }

        WallWireData.get(level).put(WallRoute.oriented(start, end, cells, length));
        sd.connect(start, end, WireData.ofLength(type, length));
        if(cord) {
            WallWireData.get(level).put(WallRoute.oriented(startRet, endRet, cells, length));
            sd.connect(startRet, endRet, WireData.ofLength(type, length));
        }
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, BlockPos.containing(WallSpace.toWorld(level, end.sourcePos().getCenter())));
        player.displayClientMessage(Component.translatable("voltworks.wall_wire.placed", String.format(java.util.Locale.ROOT, "%.2f", length)), true);

        if(!player.isCreative()) {
            held.shrink(1);
            if(!CEEConfigs.server().alternateWirePlacement.get())
                player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
        }
    }

    /** Ends in different spaces (sub-level and main world): an ordinary hanging wire, never a cord. */
    private static void hang(ServerPlayer player, ServerLevel level, ItemStack held, WireType type, InWorldNode start, InWorldNode end) {
        if(start.equals(end) || !level.isLoaded(start.sourcePos()) || !level.isLoaded(end.sourcePos())) {
            deny(player, "invalid", end.sourcePos());
            return;
        }
        InWorldNode startRet = null, endRet = null;
        if(CordPair.isCord(type)) {
            InWorldNode[] a = CordPair.ends(level, start.sourcePos()), b = CordPair.ends(level, end.sourcePos());
            if(a == null || b == null) {
                deny(player, "cord_ends", end.sourcePos());
                return;
            }
            start = a[0];
            startRet = a[1];
            end = b[0];
            endRet = b[1];
        }
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3;
        if(WallSpace.distance(level, player.getEyePosition(), end.sourcePos().getCenter()) > reach + 1) {
            deny(player, "invalid", null);
            return;
        }
        double length = WallSpace.toWorld(level, WallSpace.nodePos(level, start)).distanceTo(WallSpace.toWorld(level, WallSpace.nodePos(level, end)));
        if(length > type.getMaxLength()) {
            deny(player, "too_long", end.sourcePos());
            return;
        }
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        BlockState startState = level.getBlockState(start.sourcePos());
        BlockState endState = level.getBlockState(end.sourcePos());
        if(!(startState.getBlock() instanceof ElectricalDeviceBlock<?> startBlock) || !(endState.getBlock() instanceof ElectricalDeviceBlock<?> endBlock)) {
            deny(player, "invalid", end.sourcePos());
            return;
        }
        startBlock.ensureNodesExist(level, start.sourcePos(), startState);
        endBlock.ensureNodesExist(level, end.sourcePos(), endState);
        if(!sd.getNodes().contains(start) || !sd.getNodes().contains(end) || sd.isConnected(start, end)
                || startRet != null && (!sd.getNodes().contains(startRet) || !sd.getNodes().contains(endRet) || sd.isConnected(startRet, endRet))) {
            deny(player, "invalid", end.sourcePos());
            return;
        }
        if(startRet != null && busy(sd, start, end, startRet, endRet)) {
            deny(player, "socket_busy", end.sourcePos());
            return;
        }
        sd.connect(start, end, type);
        if(startRet != null)
            sd.connect(startRet, endRet, type);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, BlockPos.containing(WallSpace.toWorld(level, end.sourcePos().getCenter())));
        if(!player.isCreative()) {
            held.shrink(1);
            if(!CEEConfigs.server().alternateWirePlacement.get())
                player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
        }
    }

    /** Recolours a whole insulated wall wire with the dye in hand. The wire type and its electrical data stay as they are. */
    public static boolean dye(ServerPlayer player, InWorldNodeConnection connection, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        WallWireData data = WallWireData.get(level);
        WallRoute route = data.get(connection);
        if(route == null)
            return false;
        WireData wire = InfrastructureSavedData.load(level).getConnectionData(connection);
        if(wire == null || !(wire.wireType() instanceof WireType.Dyeable) || !(stack.getItem() instanceof DyeItem dye) || route.color == dye.getDyeColor())
            return true;
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 2;
        Vec3 eye = player.getEyePosition();
        WallCell near = route.cells.stream().min(java.util.Comparator.comparingDouble(c -> WallSpace.toWorld(level, c.point()).distanceToSqr(eye))).orElse(null);
        if(near == null || WallSpace.toWorld(level, near.point()).distanceTo(eye) > reach)
            return true;
        data.put(route.withColor(dye.getDyeColor()));
        level.playSound(null, BlockPos.containing(WallSpace.toWorld(level, near.point())), SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1);
        stack.consume(1, player);
        return true;
    }

    /** A cord socket takes one cord only: true when any of the given cord contacts already has a connection. */
    private static boolean busy(InfrastructureSavedData sd, InWorldNode... nodes) {
        for(InWorldNode n : nodes)
            if(n != null && !sd.getConnections(n).isEmpty())
                return true;
        return false;
    }

    private static void deny(ServerPlayer player, String key, BlockPos pos) {
        player.displayClientMessage(Component.translatable("voltworks.wall_wire." + key), true);
        AllSoundEvents.DENY.playOnServer(player.level(), pos != null ? BlockPos.containing(WallSpace.toWorld(player.level(), pos.getCenter())) : player.blockPosition());
    }
}
