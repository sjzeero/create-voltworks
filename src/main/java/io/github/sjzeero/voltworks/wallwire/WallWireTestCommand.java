package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlock;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.board.BoardDevice;
import io.github.sjzeero.voltworks.board.TestBoards;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import io.github.sjzeero.voltworks.registry.VoltworksBlocks;

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
                .then(Commands.literal("traintest").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::buildTrain)))
                .then(Commands.literal("trainassemble").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::assembleTrain)))
                .then(Commands.literal("trainboard").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> buildTrainWith(ctx, true))))
                .then(Commands.literal("boardinfo").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::boardInfo)))
                .then(Commands.literal("trainrun").then(Commands.argument("speed", DoubleArgumentType.doubleArg(-2, 2))
                        .executes(ctx -> {
                            double v = DoubleArgumentType.getDouble(ctx, "speed");
                            var server = ctx.getSource().getServer();
                            for(var t : Create.RAILWAYS.trains.values())
                                t.leaveStation();
                            push(server, v, 600);
                            for(var t : Create.RAILWAYS.trains.values())
                                ctx.getSource().sendSuccess(() -> Component.literal("train speed=" + t.speed + " target=" + t.targetSpeed + " station=" + t.currentStation + " derailed=" + t.derailed + " graph=" + t.graph + " stallBefore=" + t.speedBeforeStall), false);
                            return 1;
                        })))
                .then(Commands.literal("circuitinfo").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::circuitInfo)))
                .then(Commands.literal("trainuse").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::trainUse)))
                .then(Commands.literal("trainwire").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::trainWire)))
                .then(Commands.literal("traindisassemble").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WallWireTestCommand::disassembleTrain)))
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
        out.append(wire(level, new InWorldNode(0, battery1), new InWorldNode(0, bulb1), copper,
                List.of(new WallCell(o.offset(2, 1, 5), Direction.UP, 8, 15, 8))));
        out.append(wire(level, new InWorldNode(1, battery1), new InWorldNode(1, bulb1), copper,
                List.of(new WallCell(o.offset(3, 1, 5), Direction.UP, 8, 15, 8))));
        out.append(wire(level, new InWorldNode(0, battery2), new InWorldNode(0, bulb2), heavy,
                List.of(new WallCell(o.offset(7, 3, 3), Direction.DOWN, 8, 0, 8), new WallCell(o.offset(10, 3, 3), Direction.DOWN, 8, 0, 8))));
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
        level.setBlockAndUpdate(o, bearing.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        Block chassis = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "radial_chassis"));
        BlockState cs = chassis.defaultBlockState();
        for(var prop : cs.getProperties())
            if(prop instanceof BooleanProperty bp && prop.getName().startsWith("sticky"))
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

    private static Block create(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id));
    }

    private static int buildTrain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return buildTrainWith(ctx, false);
    }

    private static int boardInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos p = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        String out = level.getBlockState(p).getBlock() + ": ";
        if(level.getBlockEntity(p) instanceof BoardBlockEntity be) {
            var d = DevicesSavedData.load(level).getDevice(p, BoardDevice.class);
            out += "be parts=" + be.getSchematic().components().size() + " device parts=" + (d == null ? "no device" : d.schematic().components().size());
            var sd = InfrastructureSavedData.load(level);
            int conns = 0;
            for(var n : sd.getNodesAt(p))
                conns += sd.getConnections(n).size();
            out += " nodes=" + sd.getNodesAt(p).size() + " connections=" + conns;
        } else
            out += "no board entity";
        String o2 = out;
        ctx.getSource().sendSuccess(() -> Component.literal(o2), false);
        return 1;
    }

    private static int buildTrainWith(CommandContext<CommandSourceStack> ctx, boolean board) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        fill(level, o.offset(-2, -1, -3), o.offset(70, 6, 3), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(-2, -3, -3), o.offset(70, -2, 3), Blocks.STONE.defaultBlockState());
        BlockState track = create("track").defaultBlockState()
                .setValue(TrackBlock.SHAPE, TrackShape.XO)
                .setValue(TrackBlock.HAS_BE, true);
        for(int x = -1; x <= 68; x++)
            level.setBlock(o.offset(x, -1, 0), track, Block.UPDATE_ALL);
        level.setBlock(o.offset(0, -1, 1), create("track_station").defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.offset(0, -1, 0), track, Block.UPDATE_ALL);
        if(level.getBlockEntity(o.offset(0, -1, 1)) instanceof StationBlockEntity be) {
            CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
            tag.put("TargetTrack", NbtUtils.writeBlockPos(new BlockPos(0, 0, -1)));
            be.loadWithComponents(tag, level.registryAccess());
            be.setChanged();
        }
        BlockState bogey = create("small_bogey").defaultBlockState().setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X);
        level.setBlock(o.offset(1, 0, 0), bogey, Block.UPDATE_ALL);
        BlockState cs = create("radial_chassis").defaultBlockState();
        for(var prop : cs.getProperties())
            if(prop instanceof BooleanProperty bp && prop.getName().startsWith("sticky"))
                cs = cs.setValue(bp, true);
        BlockPos c = o.offset(2, 0, 0);
        level.setBlock(c, cs, Block.UPDATE_ALL);
        level.setBlock(c.north(), create("controls").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), Block.UPDATE_ALL);
        device(level, c.east(), "creative_battery");
        String w = "";
        if(board) {
            level.setBlockAndUpdate(c.south(), VoltworksBlocks.BOARD.get().defaultBlockState());
            var preset = TestBoards.build("relay", id -> ComponentRegistry.get(Voltworks.asResource(id)));
            if(level.getBlockEntity(c.south()) instanceof BoardBlockEntity be)
                be.setSchematic(preset.schematic().serializeNbt(level.registryAccess()));
            w = "board with " + preset.schematic().components().size() + " parts";
        } else {
            device(level, c.south(), "bulb");
            w = wire(level, new InWorldNode(0, c.east()), new InWorldNode(0, c.south()), CEEWireTypes.COPPER.get(), List.of());
        }
        final String wf = w; ctx.getSource().sendSuccess(() -> Component.literal("train test built, wire " + wf + ", wait a few seconds then /voltworks trainassemble " + o.toShortString()), true);
        return 1;
    }

    private static void push(MinecraftServer server, double v, int ticks) {
        if(ticks <= 0)
            return;
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            for(var t : Create.RAILWAYS.trains.values()) {
                t.manualTick = true;
                t.targetSpeed = v;
                t.approachTargetSpeed(1);
                if(Math.abs(t.speed) < Math.abs(v)) t.speed = v;
            }
            push(server, v, ticks - 1);
        }));
    }

    private static int circuitInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos p = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        var sd = InfrastructureSavedData.load(level);
        var devices = DevicesSavedData.load(level);
        var device = devices.getDevice(p);
        var frozen = FrozenDevices.get(level);
        String out = p.toShortString() + ": block=" + level.getBlockState(p).getBlock() + " device=" + (device == null ? "none" : device.getClass().getSimpleName())
                + " nodes=" + sd.getNodesAt(p).size() + " frozen=" + frozen.isFrozen(p) + " groups=" + frozen.groupCount() + " frozenDevices=" + frozen.deviceCount()
                + " allDevices=" + devices.getDevices().size() + " allNodes=" + sd.getNodes().size();
        var results = sd.ticker.lastResults;
        if(results != null && sd.getNodesAt(p).size() >= 2)
            out += String.format(Locale.ROOT, " V01=%.3f", results.getVoltageAt(p, 0, 1));
        if(device instanceof BoardDevice b)
            out += " boardParts=" + b.schematic().components().size() + " pinU=" + Arrays.toString(b.readings().pinU());
        String o2 = out;
        ctx.getSource().sendSuccess(() -> Component.literal(o2), false);
        return 1;
    }

    private static int trainUse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockPos rel = o.offset(2, 0, 1);
        String out = "";
        for(var e : level.getAllEntities()) {
            if(!(e instanceof AbstractContraptionEntity entity) || entity.getContraption() == null)
                continue;
            BlockPos local = null;
            for(var info : entity.getContraption().getBlocks().values())
                if(info.state().getBlock() instanceof BoardBlock)
                    local = info.pos();
            if(local == null)
                continue;
            var player = FakePlayerFactory.getMinecraft(level);
            Vec3 point = entity.toGlobalVector(new Vec3(local.getX() + 4.5 / 16, local.getY() + 0.1, local.getZ() + 12.5 / 16), 1f);
            player.setPos(point.x, point.y + 0.9 - player.getEyeHeight(), point.z);
            player.setXRot(90);
            player.setYRot(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            boolean ok = entity.handlePlayerInteraction(player, local, Direction.UP, InteractionHand.MAIN_HAND);
            out += "entity=" + entity.getId() + " local=" + local.toShortString() + " handled=" + ok + " ";
        }
        String o2 = "trainuse " + out;
        ctx.getSource().sendSuccess(() -> Component.literal(o2), false);
        return 1;
    }

    private static int trainWire(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockPos battery = o.offset(3, 0, 0), board = o.offset(2, 0, 1);
        for(BlockPos p : new BlockPos[]{battery, board}) {
            BlockState st = level.getBlockState(p);
            if(st.getBlock() instanceof ElectricalDeviceBlock<?> d)
                d.ensureNodesExist(level, p, st);
        }
        var sd = InfrastructureSavedData.load(level);
        String out = "";
        for(int i = 0; i < 2; i++) {
            InWorldNode a = new InWorldNode(i, battery), b = new InWorldNode(i, board);
            if(sd.getNodes().contains(a) && sd.getNodes().contains(b) && !sd.isConnected(a, b)) {
                sd.connect(a, b, WireData.ofLength(CEEWireTypes.COPPER.get(), 1.0));
                out += "w" + i + " ";
            }
        }
        String o2 = "trainwire " + out;
        ctx.getSource().sendSuccess(() -> Component.literal(o2), false);
        return 1;
    }

    private static int disassembleTrain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "pos").offset(0, -1, 1);
        if(level.getBlockEntity(o) instanceof StationBlockEntity be) {
            boolean ok = be.tryDisassembleTrain(ctx.getSource().getPlayer());
            ctx.getSource().sendSuccess(() -> Component.literal("disassembled=" + ok + " trains=" + Create.RAILWAYS.trains.size()), false);
        }
        return 1;
    }

    private static int assembleTrain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o0 = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockPos o = o0.offset(0, -1, 1);
        if(level.getBlockEntity(o) instanceof StationBlockEntity be) {
            UUID id = ctx.getSource().getEntity() != null ? ctx.getSource().getEntity().getUUID() : UUID.randomUUID();
            be.enterAssemblyMode(ctx.getSource().getPlayer());
            be.assemble(id);
            String dbg = "";
            try {
                for(String f : new String[]{"lastException", "assemblyLength", "bogeyCount", "failedCarriageIndex"}) {
                    Field fl = StationBlockEntity.class.getDeclaredField(f);
                    fl.setAccessible(true);
                    Object v = fl.get(be); dbg += f + "=" + (v instanceof AssemblyException ae ? ae.component.getString() : v) + " ";
                }
                Field fl = StationBlockEntity.class.getDeclaredField("bogeyLocations");
                fl.setAccessible(true);
                dbg += Arrays.toString((int[]) fl.get(be));
            } catch(Exception e) {
                dbg = e.toString();
            }
            String dbg2 = dbg;
            ctx.getSource().sendSuccess(() -> Component.literal(dbg2), false);
            ctx.getSource().sendSuccess(() -> Component.literal("assembling=" + be.isAssembling() + " validTrack=" + be.edgePoint.hasValidTrack() + " dir=" + be.getAssemblyDirection() +  " bogey=" + level.getBlockState(o.offset(1, 1, -1)) + " chassis=" + level.getBlockState(o.offset(1, 2, -1)) + " controls=" + level.getBlockState(o.offset(1, 3, -1)) + " track=" + be.edgePoint.getGlobalPosition() + " " + be.edgePoint.getTrackBlockState()), false);
            ctx.getSource().sendSuccess(() -> Component.literal("assemble called, station=" + be.getStation() + " trains=" + Create.RAILWAYS.trains.size()), true);
        } else
            ctx.getSource().sendFailure(Component.literal("no station at " + o.toShortString()));
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        if(level.getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "pos")) instanceof MechanicalBearingBlockEntity be) {
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
            sd.removeConnectionNoDrops(new InWorldNodeConnection(a, b));
        WallWireData.get(level).put(WallRoute.oriented(a, b, cells, length));
        sd.connect(a, b, WireData.ofLength(type, length));
        return String.format(Locale.ROOT, "%.2f m; ", length);
    }

    private static WallCell nearest(ServerLevel level, Vec3 node) {
        BlockPos centre = BlockPos.containing(node);
        WallCell best = null;
        double bestDist = Double.MAX_VALUE;
        for(BlockPos p : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
            if(level.getBlockState(p).getBlock() instanceof ElectricalDeviceBlock<?>)
                continue;
            for(Direction face : Direction.values()) {
                int[] px = {WallCell.pixel(node.x - p.getX()), WallCell.pixel(node.y - p.getY()), WallCell.pixel(node.z - p.getZ())};
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
}
