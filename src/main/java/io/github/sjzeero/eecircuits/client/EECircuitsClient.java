package io.github.sjzeero.eecircuits.client;

import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.EEMenus;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableEditScreen;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableLoadScreen;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableRenderer;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = EECircuits.MOD_ID, dist = Dist.CLIENT)
public class EECircuitsClient {
    public EECircuitsClient(IEventBus modBus) {
        EEPartialModels.register();
        modBus.addListener(ModdedKeys::register);
        modBus.addListener(EECircuitsClient::screens);
        modBus.addListener(EECircuitsClient::loaders);
        modBus.addListener(EECircuitsClient::models);
        modBus.addListener(EECircuitsClient::renderers);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(EEMenus.CIRCUIT_DESIGN_TABLE.get(), CircuitDesignTableScreen::new);
        event.<io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableEditMenu, CircuitDesignTableEditScreen<io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableEditMenu>>register(
                EEMenus.CIRCUIT_DESIGN_TABLE_EDIT.get(), CircuitDesignTableEditScreen::new);
        event.<io.github.sjzeero.eecircuits.circuits.editor.CircuitBoardEditMenu, CircuitDesignTableEditScreen<io.github.sjzeero.eecircuits.circuits.editor.CircuitBoardEditMenu>>register(
                EEMenus.CIRCUIT_BOARD_EDIT.get(), CircuitDesignTableEditScreen::new);
        event.register(EEMenus.CIRCUIT_DESIGN_TABLE_LOAD.get(), CircuitDesignTableLoadScreen::new);
    }

    private static void loaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(EECircuits.asResource("circuit_board"), new CircuitBoardModelLoader());
    }

    private static void models(ModelEvent.RegisterAdditional event) {
        for(var id : ComponentModels.collectIds())
            event.register(id);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EECircuits.BOARD_BE.get(), CircuitBoardRenderer::new);
        event.registerBlockEntityRenderer(EECircuits.DESIGN_TABLE_BE.get(), CircuitDesignTableRenderer::new);
    }
}
