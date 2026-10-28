package io.github.sjzeero.voltworks.mixin;

import io.github.sjzeero.voltworks.wallwire.SchematicWires;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StructureTemplate.class, remap = false)
public abstract class StructureTemplateMixin {
    @Unique
    private ListTag voltworks$walls;

    @Unique
    private static void voltworks$warn(String where, Throwable t) {
        io.github.sjzeero.voltworks.Voltworks.LOGGER.warn("voltworks: schematic wall wire {} skipped: {}", where, String.valueOf(t));
    }

    @Inject(method = "fillFromWorld(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Vec3i;ZLnet/minecraft/world/level/block/Block;)V", at = @At("TAIL"))
    private void voltworks$capture(Level level, BlockPos origin, Vec3i size, boolean withEntities, Block toIgnore, CallbackInfo ci) {
        try {
            if(level instanceof ServerLevel server)
                voltworks$walls = SchematicWires.capture(server, origin, size);
            else if(level.isClientSide())
                voltworks$walls = io.github.sjzeero.voltworks.wallwire.client.SchematicWiresClient.capture(origin, size);
            else
                voltworks$walls = null;
        } catch(Throwable t) {
            voltworks$walls = null;
            voltworks$warn("capture", t);
        }
    }

    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"))
    private void voltworks$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        try {
            ListTag walls = voltworks$walls;
            if(walls != null && !walls.isEmpty())
                tag.put(SchematicWires.KEY, walls.copy());
        } catch(Throwable t) {
            voltworks$warn("save", t);
        }
    }

    @Inject(method = "load(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void voltworks$load(HolderGetter<Block> blocks, CompoundTag tag, CallbackInfo ci) {
        try {
            voltworks$walls = tag.contains(SchematicWires.KEY) ? tag.getList(SchematicWires.KEY, Tag.TAG_COMPOUND) : null;
        } catch(Throwable t) {
            voltworks$walls = null;
            voltworks$warn("load", t);
        }
    }

    @Inject(method = "placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z", at = @At("RETURN"))
    private void voltworks$place(ServerLevelAccessor level, BlockPos pos, BlockPos pivot, StructurePlaceSettings settings, RandomSource random, int flags, CallbackInfoReturnable<Boolean> cir) {
        try {
            ListTag walls = voltworks$walls;
            if(Boolean.TRUE.equals(cir.getReturnValue()) && walls != null && !walls.isEmpty()) {
                if(level instanceof ServerLevel server)
                    SchematicWires.place(server, walls, pos, settings);
                else if(level instanceof net.createmod.catnip.levelWrappers.SchematicLevel sl) {
                    SchematicWires.placePreview(level, walls, pos, settings);
                    try {
                        Object real = sl.getLevel();
                        if(real instanceof ServerLevel serverReal)
                            SchematicWires.addPending(serverReal, walls, pos, settings);
                    } catch(Throwable ignored) {
                    }
                }
            }
        } catch(Throwable t) {
            voltworks$warn("place", t);
        }
    }
}
