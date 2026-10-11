/*
 * Copyright 2026 patryk3211
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

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import io.github.sjzeero.voltworks.Voltworks;

public class CircuitBoardModelQuads {
    private final Map<CacheKey, List<BakedQuad>> quads = new Object2ObjectArrayMap<>(8);
    public BlockState state;
    private boolean schematic;

    @Nullable
    public List<BakedQuad> getQuads(BlockState state, @Nullable Direction side, @Nullable RenderType type) {
        if(this.state != state || schematic != io.github.sjzeero.voltworks.VoltworksClientConfig.schematicMode())
            return null;
        return quads.get(new CacheKey(side, type));
    }

    public void putQuads(BlockState state, @Nullable Direction side, @Nullable RenderType type, List<BakedQuad> quads) {
        boolean mode = io.github.sjzeero.voltworks.VoltworksClientConfig.schematicMode();
        if(this.state != state || schematic != mode) {
            this.state = state;
            this.schematic = mode;
            this.quads.clear();
        }
        this.quads.put(new CacheKey(side, type), quads);
    }

    private record CacheKey(@Nullable Direction side, @Nullable RenderType type) { }
}
