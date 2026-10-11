package io.github.sjzeero.voltworks.motor;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.simibubi.create.foundation.block.IBE;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 */
public class ConstantSpeedMotorBlock extends AbstractPGMotorBlock implements IBE<ConstantSpeedMotorBlockEntity> {
    public ConstantSpeedMotorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public SimulatedDeviceType<PGMotorDevice> getDevice() {
        return Voltworks.CONSTANT_SPEED_MOTOR_DEVICE.get();
    }

    @Override
    public Class<ConstantSpeedMotorBlockEntity> getBlockEntityClass() {
        return ConstantSpeedMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ConstantSpeedMotorBlockEntity> getBlockEntityType() {
        return Voltworks.CONSTANT_SPEED_MOTOR_BE.get();
    }
}
