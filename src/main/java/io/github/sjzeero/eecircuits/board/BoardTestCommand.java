package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.components.ComponentRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BoardTestCommand {
    private static TestBoards.Preset current;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eecircuits").requires(s -> s.hasPermission(2))
                .then(Commands.literal("boardtest")
                        .then(Commands.literal("setup").then(Commands.argument("preset", StringArgumentType.word())
                                .executes(ctx -> setup(ctx, StringArgumentType.getString(ctx, "preset")))))
                        .then(Commands.literal("schematic").then(Commands.argument("preset", StringArgumentType.word())
                                .executes(ctx -> schematic(ctx, StringArgumentType.getString(ctx, "preset")))))
                        .then(Commands.literal("wire").executes(BoardTestCommand::wire))
                        .then(Commands.literal("attach").then(Commands.argument("board", BlockPosArgument.blockPos())
                                .then(Commands.argument("battery", BlockPosArgument.blockPos())
                                        .then(Commands.argument("volts", DoubleArgumentType.doubleArg(0, 100000))
                                                .executes(BoardTestCommand::attach)))))
                        .then(Commands.literal("battery").then(Commands.argument("which", IntegerArgumentType.integer(0, 1))
                                .then(Commands.argument("volts", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> battery(ctx, IntegerArgumentType.getInteger(ctx, "which"),
                                                DoubleArgumentType.getDouble(ctx, "volts"))))))
                        .then(Commands.literal("use").then(Commands.argument("component", StringArgumentType.word())
                                .executes(ctx -> use(ctx, StringArgumentType.getString(ctx, "component"), "", false))
                                .then(Commands.literal("sneak").executes(ctx -> use(ctx, StringArgumentType.getString(ctx, "component"), "", true)))
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(ctx -> use(ctx, StringArgumentType.getString(ctx, "component"), StringArgumentType.getString(ctx, "item"), false)))))
                        .then(Commands.literal("repair").executes(ctx -> {
                            var d = board(ctx.getSource());
                            if(d == null)
                                return reply(ctx.getSource(), "no board");
                            d.queueRepair();
                            return reply(ctx.getSource(), "repair queued");
                        }))
                        .then(Commands.literal("read").executes(BoardTestCommand::read))
                        .then(Commands.literal("world").executes(BoardTestCommand::world))));
    }

    private static BlockPos base(CommandSourceStack source) {
        BlockPos spawn = source.getLevel().getSharedSpawnPos();
        return new BlockPos(spawn.getX(), 250, spawn.getZ());
    }

    private static BlockPos boardPos(CommandSourceStack source) {
        return base(source).east(2);
    }

    private static BlockPos batteryPos(CommandSourceStack source, int which) {
        return which == 0 ? base(source) : base(source).east(4);
    }

    private static io.github.sjzeero.eecircuits.circuits.components.Component component(String id) {
        return ComponentRegistry.get(EECircuits.asResource(id));
    }

    private static int setup(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        var preset = TestBoards.build(name, BoardTestCommand::component);
        if(preset == null)
            return reply(source, "unknown preset " + name + ", one of " + TestBoards.NAMES);
        current = preset;
        BoardBlockEntity.measureSync = true;
        var board = boardPos(source);
        level.setChunkForced(board.getX() >> 4, board.getZ() >> 4, true);
        var batteryBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_battery"));
        for(int i = 0; i < 2; i++)
            level.setBlockAndUpdate(batteryPos(source, i), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(board, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(board, EECircuits.BOARD.get().defaultBlockState());
        for(int i = 0; i < preset.supplies().size(); i++) {
            var p = batteryPos(source, i);
            level.setBlockAndUpdate(p, batteryBlock.defaultBlockState());
            var state = level.getBlockState(p);
            ((ElectricalDeviceBlock<?>) state.getBlock()).ensureNodesExist(level, p, state);
        }
        if(!(level.getBlockEntity(board) instanceof BoardBlockEntity be))
            return reply(source, "board entity missing");
        be.setSchematic(preset.schematic().serializeNbt(level.registryAccess()));
        return reply(source, "setup " + name + " with " + preset.schematic().components().size() + " components");
    }

    private static int schematic(CommandContext<CommandSourceStack> ctx, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var preset = TestBoards.build(name, BoardTestCommand::component);
        if(preset == null)
            return reply(ctx.getSource(), "unknown preset " + name + ", one of " + TestBoards.NAMES);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.put("Schematic", preset.schematic().serializeNbt(ctx.getSource().registryAccess()));
        var stack = new net.minecraft.world.item.ItemStack(EECircuits.CIRCUIT_SCHEMATIC.get());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        ctx.getSource().getPlayerOrException().getInventory().add(stack);
        return reply(ctx.getSource(), "schematic " + name);
    }

    private static int attach(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        var board = BlockPosArgument.getLoadedBlockPos(ctx, "board");
        var p = BlockPosArgument.getLoadedBlockPos(ctx, "battery");
        if(!(level.getBlockState(board).getBlock() instanceof ElectricalDeviceBlock<?>))
            return reply(source, "no electrical device at " + board.toShortString());
        var batteryBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_battery"));
        level.setBlockAndUpdate(p, batteryBlock.defaultBlockState());
        var state = level.getBlockState(p);
        ((ElectricalDeviceBlock<?>) state.getBlock()).ensureNodesExist(level, p, state);
        double volts = DoubleArgumentType.getDouble(ctx, "volts");
        var battery = DevicesSavedData.load(level).getDevice(p, CreativeBatteryDevice.class);
        if(battery != null)
            battery.voltage = volts;
        var server = level.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 2, () -> {
            var later = DevicesSavedData.load(level).getDevice(p, CreativeBatteryDevice.class);
            if(later != null)
                later.voltage = volts;
        }));
        var sd = InfrastructureSavedData.load(level);
        sd.connect(new InWorldNode(0, p), new InWorldNode(0, board), CEEWireTypes.COPPER.get());
        sd.connect(new InWorldNode(1, p), new InWorldNode(1, board), CEEWireTypes.COPPER.get());
        return reply(source, "attached battery at " + p.toShortString());
    }

    private static int wire(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if(current == null)
            return reply(source, "no preset");
        var board = boardPos(source);
        var d = board(source);
        if(d == null)
            return reply(source, "no board device");
        var sd = InfrastructureSavedData.load(level);
        for(int i = 0; i < current.supplies().size(); i++) {
            var supply = current.supplies().get(i);
            var p = batteryPos(source, i);
            var battery = DevicesSavedData.load(level).getDevice(p, CreativeBatteryDevice.class);
            if(battery != null)
                battery.voltage = supply.volts();
            sd.connect(new InWorldNode(0, p), new InWorldNode(0, board), CEEWireTypes.COPPER.get());
            sd.connect(new InWorldNode(1, p), new InWorldNode(supply.pin(), board), CEEWireTypes.COPPER.get());
        }
        return reply(source, "wired " + current.supplies().size() + " supplies, registered pins " + d.registeredPins);
    }

    private static int battery(CommandContext<CommandSourceStack> ctx, int which, double volts) {
        var battery = DevicesSavedData.load(ctx.getSource().getLevel()).getDevice(batteryPos(ctx.getSource(), which), CreativeBatteryDevice.class);
        if(battery == null)
            return reply(ctx.getSource(), "no battery " + which);
        battery.voltage = volts;
        return reply(ctx.getSource(), "battery " + which + " = " + volts + " V");
    }

    private static BoardDevice board(CommandSourceStack source) {
        return DevicesSavedData.load(source.getLevel()).getDevice(boardPos(source), BoardDevice.class);
    }

    private static int use(CommandContext<CommandSourceStack> ctx, String id, String item, boolean sneak) {
        var source = ctx.getSource();
        var level = source.getLevel();
        var d = board(source);
        if(d == null || !(level.getBlockEntity(boardPos(source)) instanceof BoardBlockEntity be))
            return reply(source, "no board");
        for(var placed : be.getSchematic().components()) {
            if(!ComponentRegistry.getId(placed.component).getPath().equals(id))
                continue;
            var player = FakePlayerFactory.getMinecraft(level);
            var stack = item.isEmpty() ? ItemStack.EMPTY
                    : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setShiftKeyDown(sneak);
            d.queueUse(placed.uuid, player);
            return reply(source, "use " + id + (item.isEmpty() ? "" : " with " + item) + (sneak ? " sneaking" : ""));
        }
        return reply(source, "no component " + id);
    }

    private static int read(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var d = board(source);
        if(d == null)
            return reply(source, "no board");
        var r = d.readings();
        var sb = new StringBuilder(String.format(Locale.ROOT, "t=%d pinU=%s pinI=%s conv=%s bad=%d",
                r.tick(), fmt(r.pinU()), fmt(r.pinI()), r.converged(), r.badSteps()));
        for(var part : r.parts())
            sb.append(" | ").append(part);
        return reply(source, sb.toString());
    }

    private static int world(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var level = source.getLevel();
        var pos = boardPos(source);
        var state = level.getBlockState(pos);
        List<String> out = new ArrayList<>();
        out.add("light=" + state.getLightEmission(level, pos) + "/" + state.getValue(BoardBlock.LIGHT));
        for(var dir : net.minecraft.core.Direction.values())
            out.add(dir.getName() + "=" + level.getSignal(pos.relative(dir), dir));
        if(level.getBlockEntity(pos) instanceof BoardBlockEntity be) {
            out.add("boardLight=" + be.light());
            var dev = board(source);
            out.add("version=" + be.version() + "/" + (dev == null ? -1 : dev.schematicVersion()));
            out.add("terminals=" + be.terminals().size());
            out.add("interactables=" + be.interactables().size());
            var st = be.syncStats;
            out.add("sync schematic=" + st[0] + "x/" + st[1] + "B state=" + st[2] + "x/" + st[3] + "B");
            var nodes = ((BoardBlock) state.getBlock()).getNodePositions(level, pos, state);
            out.add("nodes=" + nodes);
            var tooltip = new ArrayList<Component>();
            be.addToGoggleTooltip(tooltip, true);
            for(var line : tooltip)
                out.add("goggles:" + line.getString().trim());
        }
        return reply(source, String.join(" ", out));
    }

    private static String fmt(double[] values) {
        StringBuilder sb = new StringBuilder("[");
        for(int i = 0; i < values.length; i++)
            sb.append(i == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.5g", values[i]));
        return sb.append("]").toString();
    }

    private static int reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }
}
