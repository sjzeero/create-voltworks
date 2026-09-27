package io.github.sjzeero.eecircuits.circuits;

import io.github.sjzeero.eecircuits.EECircuits;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class CircuitTags {
    public static final TagKey<Item> CIRCUIT_SCHEMATIC_HOLDER = TagKey.create(Registries.ITEM, EECircuits.asResource("circuit_schematic_holder"));
    public static final TagKey<Item> CIRCUIT_COMPONENT = TagKey.create(Registries.ITEM, EECircuits.asResource("circuit_component"));
}
