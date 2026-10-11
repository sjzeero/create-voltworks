package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.Voltworks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateRegistries;
import io.github.sjzeero.voltworks.solar.SolarPanelBearingContraption;

public final class VoltworksContraptions {
    private VoltworksContraptions() {}

    public static final DeferredRegister<ContraptionType> CONTRAPTION_TYPES = DeferredRegister.create(CreateRegistries.CONTRAPTION_TYPE, Voltworks.MOD_ID);

    public static final DeferredHolder<ContraptionType, ContraptionType> SOLAR_PANEL_BEARING_CONTRAPTION = CONTRAPTION_TYPES.register("solar_panel_bearing",
            () -> new ContraptionType(SolarPanelBearingContraption::new));

    public static void register(IEventBus bus) {
        CONTRAPTION_TYPES.register(bus);
    }
}
