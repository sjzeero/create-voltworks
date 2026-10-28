package io.github.sjzeero.voltworks.motor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class PGMotorRenderer<T extends KineticBlockEntity> extends KineticBlockEntityRenderer<T> {
    public PGMotorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof AbstractPGMotorBlock)) return;
        var sbb = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, state.getValue(AbstractPGMotorBlock.FACING));
        standardKineticRotationTransform(sbb, be, light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
