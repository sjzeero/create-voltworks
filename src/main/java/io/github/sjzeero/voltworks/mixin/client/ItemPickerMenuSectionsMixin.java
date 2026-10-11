package io.github.sjzeero.voltworks.mixin.client;

import io.github.sjzeero.voltworks.client.tab.CreativeTabSectionsClient;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$ItemPickerMenu")
public abstract class ItemPickerMenuSectionsMixin {
    @Shadow @Final public NonNullList<ItemStack> items;

    @Inject(method = "scrollTo", at = @At("HEAD"))
    private void voltworks$layoutSections(float pos, CallbackInfo ci) {
        var tab = CreativeScreenSelectedTabAccessor.voltworks$getSelectedTab();
        if (CreativeTabSectionsClient.isOurTab(tab)) CreativeTabSectionsClient.layout(items, tab);
    }
}
