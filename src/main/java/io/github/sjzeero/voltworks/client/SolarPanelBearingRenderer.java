package io.github.sjzeero.voltworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import io.github.sjzeero.voltworks.solar.SolarPanelBearingBlock;
import io.github.sjzeero.voltworks.solar.SolarPanelBearingBlockEntity;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create: Power Grid (patryk3211, Apache-2.0): half shaft at the back and the rotating rotor plate. */
public class SolarPanelBearingRenderer extends KineticBlockEntityRenderer<SolarPanelBearingBlockEntity> {
    public SolarPanelBearingRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SolarPanelBearingBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof SolarPanelBearingBlock)) return;
        Direction facing = state.getValue(SolarPanelBearingBlock.FACING);
        var solid = buffer.getBuffer(RenderType.solid());

        var shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, facing.getOpposite());
        standardKineticRotationTransform(shaft, be, light).renderInto(ms, solid);

        SuperByteBuffer rotor = CachedBuffers.partial(VoltworksPartialModels.SOLAR_PANEL_BEARING_ROTOR, state);
        float angle = be.getInterpolatedAngle(partialTicks - 1);
        kineticRotationTransform(rotor, be, facing.getAxis(), (float) (angle / 180f * Math.PI), light);
        if (facing.getAxis().isHorizontal())
            rotor.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        if (facing.getAxis().isVertical())
            rotor.rotateCentered(AngleHelper.rad((facing == Direction.UP ? -90f : 90f) + AngleHelper.verticalAngle(facing)), Direction.EAST);
        else
            rotor.rotateCentered(AngleHelper.rad(AngleHelper.verticalAngle(facing)), Direction.EAST);
        rotor.rotateCentered(AngleHelper.rad(AngleHelper.verticalAngle(facing)), Direction.EAST);
        rotor.renderInto(ms, solid);
    }
}
