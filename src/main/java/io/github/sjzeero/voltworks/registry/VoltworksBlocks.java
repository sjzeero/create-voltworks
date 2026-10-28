package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.cord.SocketBlock;
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
import io.github.sjzeero.voltworks.motor.ConstantSpeedMotorBlock;
import io.github.sjzeero.voltworks.solar.SolarPanelBearingBlock;
import io.github.sjzeero.voltworks.solar.SolarPanelBlock;

public final class VoltworksBlocks {
    private VoltworksBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Voltworks.MOD_ID);

    public static final DeferredBlock<BoardBlock> BOARD = BLOCKS.register("circuit_board", () -> new BoardBlock(
            BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<CircuitDesignTableBlock> DESIGN_TABLE = BLOCKS.register("circuit_design_table", () -> new CircuitDesignTableBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion()));

    public static final DeferredBlock<ServoBlock> SERVO = BLOCKS.register("servo", () -> new ServoBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredBlock<SocketBlock> SOCKET = BLOCKS.register("socket", () -> new SocketBlock(
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion()));

    /** Cord reel: two-core copper cord pulled out to a socket like a vacuum cleaner cord. */
    public static final DeferredBlock<io.github.sjzeero.voltworks.reel.CableReelBlock> CORD_REEL = BLOCKS.register("cord_reel", () -> new io.github.sjzeero.voltworks.reel.CableReelBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion(), true));

    /** Cable reel: one Electro Energetics wire pulled out to any terminal. */
    public static final DeferredBlock<io.github.sjzeero.voltworks.reel.CableReelBlock> CABLE_REEL = BLOCKS.register("cable_reel", () -> new io.github.sjzeero.voltworks.reel.CableReelBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion(), false));

    /** The same reels in a smaller frame (not a full block). */
    public static final DeferredBlock<io.github.sjzeero.voltworks.reel.CableReelBlock> SMALL_CORD_REEL = BLOCKS.register("small_cord_reel", () -> new io.github.sjzeero.voltworks.reel.CableReelBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion(), true, true));
    public static final DeferredBlock<io.github.sjzeero.voltworks.reel.CableReelBlock> SMALL_CABLE_REEL = BLOCKS.register("small_cable_reel", () -> new io.github.sjzeero.voltworks.reel.CableReelBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion(), false, true));

    public static final DeferredBlock<ConstantSpeedMotorBlock> CONSTANT_SPEED_MOTOR = BLOCKS.register("constant_speed_motor", () -> new ConstantSpeedMotorBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.register("solar_panel", () -> new SolarPanelBlock(
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<SolarPanelBearingBlock> SOLAR_PANEL_BEARING = BLOCKS.register("solar_panel_bearing", () -> new SolarPanelBearingBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredBlock<io.github.sjzeero.voltworks.light.FactoryLightBlock> FACTORY_LIGHT = BLOCKS.register("factory_light", () -> new io.github.sjzeero.voltworks.light.FactoryLightBlock(
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(io.github.sjzeero.voltworks.light.FactoryLightBlock.POWER) * 5)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
