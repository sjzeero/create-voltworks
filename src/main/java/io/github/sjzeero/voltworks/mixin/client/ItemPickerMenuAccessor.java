package io.github.sjzeero.voltworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$ItemPickerMenu")
public interface ItemPickerMenuAccessor {
    @Invoker("getRowIndexForScroll")
    int voltworks$getRowIndexForScroll(float scroll);
}
