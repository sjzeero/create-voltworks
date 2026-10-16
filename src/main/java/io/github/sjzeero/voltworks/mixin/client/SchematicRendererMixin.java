package io.github.sjzeero.voltworks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import io.github.sjzeero.voltworks.wallwire.client.SchematicWallRender;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws wall wires of the schematic hologram along their routes. Runs after Electro Energetics' own hologram wires
 * (higher priority number), whose straight wire for these connections is skipped through {@link SchematicWallRender}.
 */
@Mixin(value = SchematicRenderer.class, remap = false, priority = 2000)
public abstract class SchematicRendererMixin {
    @Shadow
    @Final
    protected SchematicLevel schematic;

    @Inject(method = "render", at = @At("HEAD"))
    private void voltworks$hideStraight(PoseStack ms, SuperRenderTypeBuffer buffer, CallbackInfo ci) {
        try {
            SchematicWallRender.begin(schematic);
        } catch(Throwable t) {
            SchematicWallRender.end();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void voltworks$drawRoutes(PoseStack ms, SuperRenderTypeBuffer buffer, CallbackInfo ci) {
        try {
            SchematicWallRender.draw(schematic, ms, buffer);
        } catch(Throwable t) {
            io.github.sjzeero.voltworks.Voltworks.LOGGER.warn("voltworks: schematic wall wire draw skipped: {}", String.valueOf(t));
        } finally {
            SchematicWallRender.end();
        }
    }
}
