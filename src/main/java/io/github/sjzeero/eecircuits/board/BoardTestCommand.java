package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.CommandDispatcher;
import io.github.sjzeero.eecircuits.EECircuits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class BoardTestCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eecircuits").requires(s -> s.hasPermission(2))
                .then(Commands.literal("boardtest")
                        .then(Commands.literal("place").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            BlockPos battery = base(ctx.getSource());
                            level.setBlockAndUpdate(battery, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_battery")).defaultBlockState());
                            level.setBlockAndUpdate(battery.east(2), EECircuits.BOARD.get().defaultBlockState());
                            return reply(ctx.getSource(), "placed battery at " + battery.toShortString() + " and board at " + battery.east(2).toShortString());
                        }))
                        .then(Commands.literal("wire").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            BlockPos battery = base(ctx.getSource());
                            InfrastructureSavedData sd = InfrastructureSavedData.load(level);
                            for (int i = 0; i < BoardDevice.PINS; i++)
                                sd.connect(new InWorldNode(i, battery), new InWorldNode(i, battery.east(2)), CEEWireTypes.COPPER.get());
                            return reply(ctx.getSource(), "wired battery to board");
                        }))
                        .then(Commands.literal("series").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            BlockPos board = base(ctx.getSource()).east(2);
                            BoardDevice device = DevicesSavedData.load(level).getDevice(board, BoardDevice.class);
                            if (device == null)
                                return reply(ctx.getSource(), "no board device at " + board.toShortString());
                            ListTag parts = new ListTag();
                            parts.add(BoardDevice.resistor(0, 2, 100));
                            parts.add(BoardDevice.resistor(2, 1, 200));
                            device.setParts(parts);
                            return reply(ctx.getSource(), "board set to 100 + 200 ohm in series");
                        }))
                        .then(Commands.literal("read").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            BlockPos board = base(ctx.getSource()).east(2);
                            BoardDevice device = DevicesSavedData.load(level).getDevice(board, BoardDevice.class);
                            if (device == null)
                                return reply(ctx.getSource(), "no board device at " + board.toShortString());
                            return reply(ctx.getSource(), String.format("board pins: %.3f V, %.4f A", device.pinVoltage, device.pinCurrent));
                        }))));
    }

    private static BlockPos base(CommandSourceStack source) {
        BlockPos spawn = source.getLevel().getSharedSpawnPos();
        return new BlockPos(spawn.getX(), 250, spawn.getZ());
    }

    private static int reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }
}
