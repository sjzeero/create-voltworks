package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.VoltworksClientConfig;
import io.github.sjzeero.voltworks.registry.VoltworksMenus;
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
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.model.ModelSwapper;
import io.github.sjzeero.voltworks.circuits.editor.CircuitBoardEditMenu;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableEditMenu;
import io.github.sjzeero.voltworks.motor.PGMotorRenderer;
import io.github.sjzeero.voltworks.registry.VoltworksBlocks;
import io.github.sjzeero.voltworks.solar.SolarPanelCTBehaviour;
import io.github.sjzeero.voltworks.wallwire.client.WallWireClient;
import net.createmod.catnip.lang.FontHelper;

@Mod(value = Voltworks.MOD_ID, dist = Dist.CLIENT)
public class VoltworksClient {
    public VoltworksClient(IEventBus modBus) {
        VoltworksPartialModels.register();
        modBus.addListener(VoltworksClient::solarPanelCT);
        modBus.addListener(ModdedKeys::register);
        modBus.addListener(VoltworksClient::screens);
        modBus.addListener(VoltworksClient::loaders);
        modBus.addListener(VoltworksClient::models);
        modBus.addListener(VoltworksClient::renderers);
        modBus.addListener(VoltworksClient::setup);
        modBus.addListener(WallWireClient::registerKeys);
        modBus.addListener(WallWireClient::registerLayers);
        modBus.addListener(ContraptionGoggles::registerLayers);
        WallWireClient.init();
        io.github.sjzeero.voltworks.reel.client.ReelClient.init();
        PotentiometerScroll.init();
        io.github.sjzeero.voltworks.redstone.client.RedstoneCableClient.init();
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(VoltworksMenus.CIRCUIT_DESIGN_TABLE.get(), CircuitDesignTableScreen::new);
        event.<CircuitDesignTableEditMenu, CircuitDesignTableEditScreen<CircuitDesignTableEditMenu>>register(
                VoltworksMenus.CIRCUIT_DESIGN_TABLE_EDIT.get(), CircuitDesignTableEditScreen::new);
        event.<CircuitBoardEditMenu, CircuitDesignTableEditScreen<CircuitBoardEditMenu>>register(
                VoltworksMenus.CIRCUIT_BOARD_EDIT.get(), CircuitDesignTableEditScreen::new);
        event.register(VoltworksMenus.CIRCUIT_DESIGN_TABLE_LOAD.get(), CircuitDesignTableLoadScreen::new);
    }

    private static void loaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(Voltworks.asResource("circuit_board"), new CircuitBoardModelLoader());
    }

    private static void models(ModelEvent.RegisterAdditional event) {
        for(var id : ComponentModels.collectIds())
            event.register(id);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VoltworksBlockEntities.BOARD_BE.get(), CircuitBoardRenderer::new);
        event.registerBlockEntityRenderer(VoltworksBlockEntities.DESIGN_TABLE_BE.get(), CircuitDesignTableRenderer::new);
        event.registerBlockEntityRenderer(VoltworksBlockEntities.SERVO_BE.get(), ServoRenderer::new);
        event.registerBlockEntityRenderer(VoltworksBlockEntities.SOLAR_PANEL_BEARING_BE.get(), SolarPanelBearingRenderer::new);
        event.registerBlockEntityRenderer(VoltworksBlockEntities.CONSTANT_SPEED_MOTOR_BE.get(), PGMotorRenderer::new);
        event.registerEntityRenderer(io.github.sjzeero.voltworks.registry.VoltworksEntities.CABLE_END.get(), io.github.sjzeero.voltworks.reel.client.CableEndRenderer::new);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CircuitItems.ACID.get(), RenderType.translucent());
            for (var item : java.util.List.of(VoltworksItems.DESIGN_TABLE_ITEM.get(), CircuitItems.INCOMPLETE_CIRCUIT.get(), VoltworksItems.SERVO_ITEM.get(), VoltworksItems.CORD_REEL_ITEM.get(), VoltworksItems.CABLE_REEL_ITEM.get(), VoltworksItems.SMALL_CORD_REEL_ITEM.get(), VoltworksItems.SMALL_CABLE_REEL_ITEM.get(), VoltworksItems.BATTERY_ITEM.get()))
                TooltipModifier.REGISTRY.register(item,
                        new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
            ItemBlockRenderTypes.setRenderLayer(CircuitItems.ACID_FLOWING.get(), RenderType.translucent());
            ItemProperties.register(VoltworksItems.BOARD_ITEM.get(), Voltworks.asResource("board_color"),
                    (stack, level, entity, seed) -> BoardColor.ofBoard(stack).ordinal() / 10f);
            // cord reel ends show the socket plug, cable reel ends a copper lug
            ItemProperties.register(VoltworksItems.REEL_PLUG.get(), Voltworks.asResource("cord"),
                    (stack, level, entity, seed) -> io.github.sjzeero.voltworks.reel.ReelPlugItem.isCord(stack) ? 1f : 0f);
        });
    }

    private static void solarPanelCT(ModelEvent.ModifyBakingResult event) {
        var behaviour = new SolarPanelCTBehaviour();
        ModelSwapper.swapModels(event.getModels(),
                ModelSwapper.getAllBlockStateModelLocations(VoltworksBlocks.SOLAR_PANEL.get()),
                m -> new CTModel(m, behaviour));
    }
}
