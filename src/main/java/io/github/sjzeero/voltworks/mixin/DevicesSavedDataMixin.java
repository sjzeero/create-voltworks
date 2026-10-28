package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.wallwire.FrozenDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DevicesSavedData.class, remap = false)
public abstract class DevicesSavedDataMixin {
    @Shadow @Final public ServerLevel level;

    @Inject(method = "removeDevice", at = @At("HEAD"), cancellable = true, require = 0)
    private void voltworks$keepFrozen(BlockPos pos, CallbackInfoReturnable<SimulatedDevice> cir) {
        try {
            if(level != null && FrozenDevices.get(level).isFrozen(pos))
                cir.setReturnValue(null);
        } catch(Throwable t) {
            Voltworks.LOGGER.warn("voltworks: frozen device check skipped: {}", String.valueOf(t));
        }
    }
}
