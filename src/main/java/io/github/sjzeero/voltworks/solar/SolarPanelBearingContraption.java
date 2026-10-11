package io.github.sjzeero.voltworks.solar;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0) to Voltworks by sjzeero.
 * Bearing contraption that counts the attached solar panels and remembers their common normal.
 */
public class SolarPanelBearingContraption extends BearingContraption {
    protected int panelBlocks;
    protected Vec3 panelNormal;

    public SolarPanelBearingContraption() {}

    public SolarPanelBearingContraption(Direction facing) {
        this.facing = facing;
    }

    @Override
    public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
        BlockPos offset = pos.relative(facing);
        if (!searchMovedStructure(world, offset, null)) return false;
        startMoving(world);
        expandBoundsAroundAxis(facing.getAxis());
        if (panelBlocks < 1) throw new AssemblyException(Component.translatable("voltworks.contraption.assembly.not_enough_panels"));
        for (StructureBlockInfo info : getBlocks().values()) {
            if (!(info.state().getBlock() instanceof SolarPanelBlock)) continue;
            var n = info.state().getValue(SolarPanelBlock.FACING).getOpposite().getNormal();
            Vec3 v = new Vec3(n.getX(), n.getY(), n.getZ());
            if (panelNormal == null) panelNormal = v;
            else if (!panelNormal.equals(v))
                throw new AssemblyException(Component.translatable("voltworks.contraption.assembly.mismatched_panel_facing"));
        }
        if (!hasValidDivisor(panelBlocks))
            throw new AssemblyException(Component.translatable("voltworks.contraption.assembly.invalid_panel_amount"));
        return !blocks.isEmpty();
    }

    public static boolean hasValidDivisor(int panelCount) {
        for (int i = 1; i <= 9; i++) if (panelCount % i == 0 && panelCount / i <= 25) return true;
        return false;
    }

    @Override
    public void addBlock(Level level, BlockPos pos, Pair<StructureBlockInfo, BlockEntity> capture) {
        BlockPos local = pos.subtract(anchor);
        if (!getBlocks().containsKey(local) && capture.getLeft().state().getBlock() instanceof SolarPanelBlock) panelBlocks++;
        super.addBlock(level, pos, capture);
    }

    @Override
    public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
        CompoundTag tag = super.writeNBT(registries, spawnPacket);
        tag.putInt("Panels", panelBlocks);
        if (panelNormal != null) {
            tag.putDouble("normalX", panelNormal.x);
            tag.putDouble("normalY", panelNormal.y);
            tag.putDouble("normalZ", panelNormal.z);
        }
        return tag;
    }

    @Override
    public void readNBT(Level world, CompoundTag tag, boolean spawnData) {
        panelBlocks = tag.getInt("Panels");
        panelNormal = tag.contains("normalX") ? new Vec3(tag.getDouble("normalX"), tag.getDouble("normalY"), tag.getDouble("normalZ")) : null;
        super.readNBT(world, tag, spawnData);
    }

    @Override
    public ContraptionType getType() {
        return Voltworks.SOLAR_PANEL_BEARING_CONTRAPTION.get();
    }

    public int getPanelBlocks() {
        return panelBlocks;
    }

    public Vec3 getPanelNormal() {
        return panelNormal;
    }
}
