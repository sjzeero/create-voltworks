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

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.VoltworksClientConfig;
import io.github.sjzeero.voltworks.circuits.components.Component;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import static io.github.sjzeero.voltworks.circuits.schematic.CircuitLayer.GRID_TO_GRID_SCALE;
import static io.github.sjzeero.voltworks.client.CircuitSchematicRender.COLOR_COMPONENT_OUTLINE;
import static io.github.sjzeero.voltworks.client.CircuitSchematicRender.COLOR_TERMINAL;

public final class FootprintRender {
    private static final ResourceLocation ARROWS = Voltworks.texture("gui/circuit_arrows");
    private static final int MARK_OUTLINE = 0xFF10141C;

    private FootprintRender() {
    }

    private static void renderPads(ComponentFootprint fp, @NotNull GuiGraphics ctx, int x, int y) {
        var ms = ctx.pose();
        ms.pushPose();
        ms.scale(0.25f, 0.25f, 0.25f);
        for(var point : fp.getPads().keySet()) {
            int x1 = (point.x() + x) * 4;
            int y1 = (point.y() + y) * 4;
            ctx.fill(x1 + 1, y1 + 1, x1 + 3, y1 + 3, COLOR_TERMINAL);
        }
        ms.popPose();
    }

    private static void renderMarks(ComponentFootprint fp, @NotNull GuiGraphics ctx, int x, int y) {
        var ms = ctx.pose();
        ms.pushPose();
        ms.scale(0.125f, 0.125f, 1);
        for(var mark : SchematicSymbols.marks(fp)) {
            int x1 = (mark.getKey().x() + x) * 8;
            int y1 = (mark.getKey().y() + y) * 8;
            boolean positive = mark.getValue();
            ctx.fill(x1, y1 + 2, x1 + 8, y1 + 6, MARK_OUTLINE);
            if(positive)
                ctx.fill(x1 + 2, y1, x1 + 6, y1 + 8, MARK_OUTLINE);
            ctx.fill(x1 + 1, y1 + 3, x1 + 7, y1 + 5, positive ? SchematicSymbols.POSITIVE : SchematicSymbols.NEGATIVE);
            if(positive)
                ctx.fill(x1 + 3, y1 + 1, x1 + 5, y1 + 7, SchematicSymbols.POSITIVE);
        }
        ms.popPose();
    }

    public static void render(ComponentFootprint fp, @NotNull GuiGraphics ctx, @NotNull Component component, int x, int y, boolean hovering) {
        render(fp, ctx, component, x, y, hovering, false);
    }

    public static void render(ComponentFootprint fp, @NotNull GuiGraphics ctx, @NotNull Component component, int x, int y, boolean hovering, boolean editor) {
        var ms = ctx.pose();
        int width = fp.getWidth();
        int height = fp.getHeight();
        if(fp.hasOutline()) {
            ms.pushPose();
            ms.scale(0.5f, 0.5f, 0.5f);
            ctx.renderOutline(x * 2, y * 2, width * GRID_TO_GRID_SCALE * 2, height * GRID_TO_GRID_SCALE * 2, COLOR_COMPONENT_OUTLINE);
            ms.popPose();
        }
        renderPads(fp, ctx, x, y);

        if(VoltworksClientConfig.schematicMode()) {
            renderPixels(SchematicSymbols.symbol(component, fp), ctx, x, y);
        } else if(fp.hasItem() && !hovering) {
            ms.pushPose();
            final int maxSize = 2;
            var scale = Math.min(Math.min(width, height), maxSize) / 16f * GRID_TO_GRID_SCALE;
            if(width > maxSize && height > maxSize) {
                ms.translate((width - maxSize) * 0.5f, (height - maxSize) * 0.5f, 0);
            } else if(width > height) {
                ms.translate((width - height) * 0.5f, 0, 0);
            } else if(height > width) {
                ms.translate(0, (height - width) * 0.5f, 0);
            }
            ms.scale(scale, scale, scale);
            var stack = fp.getRenderedStack(component);
            if(stack != null)
                ctx.renderItem(stack, (int) (x / scale), (int) (y / scale));
            ms.popPose();
        }
        if(editor)
            renderMarks(fp, ctx, x, y);
        else if(VoltworksClientConfig.polarityMarks())
            renderPixels(SchematicSymbols.polarity(fp), ctx, x, y);

        var arrow = fp.getArrow();
        if(arrow != null) {
            ms.pushPose();
            int u = (arrow.ordinal() % 2) * 8;
            int v = (arrow.ordinal() / 2) * 8;
            ms.translate(x + (width * GRID_TO_GRID_SCALE * 0.5f), y + (height * GRID_TO_GRID_SCALE * 0.5f), 0);
            switch(arrow) {
                case RIGHT -> ms.translate(width / 2, 0, 0);
                case LEFT -> ms.translate(-width / 2, 0, 0);
                case DOWN -> ms.translate(0, height / 2, 0);
                case UP -> ms.translate(0, -height / 2, 0);
            }
            ms.scale(0.25f, 0.25f, 1);
            ms.translate(-4, -4, 0);
            ctx.blit(ARROWS, 0, 0, u, v, 8, 8, 16, 16);
            ms.popPose();
        }
    }


    private static void renderPixels(java.util.List<SchematicSymbols.Pixel> pixels, GuiGraphics ctx, int x, int y) {
        for(var pixel : pixels)
            ctx.fill(x + pixel.x(), y + pixel.y(), x + pixel.x() + 1, y + pixel.y() + 1, pixel.color());
    }

    public static void renderPadIndices(ComponentFootprint fp, @NotNull GuiGraphics ctx, @NotNull Font font, int x, int y) {
        var ms = ctx.pose();
        ms.pushPose();
        int scale = 12;
        ms.translate(0.5f, 0.5f, 100);
        ms.scale(1.0f / scale, 1.0f / scale, 1.0f);
        for(var entry : fp.getPads().entrySet()) {
            var point = entry.getKey();
            int x1 = (point.x() + x) * scale;
            int y1 = (point.y() + y) * scale;
            var text = entry.getValue().shortText();
            if(text == null)
                continue;
            int width = font.width(text);
            ctx.drawString(font, text, x1 - width / 2, y1 - 4, -1, false);
        }
        ms.popPose();
    }
}
