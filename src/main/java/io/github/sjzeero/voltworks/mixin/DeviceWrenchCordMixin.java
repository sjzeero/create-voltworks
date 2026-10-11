package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.content.potentiometer.PotentiometerBlock;
import com.george_vi.electroenergetics.content.variac.VariacBlock;
import com.george_vi.electroenergetics.foundation.base.DirectionalKineticElectricBlock;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.voltworks.cord.CordWires;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** CEE's wrench pickup refunds per strand and would trip over the second strand; take cords off first. */
@Mixin(value = {SimpleElectricalDeviceBlock.class, DirectionalKineticElectricBlock.class, PotentiometerBlock.class, VariacBlock.class}, remap = false)
public abstract class DeviceWrenchCordMixin {
    @Inject(method = "onSneakWrenched", at = @At("HEAD"))
    private void voltworks$takeCords(BlockState state, UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if(context.getLevel() instanceof ServerLevel sl)
            CordWires.removeAll(sl, context.getClickedPos(), context.getPlayer());
    }
}
