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

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.NotNull;

import io.github.sjzeero.voltworks.board.BoardColor;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Function;
import io.github.sjzeero.voltworks.Voltworks;

public class UnbakedCircuitBoardModel implements IUnbakedGeometry<UnbakedCircuitBoardModel> {
    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        for(var color : BoardColor.values())
            modelGetter.apply(CircuitBoardModel.plateModel(color)).resolveParents(modelGetter);
    }

    @Override
    public BakedModel bake(@NotNull IGeometryBakingContext context, ModelBaker bakery, Function<Material, TextureAtlasSprite> textureProvider, @NotNull ModelState state, @NotNull ItemOverrides overrides) {
        var plates = new EnumMap<BoardColor, BakedModel>(BoardColor.class);
        for(var color : BoardColor.values())
            plates.put(color, Objects.requireNonNull(bakery.bake(CircuitBoardModel.plateModel(color), state)));
        return new CircuitBoardModel(
                plates,
                textureProvider.apply(CircuitBoardModel.PAD_SPRITE_ID),
                textureProvider.apply(CircuitBoardModel.COPPER_SPRITE_ID),
                textureProvider.apply(CircuitBoardModel.SYMBOL_SPRITE_ID)
        );
    }
}
