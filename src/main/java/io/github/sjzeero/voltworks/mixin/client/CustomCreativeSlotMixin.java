package io.github.sjzeero.voltworks.mixin.client;

import io.github.sjzeero.voltworks.client.tab.CreativeTabSectionsClient;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$CustomCreativeSlot")
public abstract class CustomCreativeSlotMixin extends Slot {
    private CustomCreativeSlotMixin(Container c, int i, int x, int y) { super(c, i, x, y); }

    @Override
    public boolean isActive() {
        return !CreativeTabSectionsClient.isHeaderSlot(this.getContainerSlot()) && super.isActive();
    }
}
