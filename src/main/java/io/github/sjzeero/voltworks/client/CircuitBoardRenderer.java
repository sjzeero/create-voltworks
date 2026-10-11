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
package io.github.sjzeero.voltworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import io.github.sjzeero.voltworks.board.BoardBlock;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class CircuitBoardRenderer extends SafeBlockEntityRenderer<BoardBlockEntity> {
    public CircuitBoardRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(BoardBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        var components = be.getSchematic().components();
        if(components.isEmpty())
            return;

        var state = be.getBlockState();
        var stack = TransformStack.of(ms);
        for(var placed : components) {
            if(placed.destroyed)
                continue;
            var renderer = ComponentRenderers.get(placed.component);
            if(renderer == null)
                continue;
            stack.pushPose()
                    .center()
                    .rotateYDegrees(BoardBlock.getAngleY(state))
                    .rotateXDegrees(BoardBlock.getAngleX(state))
                    .uncenter()
                    .translate(placed.x / 16f, 2 / 16f, placed.y / 16f);
            renderer.render(be, placed, partialTicks, ms, bufferSource, light, overlay);
            stack.popPose();
        }
    }
}
