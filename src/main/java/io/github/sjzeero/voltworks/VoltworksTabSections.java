package io.github.sjzeero.voltworks;

import io.github.sjzeero.voltworks.circuits.CircuitItems;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.*;
import java.util.function.Supplier;
import io.github.sjzeero.voltworks.registry.VoltworksItems;

/** Creative tab sections: order of items in the Voltworks tab and banner styling. */
public final class VoltworksTabSections {
    public record Section(String id, int background, int border, int text, List<Item> items) {
        public String titleKey() { return "voltworks.tab_section." + id; }
    }

    private static List<Section> cached;

    public static List<Section> sections() {
        if (cached != null) return cached;
        List<Item> boards = new ArrayList<>(), motors = new ArrayList<>(), solar = new ArrayList<>();
        for (Supplier<? extends ItemLike> s : List.<Supplier<? extends ItemLike>>of(
                VoltworksItems.BLANK_CIRCUIT, VoltworksItems.COPPER_CIRCUIT, VoltworksItems.GOLDEN_CIRCUIT, VoltworksItems.BOARD_ITEM, VoltworksItems.DESIGN_TABLE_ITEM,
                VoltworksItems.CIRCUIT_SCHEMATIC, CircuitItems.EMPTY_CIRCUIT, CircuitItems.UNETCHED_CIRCUIT, CircuitItems.INCOMPLETE_CIRCUIT, CircuitItems.ACID_BUCKET))
            boards.add(s.get().asItem());
        for (var component : ComponentRegistry.entries()) {
            var id = ComponentRegistry.getId(component);
            var item = ComponentRegistry.getItem(component);
            if (item == null || !id.getNamespace().equals(Voltworks.MOD_ID)
                    || !Voltworks.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) continue;
            boards.add(item);
        }
        for (Supplier<? extends ItemLike> s : List.<Supplier<? extends ItemLike>>of(VoltworksItems.SERVO_ITEM, VoltworksItems.CONSTANT_SPEED_MOTOR_ITEM, VoltworksItems.SOCKET_ITEM, VoltworksItems.COPPER_CORD))
            motors.add(s.get().asItem());
        for (Supplier<? extends ItemLike> s : List.<Supplier<? extends ItemLike>>of(VoltworksItems.SOLAR_PANEL_ITEM, VoltworksItems.SOLAR_PANEL_BEARING_ITEM, VoltworksItems.INCOMPLETE_SOLAR_PANEL))
            solar.add(s.get().asItem());
        List<Section> list = new ArrayList<>();
        add(list, new Section("boards", 0xE0402A1C, 0xFFB8743A, 0xFFFFD9A8, boards));
        add(list, new Section("motors", 0xE0102838, 0xFF40A8D0, 0xFFC8F0FF, motors));
        add(list, new Section("solar", 0xE0102030, 0xFFE0B040, 0xFFFFE8A0, solar));
        cached = Collections.unmodifiableList(list);
        return cached;
    }

    private static void add(List<Section> list, Section s) { if (!s.items().isEmpty()) list.add(s); }

    public static void fill(CreativeModeTab.Output out) {
        for (var s : sections()) for (var item : s.items()) out.accept(item);
    }

    private VoltworksTabSections() {}
}
