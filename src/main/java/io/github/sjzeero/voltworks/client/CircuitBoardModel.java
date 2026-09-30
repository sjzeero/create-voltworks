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
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.client;

import com.mojang.math.Transformation;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.VoltworksClientConfig;
import io.github.sjzeero.voltworks.board.BoardBlock;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.board.BoardColor;
import io.github.sjzeero.voltworks.circuits.components.properties.Orientation;
import io.github.sjzeero.voltworks.circuits.schematic.Area;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.schematic.Point;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.sjzeero.voltworks.circuits.schematic.CircuitLayer.GRID_TO_GRID_SCALE;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CircuitBoardModel implements BakedModel {
    public static final ModelResourceLocation MODEL_ID = new ModelResourceLocation(Voltworks.asResource("circuit_board"), "");
    public static final ResourceLocation BASE_MODEL = Voltworks.asResource("block/circuit_board");

    public static final Material COPPER_SPRITE_ID = new Material(InventoryMenu.BLOCK_ATLAS, Voltworks.asResource("block/circuit_board_trace"));
    public static final Material PAD_SPRITE_ID = new Material(InventoryMenu.BLOCK_ATLAS, Voltworks.asResource("block/circuit_board_pad"));
    public static final Material SYMBOL_SPRITE_ID = new Material(InventoryMenu.BLOCK_ATLAS, Voltworks.asResource("block/circuit_board_symbol"));

    public static final ModelProperty<BoardBlockEntity> ENTITY = new ModelProperty<>();
    public static final ModelProperty<List<Area>> FRONT_LAYER = new ModelProperty<>();
    public static final ModelProperty<List<Point>> PADS = new ModelProperty<>();
    public static final ModelProperty<List<PlacedComponent>> COMPONENTS = new ModelProperty<>();

    private static final FaceBakery bakery = new FaceBakery();

    private final TextureAtlasSprite particleSprite;
    private final TextureAtlasSprite padSprite;
    private final TextureAtlasSprite copperSprite;
    private final TextureAtlasSprite symbolSprite;
    private final BakedModel baseModel;
    private final Map<BoardColor, BakedModel> plates;

    public static ResourceLocation plateModel(BoardColor color) {
        return color == BoardColor.GREEN ? BASE_MODEL : Voltworks.asResource("block/circuit_board_" + color.getSerializedName());
    }

    public CircuitBoardModel(Map<BoardColor, BakedModel> plates, TextureAtlasSprite padSprite, TextureAtlasSprite copperSprite, TextureAtlasSprite symbolSprite) {
        this.plates = plates;
        this.baseModel = plates.get(BoardColor.GREEN);
        this.padSprite = padSprite;
        this.copperSprite = copperSprite;
        this.symbolSprite = symbolSprite;
        this.particleSprite = baseModel.getParticleIcon();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        return plate(state).getQuads(state, face, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particleSprite;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        var be = level.getBlockEntity(pos);
        if(be instanceof BoardBlockEntity circuit) {
            // Emit components
            var schematic = circuit.getSchematic();
            return ModelData.builder()
                    .with(FRONT_LAYER, schematic.calculateAreas(CircuitSchematic.Layer.FRONT))
                    .with(PADS, schematic.pads().calculatePoints())
                    .with(COMPONENTS, List.copyOf(schematic.components()))
                    .with(ENTITY, circuit)
                    .build();
        }
        return modelData;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        var circuit = data.get(ENTITY);
        CircuitBoardModelQuads cache = null;
        if(circuit != null) {
            if(circuit.quads instanceof CircuitBoardModelQuads existing) {
                cache = existing;
                var cached = existing.getQuads(state, side, renderType);
                if (cached != null)
                    return cached;
            } else {
                cache = new CircuitBoardModelQuads();
                circuit.quads = cache;
            }
        }
        var quads = new ArrayList<>(plate(state).getQuads(state, side, rand, data, renderType));

        if(data.has(COMPONENTS)) {
            var components = data.get(COMPONENTS);
            if(VoltworksClientConfig.schematicMode()) {
                if(side == null) {
                    for(var placed : components) {
                        if(placed.destroyed)
                            continue;
                        var fp = placed.footprint();
                        for(var pixel : SchematicSymbols.symbol(placed.component, fp))
                            quads.add(emitPixel(placed.x + pixel.x(), placed.y + pixel.y(), pixel.color(), 3));
                    }
                }
            } else {
                for(var placed : components) {
                    var model = ComponentModels.getModel(placed);

                    IQuadTransformer transformer;
                    if(placed.has(Orientation.PROPERTY)) {
                        var orientation = placed.get(Orientation.PROPERTY);
                        var footprint = placed.component.footprint(placed);
                        transformer = QuadTransformers.applying(new Transformation(
                                new Vector3f(placed.x / 16f - 0.5f, 2 / 16f - 0.5f, placed.y / 16f - 0.5f),
                                new Quaternionf().rotationY(orientation.ordinal() * (float) Math.PI * 0.5f),
                                null, null
                        ));
                        if(orientation != Orientation.RIGHT) {
                            transformer = transformer.andThen(QuadTransformers.applying(new Transformation(
                                    switch(orientation) {
                                        case DOWN -> new Vector3f(footprint.getOriginalHeight() / 16f, 0, 0);
                                        case LEFT -> new Vector3f(footprint.getOriginalWidth() / 16f, 0, footprint.getOriginalHeight() / 16f);
                                        case UP -> new Vector3f(0, 0, footprint.getOriginalWidth() / 16f);
                                        case RIGHT -> throw new IllegalStateException();
                                    }, null, null, null
                            )));
                        }
                    } else {
                        transformer = QuadTransformers.applying(
                                new Transformation(new Vector3f(placed.x / 16f - 0.5f, 2 / 16f - 0.5f, placed.y / 16f - 0.5f),
                                        null, null, null)
                        );
                    }
                    if(placed.destroyed)
                        transformer = transformer.andThen(QuadTransformers.applyingColor(64, 64, 64));
                    quads.addAll(transformer.process(model.getQuads(state, side, rand, data, renderType)));
                }
            }
            if(side == null && VoltworksClientConfig.polarityMarks()) {
                for(var placed : components) {
                    if(placed.destroyed)
                        continue;
                    for(var pixel : SchematicSymbols.polarity(placed.footprint()))
                        quads.add(emitPixel(placed.x + pixel.x(), placed.y + pixel.y(), pixel.color(),
                                VoltworksClientConfig.schematicMode() ? 4 : 8));
                }
            }
        }
        if(side == null) {
            if (data.has(FRONT_LAYER)) {
                var areas = data.get(FRONT_LAYER);
                for (var area : areas) {
                    quads.add(emitTrace(area));
                }
            }
            if (data.has(PADS)) {
                var pads = data.get(PADS);
                for (var pad : pads) {
                    quads.add(emitPad(pad));
                }
            }
        }

        if(state != null) {
            var transformer = QuadTransformers.applying(new Transformation(
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()
                            .rotateY((float) Math.PI * BoardBlock.getAngleY(state) / 180f)
                            .rotateX((float) Math.PI * BoardBlock.getAngleX(state) / 180f),
                    null, null
            ));
            var trQuads = transformer.process(quads);
            if(cache != null)
                cache.putQuads(state, side, renderType, trQuads);
            return trQuads;
        }
        if(cache != null)
            cache.putQuads(state, side, renderType, quads);
        return quads;
    }

    private BakedModel plate(@Nullable BlockState state) {
        if(state == null || !state.hasProperty(BoardBlock.COLOR))
            return baseModel;
        return plates.getOrDefault(state.getValue(BoardBlock.COLOR), baseModel);
    }


    private BakedQuad emitPixel(int x, int y, int color, int height) {
        var quad = bakery.bakeQuad(
                new Vector3f(x - 8, height - 8, y - 8),
                new Vector3f(x + 1 - 8, height - 8, y + 1 - 8),
                new BlockElementFace(null, BlockElementFace.NO_TINT, "circuit_board_symbol",
                        new BlockFaceUV(new float[] { 0, 0, 1, 1 }, 0)),
                symbolSprite, Direction.UP, BlockModelRotation.X0_Y0, null, true
        );
        int a = color >>> 24;
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        return QuadTransformers.applyingColor(r, g, b, a).process(List.of(quad)).getFirst();
    }

    public BakedQuad emitTrace(Area area) {
        float x1 = (float) area.x1() / GRID_TO_GRID_SCALE;
        float y1 = (float) area.y1() / GRID_TO_GRID_SCALE;
        float x2 = (float) area.x2() / GRID_TO_GRID_SCALE;
        float y2 = (float) area.y2() / GRID_TO_GRID_SCALE;

        return bakery.bakeQuad(
                new Vector3f(x1 - 8, 2.05f - 8, y1 - 8),
                new Vector3f(x2 - 8, 2.05f - 8, y2 - 8),
                new BlockElementFace(
                        null, BlockElementFace.NO_TINT, "circuit_board_trace",
                        new BlockFaceUV(new float[] { x1, y1, x2, y2 }, 0)
                ),
                copperSprite,
                Direction.UP,
                BlockModelRotation.X0_Y0,
                null,
                true
        );
    }

    public BakedQuad emitPad(Point point) {
        float x1 = point.x(), y1 = point.y(), x2 = x1 + 1, y2 = y1 + 1;
        x1 /= GRID_TO_GRID_SCALE;
        x2 /= GRID_TO_GRID_SCALE;
        y1 /= GRID_TO_GRID_SCALE;
        y2 /= GRID_TO_GRID_SCALE;
        return bakery.bakeQuad(
                new Vector3f(x1 - 8, 2.05f - 8, y1 - 8),
                new Vector3f(x2 - 8, 2.05f - 8, y2 - 8),
                new BlockElementFace(
                        null, BlockElementFace.NO_TINT, "circuit_board_trace",
                        new BlockFaceUV(new float[] { x1, y1, x2, y2 }, 0)
                ),
                padSprite,
                Direction.UP,
                BlockModelRotation.X0_Y0,
                null,
                true
        );
    }
}
