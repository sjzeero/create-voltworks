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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.fluid;

import com.simibubi.create.AllFluids;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public class AcidFluidType extends AllFluids.TintedFluidType {
    public static final ResourceKey<DamageType> ACID_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Voltworks.asResource("acid"));

    public AcidFluidType(Properties properties) {
        super(properties, Voltworks.asResource("block/acid_still"), Voltworks.asResource("block/acid_flow"));
    }

    @Override
    protected int getTintColor(FluidStack stack) {
        return 0xFFFFFFFF;
    }

    @Override
    protected int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return 0xFFFFFFFF;
    }

    @Override
    public boolean move(FluidState state, LivingEntity entity, Vec3 movementVector, double gravity) {
        if (entity.level().random.nextInt(15) >= 10) {
            entity.hurt(damageSource(entity.level()), 2);
        }
        return false;
    }

    private static DamageSource damageSource(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ACID_DAMAGE));
    }
}
