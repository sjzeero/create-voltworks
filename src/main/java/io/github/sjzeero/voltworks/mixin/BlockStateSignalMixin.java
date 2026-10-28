package io.github.sjzeero.voltworks.mixin;

import io.github.sjzeero.voltworks.redstone.RedstoneLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSignalMixin {
    @Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
    private void voltworks$cableSignal(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        int extra = RedstoneLinks.outputSignal(level, pos, direction);
        if(extra > cir.getReturnValue())
            cir.setReturnValue(extra);
    }

    @Inject(method = "getDirectSignal", at = @At("RETURN"), cancellable = true)
    private void voltworks$cableDirectSignal(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        int extra = RedstoneLinks.outputSignal(level, pos, direction);
        if(extra > cir.getReturnValue())
            cir.setReturnValue(extra);
    }
}
