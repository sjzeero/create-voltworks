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
package io.github.sjzeero.eecircuits.circuits.components;

import io.github.sjzeero.eecircuits.EECircuits;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ComponentRegistry {
    public static final ResourceKey<Registry<Component>> REGISTRY_KEY = ResourceKey.createRegistryKey(EECircuits.asResource("components"));
    public static final Registry<Component> REGISTRY = new RegistryBuilder<>(REGISTRY_KEY).sync(true).create();

    private static final Map<ResourceLocation, Supplier<? extends Item>> ITEMS = new HashMap<>();

    public static void bindItem(ResourceLocation component, Supplier<? extends Item> item) {
        ITEMS.put(component, item);
    }

    public static Iterable<Component> entries() {
        return REGISTRY;
    }

    @Contract("_, null -> null")
    public static Item getItem(@Nullable Level level, Component component) {
        return getItem(component);
    }

    @Contract("null -> null")
    public static Item getItem(Component component) {
        if(component == null)
            return null;
        var item = ITEMS.get(getId(component));
        return item == null ? null : item.get();
    }

    public static ResourceLocation getComponentId(@Nullable Level level, ItemStack stack) {
        for(var entry : ITEMS.entrySet()) {
            if(stack.is(entry.getValue().get()))
                return entry.getKey();
        }
        return null;
    }

    public static Component getComponent(Item item) {
        for(var entry : ITEMS.entrySet()) {
            if(entry.getValue().get() == item)
                return get(entry.getKey());
        }
        return null;
    }

    @NotNull
    public static ResourceLocation getId(@NotNull Component component) {
        var id = REGISTRY.getKey(component);
        if(id == null)
            throw new IllegalArgumentException("Unregistered component " + component);
        return id;
    }

    public static Component get(@NotNull ResourceLocation id) {
        if("powergrid".equals(id.getNamespace()))
            id = EECircuits.asResource("electron_tube".equals(id.getPath()) ? "triode" : id.getPath());
        var component = REGISTRY.get(id);
        if(component == null)
            throw new IllegalArgumentException("Unknown component " + id);
        return component;
    }
}
