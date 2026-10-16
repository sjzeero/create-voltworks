package io.github.sjzeero.voltworks.mixin.client;

import io.github.sjzeero.voltworks.client.tab.CreativeTabSectionsClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenSectionsMixin extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow private float scrollOffs;

    private CreativeScreenSectionsMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void voltworks$renderSectionBanners(GuiGraphics g, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        var tab = CreativeScreenSelectedTabAccessor.voltworks$getSelectedTab();
        if (!CreativeTabSectionsClient.isOurTab(tab)) { CreativeTabSectionsClient.setView(false, 0); return; }
        int row = ((ItemPickerMenuAccessor) this.menu).voltworks$getRowIndexForScroll(scrollOffs);
        CreativeTabSectionsClient.setView(true, row);
        CreativeTabSectionsClient.renderBanners(g, leftPos, topPos, row, mouseX, mouseY);
    }
}
