package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.cord.SocketDevice;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardDevice;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableDevice;
import io.github.sjzeero.voltworks.servo.ServoDevice;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github.sjzeero.voltworks.motor.PGMotorDevice;
import io.github.sjzeero.voltworks.solar.SolarPanelBearingDevice;
import io.github.sjzeero.voltworks.solar.SolarPanelDevice;

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
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<SocketDevice>> SOCKET_DEVICE = DEVICES.register("socket",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("socket"),
                    (type, level, pos, sd) -> new SocketDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<PGMotorDevice>> CONSTANT_SPEED_MOTOR_DEVICE = DEVICES.register("constant_speed_motor",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("constant_speed_motor"), (type, level, pos, sd) -> new PGMotorDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<SolarPanelDevice>> SOLAR_PANEL_DEVICE = DEVICES.register("solar_panel",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("solar_panel"), (type, level, pos, sd) -> new SolarPanelDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<SolarPanelBearingDevice>> SOLAR_PANEL_BEARING_DEVICE = DEVICES.register("solar_panel_bearing",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("solar_panel_bearing"), (type, level, pos, sd) -> new SolarPanelBearingDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.light.FactoryLightDevice>> FACTORY_LIGHT_DEVICE = DEVICES.register("factory_light",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("factory_light"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.light.FactoryLightDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.light.SignalLampDevice>> SIGNAL_LAMP_DEVICE = DEVICES.register("signal_lamp",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("signal_lamp"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.light.SignalLampDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.light.BuzzerDevice>> BUZZER_DEVICE = DEVICES.register("buzzer",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("buzzer"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.light.BuzzerDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.light.VoltageSensorDevice>> VOLTAGE_SENSOR_DEVICE = DEVICES.register("voltage_sensor",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("voltage_sensor"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.light.VoltageSensorDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<io.github.sjzeero.voltworks.light.RedstoneSwitchDevice>> REDSTONE_SWITCH_DEVICE = DEVICES.register("redstone_switch",
            () -> new SimulatedDeviceType<>(Voltworks.asResource("redstone_switch"), (type, level, pos, sd) -> new io.github.sjzeero.voltworks.light.RedstoneSwitchDevice(level, pos, sd, type)));

    public static void register(IEventBus bus) {
        DEVICES.register(bus);
    }
}
