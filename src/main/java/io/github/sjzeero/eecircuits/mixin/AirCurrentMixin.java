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
 * Modified by sjzeero: ported from Create: Power Grid to EE Circuits.
 */
package io.github.sjzeero.eecircuits.mixin;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import io.github.sjzeero.eecircuits.EECircuitsConfig;
import io.github.sjzeero.eecircuits.board.BoardDevice;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = AirCurrent.class, remap = false)
public abstract class AirCurrentMixin {
    @Shadow @Final public IAirCurrentSource source;
    @Shadow public Direction direction;
    @Shadow protected abstract int getLimit();

    @Unique
    private final List<BoardDevice> eecircuits$affectedBoards = new ArrayList<>();

    @Inject(method = "rebuild()V", at = @At("HEAD"))
    private void eecircuits$rebuildHead(CallbackInfo ci) {
        eecircuits$affectedBoards.forEach(board -> board.removeCoolingMultiplier((AirCurrent) (Object) this));
        eecircuits$affectedBoards.clear();
    }

    @Inject(method = "rebuild()V", at = @At("TAIL"))
    private void eecircuits$rebuildTail(CallbackInfo ci) {
        if(!(source.getAirCurrentWorld() instanceof ServerLevel world) || direction == null)
            return;
        var start = source.getAirCurrentPos();
        int limit = getLimit();
        float initialStrength = Math.abs(source.getSpeed()) * EECircuitsConfig.fanCoolingStrength();
        if(initialStrength == 0)
            return;
        var devices = DevicesSavedData.load(world);
        for(int i = 1; i <= limit; ++i) {
            var pos = start.relative(direction, i);
            float factor = 1.0f - (float) (i - 1) / limit;
            var board = devices.getDevice(pos, BoardDevice.class);
            if(board != null) {
                board.addCoolingMultiplier((AirCurrent) (Object) this, factor * initialStrength);
                eecircuits$affectedBoards.add(board);
            }
        }
    }
}
