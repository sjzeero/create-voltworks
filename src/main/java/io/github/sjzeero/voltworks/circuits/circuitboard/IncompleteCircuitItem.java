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
package io.github.sjzeero.voltworks.circuits.circuitboard;

import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.CircuitItems;
import io.github.sjzeero.voltworks.util.Lang;

import java.util.HashMap;
import java.util.List;
import io.github.sjzeero.voltworks.registry.VoltworksItems;

public class IncompleteCircuitItem extends Item {
    public IncompleteCircuitItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    private static CompoundTag makeAssemblyTag(HolderLookup.Provider registries, CompoundTag schematicTag) {
        var schematic = CircuitSchematic.fromNbt(registries, schematicTag);
        var componentAmounts = new HashMap<io.github.sjzeero.voltworks.circuits.components.Component, Integer>();
        int componentCount = 0;
        for(var placed : schematic.components()) {
            componentAmounts.compute(placed.component, (key, current) -> current == null ? 1 : current + 1);
            ++componentCount;
        }
        var componentTag = new CompoundTag();
        componentAmounts.forEach((component, count) -> {
            var id = ComponentRegistry.getId(component);
            componentTag.putInt(id.toString(), count);
        });

        var assemblyTag = new CompoundTag();
        assemblyTag.put("Missing", componentTag);
        assemblyTag.putInt("Inserted", 0);
        assemblyTag.putInt("Total", componentCount);
        return assemblyTag;
    }

    private static boolean insertComponent(Level level, CompoundTag assemblyTag, ItemStack component) {
        var missingComponents = assemblyTag.getCompound("Missing");
        var location = ComponentRegistry.getComponentId(level, component);
        if(location == null)
            return false;
        var id = location.toString();
        if(!missingComponents.contains(id))
            return false;
        int missingAmount = missingComponents.getInt(id);
        if(--missingAmount <= 0) {
            missingComponents.remove(id);
        } else {
            missingComponents.putInt(id, missingAmount);
        }
        assemblyTag.putInt("Inserted", assemblyTag.getInt("Inserted") + 1);
        return true;
    }

    @Nullable
    public static ItemStack insert(Level level, ItemStack circuit, ItemStack component) {
        if(!circuit.is(CircuitItems.INCOMPLETE_CIRCUIT.get()) || !circuit.has(DataComponents.CUSTOM_DATA))
            return null;
        var tag = circuit.get(DataComponents.CUSTOM_DATA).copyTag();
        if(!tag.contains("Assembly")) {
            tag.put("Assembly", makeAssemblyTag(level.registryAccess(), tag.getCompound("Schematic")));
        }
        if(!insertComponent(level, tag.getCompound("Assembly"), component))
            return null;
        var missing = tag.getCompound("Assembly").getCompound("Missing");
        ItemStack newStack;
        if(missing.isEmpty()) {
            tag.remove("Assembly");
            newStack = new ItemStack(VoltworksItems.BOARD_ITEM.get());
        } else {
            newStack = new ItemStack(CircuitItems.INCOMPLETE_CIRCUIT.get());
        }
        newStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return newStack;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if(action != ClickAction.SECONDARY || other.isEmpty() || !slot.allowModification(player))
            return false;
        var result = insert(player.level(), stack, other);
        if(result == null)
            return false;
        other.shrink(1);
        slot.set(result);
        return true;
    }

    public static float getProgress(ItemStack stack) {
        if(!stack.has(DataComponents.CUSTOM_DATA) || !stack.get(DataComponents.CUSTOM_DATA).contains("Assembly"))
            return 0;
        var assemblyTag = stack.get(DataComponents.CUSTOM_DATA).getUnsafe().getCompound("Assembly");
        return (float) assemblyTag.getInt("Inserted") / assemblyTag.getInt("Total");
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * getProgress(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Color.mixColors(0xFFFFC074, 0xFF46FFE0, getProgress(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        if(!stack.has(DataComponents.CUSTOM_DATA))
            return;
        CompoundTag assemblyTag;
        if(!stack.get(DataComponents.CUSTOM_DATA).contains("Assembly")) {
            if(!stack.get(DataComponents.CUSTOM_DATA).contains("Schematic"))
                return;
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            assemblyTag = makeAssemblyTag(level.registryAccess(), stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Schematic"));
        } else {
            assemblyTag = stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Assembly");
        }
        tooltip.add(Component.empty());
        tooltip.add(Lang.translate("tooltip.circuit_assembly")
                .style(ChatFormatting.GRAY)
                .component());

        var inserted = assemblyTag.getInt("Inserted");
        var total = assemblyTag.getInt("Total");
        tooltip.add(Lang.translate("tooltip.circuit_assembly.progress")
                .add(Component.literal(String.format(": %d/%d", inserted, total)))
                .style(ChatFormatting.DARK_GRAY)
                .component());
        var missing = assemblyTag.getCompound("Missing");
        int index = 0;
        for(var componentId : missing.getAllKeys()) {
            var component = ComponentRegistry.get(ResourceLocation.parse(componentId));
            var item = ComponentRegistry.getItem(component);
            var key = item.getDescriptionId();
            var line = switch(index) {
                case 0 -> Lang.translate("tooltip.circuit_assembly.insert")
                        .add(Component.literal(" "))
                        .add(Component.translatable(key))
                        .style(ChatFormatting.AQUA)
                        .component();
                case 1 -> Lang.text("-> ")
                        .add(Lang.translate("tooltip.circuit_assembly.insert"))
                        .add(Component.literal(" "))
                        .add(Component.translatable(key))
                        .style(ChatFormatting.DARK_AQUA)
                        .component();
                default -> throw new IllegalStateException();
            };
            tooltip.add(line);
            if(++index >= 2)
                break;
        }
    }
}
