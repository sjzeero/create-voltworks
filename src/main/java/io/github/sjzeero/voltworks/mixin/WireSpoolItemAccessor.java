package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.simulation.WireType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(value = WireSpoolItem.class, remap = false)
public interface WireSpoolItemAccessor {
    @Accessor("wireType")
    Supplier<WireType> voltworks$wireType();
}
