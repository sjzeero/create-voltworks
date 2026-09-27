package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sjzeero.eecircuits.EECircuits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.github.sjzeero.eecircuits.board.BoardParts.part;

public class BoardTestCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eecircuits").requires(s -> s.hasPermission(2))
                .then(Commands.literal("boardtest")
                        .then(Commands.literal("setup").then(Commands.argument("preset", StringArgumentType.word())
                                .executes(ctx -> setup(ctx, StringArgumentType.getString(ctx, "preset")))))
                        .then(Commands.literal("battery").then(Commands.argument("which", IntegerArgumentType.integer(0, 1))
                                .then(Commands.argument("volts", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> battery(ctx, IntegerArgumentType.getInteger(ctx, "which"),
                                                DoubleArgumentType.getDouble(ctx, "volts"))))))
                        .then(Commands.literal("ac").then(Commands.argument("on", IntegerArgumentType.integer(0, 1))
                                .executes(ctx -> {
                                    CreativeBatteryDevice battery = battery(ctx.getSource(), 0);
                                    if (battery == null)
                                        return reply(ctx.getSource(), "no battery");
                                    battery.acFrequency = IntegerArgumentType.getInteger(ctx, "on") == 1 ? 20 : 0;
                                    return reply(ctx.getSource(), "ac " + battery.acFrequency);
                                })))
                        .then(Commands.literal("read").executes(BoardTestCommand::read))
                        .then(Commands.literal("wave").executes(BoardTestCommand::wave))
                        .then(Commands.literal("trace").then(Commands.argument("pad", IntegerArgumentType.integer(0))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 400))
                                        .executes(ctx -> trace(ctx, IntegerArgumentType.getInteger(ctx, "pad"), IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(Commands.literal("stats").then(Commands.argument("count", IntegerArgumentType.integer(2, 400))
                                .executes(ctx -> stats(ctx, IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    private static BlockPos base(CommandSourceStack source) {
        BlockPos spawn = source.getLevel().getSharedSpawnPos();
        return new BlockPos(spawn.getX(), 250, spawn.getZ());
    }

    private static ListTag preset(String name) {
        ListTag list = new ListTag();
        switch (name) {
            case "resistor" -> list.add(part("resistor", 100, ResistorComponent.RESISTANCE, 1, 0));
            case "diode", "halfwave" -> {
                list.add(part("resistor", 100, ResistorComponent.RESISTANCE, 1, 4));
                list.add(part("diode", 0, null, 0, 4));
            }
            case "rc" -> {
                list.add(part("resistor", 1000, ResistorComponent.RESISTANCE, 1, 4));
                list.add(part("capacitor", 0.001f, CapacitorComponent.CAPACITANCE, 4, 0));
            }
            case "npn" -> {
                list.add(part("resistor", 1000, ResistorComponent.RESISTANCE, 1, 4));
                list.add(part("npn", 20, BJTComponent.GAIN, 4, 5, 0));
                list.add(part("resistor", 10000, ResistorComponent.RESISTANCE, 2, 5));
            }
            case "pnp" -> {
                list.add(part("pnp", 20, BJTComponent.GAIN, 4, 5, 1));
                list.add(part("resistor", 10000, ResistorComponent.RESISTANCE, 2, 5));
                list.add(part("resistor", 1000, ResistorComponent.RESISTANCE, 4, 0));
            }
            case "stiffdiode" -> list.add(part("diode", 0, null, 0, 1));
            case "stiffres" -> list.add(part("resistor", 0.1f, ResistorComponent.RESISTANCE, 1, 0));
            default -> {
                return null;
            }
        }
        return list;
    }

    private static int setup(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ListTag parts = preset(name);
        if (parts == null)
            return reply(source, "unknown preset " + name);
        BlockPos a = base(source);
        BlockPos board = a.east(2);
        BlockPos b = a.east(4);
        Block batteryBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_battery"));
        for (BlockPos p : List.of(a, board, b))
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(a, batteryBlock.defaultBlockState());
        level.setBlockAndUpdate(board, EECircuits.BOARD.get().defaultBlockState());
        boolean second = name.equals("npn") || name.equals("pnp");
        if (second)
            level.setBlockAndUpdate(b, batteryBlock.defaultBlockState());
        for (BlockPos p : second ? List.of(a, board, b) : List.of(a, board)) {
            BlockState state = level.getBlockState(p);
            ((ElectricalDeviceBlock<?>) state.getBlock()).ensureNodesExist(level, p, state);
        }
        BoardDevice device = DevicesSavedData.load(level).getDevice(board, BoardDevice.class);
        if (device == null)
            return reply(source, "board device missing");
        device.setParts(parts);
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        sd.connect(new InWorldNode(0, a), new InWorldNode(0, board), CEEWireTypes.COPPER.get());
        sd.connect(new InWorldNode(1, a), new InWorldNode(1, board), CEEWireTypes.COPPER.get());
        if (second) {
            sd.connect(new InWorldNode(0, b), new InWorldNode(0, board), CEEWireTypes.COPPER.get());
            sd.connect(new InWorldNode(1, b), new InWorldNode(2, board), CEEWireTypes.COPPER.get());
        }
        return reply(source, "setup " + name + " with " + device.parts().size() + " parts");
    }

    private static CreativeBatteryDevice battery(CommandSourceStack source, int which) {
        BlockPos p = base(source).east(which == 0 ? 0 : 4);
        return DevicesSavedData.load(source.getLevel()).getDevice(p, CreativeBatteryDevice.class);
    }

    private static int battery(CommandContext<CommandSourceStack> ctx, int which, double volts) {
        CreativeBatteryDevice battery = battery(ctx.getSource(), which);
        if (battery == null)
            return reply(ctx.getSource(), "no battery " + which);
        battery.voltage = volts;
        return reply(ctx.getSource(), "battery " + which + " = " + volts + " V");
    }

    private static BoardDevice board(CommandSourceStack source) {
        return DevicesSavedData.load(source.getLevel()).getDevice(base(source).east(2), BoardDevice.class);
    }

    private static int read(CommandContext<CommandSourceStack> ctx) {
        BoardDevice d = board(ctx.getSource());
        if (d == null)
            return reply(ctx.getSource(), "no board");
        return reply(ctx.getSource(), "ee pinV=" + fmt(d.pinVoltage) + " boardI=" + fmt(d.pinCurrent)
                + " padV=" + fmt(d.padVoltage) + " partI=" + fmt(d.partCurrent) + " converged=" + d.converged);
    }

    private static int wave(CommandContext<CommandSourceStack> ctx) {
        BoardDevice d = board(ctx.getSource());
        if (d == null)
            return reply(ctx.getSource(), "no board");
        List<String> rows = new ArrayList<>();
        for (int m = 0; m < d.wavePinCurrent.length; m++)
            if (d.wavePinCurrent[m] != null)
                rows.add(String.format(Locale.ROOT, "m%d V1=%.3f I1=%.5f", m, d.wavePadVoltage[m][1], d.wavePinCurrent[m][1]));
        return reply(ctx.getSource(), String.join(" | ", rows));
    }

    private static int trace(CommandContext<CommandSourceStack> ctx, int pad, int count) {
        BoardDevice d = board(ctx.getSource());
        if (d == null)
            return reply(ctx.getSource(), "no board");
        List<BoardDevice.Sample> samples = new ArrayList<>(d.history);
        List<String> rows = new ArrayList<>();
        for (BoardDevice.Sample s : samples.subList(Math.max(0, samples.size() - count), samples.size()))
            rows.add(String.format(Locale.ROOT, "%.4f", pad < s.padVoltage().length ? s.padVoltage()[pad] : 0));
        return reply(ctx.getSource(), "pad" + pad + " last " + rows.size() + ": " + String.join(" ", rows));
    }

    private static int stats(CommandContext<CommandSourceStack> ctx, int count) {
        BoardDevice d = board(ctx.getSource());
        if (d == null)
            return reply(ctx.getSource(), "no board");
        List<BoardDevice.Sample> samples = new ArrayList<>(d.history);
        samples = samples.subList(Math.max(0, samples.size() - count), samples.size());
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0, maxStep = 0;
        Double prev = null;
        for (BoardDevice.Sample s : samples) {
            double i = s.pinCurrent()[1];
            min = Math.min(min, i);
            max = Math.max(max, i);
            sum += i;
            if (prev != null)
                maxStep = Math.max(maxStep, Math.abs(i - prev));
            prev = i;
        }
        return reply(ctx.getSource(), String.format(Locale.ROOT, "I1 over %d ticks: min=%.6f max=%.6f mean=%.6f maxTickDelta=%.3g",
                samples.size(), min, max, sum / Math.max(1, samples.size()), maxStep));
    }

    private static String fmt(double[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++)
            sb.append(i == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.5g", values[i]));
        return sb.append("]").toString();
    }

    private static int reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }
}
