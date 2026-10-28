package io.github.sjzeero.voltworks.client;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;

public final class ContraptionGoggles {
    private ContraptionGoggles() {
    }

    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Voltworks.asResource("contraption_goggles"), ContraptionGoggles::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null || !GogglesItem.isWearingGoggles(mc.player))
            return;
        try {
            var rays = ContraptionHandlerClient.getRayInputs(mc.player);
            var origin = rays.getFirst();
            var target = rays.getSecond();
            var area = new AABB(origin, target).inflate(16);
            double bestDistance = Double.MAX_VALUE;
            AbstractContraptionEntity bestEntity = null;
            net.minecraft.world.phys.BlockHitResult bestHit = null;
            for(var ref : ContraptionHandler.loadedContraptions.get(mc.level).values()) {
                var entity = ref.get();
                if(entity == null || entity.getContraption() == null || !entity.getBoundingBox().intersects(area))
                    continue;
                var hit = ContraptionHandlerClient.rayTraceContraption(origin, target, entity);
                if(hit == null)
                    continue;
                double d = entity.toGlobalVector(hit.getLocation(), 1f).distanceTo(origin);
                if(d <= bestDistance) {
                    bestDistance = d;
                    bestEntity = entity;
                    bestHit = hit;
                }
            }
            if(bestEntity == null)
                return;
            var be = bestEntity.getContraption().getOrCreateClientContraptionLazy().getBlockEntity(bestHit.getBlockPos());
            if(!(be instanceof BoardBlockEntity board))
                return;
            var tooltip = new ArrayList<Component>();
            ClientHooks.contraptionHit = bestHit.getLocation();
            try {
                board.addToGoggleTooltip(tooltip, mc.player.isShiftKeyDown());
            } finally {
                ClientHooks.contraptionHit = null;
            }
            if(tooltip.isEmpty())
                return;
            int w = graphics.guiWidth(), h = graphics.guiHeight();
            int x = w / 2 + AllConfigs.client().overlayOffsetX.get();
            int y = h / 2 + AllConfigs.client().overlayOffsetY.get();
            GuiGameElement.of(AllItems.GOGGLES.asStack()).at(x + 10, y - 16, 450).render(graphics);
            RemovedGuiUtils.drawHoveringText(graphics, tooltip, x, y, w, h, -1, mc.font);
        } catch(RuntimeException e) {
            ClientHooks.contraptionHit = null;
        }
    }
}
