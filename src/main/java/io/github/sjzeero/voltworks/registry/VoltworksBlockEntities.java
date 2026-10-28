package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.battery.BatteryBlockEntity;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableBlockEntity;
import io.github.sjzeero.voltworks.servo.ServoBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github.sjzeero.voltworks.motor.ConstantSpeedMotorBlockEntity;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.solar.SolarPanelBearingBlockEntity;

public final class VoltworksBlockEntities {
    private VoltworksBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Voltworks.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoardBlockEntity>> BOARD_BE = BLOCK_ENTITIES.register("circuit_board",
            () -> BlockEntityType.Builder.of(BoardBlockEntity::new, VoltworksBlocks.BOARD.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CircuitDesignTableBlockEntity>> DESIGN_TABLE_BE = BLOCK_ENTITIES.register("circuit_design_table",
            () -> BlockEntityType.Builder.of(CircuitDesignTableBlockEntity::new, VoltworksBlocks.DESIGN_TABLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServoBlockEntity>> SERVO_BE = BLOCK_ENTITIES.register("servo",
            () -> BlockEntityType.Builder.of(ServoBlockEntity::new, VoltworksBlocks.SERVO.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstantSpeedMotorBlockEntity>> CONSTANT_SPEED_MOTOR_BE = BLOCK_ENTITIES.register("constant_speed_motor",
            () -> BlockEntityType.Builder.of(ConstantSpeedMotorBlockEntity::new, VoltworksBlocks.CONSTANT_SPEED_MOTOR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBearingBlockEntity>> SOLAR_PANEL_BEARING_BE = BLOCK_ENTITIES.register("solar_panel_bearing",
            () -> BlockEntityType.Builder.of(SolarPanelBearingBlockEntity::new, VoltworksBlocks.SOLAR_PANEL_BEARING.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableReelBlockEntity>> CABLE_REEL_BE = BLOCK_ENTITIES.register("cable_reel",
            () -> BlockEntityType.Builder.of(CableReelBlockEntity::new, VoltworksBlocks.CORD_REEL.get(), VoltworksBlocks.CABLE_REEL.get(),
                    VoltworksBlocks.SMALL_CORD_REEL.get(), VoltworksBlocks.SMALL_CABLE_REEL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY_BE = BLOCK_ENTITIES.register("battery",
            () -> BlockEntityType.Builder.of(BatteryBlockEntity::new, VoltworksBlocks.BATTERY.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
