package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.circuits.editor.CircuitBoardEditMenu;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableEditMenu;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableLoadMenu;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github.sjzeero.voltworks.Voltworks;

public class VoltworksMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Voltworks.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CircuitDesignTableMenu>> CIRCUIT_DESIGN_TABLE = MENUS.register("circuit_design_table",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new CircuitDesignTableMenu(VoltworksMenus.CIRCUIT_DESIGN_TABLE.get(), id, inv, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<CircuitDesignTableEditMenu>> CIRCUIT_DESIGN_TABLE_EDIT = MENUS.register("circuit_design_table_edit",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new CircuitDesignTableEditMenu(VoltworksMenus.CIRCUIT_DESIGN_TABLE_EDIT.get(), id, inv, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<CircuitDesignTableLoadMenu>> CIRCUIT_DESIGN_TABLE_LOAD = MENUS.register("circuit_design_table_load",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new CircuitDesignTableLoadMenu(VoltworksMenus.CIRCUIT_DESIGN_TABLE_LOAD.get(), id, inv, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<CircuitBoardEditMenu>> CIRCUIT_BOARD_EDIT = MENUS.register("circuit_board_edit",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new CircuitBoardEditMenu(VoltworksMenus.CIRCUIT_BOARD_EDIT.get(), id, inv, buf)));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
