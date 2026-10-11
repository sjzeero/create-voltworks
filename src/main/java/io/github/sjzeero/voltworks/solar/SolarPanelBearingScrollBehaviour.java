package io.github.sjzeero.voltworks.solar;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0) to Voltworks by sjzeero.
 * Side slider choosing how the panels are wired: strings in parallel x panels per string.
 */
public class SolarPanelBearingScrollBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<SolarPanelBearingScrollBehaviour> TYPE = new BehaviourType<>();
    private List<Integer> divisors = new ArrayList<>(List.of(1));
    private int panelCount = 1;

    public SolarPanelBearingScrollBehaviour(SmartBlockEntity be) {
        super(Component.translatable("voltworks.gui.solar_panel_bearing.slider"), be, new BearingFaceBox());
        between(0, 0);
        value = 0;
        withFormatter(this::formatIndex);
    }

    public int getPanelCount() {
        return panelCount;
    }

    public void refreshDivisors(int count) {
        panelCount = count;
        divisors = new ArrayList<>();
        for (int i = 1; i <= 9; i++) if (count % i == 0 && count / i <= 25) divisors.add(i);
        int max = Math.max(0, divisors.size() - 1);
        between(0, max);
        if (value > max) value = max;
    }

    public int getDivisor() {
        if (divisors.isEmpty()) return 1;
        return divisors.get(Math.max(0, Math.min(value, divisors.size() - 1)));
    }

    public void setByDivisor(int saved) {
        int i = divisors.indexOf(saved);
        value = Math.max(i, 0);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        DivisorOption[] options = new DivisorOption[divisors.size()];
        for (int i = 0; i < options.length; i++) options[i] = new DivisorOption(formatIndex(i));
        return new ValueSettingsBoard(label, Math.max(0, options.length - 1), 1, ImmutableList.of(Component.empty()),
                new ValueSettingsFormatter.ScrollOptionSettingsFormatter(options));
    }

    private String formatIndex(int idx) {
        if (divisors.isEmpty() || panelCount == 0) return "1 × 1";
        int d = divisors.get(Math.max(0, Math.min(idx, divisors.size() - 1)));
        return d + " × " + panelCount / d;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    static class BearingFaceBox extends ValueBoxTransform.Sided {
        @Override
        public float getScale() {
            return 0.4f;
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            if (!state.hasProperty(SolarPanelBearingBlock.FACING)) return false;
            Direction facing = state.getValue(SolarPanelBearingBlock.FACING);
            if (facing.getAxis() == Direction.Axis.Y) return getSide() == Direction.EAST || getSide() == Direction.WEST;
            return getSide() == facing.getClockWise() || getSide() == facing.getCounterClockWise();
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction side = getSide();
            Direction facing = state.getValue(SolarPanelBearingBlock.FACING);
            float roll = 0;
            for (Pointing p : Pointing.values()) if (p.getCombinedDirection(facing) == side) roll = p.getXRotation();
            if (facing == Direction.UP) roll += 180;
            Vec3 local = VecHelper.voxelSpace(8, 14.5, 9);
            local = VecHelper.rotateCentered(local, roll, Direction.Axis.Z);
            local = VecHelper.rotateCentered(local, AngleHelper.horizontalAngle(facing), Direction.Axis.Y);
            return VecHelper.rotateCentered(local, AngleHelper.verticalAngle(facing), Direction.Axis.X);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction facing = state.getValue(SolarPanelBearingBlock.FACING);
            if (facing.getAxis() == Direction.Axis.Y) {
                super.rotate(level, pos, state, ms);
                return;
            }
            float roll = 0;
            for (Pointing p : Pointing.values()) if (p.getCombinedDirection(facing) == getSide()) roll = p.getXRotation();
            float yRot = AngleHelper.horizontalAngle(facing) + (facing == Direction.DOWN ? 180 : 0);
            TransformStack.of(ms).rotateYDegrees(yRot).rotateYDegrees(roll);
        }

        @Override
        protected Vec3 getSouthLocation() {
            return Vec3.ZERO;
        }
    }

    private record DivisorOption(String label) implements INamedIconOptions {
        @Override
        public AllIcons getIcon() {
            return AllIcons.I_NONE;
        }

        @Override
        public String getTranslationKey() {
            return label;
        }
    }
}
