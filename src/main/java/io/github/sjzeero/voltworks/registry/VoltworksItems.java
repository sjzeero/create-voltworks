package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.cord.CordSpoolItem;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematicItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;

public final class VoltworksItems {
    private VoltworksItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Voltworks.MOD_ID);

    public static final DeferredItem<Item> BLANK_CIRCUIT = ITEMS.registerSimpleItem("blank_circuit");

    public static final DeferredItem<Item> COPPER_CIRCUIT = ITEMS.registerSimpleItem("copper_circuit");

    public static final DeferredItem<Item> INCOMPLETE_GOLDEN_CIRCUIT = ITEMS.registerSimpleItem("incomplete_golden_circuit");

    public static final DeferredItem<Item> GOLDEN_CIRCUIT = ITEMS.registerSimpleItem("golden_circuit");

    public static final DeferredItem<?> BOARD_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.BOARD);

    public static final DeferredItem<?> DESIGN_TABLE_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.DESIGN_TABLE);

    public static final DeferredItem<CircuitSchematicItem> CIRCUIT_SCHEMATIC = ITEMS.registerItem("circuit_schematic", CircuitSchematicItem::new);

    public static final DeferredItem<?> SERVO_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SERVO);
    public static final DeferredItem<?> SOCKET_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SOCKET);
    /** Two-conductor copper cord (Power Grid port); laid like any other Voltworks wall wire. */
    public static final DeferredItem<CordSpoolItem> COPPER_CORD = ITEMS.registerItem("copper_cord",
            p -> new CordSpoolItem(p, VoltworksWireTypes.COPPER_CORD));

    /** Redstone cable: links the redstone level of one block face to other block faces, with no wire in the world. */
    public static final DeferredItem<io.github.sjzeero.voltworks.redstone.RedstoneCableItem> REDSTONE_CABLE = ITEMS.registerItem("redstone_cable",
            p -> new io.github.sjzeero.voltworks.redstone.RedstoneCableItem(p.stacksTo(1)));

    public static final DeferredItem<?> CONSTANT_SPEED_MOTOR_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.CONSTANT_SPEED_MOTOR);

    public static final DeferredItem<?> SOLAR_PANEL_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SOLAR_PANEL);

    public static final DeferredItem<?> SOLAR_PANEL_BEARING_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SOLAR_PANEL_BEARING);

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_SOLAR_PANEL = ITEMS.registerItem("incomplete_solar_panel", SequencedAssemblyItem::new);

    public static final DeferredItem<?> FACTORY_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.FACTORY_LIGHT);

    public static final DeferredItem<?> SIGNAL_LAMP_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SIGNAL_LAMP);

    public static final DeferredItem<?> VOLTAGE_SENSOR_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.VOLTAGE_SENSOR);

    public static final DeferredItem<?> REDSTONE_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.REDSTONE_SWITCH);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
