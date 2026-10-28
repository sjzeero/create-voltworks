package io.github.sjzeero.voltworks.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.wallwire.ContraptionWireView;
import io.github.sjzeero.voltworks.wallwire.ContraptionWires;
import io.github.sjzeero.voltworks.wallwire.FrozenDevices;
import java.util.List;
import java.util.UUID;
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

@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin implements ContraptionWireView {
    @Unique
    private ListTag voltworks$wires;
    @Unique
    private List<ContraptionWires.Drawn> voltworks$drawn;
    @Unique
    private UUID voltworks$frozen;

    @Unique
    private ListTag voltworks$wireList() {
        ListTag l = voltworks$wires;
        if(l == null)
            voltworks$wires = l = new ListTag();
        return l;
    }

    @Unique
    private static void voltworks$warn(String where, Throwable t) {
        Voltworks.LOGGER.warn("voltworks: contraption wire {} skipped: {}", where, String.valueOf(t));
    }

    @Override
    public UUID voltworks$frozenId() {
        return voltworks$frozen;
    }

    @Override
    public List<ContraptionWires.Drawn> voltworks$drawn() {
        List<ContraptionWires.Drawn> d = voltworks$drawn;
        return d == null ? List.of() : d;
    }

    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void voltworks$captureWires(Level world, BlockPos offset, CallbackInfo ci) {
        try {
            if(world instanceof ServerLevel level) {
                ListTag captured = ContraptionWires.capture(level, (Contraption) (Object) this, offset);
                voltworks$wireList().addAll(captured);
                try {
                    if(voltworks$frozen == null)
                        voltworks$frozen = FrozenDevices.get(level).freeze(level, (Contraption) (Object) this, offset, captured);
                } catch(Throwable t) {
                    voltworks$warn("freeze", t);
                }
            }
        } catch(Throwable t) {
            voltworks$warn("capture", t);
        }
    }

    @Inject(method = "addBlocksToWorld", at = @At("TAIL"))
    private void voltworks$restoreWires(Level world, StructureTransform transform, CallbackInfo ci) {
        try {
            ListTag wires = voltworks$wires;
            if(wires != null && !wires.isEmpty() && world instanceof ServerLevel level) {
                voltworks$wires = new ListTag();
                ContraptionWires.restore(level, wires, transform);
            }
        } catch(Throwable t) {
            voltworks$warn("restore", t);
        }
        try {
            UUID id = voltworks$frozen;
            if(id != null && world instanceof ServerLevel level) {
                voltworks$frozen = null;
                FrozenDevices.get(level).thaw(level, id, transform);
            }
        } catch(Throwable t) {
            voltworks$warn("thaw", t);
        }
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void voltworks$writeWires(HolderLookup.Provider registries, boolean spawnPacket, CallbackInfoReturnable<CompoundTag> cir) {
        try {
            ListTag wires = voltworks$wires;
            CompoundTag out = cir.getReturnValue();
            if(out != null && !spawnPacket && voltworks$frozen != null)
                out.putString("VoltworksFrozen", voltworks$frozen.toString());
            if(wires == null || wires.isEmpty() || out == null)
                return;
            if(spawnPacket)
                out.put("VoltworksRender", ContraptionWires.renderData(wires));
            else
                out.put("VoltworksWires", wires.copy());
        } catch(Throwable t) {
            voltworks$warn("write", t);
        }
    }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void voltworks$readWires(Level world, CompoundTag nbt, boolean spawnData, CallbackInfo ci) {
        try {
            voltworks$frozen = nbt != null && nbt.contains("VoltworksFrozen") ? UUID.fromString(nbt.getString("VoltworksFrozen")) : null;
            voltworks$wires = nbt != null && nbt.contains("VoltworksWires") ? nbt.getList("VoltworksWires", Tag.TAG_COMPOUND) : new ListTag();
            voltworks$drawn = nbt != null && nbt.contains("VoltworksRender") ? ContraptionWires.parseRender(nbt.getList("VoltworksRender", Tag.TAG_COMPOUND)) : List.of();
        } catch(Throwable t) {
            voltworks$wires = new ListTag();
            voltworks$drawn = List.of();
            voltworks$warn("read", t);
        }
    }
}
