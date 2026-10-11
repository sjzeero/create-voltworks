package io.github.sjzeero.voltworks.mixin.client;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenSelectedTabAccessor {
    @Accessor("selectedTab")
    static CreativeModeTab voltworks$getSelectedTab() { throw new AssertionError(); }
}
