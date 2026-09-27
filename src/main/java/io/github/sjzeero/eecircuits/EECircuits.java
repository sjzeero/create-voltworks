package io.github.sjzeero.eecircuits;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import io.github.sjzeero.eecircuits.board.BoardBlock;
import io.github.sjzeero.eecircuits.board.BoardDevice;
import io.github.sjzeero.eecircuits.board.BoardTestCommand;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(EECircuits.MOD_ID)
public class EECircuits {
    public static final String MOD_ID = "eecircuits";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<SimulatedDeviceType<?>> DEVICES = DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, MOD_ID);

    public static final DeferredItem<Item> BLANK_CIRCUIT = ITEMS.registerSimpleItem("blank_circuit");
    public static final DeferredItem<Item> COPPER_CIRCUIT = ITEMS.registerSimpleItem("copper_circuit");
    public static final DeferredItem<Item> INCOMPLETE_GOLDEN_CIRCUIT = ITEMS.registerSimpleItem("incomplete_golden_circuit");
    public static final DeferredItem<Item> GOLDEN_CIRCUIT = ITEMS.registerSimpleItem("golden_circuit");

    public static final DeferredBlock<BoardBlock> BOARD = BLOCKS.register("circuit_board", () -> new BoardBlock(
            BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<?> BOARD_ITEM = ITEMS.registerSimpleBlockItem(BOARD);
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<BoardDevice>> BOARD_DEVICE = DEVICES.register("circuit_board",
            () -> new SimulatedDeviceType<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "circuit_board"),
                    (type, level, pos, sd) -> new BoardDevice(level, pos, sd, type)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.eecircuits"))
            .icon(() -> COPPER_CIRCUIT.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(BLANK_CIRCUIT);
                out.accept(COPPER_CIRCUIT);
                out.accept(GOLDEN_CIRCUIT);
                out.accept(BOARD_ITEM.get());
            })
            .build());

    public EECircuits(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        DEVICES.register(modBus);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BoardTestCommand.register(e.getDispatcher()));
        TABS.register(modBus);
    }
}
