package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** {@code /voltworks wallwiretest <pos>}: builds a small test room with wall wires over a wall and along a ceiling. */
public final class WallWireTestCommand {
    private WallWireTestCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("voltworks").requires(s -> s.hasPermission(2))
                .then(Commands.literal("wallwiretest").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::build)))
                .then(Commands.literal("contraptiontest").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::buildContraption)))
                .then(Commands.literal("contraptiontoggle").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::toggle)))
                .then(Commands.literal("wirestatus").executes(WallWireTestCommand::status)));
    }

    private static int build(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        fill(level, o.offset(-1, 0, -1), o.offset(11, 5, 10), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(-1, -1, -1), o.offset(11, -1, 10), Blocks.SMOOTH_STONE.defaultBlockState());
        fill(level, o.offset(0, 0, 5), o.offset(10, 1, 5), Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, o.offset(6, 2, 5), o.offset(10, 2, 5), Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, o.offset(6, 3, 3), o.offset(10, 3, 5), Blocks.STONE_BRICKS.defaultBlockState());

        BlockPos battery1 = o.offset(1, 0, 1), bulb1 = o.offset(2, 0, 7);
        BlockPos battery2 = o.offset(7, 0, 1), bulb2 = o.offset(10, 0, 1);
        device(level, battery1, "creative_battery");
        device(level, bulb1, "bulb");
        device(level, battery2, "creative_battery");
        device(level, bulb2, "bulb");

        StringBuilder out = new StringBuilder();
        WireType copper = CEEWireTypes.COPPER.get(), heavy = CEEWireTypes.HEAVILY_INSULATED.get();
        // copper over the wall: floor, up the front, over the top, down the back
        out.append(wire(level, new InWorldNode(0, battery1), new InWorldNode(0, bulb1), copper,
                List.of(new WallCell(o.offset(2, 1, 5), Direction.UP, 8, 15, 8))));
        out.append(wire(level, new InWorldNode(1, battery1), new InWorldNode(1, bulb1), copper,
                List.of(new WallCell(o.offset(3, 1, 5), Direction.UP, 8, 15, 8))));
        // heavily insulated up the wall, along the ceiling and back down
        out.append(wire(level, new InWorldNode(0, battery2), new InWorldNode(0, bulb2), heavy,
                List.of(new WallCell(o.offset(7, 3, 3), Direction.DOWN, 8, 0, 8), new WallCell(o.offset(10, 3, 3), Direction.DOWN, 8, 0, 8))));
        // heavily insulated on the floor
        out.append(wire(level, new InWorldNode(1, battery2), new InWorldNode(1, bulb2), heavy, List.of()));
        source.sendSuccess(() -> Component.literal(out.toString().trim()), true);
        return 1;
    }

    private static int buildContraption(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        fill(level, o.offset(-2, 0, -2), o.offset(8, 6, 4), Blocks.AIR.defaultBlockState());
        Block bearing = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "mechanical_bearing"));
        level.setBlockAndUpdate(o, bearing.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
        Block chassis = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "radial_chassis"));
        BlockState cs = chassis.defaultBlockState();
        for(var prop : cs.getProperties())
            if(prop instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bp && prop.getName().startsWith("sticky"))
                cs = cs.setValue(bp, true);
        level.setBlockAndUpdate(o.offset(0, 1, 0), cs);
        fill(level, o.offset(1, 1, 0), o.offset(3, 1, 1), Blocks.STONE_BRICKS.defaultBlockState());
        BlockPos battery = o.offset(1, 1, 0), bulb = o.offset(3, 1, 0);
        device(level, battery, "creative_battery");
        device(level, bulb, "bulb");
        String out = wire(level, new InWorldNode(0, battery), new InWorldNode(0, bulb), CEEWireTypes.COPPER.get(), List.of());
        source.sendSuccess(() -> Component.literal("contraption test: " + out), true);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        if(level.getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "pos")) instanceof com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity be) {
            if(be.isRunning())
                be.disassemble();
            else
                be.assemble();
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        StringBuilder out = new StringBuilder("wall routes: ");
        for(WallRoute r : WallWireData.get(level).all())
            out.append(r.connection).append(" first=").append(r.cells.getFirst().pos().toShortString())
                    .append(" connected=").append(sd.isConnected(r.connection.node1(), r.connection.node2())).append("; ");
        out.append("| contraption entities: ").append(level.getEntities().getAll().spliterator().estimateSize());
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static void fill(ServerLevel level, BlockPos a, BlockPos b, BlockState state) {
        for(BlockPos p : BlockPos.betweenClosed(a, b))
            level.setBlock(p, state, Block.UPDATE_ALL);
    }

    private static void device(ServerLevel level, BlockPos pos, String id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", id));
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        BlockState state = level.getBlockState(pos);
        if(state.getBlock() instanceof ElectricalDeviceBlock<?> device)
            device.ensureNodesExist(level, pos, state);
    }

    private static String wire(ServerLevel level, InWorldNode a, InWorldNode b, WireType type, List<WallCell> via) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        if(!sd.getNodes().contains(a) || !sd.getNodes().contains(b))
            return "missing node; ";
        Vec3 pa = sd.getNodePositionOrCenter(a), pb = sd.getNodePositionOrCenter(b);
        WallCell first = nearest(level, pa), last = nearest(level, pb);
        if(first == null || last == null)
            return "no face near node; ";
        List<WallCell> points = new ArrayList<>();
        points.add(first);
        points.addAll(via);
        points.add(last);
        List<WallCell> cells = WallRouter.route(level, points, 16 * 64);
        if(cells == null)
            return "no path; ";
        double length = WallRouter.pixels(cells) / 16.0 + pa.distanceTo(first.point()) + pb.distanceTo(last.point());
        if(sd.isConnected(a, b))
            sd.removeConnectionNoDrops(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(a, b));
        WallWireData.get(level).put(WallRoute.oriented(a, b, cells, length));
        sd.connect(a, b, WireData.ofLength(type, length));
        return String.format(java.util.Locale.ROOT, "%.2f m; ", length);
    }

    /** Closest face pixel to a node that a wall wire can lie on, not on the device itself. */
    private static WallCell nearest(ServerLevel level, Vec3 node) {
        BlockPos centre = BlockPos.containing(node);
        WallCell best = null;
        double bestDist = Double.MAX_VALUE;
        for(BlockPos p : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
            if(level.getBlockState(p).getBlock() instanceof ElectricalDeviceBlock<?>)
                continue;
            for(Direction face : Direction.values()) {
                int[] px = {pixel(node.x - p.getX()), pixel(node.y - p.getY()), pixel(node.z - p.getZ())};
                WallCell cell = WallCell.of(p, face, px);
                if(!WallRouter.supports(level, cell))
                    continue;
                double d = cell.point().distanceTo(node);
                if(d < bestDist) {
                    bestDist = d;
                    best = cell;
                }
            }
        }
        return best;
    }

    private static int pixel(double v) {
        return Mth.clamp(Mth.floor(v * 16), 0, 15);
    }
}
