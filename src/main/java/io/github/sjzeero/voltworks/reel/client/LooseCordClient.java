package io.github.sjzeero.voltworks.reel.client;

import com.george_vi.electroenergetics.simulation.WireType;
import io.github.sjzeero.voltworks.cord.LooseCords;
import io.github.sjzeero.voltworks.cord.Outlets;
import io.github.sjzeero.voltworks.reel.CableEndEntity;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class LooseCordClient {
    private LooseCordClient() {}

    public record Anchor(BlockPos pos, int supply) {}

    public static boolean isLooseCord(ItemStack stack) {
        return LooseCords.isLooseCord(stack);
    }

    private static @Nullable CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains("LooseCord") ? null : data.copyTag();
    }

    public static @Nullable Anchor anchor(ItemStack stack) {
        CompoundTag t = tag(stack);
        if (t == null || !t.contains("OtherSupply") || t.getInt("OtherSupply") < 0)
            return null;
        return new Anchor(BlockPos.of(t.getLong("OtherPos")), t.getInt("OtherSupply"));
    }

    static @Nullable ReelClientSource heldSource(Level level, Player holder, ItemStack stack, float pt) {
        CompoundTag t = tag(stack);
        if (t == null || !t.hasUUID("LooseCord"))
            return null;
        WireType type = VoltworksWireTypes.COPPER_CORD.get();
        double reach = t.getDouble("Reach");
        Anchor a = anchor(stack);
        if (a != null) {
            if (!level.isLoaded(a.pos()))
                return null;
            Vec3 from = LooseCords.plugWorld(level, a.pos(), a.supply());
            Vec3 out = WallSpace.rotate(WallSpace.renderPose(level, a.pos(), pt), Outlets.cordDir(level.getBlockState(a.pos()), a.supply()));
            return new ReelClientSource(from, out, type, reach, a.pos());
        }
        UUID id = t.getUUID("LooseCord");
        int end = t.getInt("End");
        AABB box = holder.getBoundingBox().inflate(reach + 8);
        for (CableEndEntity e : level.getEntitiesOfClass(CableEndEntity.class, box, e -> id.equals(e.cordId()) && e.end() != end)) {
            Vec3[] back = CableEndRenderer.cordBack(e, pt);
            return new ReelClientSource(back[0], back[1], type, reach, e.blockPosition());
        }
        return null;
    }

    record ReelClientSource(Vec3 from, Vec3 fromOut, WireType type, double length, BlockPos skip) {}
}
