package io.github.sjzeero.eecircuits;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.mojang.logging.LogUtils;
import io.github.sjzeero.eecircuits.board.BoardBlock;
import io.github.sjzeero.eecircuits.board.BoardBlockEntity;
import io.github.sjzeero.eecircuits.board.BoardDevice;
import io.github.sjzeero.eecircuits.board.BoardTestCommand;
import io.github.sjzeero.eecircuits.circuits.CircuitItems;
import io.github.sjzeero.eecircuits.circuits.circuitboard.CircuitInsertion;
import io.github.sjzeero.eecircuits.circuits.components.ComponentRegistry;
import io.github.sjzeero.eecircuits.circuits.components.Components;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableBlock;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableBlockEntity;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableDevice;
import io.github.sjzeero.eecircuits.circuits.schematic.CircuitSchematicItem;
import io.github.sjzeero.eecircuits.network.EENetwork;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.slf4j.Logger;

@Mod(EECircuits.MOD_ID)
public class EECircuits {
    public static final String MOD_ID = "eecircuits";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<SimulatedDeviceType<?>> DEVICES = DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, MOD_ID);

    public static final DeferredItem<Item> BLANK_CIRCUIT = ITEMS.registerSimpleItem("blank_circuit");
    public static final DeferredItem<Item> COPPER_CIRCUIT = ITEMS.registerSimpleItem("copper_circuit");
    public static final DeferredItem<Item> INCOMPLETE_GOLDEN_CIRCUIT = ITEMS.registerSimpleItem("incomplete_golden_circuit");
    public static final DeferredItem<Item> GOLDEN_CIRCUIT = ITEMS.registerSimpleItem("golden_circuit");

    public static final DeferredBlock<BoardBlock> BOARD = BLOCKS.register("circuit_board", () -> new BoardBlock(
            BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<?> BOARD_ITEM = ITEMS.registerSimpleBlockItem(BOARD);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoardBlockEntity>> BOARD_BE = BLOCK_ENTITIES.register("circuit_board",
            () -> BlockEntityType.Builder.of(BoardBlockEntity::new, BOARD.get()).build(null));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<BoardDevice>> BOARD_DEVICE = DEVICES.register("circuit_board",
            () -> new SimulatedDeviceType<>(asResource("circuit_board"),
                    (type, level, pos, sd) -> new BoardDevice(level, pos, sd, type)));

    public static final DeferredBlock<CircuitDesignTableBlock> DESIGN_TABLE = BLOCKS.register("circuit_design_table", () -> new CircuitDesignTableBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion()));
    public static final DeferredItem<?> DESIGN_TABLE_ITEM = ITEMS.registerSimpleBlockItem(DESIGN_TABLE);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CircuitDesignTableBlockEntity>> DESIGN_TABLE_BE = BLOCK_ENTITIES.register("circuit_design_table",
            () -> BlockEntityType.Builder.of(CircuitDesignTableBlockEntity::new, DESIGN_TABLE.get()).build(null));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<CircuitDesignTableDevice>> DESIGN_TABLE_DEVICE = DEVICES.register("circuit_design_table",
            () -> new SimulatedDeviceType<>(asResource("circuit_design_table"),
                    (type, level, pos, sd) -> new CircuitDesignTableDevice(level, pos, sd, type)));
    public static final DeferredItem<CircuitSchematicItem> CIRCUIT_SCHEMATIC = ITEMS.registerItem("circuit_schematic", CircuitSchematicItem::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.eecircuits"))
            .icon(() -> COPPER_CIRCUIT.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(BLANK_CIRCUIT);
                out.accept(COPPER_CIRCUIT);
                out.accept(GOLDEN_CIRCUIT);
                out.accept(BOARD_ITEM.get());
                out.accept(DESIGN_TABLE_ITEM.get());
                out.accept(CIRCUIT_SCHEMATIC.get());
                out.accept(CircuitItems.EMPTY_CIRCUIT.get());
                out.accept(CircuitItems.UNETCHED_CIRCUIT.get());
                out.accept(CircuitItems.INCOMPLETE_CIRCUIT.get());
                out.accept(CircuitItems.ACID_BUCKET.get());
                for(var component : ComponentRegistry.entries()) {
                    var item = ComponentRegistry.getItem(component);
                    if(item != null && ComponentRegistry.getId(component).getNamespace().equals(MOD_ID)
                            && MOD_ID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace()))
                        out.accept(item);
                }
            })
            .build());

    public EECircuits(IEventBus modBus, ModContainer container) {
        Components.register(modBus);
        CircuitItems.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DEVICES.register(modBus);
        TABS.register(modBus);
        EEMenus.register(modBus);
        modBus.addListener(EENetwork::register);
        modBus.addListener((NewRegistryEvent e) -> e.register(ComponentRegistry.REGISTRY));
        container.registerConfig(ModConfig.Type.SERVER, EECircuitsConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, EECircuitsClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BoardTestCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(CircuitInsertion::onDeployerRecipeSearch);
        NeoForge.EVENT_BUS.addListener(BoardTestCommand::onServerTick);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ResourceLocation texture(String path) {
        return asResource("textures/" + path + ".png");
    }
}
