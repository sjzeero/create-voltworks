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
    public static final DeferredItem<?> POWER_STRIP_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.POWER_STRIP);
    public static final DeferredItem<Item> TWO_CORE_WIRE = ITEMS.registerSimpleItem("two_core_wire");

    public static final DeferredItem<Item> REDSTONE_CABLE_WIRE = ITEMS.registerSimpleItem("redstone_cable_wire");

    public static final DeferredItem<CordSpoolItem> COPPER_CORD = ITEMS.registerItem("copper_cord",
            p -> new CordSpoolItem(p, VoltworksWireTypes.COPPER_CORD));

    public static final DeferredItem<io.github.sjzeero.voltworks.redstone.RedstoneCableItem> REDSTONE_CABLE = ITEMS.registerItem("redstone_cable",
            p -> new io.github.sjzeero.voltworks.redstone.RedstoneCableItem(p));

    public static final DeferredItem<?> CORD_REEL_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.CORD_REEL);

    public static final DeferredItem<?> CABLE_REEL_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.CABLE_REEL);
    public static final DeferredItem<?> SMALL_CORD_REEL_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SMALL_CORD_REEL);
    public static final DeferredItem<?> SMALL_CABLE_REEL_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SMALL_CABLE_REEL);

    public static final DeferredItem<io.github.sjzeero.voltworks.reel.ReelPlugItem> REEL_PLUG = ITEMS.registerItem("reel_plug",
            p -> new io.github.sjzeero.voltworks.reel.ReelPlugItem(p.stacksTo(1)));

    public static final DeferredItem<?> CONSTANT_SPEED_MOTOR_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.CONSTANT_SPEED_MOTOR);

    public static final DeferredItem<?> SOLAR_PANEL_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SOLAR_PANEL);

    public static final DeferredItem<?> SOLAR_PANEL_BEARING_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.SOLAR_PANEL_BEARING);

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_SOLAR_PANEL = ITEMS.registerItem("incomplete_solar_panel", SequencedAssemblyItem::new);

    public static final DeferredItem<io.github.sjzeero.voltworks.battery.BatteryItem> BATTERY_ITEM = ITEMS.register("battery",
            () -> new io.github.sjzeero.voltworks.battery.BatteryItem(VoltworksBlocks.BATTERY.get(), new net.minecraft.world.item.Item.Properties()));

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_BATTERY = ITEMS.registerItem("incomplete_battery", SequencedAssemblyItem::new);

    public static final DeferredItem<?> FACTORY_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(VoltworksBlocks.FACTORY_LIGHT);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
