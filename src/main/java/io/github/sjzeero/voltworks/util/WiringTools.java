package io.github.sjzeero.voltworks.util;

import com.george_vi.electroenergetics.content.wire_spool.EmptySpoolItem;
import com.george_vi.electroenergetics.content.wire_spool.HangingWireSpoolItem;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.world.item.ItemStack;

public class WiringTools {
    /** Items whose own use should win over opening a block's screen or pressing a part, same list Electro Energetics uses. */
    public static boolean isWiringTool(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof WrenchItem || item instanceof WireSpoolItem || item instanceof HangingWireSpoolItem || item instanceof EmptySpoolItem;
    }
}
