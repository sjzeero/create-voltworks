package io.github.sjzeero.voltworks.util;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.config.CEEConfigs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Spools {
    private Spools() {}

    public static void consume(Player player, ItemStack held) {
        if(player.isCreative())
            return;
        held.shrink(1);
        if(!CEEConfigs.server().alternateWirePlacement.get())
            player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
    }

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
