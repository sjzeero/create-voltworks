package io.github.sjzeero.voltworks.registry;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.simulation.WireType;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.github.sjzeero.voltworks.Voltworks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VoltworksWireTypes {
    private VoltworksWireTypes() {}

    public static final DeferredRegister<WireType> WIRE_TYPES = DeferredRegister.create(CEERegistries.WIRE_TYPE, Voltworks.MOD_ID);

    public static final PartialModel CORD_SEGMENT = PartialModel.of(Voltworks.asResource("block/cord_segment"));

    public static final DeferredHolder<WireType, WireType> COPPER_CORD = WIRE_TYPES.register("copper_cord",
            () -> new WireType.Builder(CORD_SEGMENT)
                    .resistance(CEEConfigs.server().resistanceValues.wireResistance::get)
                    .droppedItem(() -> VoltworksItems.TWO_CORE_WIRE.get())
                    .spoolItem(() -> VoltworksItems.COPPER_CORD.get())
                    .maxInsulationVoltage(CEEConfigs.server().voltageValues.wireMaxVoltage::get)
                    .maxTemperature(() -> 3540.0)
                    .insulationResistance(330000.0)
                    .maxLength(() -> 16)
                    .renderType(WireType.WireRenderType.SOLID)
                    .build());

    public static void register(IEventBus bus) {
        WIRE_TYPES.register(bus);
    }
}
