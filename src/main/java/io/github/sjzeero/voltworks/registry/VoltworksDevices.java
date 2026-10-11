package io.github.sjzeero.voltworks.registry;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardDevice;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableDevice;
import io.github.sjzeero.voltworks.servo.ServoDevice;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VoltworksDevices {
    private VoltworksDevices() {}

    public static final DeferredRegister<SimulatedDeviceType<?>> DEVICES = DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, Voltworks.MOD_ID);

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<BoardDevice>> BOARD_DEVICE = DEVICES.register("circuit_board",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("circuit_board"),
                    (type, level, pos, sd) -> new BoardDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<CircuitDesignTableDevice>> DESIGN_TABLE_DEVICE = DEVICES.register("circuit_design_table",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("circuit_design_table"),
                    (type, level, pos, sd) -> new CircuitDesignTableDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ServoDevice>> SERVO_DEVICE = DEVICES.register("servo",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("servo"),
                    (type, level, pos, sd) -> new ServoDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.motor.PGMotorDevice>> CONSTANT_SPEED_MOTOR_DEVICE = DEVICES.register("constant_speed_motor",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("constant_speed_motor"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.motor.PGMotorDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.solar.SolarPanelDevice>> SOLAR_PANEL_DEVICE = DEVICES.register("solar_panel",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("solar_panel"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.solar.SolarPanelDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.solar.SolarPanelBearingDevice>> SOLAR_PANEL_BEARING_DEVICE = DEVICES.register("solar_panel_bearing",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("solar_panel_bearing"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.solar.SolarPanelBearingDevice(level, pos, sd, type)));

    public static void register(IEventBus bus) {
        DEVICES.register(bus);
    }
}
