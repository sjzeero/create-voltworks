/*
 * Copyright 2025 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.editor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import io.github.sjzeero.voltworks.client.VoltworksPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.Direction;

public class CircuitDesignTableRenderer extends SafeBlockEntityRenderer<CircuitDesignTableBlockEntity> {
    public CircuitDesignTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(CircuitDesignTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        float power = Mth.clamp((be.power() - 15) / 15, 0, 1);
        var state = be.getBlockState();
        var glow = CachedBuffers.partial(VoltworksPartialModels.CIRCUIT_TABLE_GLOW, state);
        int a = (int) (power * 192);
        glow.rotateYCenteredDegrees(state.getValue(CircuitDesignTableBlock.HORIZONTAL_FACING).getAxis() == Direction.Axis.X ? 90 : 0)
                .disableDiffuse()
                .color(a, a, a, 255)
                .renderInto(ms, bufferSource.getBuffer(RenderTypes.additive()));
    }
}
