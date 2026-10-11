package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.VoltworksTabSections;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VoltworksCreativeTabs {
    private VoltworksCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Voltworks.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.voltworks"))
            .icon(() -> VoltworksItems.COPPER_CIRCUIT.get().getDefaultInstance())
            .displayItems((params, out) -> VoltworksTabSections.fill(out))
            .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
