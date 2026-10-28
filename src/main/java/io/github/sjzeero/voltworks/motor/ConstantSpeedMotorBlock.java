/*
 * Copyright 2025-2026 patryk3211
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
package io.github.sjzeero.voltworks.motor;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.simibubi.create.foundation.block.IBE;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;

public class ConstantSpeedMotorBlock extends AbstractPGMotorBlock implements IBE<ConstantSpeedMotorBlockEntity> {
    public ConstantSpeedMotorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public SimulatedDeviceType<PGMotorDevice> getDevice() {
        return VoltworksDevices.CONSTANT_SPEED_MOTOR_DEVICE.get();
    }

    @Override
    public Class<ConstantSpeedMotorBlockEntity> getBlockEntityClass() {
        return ConstantSpeedMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ConstantSpeedMotorBlockEntity> getBlockEntityType() {
        return VoltworksBlockEntities.CONSTANT_SPEED_MOTOR_BE.get();
    }
}
