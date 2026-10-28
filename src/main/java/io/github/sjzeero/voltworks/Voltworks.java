package io.github.sjzeero.voltworks;

import io.github.sjzeero.voltworks.redstone.RedstoneCableEvents;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import com.mojang.logging.LogUtils;
import io.github.sjzeero.voltworks.board.BoardTestCommand;
import io.github.sjzeero.voltworks.circuits.CircuitItems;
import io.github.sjzeero.voltworks.circuits.circuitboard.CircuitInsertion;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import io.github.sjzeero.voltworks.circuits.components.Components;
import io.github.sjzeero.voltworks.network.VoltworksNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.slf4j.Logger;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksBlocks;
import io.github.sjzeero.voltworks.registry.VoltworksContraptions;
import io.github.sjzeero.voltworks.registry.VoltworksCreativeTabs;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import io.github.sjzeero.voltworks.registry.VoltworksEntities;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import io.github.sjzeero.voltworks.registry.VoltworksRecipes;
import io.github.sjzeero.voltworks.registry.VoltworksMenus;
import io.github.sjzeero.voltworks.registry.VoltworksSounds;
import io.github.sjzeero.voltworks.wallwire.WallWireEvents;

@Mod(Voltworks.MOD_ID)
public class Voltworks {
    public static final String MOD_ID = "voltworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Voltworks(IEventBus modBus, ModContainer container) {
        Components.register(modBus);
        CircuitItems.register(modBus);
        VoltworksBlocks.register(modBus);
        VoltworksItems.register(modBus);
        VoltworksBlockEntities.register(modBus);
        VoltworksDevices.register(modBus);
        VoltworksWireTypes.register(modBus);
        VoltworksContraptions.register(modBus);
        VoltworksRecipes.register(modBus);
        VoltworksCreativeTabs.register(modBus);
        VoltworksMenus.register(modBus);
        VoltworksSounds.register(modBus);
        VoltworksEntities.register(modBus);
        modBus.addListener(VoltworksNetwork::register);
        modBus.addListener((NewRegistryEvent e) -> e.register(ComponentRegistry.REGISTRY));
        container.registerConfig(ModConfig.Type.SERVER, VoltworksConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, VoltworksClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BoardTestCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(CircuitInsertion::onDeployerRecipeSearch);
        NeoForge.EVENT_BUS.addListener(BoardTestCommand::onServerTick);
        WallWireEvents.register();
        RedstoneCableEvents.register();
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ResourceLocation texture(String path) {
        return asResource("textures/" + path + ".png");
    }
}
