package io.github.sjzeero.voltworks.client.tab;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.VoltworksTabSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import io.github.sjzeero.voltworks.registry.VoltworksCreativeTabs;

/** Lays out the Voltworks creative tab into titled sections and draws their banners. */
public final class CreativeTabSectionsClient {
    private static final int PER_ROW = 9;
    private static final int[][] OUTLINE = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final Map<Integer, VoltworksTabSections.Section> HEADER_ROWS = new HashMap<>();
    private static boolean ourTabOpen;
    private static int currentFirstRow;

    public static void setView(boolean open, int firstRow) { ourTabOpen = open; currentFirstRow = firstRow; }

    public static boolean isHeaderSlot(int slotIndex) {
        return ourTabOpen && slotIndex >= 0 && slotIndex < 45 && HEADER_ROWS.containsKey(currentFirstRow + slotIndex / PER_ROW);
    }

    public static boolean isOurTab(CreativeModeTab tab) {
        return tab != null && tab == VoltworksCreativeTabs.TAB.get();
    }

    /** Rebuilds the menu item list with an empty header row before each section and row padding after it. */
    public static void layout(List<ItemStack> menuItems, CreativeModeTab tab) {
        Map<Item, ItemStack> available = new LinkedHashMap<>();
        for (var st : tab.getDisplayItems()) available.putIfAbsent(st.getItem(), st);
        List<ItemStack> out = new ArrayList<>();
        HEADER_ROWS.clear();
        for (var section : VoltworksTabSections.sections()) {
            List<ItemStack> items = new ArrayList<>();
            for (var item : section.items()) { var st = available.remove(item); if (st != null) items.add(st); }
            if (items.isEmpty()) continue;
            HEADER_ROWS.put(out.size() / PER_ROW, section);
            for (int i = 0; i < PER_ROW; i++) out.add(ItemStack.EMPTY);
            out.addAll(items);
            while (out.size() % PER_ROW != 0) out.add(ItemStack.EMPTY);
        }
        out.addAll(available.values());
        menuItems.clear();
        menuItems.addAll(out);
    }

    public static void renderBanners(GuiGraphics g, int left, int top, int firstRow) {
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < 5; i++) {
            var section = HEADER_ROWS.get(firstRow + i);
            if (section == null) continue;
            int x = left + 8, y = top + 17 + i * 18, w = 162, h = 18;
            var tex = ResourceLocation.fromNamespaceAndPath(Voltworks.MOD_ID, "textures/gui/sections/" + section.id() + ".png");
            g.blit(tex, x, y, 0, 0, w, h, w, h);
            var title = Component.translatable(section.titleKey());
            int tx = x + 9, ty = y + 5;
            for (int[] o : OUTLINE) g.drawString(font, title, tx + o[0], ty + o[1], 0xFF0A0E12, false);
            g.drawString(font, title, tx, ty, section.text(), false);
        }
    }

    private CreativeTabSectionsClient() {}
}
