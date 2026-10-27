package io.github.sjzeero.voltworks.util;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.config.CEEConfigs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Spool bookkeeping shared by Voltworks wires that are not plain Electro Energetics wires (redstone cable), done the
 * way Electro Energetics does it for its own spools: laying a wire uses up one full spool and gives the empty spool
 * back (unless {@code alternateWirePlacement} is on), and a removed wire drops {@code wireItemsPerSpool} wire items
 * (or the full spool when {@code alternateWirePlacement} is on), so wires go back into spools by crafting.
 */
public final class Spools {
    private Spools() {}

    /** Uses up one full spool from {@code held} after a wire was laid; creative players keep it. */
    public static void consume(Player player, ItemStack held) {
        if(player.isCreative())
            return;
        held.shrink(1);
        if(!CEEConfigs.server().alternateWirePlacement.get())
            player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
    }

    /** What one removed wire gives back, as in Electro Energetics' {@code removeAndDropConnection}. */
    public static List<ItemStack> refund(Item wire, Item spool) {
        return refund(wire, spool, CEEConfigs.server().alternateWirePlacement.get(), CEEConfigs.server().wiresPerSpool.get());
    }

    public static List<ItemStack> refund(Item wire, Item spool, boolean alternatePlacement, int wiresPerSpool) {
        List<ItemStack> out = new ArrayList<>();
        if(alternatePlacement) {
            out.add(new ItemStack(spool));
        } else {
            for(int i = 0; i < wiresPerSpool; i++)
                out.add(new ItemStack(wire));
        }
        return out;
    }
}
