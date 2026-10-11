package io.github.sjzeero.voltworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import io.github.sjzeero.voltworks.servo.ServoBlock;
import io.github.sjzeero.voltworks.servo.ServoBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** PG servo renderer: half shaft on the FACING side. */
public class ServoRenderer extends KineticBlockEntityRenderer<ServoBlockEntity> {
    public ServoRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ServoBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ServoBlock)) return;
        var sbb = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, state.getValue(ServoBlock.FACING));
        standardKineticRotationTransform(sbb, be, light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
