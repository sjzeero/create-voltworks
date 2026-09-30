package io.github.sjzeero.voltworks.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import io.github.sjzeero.voltworks.wallwire.ContraptionWires;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the wires of moved blocks in the contraption instead of letting Electro Energetics drop them. */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin implements io.github.sjzeero.voltworks.wallwire.ContraptionWireView {
    @Unique
    private ListTag voltworks$wires = new ListTag();
    @Unique
    private java.util.List<ContraptionWires.Drawn> voltworks$drawn = java.util.List.of();

    @Override
    public java.util.List<ContraptionWires.Drawn> voltworks$drawn() {
        return voltworks$drawn;
    }

    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void voltworks$captureWires(Level world, BlockPos offset, CallbackInfo ci) {
        if(world instanceof ServerLevel level)
            voltworks$wires.addAll(ContraptionWires.capture(level, (Contraption) (Object) this, offset));
    }

    @Inject(method = "addBlocksToWorld", at = @At("TAIL"))
    private void voltworks$restoreWires(Level world, StructureTransform transform, CallbackInfo ci) {
        if(world instanceof ServerLevel level && !voltworks$wires.isEmpty()) {
            ListTag wires = voltworks$wires;
            voltworks$wires = new ListTag();
            ContraptionWires.restore(level, wires, transform);
        }
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void voltworks$writeWires(HolderLookup.Provider registries, boolean spawnPacket, CallbackInfoReturnable<CompoundTag> cir) {
        if(voltworks$wires.isEmpty())
            return;
        if(spawnPacket)
            cir.getReturnValue().put("VoltworksRender", ContraptionWires.renderData(voltworks$wires));
        else
            cir.getReturnValue().put("VoltworksWires", voltworks$wires.copy());
    }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void voltworks$readWires(Level world, CompoundTag nbt, boolean spawnData, CallbackInfo ci) {
        voltworks$wires = nbt.contains("VoltworksWires") ? nbt.getList("VoltworksWires", Tag.TAG_COMPOUND) : new ListTag();
        voltworks$drawn = nbt.contains("VoltworksRender") ? ContraptionWires.parseRender(nbt.getList("VoltworksRender", Tag.TAG_COMPOUND)) : java.util.List.of();
    }
}
