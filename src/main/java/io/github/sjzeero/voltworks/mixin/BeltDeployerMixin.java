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
package io.github.sjzeero.voltworks.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import io.github.sjzeero.voltworks.circuits.CircuitTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import io.github.sjzeero.voltworks.Voltworks;

@Mixin(value = BeltDeployerCallbacks.class, remap = false)
public class BeltDeployerMixin {
    @Inject(method = "activate",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/deployer/DeployerBlockEntity;award(Lcom/simibubi/create/foundation/advancement/CreateAdvancement;)V"))
    private static void voltworks$activateMixin(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity blockEntity, Recipe<?> recipe, CallbackInfo ci, @Local List<TransportedItemStack> collect) {
        var player = blockEntity.getPlayer();
        var stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if(stack.is(CircuitTags.CIRCUIT_SCHEMATIC_HOLDER)) {
            if(!stack.has(DataComponents.CUSTOM_DATA))
                return;
            var schematic = stack.get(DataComponents.CUSTOM_DATA).copyTag().get("Schematic");
            if(schematic == null)
                return;
            for(var item : collect) {
                if(!item.stack.is(CircuitTags.CIRCUIT_SCHEMATIC_HOLDER))
                    continue;
                var tag = item.stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.put("Schematic", schematic.copy());
                item.stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }
    }
}
