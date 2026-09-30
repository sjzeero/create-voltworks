package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.EEMenus;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableEditScreen;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableLoadScreen;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableRenderer;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import io.github.sjzeero.voltworks.board.BoardColor;
import io.github.sjzeero.voltworks.circuits.CircuitItems;

@Mod(value = Voltworks.MOD_ID, dist = Dist.CLIENT)
public class VoltworksClient {
    public VoltworksClient(IEventBus modBus) {
        EEPartialModels.register();
        modBus.addListener(ModdedKeys::register);
        modBus.addListener(VoltworksClient::screens);
        modBus.addListener(VoltworksClient::loaders);
        modBus.addListener(VoltworksClient::models);
        modBus.addListener(VoltworksClient::renderers);
        modBus.addListener(VoltworksClient::setup);
        modBus.addListener(io.github.sjzeero.voltworks.wallwire.client.WallWireClient::registerKeys);
        modBus.addListener(io.github.sjzeero.voltworks.wallwire.client.WallWireClient::registerLayers);
        io.github.sjzeero.voltworks.wallwire.client.WallWireClient.init();
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(EEMenus.CIRCUIT_DESIGN_TABLE.get(), CircuitDesignTableScreen::new);
        event.<io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableEditMenu, CircuitDesignTableEditScreen<io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableEditMenu>>register(
                EEMenus.CIRCUIT_DESIGN_TABLE_EDIT.get(), CircuitDesignTableEditScreen::new);
        event.<io.github.sjzeero.voltworks.circuits.editor.CircuitBoardEditMenu, CircuitDesignTableEditScreen<io.github.sjzeero.voltworks.circuits.editor.CircuitBoardEditMenu>>register(
                EEMenus.CIRCUIT_BOARD_EDIT.get(), CircuitDesignTableEditScreen::new);
        event.register(EEMenus.CIRCUIT_DESIGN_TABLE_LOAD.get(), CircuitDesignTableLoadScreen::new);
    }

    private static void loaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(Voltworks.asResource("circuit_board"), new CircuitBoardModelLoader());
    }

    private static void models(ModelEvent.RegisterAdditional event) {
        for(var id : ComponentModels.collectIds())
            event.register(id);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Voltworks.BOARD_BE.get(), CircuitBoardRenderer::new);
        event.registerBlockEntityRenderer(Voltworks.DESIGN_TABLE_BE.get(), CircuitDesignTableRenderer::new);
        event.registerBlockEntityRenderer(Voltworks.SERVO_BE.get(), ServoRenderer::new);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CircuitItems.ACID.get(), RenderType.translucent());
            for (var item : java.util.List.of(Voltworks.DESIGN_TABLE_ITEM.get(), CircuitItems.INCOMPLETE_CIRCUIT.get(), Voltworks.SERVO_ITEM.get()))
                com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(item,
                        new com.simibubi.create.foundation.item.ItemDescription.Modifier(item, net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE));
            ItemBlockRenderTypes.setRenderLayer(CircuitItems.ACID_FLOWING.get(), RenderType.translucent());
            ItemProperties.register(Voltworks.BOARD_ITEM.get(), Voltworks.asResource("board_color"),
                    (stack, level, entity, seed) -> BoardColor.ofBoard(stack).ordinal() / 10f);
        });
    }
}
