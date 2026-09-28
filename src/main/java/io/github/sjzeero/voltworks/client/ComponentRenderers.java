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
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
package io.github.sjzeero.voltworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.circuits.components.*;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ComponentRenderers {
    @FunctionalInterface
    public interface Renderer {
        void render(BoardBlockEntity be, PlacedComponent placed, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay);
    }

    private static final Map<Class<?>, Renderer> RENDERERS = new HashMap<>();

    static {
        RENDERERS.put(LightBulbComponent.class, ComponentRenderers::lightBulb);
        RENDERERS.put(BarretterTubeComponent.class, (be, placed, pt, ms, buf, light, overlay) -> {
            if(placed.customData instanceof Component.FloatPair data)
                glow(be.getBlockState(), EEPartialModels.BARRETTER_GLOW, (int) (data.lerped(pt) * 64), ms, buf);
        });
        RENDERERS.put(ElectronTubeComponent.class, (be, placed, pt, ms, buf, light, overlay) -> {
            if(placed.customData instanceof ElectronTubeComponent.RenderData data)
                glow(be.getBlockState(), EEPartialModels.ELECTRON_TUBE_GLOW, (int) (Mth.lerp(pt, data.prev, data.current) * 160), ms, buf);
        });
        RENDERERS.put(PentodeComponent.class, (be, placed, pt, ms, buf, light, overlay) -> {
            if(placed.customData instanceof ElectronTubeComponent.RenderData data)
                glow(be.getBlockState(), EEPartialModels.PENTODE_GLOW, (int) (Mth.lerp(pt, data.prev, data.current) * 170), ms, buf);
        });
        RENDERERS.put(ThyratronComponent.class, (be, placed, pt, ms, buf, light, overlay) -> {
            if(placed.customData instanceof ThyratronComponent.RenderData data)
                glow(be.getBlockState(), EEPartialModels.THYRATRON_GLOW, (int) (Mth.lerp(pt, data.heaterPrev, data.heater) * 82), ms, buf);
        });
        RENDERERS.put(PotentiometerComponent.class, (be, placed, pt, ms, buf, light, overlay) -> {
            var angle = 135 - 135 * 2 * (placed.get(PotentiometerComponent.VALUE) / 100.0f);
            CachedBuffers.partial(EEPartialModels.POTENTIOMETER_KNOB, be.getBlockState())
                    .translate(2.5 / 16, 0, 2.5 / 16)
                    .rotateYDegrees(angle)
                    .translate(-2.5 / 16, 0, -2.5 / 16)
                    .light(light)
                    .renderInto(ms, buf.getBuffer(RenderType.solid()));
        });
        RENDERERS.put(NeonBulbComponent.class, ComponentRenderers::neon);
        RENDERERS.put(LabelComponent.class, ComponentRenderers::label);
        RENDERERS.put(ModularDisplayComponent.class, ComponentRenderers::modularDisplay);
    }

    private ComponentRenderers() {
    }

    public static Renderer get(Component component) {
        if(component instanceof GaugeComponent)
            return ComponentRenderers::gauge;
        return RENDERERS.get(component.getClass());
    }

    @SuppressWarnings("unchecked")
    private static float[] lerpState(BoardBlockEntity be, PlacedComponent placed, int size) {
        if(!(be.clientState instanceof Map))
            be.clientState = new IdentityHashMap<PlacedComponent, float[]>();
        var map = (Map<PlacedComponent, float[]>) be.clientState;
        return map.computeIfAbsent(placed, p -> new float[size]);
    }

    private static float approach(float[] state, float target, float speed, long gameTime) {
        if(state[2] != gameTime) {
            state[2] = gameTime;
            state[0] = state[1];
            state[1] += Mth.clamp(target - state[1], -speed, speed);
        }
        return state[1];
    }

    private static void glow(BlockState state, dev.engine_room.flywheel.lib.model.baked.PartialModel model, int a, PoseStack ms, MultiBufferSource buf) {
        if(a <= 0)
            return;
        CachedBuffers.partial(model, state)
                .disableDiffuse()
                .color(a, a, a, 255)
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buf.getBuffer(RenderTypes.additive()));
    }

    private static void lightBulb(BoardBlockEntity be, PlacedComponent placed, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        var glowModel = EEPartialModels.LIGHT_BULB_GLOW_DYED;
        var bulbModel = EEPartialModels.LIGHT_BULB_BULB_DYED;
        if(placed.get(LightBulbComponent.COLOR) == DyeColor.WHITE) {
            glowModel = EEPartialModels.LIGHT_BULB_GLOW;
            bulbModel = EEPartialModels.LIGHT_BULB_BULB;
        }
        var color = placed.get(LightBulbComponent.COLOR).getTextureDiffuseColor();
        var red = (color >> 16) & 0xFF;
        var green = (color >> 8) & 0xFF;
        var blue = color & 0xFF;

        CachedBuffers.partial(bulbModel, be.getBlockState())
                .color(red, green, blue, 255)
                .light(light)
                .renderInto(ms, buf.getBuffer(RenderType.cutoutMipped()));

        if(!(placed.customData instanceof Component.FloatPair temps))
            return;
        float x = temps.lerped(pt);
        int a = (int) (x * 128);
        if(a == 0)
            return;
        var center = 1.5f / 16f;
        CachedBuffers.partial(glowModel, be.getBlockState())
                .disableDiffuse()
                .color((int) (red * x * 128 / 256), (int) (green * x * 128 / 256), (int) (blue * x * 128 / 256), 255)
                .light(LightTexture.FULL_BRIGHT)
                .translate(center, center, center)
                .rotateYDegrees(placed.get(OrientableComponent.ORIENTATION).ordinal() * 90)
                .translateBack(center, center, center)
                .renderInto(ms, buf.getBuffer(RenderTypes.additive()));
    }

    private static void gauge(BoardBlockEntity be, PlacedComponent placed, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        var gauge = (GaugeComponent) placed.component;
        var max = gauge.getMaxValue(placed);
        float target = max == 0 ? 0 : Mth.clamp(Math.abs(placed.get(GaugeComponent.READING)) / max, 0, 1);
        var state = lerpState(be, placed, 3);
        long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        approach(state, target, 0.1f, time);
        var value = Mth.lerp(pt, state[0], state[1]);
        CachedBuffers.partial(EEPartialModels.COMPONENT_GAUGE_NEEDLE, be.getBlockState())
                .translate(2.5 / 16, 0, 2.5 / 16)
                .rotateYDegrees(placed.get(OrientableComponent.ORIENTATION).ordinal() * 90)
                .translate(-1 / 16f, 0, 1 / 16f)
                .rotateYDegrees(-90 * value)
                .translate(-1.5 / 16, 0, -3.5 / 16)
                .light(light)
                .renderInto(ms, buf.getBuffer(RenderType.solid()));
    }

    private static void neon(BoardBlockEntity be, PlacedComponent placed, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        CachedBuffers.partial(EEPartialModels.NEON_TUBE_BULB, be.getBlockState())
                .light(light)
                .renderInto(ms, buf.getBuffer(RenderType.translucent()));

        var vertical = placed.get(NeonBulbComponent.VERTICAL);
        var state = lerpState(be, placed, 3);
        long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        approach(state, placed.get(NeonBulbComponent.LIT) ? 1 : 0, 0.34f, time);
        float x = Mth.lerp(pt, state[0], state[1]);
        if(x <= 0)
            return;
        var color = placed.get(NeonBulbComponent.COLOR).getTextureDiffuseColor();
        int r = (int) (((color >> 16) & 0xFF) * x * 128 / 256);
        int g = (int) (((color >> 8) & 0xFF) * x * 128 / 256);
        int b = (int) ((color & 0xFF) * x * 128 / 256);
        var partial = vertical ? EEPartialModels.NEON_TUBE_VERTICAL_GLOW : EEPartialModels.NEON_TUBE_GLOW;
        var yRotation = vertical ? 0 : 1;
        var center = 1 / 16f;
        var orientation = placed.get(OrientableComponent.ORIENTATION);
        CachedBuffers.partial(partial, be.getBlockState())
                .disableDiffuse()
                .color(r, g, b, 255)
                .light(LightTexture.FULL_BRIGHT)
                .translate(center, center, center)
                .rotateYDegrees(yRotation * orientation.ordinal() * 90)
                .translateBack(center, center, center)
                .renderInto(ms, buf.getBuffer(RenderTypes.additive()));
    }

    private static void label(BoardBlockEntity be, PlacedComponent placed, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        int size = placed.get(LabelComponent.SIZE);
        for(int i = 1; i < size; i++) {
            var tint = LabelComponent.SEGMENT_TINT[i];
            CachedBuffers.partial(EEPartialModels.LABEL, be.getBlockState())
                    .translate((1 / 16f) * (float) i, 0, 0)
                    .light(light)
                    .color(tint, tint, tint, 255)
                    .renderInto(ms, buf.getBuffer(RenderType.solid()));
        }
        ms.pushPose();
        var msr = TransformStack.of(ms);
        msr.center();
        msr.rotateXDegrees(90);
        msr.uncenter();
        ms.translate(0, 0, 1f - (1 / 512f));
        ms.scale(1 / 128f, 1 / 128f, 1 / 128f);
        Font font = Minecraft.getInstance().font;
        int color = placed.get(LabelComponent.COLOR).getTextColor();
        font.drawInBatch(font.plainSubstrByWidth(placed.get(Component.LABEL), 8 * size), 0, 0, color, false, ms.last().pose(), buf, Font.DisplayMode.NORMAL, 0, light);
        ms.popPose();
    }

    private static void modularDisplay(BoardBlockEntity be, PlacedComponent placed, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        final float pixel = 1 / 16f;
        var module = placed.get(ModularDisplayComponent.CURRENT_MODULE);
        ms.pushPose();
        ms.translate(0, 6f / 16f + 0.0001f, 0);
        var matrix = ms.last().pose();
        float frame = placed.get(ModularDisplayComponent.INDEX);
        if(placed.has(ModularDisplayComponent.HALF_CLICK) && placed.get(ModularDisplayComponent.HALF_CLICK))
            frame -= .5f;
        var texture = Voltworks.asResource("textures/block/modular_display/" + (placed.get(ModularDisplayComponent.REMOVE_BLANKING_PAGE)
                ? module.getDisplayTexture() + "noblank" : module.getDisplayTexture()) + ".png");
        float x = pixel, z = .75f * pixel, w = 2f * pixel, h = 2.5f * pixel;
        float uMin = (frame * 6f) / module.getSpriteWidth();
        float uMax = (frame * 6f + 5f) / module.getSpriteWidth();
        float vMin = 0f, vMax = 7f / 16f;
        int rgb = placed.get(ModularDisplayComponent.CURRENT_COLOR).getTextureDiffuseColor();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        var vc = buf.getBuffer(RenderType.text(texture));
        vc.addVertex(matrix, x + w, 0f, z).setColor(r, g, b, 255).setUv(uMax, vMin).setOverlay(overlay).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(matrix, x, 0f, z).setColor(r, g, b, 255).setUv(uMin, vMin).setOverlay(overlay).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(matrix, x, 0f, z + h).setColor(r, g, b, 255).setUv(uMin, vMax).setOverlay(overlay).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(matrix, x + w, 0f, z + h).setColor(r, g, b, 255).setUv(uMax, vMax).setOverlay(overlay).setLight(light).setNormal(0f, 0f, 1f);
        ms.popPose();
    }
}
