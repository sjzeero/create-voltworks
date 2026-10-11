package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlock;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableBlock;
import io.github.sjzeero.voltworks.servo.ServoBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VoltworksBlocks {
    private VoltworksBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Voltworks.MOD_ID);

    public static final DeferredBlock<BoardBlock> BOARD = BLOCKS.register("circuit_board", () -> new BoardBlock(
            BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<CircuitDesignTableBlock> DESIGN_TABLE = BLOCKS.register("circuit_design_table", () -> new CircuitDesignTableBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion()));

    public static final DeferredBlock<ServoBlock> SERVO = BLOCKS.register("servo", () -> new ServoBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredBlock<io.github.sjzeero.voltworks.motor.ConstantSpeedMotorBlock> CONSTANT_SPEED_MOTOR = BLOCKS.register("constant_speed_motor", () -> new io.github.sjzeero.voltworks.motor.ConstantSpeedMotorBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredBlock<io.github.sjzeero.voltworks.solar.SolarPanelBlock> SOLAR_PANEL = BLOCKS.register("solar_panel", () -> new io.github.sjzeero.voltworks.solar.SolarPanelBlock(
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<io.github.sjzeero.voltworks.solar.SolarPanelBearingBlock> SOLAR_PANEL_BEARING = BLOCKS.register("solar_panel_bearing", () -> new io.github.sjzeero.voltworks.solar.SolarPanelBearingBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
