package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.Voltworks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VoltworksContraptions {
    private VoltworksContraptions() {}

    public static final DeferredRegister<com.simibubi.create.api.contraption.ContraptionType> CONTRAPTION_TYPES = DeferredRegister.create(com.simibubi.create.api.registry.CreateRegistries.CONTRAPTION_TYPE, Voltworks.MOD_ID);

    public static final DeferredHolder<com.simibubi.create.api.contraption.ContraptionType, com.simibubi.create.api.contraption.ContraptionType> SOLAR_PANEL_BEARING_CONTRAPTION = CONTRAPTION_TYPES.register("solar_panel_bearing",
            () -> new com.simibubi.create.api.contraption.ContraptionType(io.github.sjzeero.voltworks.solar.SolarPanelBearingContraption::new));

    public static void register(IEventBus bus) {
        CONTRAPTION_TYPES.register(bus);
    }
}
