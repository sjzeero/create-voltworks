package io.github.sjzeero.voltworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import io.github.sjzeero.voltworks.servo.ServoBlock;
import io.github.sjzeero.voltworks.servo.ServoBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class ServoRenderer extends KineticBlockEntityRenderer<ServoBlockEntity> {
    public ServoRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ServoBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ServoBlock)) {
            return;
        }
        Direction facing = state.getValue(ServoBlock.FACING);
        SuperByteBuffer superBuffer = CachedBuffers.partialFacing(EEPartialModels.SERVO_SHAFT, state);
        float angleDeg = be.getInterpolatedAngle(partialTicks);
        float angleRad = (float) Math.toRadians(angleDeg);
        KineticBlockEntityRenderer.kineticRotationTransform(superBuffer, be, facing.getAxis(), angleRad, light);
        superBuffer.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
